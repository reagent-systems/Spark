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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalCoroutinesApi::class)

class LLMRepositoryImpl(
    private val context: Context,
    private val huggingFaceAuth: HuggingFaceAuth
) : LLMRepository {
    
    companion object {
        private const val TAG = "LLMRepositoryImpl"
    }
    
    // Use dedicated dispatchers for different types of operations to prevent blocking
    private val modelLoadingDispatcher = Dispatchers.IO.limitedParallelism(1) // Single thread for MediaPipe model loading
    private val fileOperationsDispatcher = Dispatchers.IO.limitedParallelism(2) // Limited threads for file ops
    private val inferenceDispatcher = Dispatchers.Default // Use Default for CPU-intensive inference
    
    private val loadedModels = ConcurrentHashMap<String, LlmInference>()
    private val availableModels = mutableListOf<LLMModel>()
    private val chatSessions = mutableListOf<ChatSession>()
    private val modelMutex = Mutex()
    
    // Background scope for non-UI operations
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Delegate managers
    private val persistenceManager = ModelPersistenceManager(context)
    private val downloadManager = ModelDownloadManager(context, huggingFaceAuth)
    private val fileManager = ModelFileManager(context)
    
    init {
        // Load persisted models synchronously for immediate availability
        loadPersistedModels()
        loadPersistedChatSessions()
        
        // Initialize default models asynchronously in background scope
        backgroundScope.launch {
            try {
                initializeDefaultModels()
            } catch (e: Exception) {
                Log.e(TAG, "Error during default model initialization", e)
            }
        }
    }
    
    private suspend fun initializeDefaultModels() = withContext(fileOperationsDispatcher) {
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
    
    // MediaPipe model loading - this is the ONLY place where MediaPipe operations should happen
    override suspend fun loadModel(modelId: String, config: ModelConfig): Result<Unit> = withContext(modelLoadingDispatcher) {
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
                val actualFilePath = withContext(fileOperationsDispatcher) {
                    fileManager.getActualFilePath(model.filePath, model.name)
                }
                Log.d(TAG, "Actual file path: $actualFilePath")
                
                Log.d(TAG, "Creating MediaPipe LlmInference with options...")
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(actualFilePath)
                    .setMaxTokens(model.maxTokens)
                    .setMaxTopK(model.topK)
                    .build()
                
                Log.d(TAG, "Creating LlmInference from options (this is the heavy MediaPipe operation)...")
                // This is the ONLY heavy MediaPipe operation - properly isolated on dedicated dispatcher
                val llmInference = LlmInference.createFromOptions(context, options)
                loadedModels[modelId] = llmInference
                
                // Update model status
                val index = availableModels.indexOfFirst { it.id == modelId }
                if (index != -1) {
                    availableModels[index] = availableModels[index].copy(isLoaded = true)
                }
                
                // Save to persistence in background to avoid blocking
                backgroundScope.launch {
                    savePersistedModels()
                }
                
                Log.d(TAG, "Successfully loaded model: ${model.name}")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: $modelId", e)
            Result.failure(e)
        }
    }
    
    override suspend fun unloadModel(modelId: String): Result<Unit> = withContext(modelLoadingDispatcher) {
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
                
                // Save to persistence in background to avoid blocking
                backgroundScope.launch {
                    savePersistedModels()
                }
                
                Log.d(TAG, "Successfully unloaded model: ${model.name}")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unload model: $modelId", e)
            Result.failure(e)
        }
    }
    
    // Smart response generation - choose streaming or non-streaming based on config
    override suspend fun generateResponse(
        modelId: String,
        prompt: String,
        config: ModelConfig
    ): Flow<String> = 
        if (config.enableStreaming) {
            generateResponseStream(modelId, prompt, config)
        } else {
            flow {
                val llmInference = loadedModels[modelId]
                    ?: throw Exception("Model not loaded")
                
                try {
                    // Use inference dispatcher for CPU-intensive text generation
                    val result = withContext(inferenceDispatcher) {
                        ensureActive() // Check for cancellation
                        llmInference.generateResponse(prompt)
                    }
                    
                    // Emit the complete response (no streaming)
                    emit(result)
                } catch (e: Exception) {
                    throw e
                }
            }
        }
    
    override suspend fun generateResponseSync(
        modelId: String,
        prompt: String,
        config: ModelConfig
    ): Result<String> = withContext(inferenceDispatcher) {
        return@withContext try {
            val llmInference = loadedModels[modelId]
                ?: return@withContext Result.failure(Exception("Model not loaded"))
            
            ensureActive() // Check for cancellation
            val result = llmInference.generateResponse(prompt)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun generateResponseStream(
        modelId: String,
        prompt: String,
        config: ModelConfig
    ): Flow<String> = callbackFlow {
        val llmInference = loadedModels[modelId]
            ?: throw Exception("Model not loaded: $modelId")
        
        try {
            Log.d(TAG, "Starting real streaming for model: $modelId")
            
            // StringBuilder to accumulate tokens
            val accumulatedResponse = StringBuilder()
            
            withContext(inferenceDispatcher) {
                ensureActive() // Check for cancellation
                
                // Use MediaPipe's true streaming with generateResponseAsync
                llmInference.generateResponseAsync(prompt) { partialResult, done ->
                    try {
                        // MediaPipe sends individual tokens, not cumulative text
                        // We need to accumulate them ourselves
                        accumulatedResponse.append(partialResult)
                        val cumulativeText = accumulatedResponse.toString()
                        
                        // Debug logging
                        Log.d(TAG, "MediaPipe callback: token='$partialResult', accumulated_length=${cumulativeText.length}, done=$done")
                        
                        // Send the cumulative response through the channel
                        trySend(cumulativeText).isSuccess
                        
                        if (done) {
                            Log.d(TAG, "Streaming completed for model: $modelId, final length: ${cumulativeText.length}")
                            close() // Close the channel when done
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in streaming callback", e)
                        close(e) // Close with error
                    }
                }
            }
            
            // Wait for the channel to be closed
            awaitClose {
                Log.d(TAG, "Streaming channel closed for model: $modelId")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in generateResponseStream", e)
            close(e)
        }
    }

    
    override suspend fun addModel(
        filePath: String,
        name: String,
        description: String
    ): Result<LLMModel> = withContext(fileOperationsDispatcher) {
        Log.d(TAG, "Adding model: $name, path: $filePath")
        return@withContext try {
            // Get file size
            val fileSize = fileManager.getFileSize(filePath)
            
            // Handle file path validation for regular files
            if (!filePath.startsWith("content://") && !fileManager.fileExists(filePath)) {
                Log.e(TAG, "Model file does not exist: $filePath")
                return@withContext Result.failure(Exception("Model file does not exist"))
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
            
            // Save to persistence in background to avoid blocking
            backgroundScope.launch {
                savePersistedModels()
            }
            
            Log.d(TAG, "Successfully added model: $name with ID: ${model.id}")
            Result.success(model)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add model: $name", e)
            Result.failure(e)
        }
    }
    
    override suspend fun removeModel(modelId: String): Result<Unit> = withContext(fileOperationsDispatcher) {
        return@withContext try {
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
    
    override suspend fun createChatSession(name: String, modelId: String): ChatSession = withContext(fileOperationsDispatcher) {
        return@withContext createChatSession(name, modelId, "")
    }
    
    override suspend fun createChatSession(name: String, modelId: String, systemPrompt: String): ChatSession = withContext(fileOperationsDispatcher) {
        val session = ChatSession(
            id = UUID.randomUUID().toString(),
            name = name,
            messages = emptyList(),
            modelId = modelId,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            systemPrompt = systemPrompt
        )
        chatSessions.add(session)
        
        // Save to persistence in background to avoid blocking
        backgroundScope.launch {
            savePersistedChatSessions()
        }
        
        return@withContext session
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
    ): Result<Unit> = withContext(fileOperationsDispatcher) {
        return@withContext try {
            val index = chatSessions.indexOfFirst { it.id == sessionId }
            if (index != -1) {
                val session = chatSessions[index]
                val updatedSession = session.copy(
                    messages = session.messages + message,
                    updatedAt = System.currentTimeMillis()
                )
                chatSessions[index] = updatedSession
                
                // Save to persistence in background to avoid blocking
                backgroundScope.launch {
                    savePersistedChatSessions()
                }
                
                Result.success(Unit)
            } else {
                Result.failure(Exception("Chat session not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun updateChatSession(session: ChatSession): Result<Unit> = withContext(fileOperationsDispatcher) {
        return@withContext try {
            val index = chatSessions.indexOfFirst { it.id == session.id }
            if (index != -1) {
                chatSessions[index] = session
                
                // Save to persistence in background to avoid blocking
                backgroundScope.launch {
                    savePersistedChatSessions()
                }
                
                Result.success(Unit)
            } else {
                Result.failure(Exception("Chat session not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun deleteChatSession(sessionId: String): Result<Unit> = withContext(fileOperationsDispatcher) {
        return@withContext try {
            chatSessions.removeIf { it.id == sessionId }
            
            // Save to persistence in background to avoid blocking
            backgroundScope.launch {
                savePersistedChatSessions()
            }
            
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
                    
                    // Save to persistence in background to avoid blocking
                    backgroundScope.launch {
                        savePersistedModels()
                    }
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
    
    override suspend fun downloadModelFromUrl(
        url: String,
        name: String,
        description: String,
        onProgress: (Float) -> Unit
    ): Result<LLMModel> = withContext(fileOperationsDispatcher) {
        Log.d(TAG, "Starting URL download: $name from $url")
        
        try {
            // Create a unique ID from the URL
            val modelId = url.substringAfterLast("/").substringBeforeLast(".")
                .replace("-", "_")
                .replace(" ", "_")
                .lowercase()
            
            // Check if model already exists
            val existingModel = availableModels.find { it.id == modelId }
            if (existingModel != null) {
                Log.d(TAG, "Model already exists: $modelId")
                return@withContext Result.success(existingModel)
            }
            
            // Determine if this is a HuggingFace URL that might need authentication
            val needsAuth = url.contains("huggingface.co") && 
                           (url.contains("gated") || url.contains("private") || name.lowercase().contains("gemma"))
            
            // Create an AvailableModel from the URL
            val availableModel = AvailableModel(
                id = modelId,
                name = name,
                description = description,
                author = if (url.contains("huggingface.co")) {
                    // Extract author from HuggingFace URL
                    val pathParts = url.substringAfter("huggingface.co/").split("/")
                    if (pathParts.size >= 2) pathParts[0] else "Unknown"
                } else "Custom",
                downloadUrl = url,
                size = "Unknown", // We'll get the actual size during download
                modelType = ModelType.TEXT_ONLY, // Default to text, could be enhanced to detect multimodal
                quantization = if (url.contains("q8")) "Q8" else if (url.contains("q4")) "Q4" else "Unknown",
                contextLength = if (url.contains("seq128")) 128 else 0,
                tags = listOf("Custom", "URL Download"),
                needsHuggingFaceAuth = needsAuth,
                requirements = ModelRequirements(
                    minRam = "Unknown",
                    recommendedRam = "Unknown"
                )
            )
            
            // Use the existing download infrastructure
            val result = downloadManager.downloadModel(availableModel, onProgress)
            
            return@withContext result.fold(
                onSuccess = { model ->
                    // Add to available models list
                    availableModels.add(model)
                    
                    // Save to persistence in background to avoid blocking
                    backgroundScope.launch {
                        savePersistedModels()
                    }
                    
                    Log.d(TAG, "Successfully downloaded model from URL: ${model.name}")
                    Result.success(model)
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to download model from URL: $url", error)
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading model from URL: $url", e)
            Result.failure(e)
        }
    }
    
    override suspend fun deleteModel(modelId: String): Result<Unit> = withContext(fileOperationsDispatcher) {
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
                
                // Save to persistence in background to avoid blocking
                backgroundScope.launch {
                    savePersistedModels()
                }
                
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
        chatSessions.addAll(sessions)
    }
    
    private fun savePersistedChatSessions() {
        persistenceManager.savePersistedChatSessions(chatSessions.toList())
    }
} 