package com.example.spark.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spark.domain.models.*
import com.example.spark.domain.repository.LLMRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.util.*

class ChatViewModel(
    private val llmRepository: LLMRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    private var generationJob: Job? = null
    
    // Callback for when models are loaded/unloaded
    private var onModelStateChanged: (() -> Unit)? = null
    
    init {
        loadChatSessions()
    }
    
    private fun loadChatSessions() {
        viewModelScope.launch {
            try {
                val sessions = llmRepository.getChatSessions()
                _uiState.update {
                    it.copy(chatSessions = sessions)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Failed to load chat sessions: ${e.message}")
                }
            }
        }
    }
    
    fun createChatSession(name: String, modelId: String, systemPrompt: String = "") {
        viewModelScope.launch {
            try {
                val session = llmRepository.createChatSession(name, modelId, systemPrompt)
                _uiState.update {
                    it.copy(
                        chatSessions = it.chatSessions + session,
                        currentChatSession = session
                    )
                }
                // Notify that model state might need to change
                onModelStateChanged?.invoke()
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
    
    fun sendMessage(content: String, modelConfig: ModelConfig) {
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
                
                // Construct the full prompt with system prompt and conversation history
                val fullPrompt = buildPrompt(
                    systemPrompt = modelConfig.systemPrompt.ifBlank { currentSession.systemPrompt },
                    conversationHistory = updatedSession?.messages ?: emptyList(),
                    currentMessage = content
                )
                
                // Generate AI response using the constructed prompt
                val response = llmRepository.generateResponse(
                    currentSession.modelId,
                    fullPrompt,
                    modelConfig
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
    
    private fun buildPrompt(
        systemPrompt: String,
        conversationHistory: List<ChatMessage>,
        currentMessage: String
    ): String {
        val prompt = StringBuilder()
        
        // Add system prompt if provided
        if (systemPrompt.isNotBlank()) {
            prompt.append("System: $systemPrompt\n\n")
        }
        
        // Add conversation history (last 10 messages to avoid context overflow)
        val recentMessages = conversationHistory.takeLast(10)
        for (message in recentMessages) {
            if (message.isUser) {
                prompt.append("Human: ${message.content}\n")
            } else {
                prompt.append("Assistant: ${message.content}\n")
            }
        }
        
        // Add current message
        prompt.append("Human: $currentMessage\n")
        prompt.append("Assistant: ")
        
        return prompt.toString()
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
    
    fun setModelStateChangeCallback(callback: () -> Unit) {
        onModelStateChanged = callback
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
            
            // Set loading state
            _uiState.update {
                it.copy(
                    isLoadingModel = true,
                    loadingModelId = requiredModelId,
                    loadingModelName = requiredModel.name
                )
            }
            
            // If there are other models loaded, unload them first to free memory
            if (currentLoadedModels.isNotEmpty()) {
                for (loadedModel in currentLoadedModels) {
                    if (loadedModel.id != requiredModelId) {
                        llmRepository.unloadModel(loadedModel.id)
                    }
                }
            }
            
            // Now load the required model
            val loadResult = llmRepository.loadModel(requiredModelId, ModelConfig())
            loadResult.fold(
                onSuccess = {
                    // Model loaded successfully
                    _uiState.update {
                        it.copy(
                            isLoadingModel = false,
                            loadingModelId = null,
                            loadingModelName = null
                        )
                    }
                    // Notify that model state changed
                    onModelStateChanged?.invoke()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingModel = false,
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
                    isLoadingModel = false,
                    loadingModelId = null,
                    loadingModelName = null,
                    errorMessage = "Failed to manage model loading: ${e.message}"
                )
            }
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
    
    fun updateChatSession(session: ChatSession) {
        viewModelScope.launch {
            try {
                val result = llmRepository.updateChatSession(session)
                result.fold(
                    onSuccess = {
                        // Update the current session if it's the one being updated
                        _uiState.update { state ->
                            val updatedSessions = state.chatSessions.map { 
                                if (it.id == session.id) session else it 
                            }
                            state.copy(
                                chatSessions = updatedSessions,
                                currentChatSession = if (state.currentChatSession?.id == session.id) session else state.currentChatSession
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(errorMessage = "Failed to update chat session: ${error.message}")
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Error updating chat session: ${e.message}")
                }
            }
        }
    }
    
    fun refreshChatSessions() {
        loadChatSessions()
    }
}

data class ChatUiState(
    val chatSessions: List<ChatSession> = emptyList(),
    val currentChatSession: ChatSession? = null,
    val currentMessage: String = "",
    val isGenerating: Boolean = false,
    val isLoadingModel: Boolean = false,
    val loadingModelId: String? = null,
    val loadingModelName: String? = null,
    val errorMessage: String? = null
) 