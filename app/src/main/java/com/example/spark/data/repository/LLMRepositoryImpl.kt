package com.example.spark.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.spark.domain.models.*
import com.example.spark.domain.repository.LLMRepository
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class LLMRepositoryImpl(
    private val context: Context
) : LLMRepository {
    
    companion object {
        private const val TAG = "LLMRepositoryImpl"
    }
    
    private val loadedModels = ConcurrentHashMap<String, LlmInference>()
    private val availableModels = mutableListOf<LLMModel>()
    private val chatSessions = mutableListOf<ChatSession>()
    private val modelMutex = Mutex()
    
    init {
        // Initialize with some default models if they exist
        initializeDefaultModels()
    }
    
    private fun initializeDefaultModels() {
        // Check for models in the default directory
        val modelsDir = File(context.filesDir, "models")
        if (modelsDir.exists()) {
            modelsDir.listFiles()?.forEach { file ->
                if (file.extension == "task") {
                    val model = LLMModel(
                        id = UUID.randomUUID().toString(),
                        name = file.nameWithoutExtension,
                        description = "Local model",
                        filePath = file.absolutePath,
                        size = file.length()
                    )
                    availableModels.add(model)
                }
            }
        }
    }
    
    override suspend fun getAvailableModels(): List<LLMModel> {
        return availableModels.toList()
    }
    
    override suspend fun loadModel(modelId: String): Result<Unit> {
        Log.d(TAG, "Starting to load model: $modelId")
        return try {
            modelMutex.withLock {
                val model = availableModels.find { it.id == modelId }
                if (model == null) {
                    Log.e(TAG, "Model not found: $modelId")
                    return Result.failure(Exception("Model not found"))
                }
                
                Log.d(TAG, "Found model: ${model.name} at path: ${model.filePath}")
                
                if (loadedModels.containsKey(modelId)) {
                    Log.d(TAG, "Model already loaded: $modelId")
                    return Result.success(Unit)
                }
                
                // Get the actual file path for MediaPipe
                Log.d(TAG, "Getting actual file path for model: ${model.name}")
                val actualFilePath = getActualFilePath(model.filePath, model.name)
                Log.d(TAG, "Actual file path: $actualFilePath")
                
                Log.d(TAG, "Creating MediaPipe LlmInference with options...")
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(actualFilePath)
                    .setMaxTokens(model.maxTokens)
                    .setMaxTopK(model.topK)
                    .build()
                
                Log.d(TAG, "Creating LlmInference from options...")
                val llmInference = LlmInference.createFromOptions(context, options)
                loadedModels[modelId] = llmInference
                
                // Update model status
                val index = availableModels.indexOfFirst { it.id == modelId }
                if (index != -1) {
                    availableModels[index] = availableModels[index].copy(isLoaded = true)
                }
                
                Log.d(TAG, "Successfully loaded model: ${model.name}")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: $modelId", e)
            Result.failure(e)
        }
    }
    
    override suspend fun unloadModel(modelId: String): Result<Unit> {
        Log.d(TAG, "Starting to unload model: $modelId")
        return try {
            modelMutex.withLock {
                val model = availableModels.find { it.id == modelId }
                if (model == null) {
                    Log.e(TAG, "Model not found for unloading: $modelId")
                    return Result.failure(Exception("Model not found"))
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
            // For now, we'll use the synchronous method and emit the complete response
            // In a real implementation, you'd want to stream partial results
            val result = llmInference.generateResponse(prompt)
            emit(result)
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
            // Handle both content URIs and regular file paths
            val fileSize = if (filePath.startsWith("content://")) {
                Log.d(TAG, "Handling content URI for model: $name")
                // For content URIs, get file size from content resolver
                try {
                    val uri = android.net.Uri.parse(filePath)
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.available().toLong()
                    } ?: 0L
                } catch (e: Exception) {
                    Log.w(TAG, "Could not determine file size for content URI", e)
                    0L // Default size if we can't determine it
                }
            } else {
                Log.d(TAG, "Handling regular file path for model: $name")
                // For regular file paths
                val file = File(filePath)
                if (!file.exists()) {
                    Log.e(TAG, "Model file does not exist: $filePath")
                    return Result.failure(Exception("Model file does not exist"))
                }
                file.length()
            }
            
            val model = LLMModel(
                id = UUID.randomUUID().toString(),
                name = name,
                description = description,
                filePath = filePath,
                size = fileSize
            )
            
            availableModels.add(model)
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
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun getActualFilePath(originalPath: String, modelName: String): String {
        return if (originalPath.startsWith("content://")) {
            Log.d(TAG, "Converting content URI to file path for: $modelName")
            // Copy content URI to internal storage
            copyContentUriToInternalStorage(originalPath, modelName)
        } else {
            Log.d(TAG, "Using existing file path for: $modelName")
            // Already a regular file path
            originalPath
        }
    }
    
    private suspend fun copyContentUriToInternalStorage(contentUri: String, modelName: String): String {
        Log.d(TAG, "Copying content URI to internal storage: $contentUri")
        val uri = Uri.parse(contentUri)
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) {
            Log.d(TAG, "Creating models directory: ${modelsDir.absolutePath}")
            modelsDir.mkdirs()
        }
        
        val fileName = "${modelName.replace(" ", "_")}.task"
        val targetFile = File(modelsDir, fileName)
        Log.d(TAG, "Target file path: ${targetFile.absolutePath}")
        
        // If file already exists, return its path
        if (targetFile.exists()) {
            Log.d(TAG, "File already exists, using existing copy: ${targetFile.absolutePath}")
            return targetFile.absolutePath
        }
        
        Log.d(TAG, "Starting file copy operation...")
        // Copy the file
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            targetFile.outputStream().use { outputStream ->
                val bytesCopied = inputStream.copyTo(outputStream)
                Log.d(TAG, "Successfully copied $bytesCopied bytes to: ${targetFile.absolutePath}")
            }
        } ?: throw Exception("Could not open input stream for content URI")
        
        Log.d(TAG, "File copy completed: ${targetFile.absolutePath}")
        return targetFile.absolutePath
    }
} 