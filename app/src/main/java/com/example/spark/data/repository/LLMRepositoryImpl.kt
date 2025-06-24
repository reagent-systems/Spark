package com.example.spark.data.repository

import android.content.Context
import android.util.Log
import com.example.spark.domain.models.*
import com.example.spark.domain.repository.LLMRepository
import com.example.spark.utils.HuggingFaceAuth
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class LLMRepositoryImpl(
    private val context: Context,
    private val huggingFaceAuth: HuggingFaceAuth
) : LLMRepository {
    
    companion object {
        private const val TAG = "LLMRepositoryImpl"
    }
    
    private val loadedModels = ConcurrentHashMap<String, LlmInference>()
    private val availableModels = mutableListOf<LLMModel>()
    private val chatSessions = mutableListOf<ChatSession>()
    private val modelMutex = Mutex()
    
    // Delegate managers
    private val persistenceManager = ModelPersistenceManager(context)
    private val downloadManager = ModelDownloadManager(context, huggingFaceAuth)
    private val fileManager = ModelFileManager(context)
    
    init {
        // Load persisted models first, then initialize any new models found
        loadPersistedModels()
        initializeDefaultModels()
        // Load persisted chat sessions
        loadPersistedChatSessions()
    }
    
    private fun initializeDefaultModels() {
        val foundModels = persistenceManager.initializeDefaultModels()
        foundModels.forEach { model ->
            // Check if this model is already in our list (from persistence)
            val existingModel = availableModels.find { it.filePath == model.filePath }
            if (existingModel == null) {
                availableModels.add(model)
                Log.d(TAG, "Added new model found in directory: ${model.name}")
            }
        }
        
        // Save any new models found
        if (foundModels.isNotEmpty()) {
            savePersistedModels()
        }
    }
    
    override suspend fun getAvailableModels(): List<LLMModel> {
        return availableModels.toList()
    }
    
    override suspend fun loadModel(modelId: String, config: ModelConfig): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting to load model: $modelId")
        return@withContext try {
            modelMutex.withLock {
                val model = availableModels.find { it.id == modelId }
                if (model == null) {
                    Log.e(TAG, "Model not found: $modelId")
                    return@withLock Result.failure(Exception("Model not found"))
                }
                
                Log.d(TAG, "Found model: ${model.name} at path: ${model.filePath}")
                
                if (loadedModels.containsKey(modelId)) {
                    Log.d(TAG, "Model already loaded: $modelId")
                    return@withLock Result.success(Unit)
                }
                
                // Get the actual file path for MediaPipe
                Log.d(TAG, "Getting actual file path for model: ${model.name}")
                val actualFilePath = fileManager.getActualFilePath(model.filePath, model.name)
                Log.d(TAG, "Actual file path: $actualFilePath")
                
                Log.d(TAG, "Creating MediaPipe LlmInference with options...")
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(actualFilePath)
                    .setMaxTokens(model.maxTokens)
                    .setMaxTopK(model.topK)
                    .build()
                
                Log.d(TAG, "Creating LlmInference from options (this may take a while)...")
                // This is the heavy operation that was blocking the UI
                val llmInference = LlmInference.createFromOptions(context, options)
                loadedModels[modelId] = llmInference
                
                // Update model status
                val index = availableModels.indexOfFirst { it.id == modelId }
                if (index != -1) {
                    availableModels[index] = availableModels[index].copy(isLoaded = true)
                }
                
                savePersistedModels() // Save to persistence
                Log.d(TAG, "Successfully loaded model: ${model.name}")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: $modelId", e)
            Result.failure(e)
        }
    }
    
    override suspend fun unloadModel(modelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting to unload model: $modelId")
        return@withContext try {
            modelMutex.withLock {
                val model = availableModels.find { it.id == modelId }
                if (model == null) {
                    Log.e(TAG, "Model not found for unloading: $modelId")
                    return@withLock Result.failure(Exception("Model not found"))
                }
                
                Log.d(TAG, "Unloading model: ${model.name}")
                loadedModels[modelId]?.close()
                loadedModels.remove(modelId)
                
                // Update model status
                val index = availableModels.indexOfFirst { it.id == modelId }
                if (index != -1) {
                    availableModels[index] = availableModels[index].copy(isLoaded = false)
                    Log.d(TAG, "Updated model status to unloaded: ${model.name}")
                }
                
                savePersistedModels() // Save to persistence
                Log.d(TAG, "Successfully unloaded model: ${model.name}")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unload model: $modelId", e)
            Result.failure(e)
        }
    }
    
    override suspend fun generateResponse(
        modelId: String,
        prompt: String,
        config: ModelConfig
    ): Flow<String> = flow {
        val llmInference = loadedModels[modelId]
            ?: throw Exception("Model not loaded")
        
        try {
            // Get the complete response from MediaPipe
            val result = withContext(Dispatchers.IO) {
                llmInference.generateResponse(prompt)
            }
            
            // Replace \n with actual newlines and emit the complete response
            val formattedResult = result.replace("\n", "\n")
            emit(formattedResult)
        } catch (e: Exception) {
            throw e
        }
    }
    
    override suspend fun generateResponseSync(
        modelId: String,
        prompt: String,
        config: ModelConfig
    ): Result<String> {
        return try {
            val llmInference = loadedModels[modelId]
                ?: return Result.failure(Exception("Model not loaded"))
            
            val result = llmInference.generateResponse(prompt)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun addModel(
        filePath: String,
        name: String,
        description: String
    ): Result<LLMModel> {
        Log.d(TAG, "Adding model: $name, path: $filePath")
        return try {
            // Get file size
            val fileSize = fileManager.getFileSize(filePath)
            
            // Handle file path validation for regular files
            if (!filePath.startsWith("content://") && !fileManager.fileExists(filePath)) {
                Log.e(TAG, "Model file does not exist: $filePath")
                return Result.failure(Exception("Model file does not exist"))
            }
            
            // Get actual file path (copy content URI if needed)
            val actualFilePath = fileManager.getActualFilePath(filePath, name)
            val actualFile = File(actualFilePath)
            
            // Use filename without extension as ID for consistency
            val modelId = actualFile.nameWithoutExtension
            
            val model = LLMModel(
                id = modelId,
                name = name,
                description = description,
                filePath = actualFilePath,
                size = actualFile.length()
            )
            
            availableModels.add(model)
            savePersistedModels() // Save to persistence
            Log.d(TAG, "Successfully added model: $name with ID: ${model.id}")
            Result.success(model)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add model: $name", e)
            Result.failure(e)
        }
    }
    
    override suspend fun removeModel(modelId: String): Result<Unit> {
        return try {
            // Unload if loaded
            if (loadedModels.containsKey(modelId)) {
                unloadModel(modelId)
            }
            
            availableModels.removeIf { it.id == modelId }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getLoadedModels(): List<LLMModel> {
        return availableModels.filter { it.isLoaded }
    }
    
    override suspend fun isModelLoaded(modelId: String): Boolean {
        return loadedModels.containsKey(modelId)
    }
    
    override suspend fun createChatSession(name: String, modelId: String): ChatSession {
        val session = ChatSession(
            id = UUID.randomUUID().toString(),
            name = name,
            messages = emptyList(),
            modelId = modelId,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        chatSessions.add(session)
        savePersistedChatSessions() // Save to persistence
        return session
    }
    
    override suspend fun getChatSessions(): List<ChatSession> {
        return chatSessions.toList()
    }
    
    override suspend fun getChatSession(sessionId: String): ChatSession? {
        return chatSessions.find { it.id == sessionId }
    }
    
    override suspend fun addMessageToSession(
        sessionId: String,
        message: ChatMessage
    ): Result<Unit> {
        return try {
            val index = chatSessions.indexOfFirst { it.id == sessionId }
            if (index != -1) {
                val session = chatSessions[index]
                val updatedSession = session.copy(
                    messages = session.messages + message,
                    updatedAt = System.currentTimeMillis()
                )
                chatSessions[index] = updatedSession
                savePersistedChatSessions() // Save to persistence
                Result.success(Unit)
            } else {
                Result.failure(Exception("Chat session not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun updateChatSession(session: ChatSession): Result<Unit> {
        return try {
            val index = chatSessions.indexOfFirst { it.id == session.id }
            if (index != -1) {
                chatSessions[index] = session
                savePersistedChatSessions() // Save to persistence
                Result.success(Unit)
            } else {
                Result.failure(Exception("Chat session not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun deleteChatSession(sessionId: String): Result<Unit> {
        return try {
            chatSessions.removeIf { it.id == sessionId }
            savePersistedChatSessions() // Save to persistence
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    

    
    override suspend fun downloadModel(
        availableModel: AvailableModel,
        onProgress: (Float) -> Unit
    ): Result<LLMModel> {
        val result = downloadManager.downloadModel(availableModel, onProgress)
        
        return result.fold(
            onSuccess = { model ->
                // Check if not already in list to avoid duplicates
                if (!availableModels.any { it.id == availableModel.id }) {
                    availableModels.add(model)
                    savePersistedModels() // Save to persistence
                }
                Log.d(TAG, "Successfully downloaded and added model: ${model.name}")
                Result.success(model)
            },
            onFailure = { error ->
                Log.e(TAG, "Failed to download model: ${availableModel.name}", error)
                Result.failure(error)
            }
        )
    }
    

    
    override suspend fun deleteModel(modelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Deleting model: $modelId")
        try {
            // First unload the model if it's loaded
            if (loadedModels.containsKey(modelId)) {
                Log.d(TAG, "Unloading model before deletion: $modelId")
                unloadModel(modelId)
            }
            
            // Find the model in our available models
            val model = availableModels.find { it.id == modelId }
            if (model == null) {
                Log.w(TAG, "Model not found for deletion: $modelId")
                return@withContext Result.failure(Exception("Model not found: $modelId"))
            }
            
            // Delete the file using file manager
            val deleted = fileManager.deleteModelFile(model.filePath)
            if (deleted) {
                // Remove from available models list
                availableModels.removeAll { it.id == modelId }
                savePersistedModels() // Save to persistence
                Log.d(TAG, "Model removed from available models list: $modelId")
                Result.success(Unit)
            } else {
                Log.e(TAG, "Failed to delete model file: ${model.filePath}")
                Result.failure(Exception("Failed to delete model file"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting model: $modelId", e)
            Result.failure(e)
        }
    }
    
    override suspend fun cancelDownload(modelId: String): Result<Unit> {
        return downloadManager.cancelDownload(modelId)
    }
    
    private fun loadPersistedModels() {
        val models = persistenceManager.loadPersistedModels()
        availableModels.addAll(models)
    }
    
    private fun savePersistedModels() {
        persistenceManager.savePersistedModels(availableModels.toList())
    }
    
    private fun loadPersistedChatSessions() {
        val sessions = persistenceManager.loadPersistedChatSessions()
        chatSessions.clear()
        chatSessions.addAll(sessions)
    }
    
    private fun savePersistedChatSessions() {
        persistenceManager.savePersistedChatSessions(chatSessions.toList())
    }
} 