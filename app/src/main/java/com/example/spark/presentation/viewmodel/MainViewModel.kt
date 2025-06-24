package com.example.spark.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spark.domain.models.*
import com.example.spark.domain.repository.LLMRepository
import com.example.spark.network.server.ApiServer
import com.example.spark.utils.HuggingFaceAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.content.Context

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
    
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    init {
        // Combine states from all delegate ViewModels
        combineViewModelStates()
    }
    
    private fun combineViewModelStates() {
        viewModelScope.launch {
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
                    isLoading = modelMgmt.isLoading || server.isLoading,
                    loadingModelId = modelMgmt.loadingModelId,
                    loadingModelName = modelMgmt.loadingModelName,
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
            }.collect { combinedState ->
                _uiState.value = combinedState
            }
        }
    }
    
    // Delegation methods for UI layer convenience
    fun loadModel(modelId: String) = modelManagementViewModel.loadModel(modelId)
    fun unloadModel(modelId: String) = modelManagementViewModel.unloadModel(modelId)
    fun addModel(filePath: String, name: String, description: String) = modelManagementViewModel.addModel(filePath, name, description)
    fun deleteModel(modelId: String) = modelManagementViewModel.deleteModel(modelId)
    fun confirmDeleteModel() = modelManagementViewModel.confirmDeleteModel()
    fun cancelDeleteModel() = modelManagementViewModel.cancelDeleteModel()
    
    fun downloadModel(availableModel: AvailableModel) = modelDownloadViewModel.downloadModel(availableModel)
    fun cancelDownload(availableModel: AvailableModel) = modelDownloadViewModel.cancelDownload(availableModel)
    fun downloadFromCustomUrl(url: String, name: String, description: String) = modelDownloadViewModel.downloadFromCustomUrl(url, name, description)
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
    
    fun createChatSession(name: String, modelId: String) = chatViewModel.createChatSession(name, modelId)
    fun selectChatSession(sessionId: String) = chatViewModel.selectChatSession(sessionId)
    fun deleteChatSession(sessionId: String) = chatViewModel.deleteChatSession(sessionId)
    fun sendMessage(content: String) = chatViewModel.sendMessage(content, _uiState.value.modelConfig)
    fun stopGeneration() = chatViewModel.stopGeneration()
    fun updateCurrentMessage(message: String) = chatViewModel.updateCurrentMessage(message)
    
    fun startServer() = serverViewModel.startServer()
    fun stopServer() = serverViewModel.stopServer()
    fun updateModelConfig(newConfig: ModelConfig) = serverViewModel.updateModelConfig(newConfig)
    
    // Additional utility methods
    fun clearError() {
        modelManagementViewModel.clearError()
        modelDownloadViewModel.clearError()
        chatViewModel.clearError()
        serverViewModel.clearError()
    }
    
    fun refreshData() {
        modelManagementViewModel.refreshModels()
        modelDownloadViewModel.refreshDownloadableModels()
        chatViewModel.refreshChatSessions()
    }
    
    fun openHuggingFaceTokenPage() {
        viewModelScope.launch {
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