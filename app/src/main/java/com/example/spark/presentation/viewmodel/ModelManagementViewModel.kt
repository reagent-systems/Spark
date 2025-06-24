package com.example.spark.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spark.domain.models.*
import com.example.spark.domain.repository.LLMRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ModelManagementViewModel(
    private val llmRepository: LLMRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ModelManagementUiState())
    val uiState: StateFlow<ModelManagementUiState> = _uiState.asStateFlow()
    
    init {
        loadModels()
    }
    
    private fun loadModels() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val models = llmRepository.getAvailableModels()
                val loadedModels = llmRepository.getLoadedModels()
                _uiState.update {
                    it.copy(
                        availableModels = models,
                        loadedModels = loadedModels,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load models: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun loadModel(modelId: String) {
        viewModelScope.launch {
            val model = _uiState.value.availableModels.find { it.id == modelId }
            val modelName = model?.name ?: "Unknown Model"
            
            _uiState.update { 
                it.copy(
                    loadingModelId = modelId,
                    loadingModelName = modelName
                )
            }
            
            try {
                val result = llmRepository.loadModel(modelId, ModelConfig())
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
    
    fun deleteModel(modelId: String) {
        val model = _uiState.value.availableModels.find { it.id == modelId }
        if (model != null) {
            _uiState.update {
                it.copy(
                    showDeleteConfirmationDialog = true,
                    modelToDelete = model
                )
            }
        }
    }
    
    fun confirmDeleteModel() {
        val modelToDelete = _uiState.value.modelToDelete
        if (modelToDelete != null) {
            viewModelScope.launch {
                _uiState.update { 
                    it.copy(
                        loadingModelId = modelToDelete.id,
                        showDeleteConfirmationDialog = false,
                        modelToDelete = null
                    ) 
                }
                try {
                    val result = llmRepository.deleteModel(modelToDelete.id)
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
    
    fun cancelDeleteModel() {
        _uiState.update {
            it.copy(
                showDeleteConfirmationDialog = false,
                modelToDelete = null
            )
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
    
    fun refreshModels() {
        loadModels()
    }
}

data class ModelManagementUiState(
    val availableModels: List<LLMModel> = emptyList(),
    val loadedModels: List<LLMModel> = emptyList(),
    val isLoading: Boolean = false,
    val loadingModelId: String? = null,
    val loadingModelName: String? = null,
    val errorMessage: String? = null,
    val showDeleteConfirmationDialog: Boolean = false,
    val modelToDelete: LLMModel? = null
) 