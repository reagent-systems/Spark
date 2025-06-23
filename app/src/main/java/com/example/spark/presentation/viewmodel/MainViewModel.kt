package com.example.spark.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spark.domain.models.*
import com.example.spark.domain.repository.LLMRepository
import com.example.spark.network.server.ApiServer
import com.example.spark.utils.NetworkUtils
import com.example.spark.utils.ModelCatalog
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.content.Context
import java.io.File
import java.util.*

data class MainUiState(
    val availableModels: List<LLMModel> = emptyList(),
    val loadedModels: List<LLMModel> = emptyList(),
    val downloadableModels: List<AvailableModel> = emptyList(),
    val chatSessions: List<ChatSession> = emptyList(),
    val currentChatSession: ChatSession? = null,
    val isServerRunning: Boolean = false,
    val serverPort: Int = 8080,
    val serverLocalIp: String = "",
    val isLoading: Boolean = false,
    val loadingModelId: String? = null,
    val loadingModelName: String? = null,
    val downloadingModelId: String? = null,
    val downloadProgress: Float = 0f,
    val errorMessage: String? = null,
    val currentMessage: String = "",
    val isGenerating: Boolean = false,
    val modelConfig: ModelConfig = ModelConfig()
)

class MainViewModel(
    private val llmRepository: LLMRepository,
    private val apiServer: ApiServer,
    private val context: Context
) : ViewModel() {
    
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    init {
        loadInitialData()
        loadModelConfig()
    }
    
    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val models = llmRepository.getAvailableModels()
                val loadedModels = llmRepository.getLoadedModels()
                val sessions = llmRepository.getChatSessions()
                val downloadableModels = ModelCatalog.getAvailableModels()
                
                _uiState.update {
                    it.copy(
                        availableModels = models,
                        loadedModels = loadedModels,
                        chatSessions = sessions,
                        downloadableModels = downloadableModels,
                        isLoading = false,
                        isServerRunning = apiServer.isRunning(),
                        serverPort = apiServer.getPort()
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load initial data: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun loadModel(modelId: String) {
        viewModelScope.launch {
            // Find the model name
            val model = _uiState.value.availableModels.find { it.id == modelId }
            val modelName = model?.name ?: "Unknown Model"
            
            _uiState.update { 
                it.copy(
                    loadingModelId = modelId,
                    loadingModelName = modelName
                )
            }
            
            try {
                // Use a separate coroutine for the actual loading to avoid blocking the UI
                val result = llmRepository.loadModel(modelId, _uiState.value.modelConfig)
                result.fold(
                    onSuccess = {
                        val loadedModels = llmRepository.getLoadedModels()
                        val availableModels = llmRepository.getAvailableModels()
                        _uiState.update {
                            it.copy(
                                availableModels = availableModels,
                                loadedModels = loadedModels,
                                loadingModelId = null,
                                loadingModelName = null
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                loadingModelId = null,
                                loadingModelName = null,
                                errorMessage = "Failed to load model: ${error.message}"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        loadingModelId = null,
                        loadingModelName = null,
                        errorMessage = "Error loading model: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun unloadModel(modelId: String) {
        viewModelScope.launch {
            // Find the model name
            val model = _uiState.value.availableModels.find { it.id == modelId }
            val modelName = model?.name ?: "Unknown Model"
            
            _uiState.update { 
                it.copy(
                    loadingModelId = modelId,
                    loadingModelName = modelName
                )
            }
            
            try {
                val result = llmRepository.unloadModel(modelId)
                result.fold(
                    onSuccess = {
                        val loadedModels = llmRepository.getLoadedModels()
                        val availableModels = llmRepository.getAvailableModels()
                        _uiState.update {
                            it.copy(
                                availableModels = availableModels,
                                loadedModels = loadedModels,
                                loadingModelId = null,
                                loadingModelName = null
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                loadingModelId = null,
                                loadingModelName = null,
                                errorMessage = "Failed to unload model: ${error.message}"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        loadingModelId = null,
                        loadingModelName = null,
                        errorMessage = "Error unloading model: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun addModel(filePath: String, name: String, description: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val result = llmRepository.addModel(filePath, name, description)
                result.fold(
                    onSuccess = { model ->
                        val models = llmRepository.getAvailableModels()
                        _uiState.update {
                            it.copy(
                                availableModels = models,
                                isLoading = false
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Failed to add model: ${error.message}"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Error adding model: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun startServer() {
        viewModelScope.launch {
            try {
                apiServer.start()
                val localIp = NetworkUtils.getLocalIpAddress() ?: "localhost"
                _uiState.update {
                    it.copy(
                        isServerRunning = true,
                        serverPort = apiServer.getPort(),
                        serverLocalIp = localIp
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Failed to start server: ${e.message}")
                }
            }
        }
    }
    
    fun stopServer() {
        viewModelScope.launch {
            try {
                apiServer.stop()
                _uiState.update {
                    it.copy(
                        isServerRunning = false,
                        serverLocalIp = ""
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Failed to stop server: ${e.message}")
                }
            }
        }
    }
    
    fun createChatSession(name: String, modelId: String) {
        viewModelScope.launch {
            try {
                val session = llmRepository.createChatSession(name, modelId)
                val sessions = llmRepository.getChatSessions()
                _uiState.update {
                    it.copy(
                        chatSessions = sessions,
                        currentChatSession = session
                    )
                }
                
                // Automatically ensure the correct model is loaded for the new chat
                ensureCorrectModelLoaded(modelId)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Failed to create chat session: ${e.message}")
                }
            }
        }
    }
    
    fun selectChatSession(sessionId: String) {
        viewModelScope.launch {
            try {
                val session = llmRepository.getChatSession(sessionId)
                _uiState.update {
                    it.copy(currentChatSession = session)
                }
                
                // Automatically ensure the correct model is loaded for this chat
                session?.let { ensureCorrectModelLoaded(it.modelId) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Failed to select chat session: ${e.message}")
                }
            }
        }
    }
    
    private suspend fun ensureCorrectModelLoaded(requiredModelId: String) {
        try {
            val currentLoadedModels = llmRepository.getLoadedModels()
            val isRequiredModelLoaded = currentLoadedModels.any { it.id == requiredModelId }
            
            // If the required model is already loaded, we're good
            if (isRequiredModelLoaded) {
                return
            }
            
            // Check if the required model exists in available models
            val availableModels = llmRepository.getAvailableModels()
            val requiredModel = availableModels.find { it.id == requiredModelId }
            if (requiredModel == null) {
                _uiState.update {
                    it.copy(errorMessage = "Required model not found: $requiredModelId")
                }
                return
            }
            
            // If there are other models loaded, unload them first to free memory
            if (currentLoadedModels.isNotEmpty()) {
                for (loadedModel in currentLoadedModels) {
                    if (loadedModel.id != requiredModelId) {
                        // Find the model name for loading dialog
                        val modelName = loadedModel.name
                        _uiState.update { 
                            it.copy(
                                loadingModelId = loadedModel.id,
                                loadingModelName = "Unloading $modelName"
                            )
                        }
                        
                        val unloadResult = llmRepository.unloadModel(loadedModel.id)
                        unloadResult.fold(
                            onSuccess = {
                                // Update UI state after successful unload
                                val updatedLoadedModels = llmRepository.getLoadedModels()
                                val updatedAvailableModels = llmRepository.getAvailableModels()
                                _uiState.update {
                                    it.copy(
                                        availableModels = updatedAvailableModels,
                                        loadedModels = updatedLoadedModels
                                    )
                                }
                            },
                            onFailure = { error ->
                                _uiState.update {
                                    it.copy(
                                        loadingModelId = null,
                                        loadingModelName = null,
                                        errorMessage = "Failed to unload model ${loadedModel.name}: ${error.message}"
                                    )
                                }
                                return
                            }
                        )
                    }
                }
            }
            
            // Now load the required model
            _uiState.update { 
                it.copy(
                    loadingModelId = requiredModelId,
                    loadingModelName = requiredModel.name
                )
            }
            
            val loadResult = llmRepository.loadModel(requiredModelId, _uiState.value.modelConfig)
            loadResult.fold(
                onSuccess = {
                    val updatedLoadedModels = llmRepository.getLoadedModels()
                    val updatedAvailableModels = llmRepository.getAvailableModels()
                    _uiState.update {
                        it.copy(
                            availableModels = updatedAvailableModels,
                            loadedModels = updatedLoadedModels,
                            loadingModelId = null,
                            loadingModelName = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            loadingModelId = null,
                            loadingModelName = null,
                            errorMessage = "Failed to load model ${requiredModel.name}: ${error.message}"
                        )
                    }
                }
            )
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    loadingModelId = null,
                    loadingModelName = null,
                    errorMessage = "Failed to manage model loading: ${e.message}"
                )
            }
        }
    }
    
    private var generationJob: Job? = null
    
    fun sendMessage(content: String) {
        val currentSession = _uiState.value.currentChatSession ?: return
        
        generationJob?.cancel() // Cancel any existing generation
        generationJob = viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true) }
            
            try {
                // Ensure the correct model is loaded before sending message
                ensureCorrectModelLoaded(currentSession.modelId)
                
                // Add user message
                val userMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    content = content,
                    isUser = true,
                    timestamp = System.currentTimeMillis(),
                    modelId = currentSession.modelId
                )
                
                llmRepository.addMessageToSession(currentSession.id, userMessage)
                
                // Refresh the UI to show the user message immediately
                val updatedSession = llmRepository.getChatSession(currentSession.id)
                _uiState.update {
                    it.copy(
                        currentChatSession = updatedSession,
                        currentMessage = "" // Clear the input field
                    )
                }
                
                // Generate AI response using current config
                val config = _uiState.value.modelConfig
                val response = llmRepository.generateResponse(
                    currentSession.modelId,
                    content,
                    config
                ).first() // Get the complete response
                
                // Create AI message with complete response
                val aiMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    content = response,
                    isUser = false,
                    timestamp = System.currentTimeMillis(),
                    modelId = currentSession.modelId
                )
                
                llmRepository.addMessageToSession(currentSession.id, aiMessage)
                
                // Update UI with the complete response
                val finalSession = llmRepository.getChatSession(currentSession.id)
                _uiState.update {
                    it.copy(currentChatSession = finalSession)
                }
                
                // Mark generation as complete
                _uiState.update {
                    it.copy(isGenerating = false)
                }
                
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        errorMessage = if (e is kotlinx.coroutines.CancellationException) {
                            null // Don't show error for user cancellation
                        } else {
                            "Error sending message: ${e.message}"
                        }
                    )
                }
            } finally {
                generationJob = null
            }
        }
    }
    
    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
        _uiState.update {
            it.copy(isGenerating = false)
        }
    }
    

    
    fun updateCurrentMessage(message: String) {
        _uiState.update { it.copy(currentMessage = message) }
    }
    
    fun updateModelConfig(newConfig: ModelConfig) {
        _uiState.update { it.copy(modelConfig = newConfig) }
        saveModelConfig(newConfig)
        
        // Update the API server's default config
        apiServer.updateDefaultModelConfig(newConfig)
        
        // Check if GPU setting changed and reload models if necessary
        val currentConfig = _uiState.value.modelConfig
        if (currentConfig.useGpu != newConfig.useGpu) {
            reloadModelsForGpuChange()
        }
    }
    
    private fun reloadModelsForGpuChange() {
        viewModelScope.launch {
            try {
                val loadedModels = llmRepository.getLoadedModels()
                if (loadedModels.isNotEmpty()) {
                    // Show loading state
                    _uiState.update { 
                        it.copy(
                            loadingModelId = "gpu_change",
                            loadingModelName = "Reloading models for GPU change"
                        )
                    }
                    
                    // Unload all models first
                    for (model in loadedModels) {
                        llmRepository.unloadModel(model.id)
                    }
                    
                    // Reload them with new GPU setting
                    for (model in loadedModels) {
                        llmRepository.loadModel(model.id, _uiState.value.modelConfig)
                    }
                    
                    // Update UI state
                    val updatedLoadedModels = llmRepository.getLoadedModels()
                    val updatedAvailableModels = llmRepository.getAvailableModels()
                    _uiState.update {
                        it.copy(
                            availableModels = updatedAvailableModels,
                            loadedModels = updatedLoadedModels,
                            loadingModelId = null,
                            loadingModelName = null
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        loadingModelId = null,
                        loadingModelName = null,
                        errorMessage = "Failed to reload models for GPU change: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
    
    fun downloadModel(availableModel: AvailableModel) {
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    downloadingModelId = availableModel.id,
                    downloadProgress = 0f
                ) 
            }
            
            try {
                val result = llmRepository.downloadModel(availableModel) { progress ->
                    _uiState.update { 
                        it.copy(downloadProgress = progress) 
                    }
                }
                
                result.fold(
                    onSuccess = { model ->
                        val models = llmRepository.getAvailableModels()
                        _uiState.update {
                            it.copy(
                                availableModels = models,
                                downloadingModelId = null,
                                downloadProgress = 0f
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                downloadingModelId = null,
                                downloadProgress = 0f,
                                errorMessage = "Failed to download model: ${error.message}"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        downloadingModelId = null,
                        downloadProgress = 0f,
                        errorMessage = "Error downloading model: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun refreshData() {
        loadInitialData()
    }
    
    fun refreshDownloadableModels() {
        viewModelScope.launch {
            try {
                ModelCatalog.clearCache()
                val downloadableModels = ModelCatalog.getAvailableModels()
                _uiState.update {
                    it.copy(downloadableModels = downloadableModels)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Failed to refresh downloadable models: ${e.message}")
                }
            }
        }
    }
    
    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loadingModelId = modelId) }
            try {
                val result = llmRepository.deleteModel(modelId)
                result.fold(
                    onSuccess = {
                        val availableModels = llmRepository.getAvailableModels()
                        val loadedModels = llmRepository.getLoadedModels()
                        _uiState.update {
                            it.copy(
                                availableModels = availableModels,
                                loadedModels = loadedModels,
                                loadingModelId = null
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                loadingModelId = null,
                                errorMessage = "Failed to delete model: ${error.message}"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        loadingModelId = null,
                        errorMessage = "Error deleting model: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun deleteChatSession(sessionId: String) {
        viewModelScope.launch {
            try {
                val result = llmRepository.deleteChatSession(sessionId)
                result.fold(
                    onSuccess = {
                        val sessions = llmRepository.getChatSessions()
                        _uiState.update {
                            it.copy(
                                chatSessions = sessions,
                                // Clear current session if it was deleted
                                currentChatSession = if (it.currentChatSession?.id == sessionId) null else it.currentChatSession
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(errorMessage = "Failed to delete chat session: ${error.message}")
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Error deleting chat session: ${e.message}")
                }
            }
        }
    }
    
    private fun loadModelConfig() {
        try {
            val configFile = File(context.filesDir, "model_config.json")
            if (configFile.exists()) {
                val configJson = configFile.readText()
                val config = json.decodeFromString<ModelConfig>(configJson)
                _uiState.update { it.copy(modelConfig = config) }
                
                // Initialize API server with the loaded config
                apiServer.updateDefaultModelConfig(config)
            } else {
                // Initialize API server with default config
                apiServer.updateDefaultModelConfig(ModelConfig())
            }
        } catch (e: Exception) {
            // If loading fails, use default config
            val defaultConfig = ModelConfig()
            _uiState.update { it.copy(modelConfig = defaultConfig) }
            apiServer.updateDefaultModelConfig(defaultConfig)
        }
    }
    
    private fun saveModelConfig(config: ModelConfig) {
        try {
            val configFile = File(context.filesDir, "model_config.json")
            val jsonString = json.encodeToString(config)
            configFile.writeText(jsonString)
        } catch (e: Exception) {
            // Handle save error silently
        }
    }
} 