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
    val downloadingModelId: String? = null,
    val downloadProgress: Float = 0f,
    val errorMessage: String? = null,
    val currentMessage: String = "",
    val isGenerating: Boolean = false
)

class MainViewModel(
    private val llmRepository: LLMRepository,
    private val apiServer: ApiServer
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    init {
        loadInitialData()
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
            _uiState.update { it.copy(loadingModelId = modelId) }
            try {
                val result = llmRepository.loadModel(modelId)
                result.fold(
                    onSuccess = {
                        val loadedModels = llmRepository.getLoadedModels()
                        val availableModels = llmRepository.getAvailableModels()
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
                                errorMessage = "Failed to load model: ${error.message}"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        loadingModelId = null,
                        errorMessage = "Error loading model: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun unloadModel(modelId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loadingModelId = modelId) }
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
                                loadingModelId = null
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                loadingModelId = null,
                                errorMessage = "Failed to unload model: ${error.message}"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        loadingModelId = null,
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
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Failed to select chat session: ${e.message}")
                }
            }
        }
    }
    
    fun sendMessage(content: String) {
        val currentSession = _uiState.value.currentChatSession ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true) }
            
            try {
                // Add user message
                val userMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    content = content,
                    isUser = true,
                    timestamp = System.currentTimeMillis(),
                    modelId = currentSession.modelId
                )
                
                llmRepository.addMessageToSession(currentSession.id, userMessage)
                
                // Generate AI response
                val config = ModelConfig() // Use default config for now
                val result = llmRepository.generateResponseSync(
                    currentSession.modelId,
                    content,
                    config
                )
                
                result.fold(
                    onSuccess = { response ->
                        val aiMessage = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            content = response,
                            isUser = false,
                            timestamp = System.currentTimeMillis(),
                            modelId = currentSession.modelId
                        )
                        
                        llmRepository.addMessageToSession(currentSession.id, aiMessage)
                        
                        // Refresh current session
                        val updatedSession = llmRepository.getChatSession(currentSession.id)
                        _uiState.update {
                            it.copy(
                                currentChatSession = updatedSession,
                                isGenerating = false
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isGenerating = false,
                                errorMessage = "Failed to generate response: ${error.message}"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        errorMessage = "Error sending message: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun updateCurrentMessage(message: String) {
        _uiState.update { it.copy(currentMessage = message) }
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
} 