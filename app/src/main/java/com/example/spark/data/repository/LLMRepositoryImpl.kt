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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URL
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
    
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    
    init {
        // Load persisted models first, then initialize any new models found
        loadPersistedModels()
        initializeDefaultModels()
        // Load persisted chat sessions
        loadPersistedChatSessions()
    }
    
    private fun initializeDefaultModels() {
        // Check for models in the default directory that aren't already loaded from persistence
        val modelsDir = File(context.filesDir, "models")
        if (modelsDir.exists()) {
            modelsDir.listFiles()?.forEach { file ->
                if (file.extension == "task") {
                    // Check if this model is already in our list (from persistence)
                    val existingModel = availableModels.find { it.filePath == file.absolutePath }
                    if (existingModel == null) {
                        // Use filename without extension as ID for consistency
                        val modelId = file.nameWithoutExtension
                        val model = LLMModel(
                            id = modelId,
                            name = file.nameWithoutExtension.replace("_", " ").replace("-", " "),
                            description = "Local model",
                            filePath = file.absolutePath,
                            size = file.length()
                        )
                        availableModels.add(model)
                        Log.d(TAG, "Added new model found in directory: ${model.name}")
                    }
                }
            }
            // Save any new models found
            if (availableModels.isNotEmpty()) {
                savePersistedModels()
            }
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
                val actualFilePath = getActualFilePath(model.filePath, model.name)
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
            
            // Get actual file path (copy content URI if needed)
            val actualFilePath = getActualFilePath(filePath, name)
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
    
    override suspend fun downloadModel(
        availableModel: AvailableModel,
        onProgress: (Float) -> Unit
    ): Result<LLMModel> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting download for model: ${availableModel.name}")
        try {
            // Create models directory if it doesn't exist
            val modelsDir = File(context.filesDir, "models")
            if (!modelsDir.exists()) {
                Log.d(TAG, "Creating models directory: ${modelsDir.absolutePath}")
                modelsDir.mkdirs()
            }
            
            // Create target file
            val fileName = "${availableModel.id}.task"
            val targetFile = File(modelsDir, fileName)
            
            // Check if model already exists
            if (targetFile.exists()) {
                Log.d(TAG, "Model already exists: ${targetFile.absolutePath}")
                val existingModel = LLMModel(
                    id = availableModel.id, // Use the same ID as AvailableModel
                    name = availableModel.name,
                    description = availableModel.description,
                    filePath = targetFile.absolutePath,
                    size = targetFile.length(),
                    modelType = availableModel.modelType
                )
                // Check if not already in list to avoid duplicates
                if (!availableModels.any { it.id == availableModel.id }) {
                    availableModels.add(existingModel)
                    savePersistedModels() // Save to persistence
                }
                return@withContext Result.success(existingModel)
            }
            
            Log.d(TAG, "Downloading from URL: ${availableModel.downloadUrl}")
            
            // Download the file
            val url = URL(availableModel.downloadUrl)
            val connection = url.openConnection()
            val contentLength = connection.contentLength
            
            Log.d(TAG, "Content length: $contentLength bytes")
            
            connection.getInputStream().use { inputStream ->
                targetFile.outputStream().use { outputStream ->
                    val buffer = ByteArray(8192)
                    var totalBytesRead = 0L
                    var bytesRead: Int
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        // Update progress
                        if (contentLength > 0) {
                            val progress = totalBytesRead.toFloat() / contentLength.toFloat()
                            onProgress(progress)
                        }
                    }
                }
            }
            
            Log.d(TAG, "Download completed: ${targetFile.absolutePath}")
            
            // Create LLMModel and add to available models
            val model = LLMModel(
                id = availableModel.id, // Use the same ID as AvailableModel
                name = availableModel.name,
                description = availableModel.description,
                filePath = targetFile.absolutePath,
                size = targetFile.length(),
                modelType = availableModel.modelType
            )
            
            // Check if not already in list to avoid duplicates
            if (!availableModels.any { it.id == availableModel.id }) {
                availableModels.add(model)
                savePersistedModels() // Save to persistence
            }
            Log.d(TAG, "Successfully downloaded and added model: ${model.name}")
            
            Result.success(model)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download model: ${availableModel.name}", e)
            Result.failure(e)
        }
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
            
            // Delete the file
            val file = File(model.filePath)
            if (file.exists()) {
                val deleted = file.delete()
                if (deleted) {
                    Log.d(TAG, "Successfully deleted model file: ${model.filePath}")
                    // Remove from available models list
                    availableModels.removeAll { it.id == modelId }
                    savePersistedModels() // Save to persistence
                    Log.d(TAG, "Model removed from available models list: $modelId")
                    Result.success(Unit)
                } else {
                    Log.e(TAG, "Failed to delete model file: ${model.filePath}")
                    Result.failure(Exception("Failed to delete model file"))
                }
            } else {
                Log.w(TAG, "Model file does not exist: ${model.filePath}")
                // Remove from available models list anyway
                availableModels.removeAll { it.id == modelId }
                savePersistedModels() // Save to persistence
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting model: $modelId", e)
            Result.failure(e)
        }
    }
    
    private fun loadPersistedModels() {
        try {
            val modelsFile = File(context.filesDir, "models_list.json")
            if (modelsFile.exists()) {
                Log.d(TAG, "Loading persisted models from: ${modelsFile.absolutePath}")
                val jsonString = modelsFile.readText()
                val models = json.decodeFromString<List<LLMModel>>(jsonString)
                
                // Verify files still exist and add to available models
                models.forEach { model ->
                    val file = File(model.filePath)
                    if (file.exists()) {
                        Log.d(TAG, "Found persisted model: ${model.name}")
                        availableModels.add(model)
                    } else {
                        Log.w(TAG, "Persisted model file no longer exists: ${model.filePath}")
                    }
                }
                Log.d(TAG, "Loaded ${availableModels.size} persisted models")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load persisted models", e)
        }
    }
    
    private fun savePersistedModels() {
        try {
            val modelsFile = File(context.filesDir, "models_list.json")
            val jsonString = json.encodeToString(availableModels.toList())
            modelsFile.writeText(jsonString)
            Log.d(TAG, "Saved ${availableModels.size} models to persistence")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save persisted models", e)
        }
    }
    
    private fun loadPersistedChatSessions() {
        try {
            val chatSessionsFile = File(context.filesDir, "chat_sessions.json")
            if (chatSessionsFile.exists()) {
                Log.d(TAG, "Loading persisted chat sessions from: ${chatSessionsFile.absolutePath}")
                val jsonString = chatSessionsFile.readText()
                val sessions = json.decodeFromString<List<ChatSession>>(jsonString)
                chatSessions.clear()
                chatSessions.addAll(sessions)
                Log.d(TAG, "Loaded ${chatSessions.size} persisted chat sessions")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load persisted chat sessions", e)
        }
    }
    
    private fun savePersistedChatSessions() {
        try {
            val chatSessionsFile = File(context.filesDir, "chat_sessions.json")
            val jsonString = json.encodeToString(chatSessions.toList())
            chatSessionsFile.writeText(jsonString)
            Log.d(TAG, "Saved ${chatSessions.size} chat sessions to persistence")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save persisted chat sessions", e)
        }
    }
} 