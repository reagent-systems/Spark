package com.example.spark.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spark.domain.models.*
import com.example.spark.domain.repository.LLMRepository
import com.example.spark.utils.HuggingFaceAuth
import com.example.spark.utils.ModelCatalog
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

class ModelDownloadViewModel(
    private val llmRepository: LLMRepository,
    private val huggingFaceAuth: HuggingFaceAuth
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ModelDownloadUiState())
    val uiState: StateFlow<ModelDownloadUiState> = _uiState.asStateFlow()
    
    // Callback for when models are downloaded successfully
    private var modelStateChangeCallback: (() -> Unit)? = null
    
    fun setModelStateChangeCallback(callback: () -> Unit) {
        modelStateChangeCallback = callback
    }
    
    init {
        // Initialize authentication check immediately (lightweight)
        checkHuggingFaceAuthentication()
        // Load downloadable models from CDN
        loadDownloadableModels()
    }
    
    private fun checkHuggingFaceAuthentication() {
        val isAuthenticated = huggingFaceAuth.isAuthenticated()
        _uiState.update { it.copy(isHuggingFaceAuthenticated = isAuthenticated) }
    }
    
    private fun loadDownloadableModels() {
        viewModelScope.launch {
            try {
                val downloadableModels = ModelCatalog.getAvailableModels()
                _uiState.update {
                    it.copy(downloadableModels = downloadableModels)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Failed to load downloadable models: ${e.message}")
                }
            }
        }
    }
    
    fun downloadModel(availableModel: AvailableModel) {
        viewModelScope.launch {
            // Check if model requires authentication
            if (availableModel.needsHuggingFaceAuth && !huggingFaceAuth.isAuthenticated()) {
                // Show authentication dialog
                _uiState.update { 
                    it.copy(
                        showHuggingFaceTokenDialog = true,
                        pendingDownloadModel = availableModel
                    ) 
                }
                return@launch
            }
            
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
                        _uiState.update {
                            it.copy(
                                downloadingModelId = null,
                                downloadProgress = 0f
                            )
                        }
                        // Trigger callback to refresh models list on main thread with small delay
                        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            kotlinx.coroutines.delay(100) // Small delay to ensure UI is ready
                            modelStateChangeCallback?.invoke()
                        }
                    },
                    onFailure = { error ->
                        // Don't show error for cancellation
                        if (error !is CancellationException) {
                            _uiState.update {
                                it.copy(
                                    downloadingModelId = null,
                                    downloadProgress = 0f,
                                    errorMessage = "Failed to download model: ${error.message}"
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    downloadingModelId = null,
                                    downloadProgress = 0f
                                )
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                // Don't show error for cancellation
                if (e !is CancellationException) {
                    _uiState.update {
                        it.copy(
                            downloadingModelId = null,
                            downloadProgress = 0f,
                            errorMessage = "Error downloading model: ${e.message}"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            downloadingModelId = null,
                            downloadProgress = 0f
                        )
                    }
                }
            }
        }
    }
    
    fun cancelDownload(availableModel: AvailableModel) {
        viewModelScope.launch {
            try {
                val result = llmRepository.cancelDownload(availableModel.id)
                result.fold(
                    onSuccess = {
                        _uiState.update {
                            it.copy(
                                downloadingModelId = null,
                                downloadProgress = 0f
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                errorMessage = "Failed to cancel download: ${error.message}"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = "Error cancelling download: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun downloadFromCustomUrl(url: String, name: String, description: String) {
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    isDownloadingCustomUrl = true,
                    downloadProgress = 0f
                ) 
            }
            
            try {
                val result = llmRepository.addModel(url, name, description)
                result.fold(
                    onSuccess = { model ->
                        _uiState.update {
                            it.copy(
                                isDownloadingCustomUrl = false,
                                showCustomUrlDialog = false,
                                customUrlInput = "",
                                downloadProgress = 0f
                            )
                        }
                        // Trigger callback to refresh models list on main thread with small delay
                        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            kotlinx.coroutines.delay(100) // Small delay to ensure UI is ready
                            modelStateChangeCallback?.invoke()
                        }
                    },
                    onFailure = { error ->
                        if (error !is CancellationException) {
                            _uiState.update {
                                it.copy(
                                    isDownloadingCustomUrl = false,
                                    errorMessage = "Failed to download from URL: ${error.message}"
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    isDownloadingCustomUrl = false
                                )
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _uiState.update {
                        it.copy(
                            isDownloadingCustomUrl = false,
                            errorMessage = "Error downloading from URL: ${e.message}"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isDownloadingCustomUrl = false
                        )
                    }
                }
            }
        }
    }
    
    // HuggingFace Authentication Methods
    fun showHuggingFaceTokenDialog() {
        _uiState.update { it.copy(showHuggingFaceTokenDialog = true) }
    }
    
    fun hideHuggingFaceTokenDialog() {
        _uiState.update { 
            it.copy(
                showHuggingFaceTokenDialog = false,
                pendingDownloadModel = null
            ) 
        }
    }
    
    fun submitHuggingFaceToken(token: String) {
        huggingFaceAuth.saveAccessToken(token)
        _uiState.update { 
            it.copy(
                showHuggingFaceTokenDialog = false,
                isHuggingFaceAuthenticated = true
            ) 
        }
        
        // If there's a pending download, proceed with it
        val pendingModel = _uiState.value.pendingDownloadModel
        if (pendingModel != null) {
            _uiState.update { it.copy(pendingDownloadModel = null) }
            downloadModel(pendingModel)
        }
    }
    
    fun showHuggingFaceSettingsDialog() {
        _uiState.update { it.copy(showHuggingFaceSettingsDialog = true) }
    }
    
    fun hideHuggingFaceSettingsDialog() {
        _uiState.update { it.copy(showHuggingFaceSettingsDialog = false) }
    }
    
    fun saveHuggingFaceToken(token: String) {
        huggingFaceAuth.saveAccessToken(token)
        checkHuggingFaceAuthentication()
        hideHuggingFaceSettingsDialog()
        
        // If there was a pending download, proceed with it
        _uiState.value.pendingDownloadModel?.let { model ->
            downloadModel(model)
        }
    }
    
    fun removeHuggingFaceToken() {
        huggingFaceAuth.clearAccessToken()
        checkHuggingFaceAuthentication()
        hideHuggingFaceSettingsDialog()
    }
    
    // Custom URL Methods
    fun showCustomUrlDialog() {
        _uiState.update { it.copy(showCustomUrlDialog = true) }
    }
    
    fun hideCustomUrlDialog() {
        _uiState.update { 
            it.copy(
                showCustomUrlDialog = false,
                customUrlInput = "",
                isDownloadingCustomUrl = false
            ) 
        }
    }
    
    fun updateCustomUrlInput(url: String) {
        _uiState.update { it.copy(customUrlInput = url) }
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
    
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

data class ModelDownloadUiState(
    val downloadableModels: List<AvailableModel> = emptyList(),
    val downloadingModelId: String? = null,
    val downloadProgress: Float = 0f,
    val errorMessage: String? = null,
    val showHuggingFaceTokenDialog: Boolean = false,
    val isHuggingFaceAuthenticated: Boolean = false,
    val pendingDownloadModel: AvailableModel? = null,
    val showHuggingFaceSettingsDialog: Boolean = false,
    val showCustomUrlDialog: Boolean = false,
    val customUrlInput: String = "",
    val isDownloadingCustomUrl: Boolean = false
) 