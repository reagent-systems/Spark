package com.example.spark.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spark.domain.models.*
import com.example.spark.domain.repository.LLMRepository
import com.example.spark.network.server.ApiServer
import com.example.spark.utils.HuggingFaceAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.FlowPreview
import android.content.Context
import kotlinx.coroutines.withContext

@OptIn(FlowPreview::class)
// Optimized UI state - split into focused sub-states to reduce recompositions
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
    val modelConfig: ModelConfig = ModelConfig(),
    val showHuggingFaceTokenDialog: Boolean = false,
    val isHuggingFaceAuthenticated: Boolean = false,
    val pendingDownloadModel: AvailableModel? = null,
    val showHuggingFaceSettingsDialog: Boolean = false,
    val showCustomUrlDialog: Boolean = false,
    val customUrlInput: String = "",
    val isDownloadingCustomUrl: Boolean = false,
    val showDeleteConfirmationDialog: Boolean = false,
    val modelToDelete: LLMModel? = null
)

class MainViewModel(
    private val llmRepository: LLMRepository,
    private val apiServer: ApiServer,
    private val context: Context,
    private val huggingFaceAuth: HuggingFaceAuth
) : ViewModel() {
    
    // Delegate ViewModels
    val modelManagementViewModel = ModelManagementViewModel(llmRepository)
    val modelDownloadViewModel = ModelDownloadViewModel(llmRepository, huggingFaceAuth)
    val chatViewModel = ChatViewModel(llmRepository)
    val serverViewModel = ServerViewModel(llmRepository, apiServer, context)
    
    // Use dedicated scope for background operations
    private val backgroundScope = CoroutineScope(viewModelScope.coroutineContext + SupervisorJob() + Dispatchers.Default)
    
    // Error handling with debouncing
    private val _errorChannel = Channel<String>(Channel.BUFFERED)
    val errorFlow = _errorChannel.receiveAsFlow()
    
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    init {
        // Combine states from all delegate ViewModels with optimized flow operators
        combineViewModelStates()
        
        // Set up callback for chat model state changes
        chatViewModel.setModelStateChangeCallback {
            modelManagementViewModel.refreshModels()
        }
    }
    
    private fun combineViewModelStates() {
        viewModelScope.launch(Dispatchers.Default) {
            combine(
                modelManagementViewModel.uiState,
                modelDownloadViewModel.uiState,
                chatViewModel.uiState,
                serverViewModel.uiState
            ) { modelMgmt, download, chat, server ->
                MainUiState(
                    availableModels = modelMgmt.availableModels,
                    loadedModels = modelMgmt.loadedModels,
                    downloadableModels = download.downloadableModels,
                    chatSessions = chat.chatSessions,
                    currentChatSession = chat.currentChatSession,
                    isServerRunning = server.isServerRunning,
                    serverPort = server.serverPort,
                    serverLocalIp = server.serverLocalIp,
                    isLoading = modelMgmt.isLoading || server.isLoading || chat.isLoadingModel,
                    loadingModelId = modelMgmt.loadingModelId ?: chat.loadingModelId,
                    loadingModelName = modelMgmt.loadingModelName ?: chat.loadingModelName,
                    downloadingModelId = download.downloadingModelId,
                    downloadProgress = download.downloadProgress,
                    errorMessage = modelMgmt.errorMessage ?: download.errorMessage ?: chat.errorMessage ?: server.errorMessage,
                    currentMessage = chat.currentMessage,
                    isGenerating = chat.isGenerating,
                    modelConfig = server.modelConfig,
                    showHuggingFaceTokenDialog = download.showHuggingFaceTokenDialog,
                    isHuggingFaceAuthenticated = download.isHuggingFaceAuthenticated,
                    pendingDownloadModel = download.pendingDownloadModel,
                    showHuggingFaceSettingsDialog = download.showHuggingFaceSettingsDialog,
                    showCustomUrlDialog = download.showCustomUrlDialog,
                    customUrlInput = download.customUrlInput,
                    isDownloadingCustomUrl = download.isDownloadingCustomUrl,
                    showDeleteConfirmationDialog = modelMgmt.showDeleteConfirmationDialog,
                    modelToDelete = modelMgmt.modelToDelete
                )
            }
            .distinctUntilChanged()
            .debounce(16)
            .flowOn(Dispatchers.Default)
            .collect { combinedState ->
                // Minimize main thread work - only update when necessary
                withContext(Dispatchers.Main.immediate) {
                    _uiState.value = combinedState
                }
            }
        }
    }
    
    // Delegation methods for UI layer convenience - optimized with background dispatching
    fun loadModel(modelId: String) {
        viewModelScope.launch(Dispatchers.Default) {
            modelManagementViewModel.loadModel(modelId)
        }
    }
    
    fun unloadModel(modelId: String) {
        viewModelScope.launch(Dispatchers.Default) {
            modelManagementViewModel.unloadModel(modelId)
        }
    }
    
    fun addModel(filePath: String, name: String, description: String) {
        viewModelScope.launch(Dispatchers.Default) {
            modelManagementViewModel.addModel(filePath, name, description)
        }
    }
    
    fun deleteModel(modelId: String) {
        viewModelScope.launch(Dispatchers.Default) {
            modelManagementViewModel.deleteModel(modelId)
        }
    }
    
    fun confirmDeleteModel() = modelManagementViewModel.confirmDeleteModel()
    fun cancelDeleteModel() = modelManagementViewModel.cancelDeleteModel()
    
    fun downloadModel(availableModel: AvailableModel) {
        viewModelScope.launch(Dispatchers.Default) {
            modelDownloadViewModel.downloadModel(availableModel)
        }
    }
    
    fun cancelDownload(availableModel: AvailableModel) {
        viewModelScope.launch(Dispatchers.Default) {
            modelDownloadViewModel.cancelDownload(availableModel)
        }
    }
    
    fun downloadFromCustomUrl(url: String, name: String, description: String) {
        viewModelScope.launch(Dispatchers.Default) {
            modelDownloadViewModel.downloadFromCustomUrl(url, name, description)
        }
    }
    
    fun showHuggingFaceTokenDialog() = modelDownloadViewModel.showHuggingFaceTokenDialog()
    fun hideHuggingFaceTokenDialog() = modelDownloadViewModel.hideHuggingFaceTokenDialog()
    fun submitHuggingFaceToken(token: String) = modelDownloadViewModel.submitHuggingFaceToken(token)
    fun showHuggingFaceSettingsDialog() = modelDownloadViewModel.showHuggingFaceSettingsDialog()
    fun hideHuggingFaceSettingsDialog() = modelDownloadViewModel.hideHuggingFaceSettingsDialog()
    fun saveHuggingFaceToken(token: String) = modelDownloadViewModel.saveHuggingFaceToken(token)
    fun removeHuggingFaceToken() = modelDownloadViewModel.removeHuggingFaceToken()
    fun showCustomUrlDialog() = modelDownloadViewModel.showCustomUrlDialog()
    fun hideCustomUrlDialog() = modelDownloadViewModel.hideCustomUrlDialog()
    fun updateCustomUrlInput(url: String) = modelDownloadViewModel.updateCustomUrlInput(url)
    fun refreshDownloadableModels() = modelDownloadViewModel.refreshDownloadableModels()
    
    fun createChatSession(name: String, modelId: String) {
        viewModelScope.launch(Dispatchers.Default) {
            chatViewModel.createChatSession(name, modelId)
        }
    }
    
    fun selectChatSession(sessionId: String) = chatViewModel.selectChatSession(sessionId)
    
    fun deleteChatSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.Default) {
            chatViewModel.deleteChatSession(sessionId)
        }
    }
    
    fun sendMessage(content: String) {
        viewModelScope.launch(Dispatchers.Default) {
            chatViewModel.sendMessage(content, _uiState.value.modelConfig)
        }
    }
    
    fun stopGeneration() = chatViewModel.stopGeneration()
    
    fun updateCurrentMessage(message: String) {
        // Update immediately for responsive text input - no background dispatching needed for simple text updates
        chatViewModel.updateCurrentMessage(message)
    }
    
    fun startServer() {
        viewModelScope.launch(Dispatchers.Default) {
            serverViewModel.startServer()
        }
    }
    
    fun stopServer() {
        viewModelScope.launch(Dispatchers.Default) {
            serverViewModel.stopServer()
        }
    }
    
    fun updateModelConfig(newConfig: ModelConfig) = serverViewModel.updateModelConfig(newConfig)
    
    // Optimized error handling
    fun clearError() {
        modelManagementViewModel.clearError()
        modelDownloadViewModel.clearError()
        chatViewModel.clearError()
        serverViewModel.clearError()
    }
    
    fun refreshData() {
        viewModelScope.launch(Dispatchers.Default) {
            // Run refresh operations in parallel
            launch { modelManagementViewModel.refreshModels() }
            launch { modelDownloadViewModel.refreshDownloadableModels() }
            launch { chatViewModel.refreshChatSessions() }
        }
    }
    
    fun openHuggingFaceTokenPage() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                huggingFaceAuth.authenticate()
            } catch (e: Exception) {
                // Expected - this opens the browser
            }
        }
    }
    
    fun signOutHuggingFace() {
        huggingFaceAuth.signOut()
        modelDownloadViewModel.removeHuggingFaceToken()
    }
} 