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
            // Set generating state immediately for instant visual feedback
            _uiState.update { 
                it.copy(
                    isGenerating = true,
                    currentMessage = "" // Clear input field immediately
                ) 
            }
            
            try {
                // Add user message first for immediate UI feedback
                val userMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    content = content,
                    isUser = true,
                    timestamp = System.currentTimeMillis(),
                    modelId = currentSession.modelId
                )
                
                llmRepository.addMessageToSession(currentSession.id, userMessage)
                
                // Update UI with user message immediately
                val updatedSession = llmRepository.getChatSession(currentSession.id)
                _uiState.update {
                    it.copy(currentChatSession = updatedSession)
                }
                
                // Ensure the correct model is loaded (in parallel with UI update)
                ensureCorrectModelLoaded(currentSession.modelId)
                
                // Construct the full prompt with system prompt and conversation history
                val fullPrompt = buildPrompt(
                    systemPrompt = modelConfig.systemPrompt.ifBlank { currentSession.systemPrompt },
                    conversationHistory = updatedSession?.messages ?: emptyList(),
                    currentMessage = content
                )
                
                // Generate AI response using the constructed prompt
                if (modelConfig.enableStreaming) {
                    // Handle real streaming response
                    val aiMessageId = UUID.randomUUID().toString()
                    
                    // Initialize streaming state with empty content
                    _uiState.update {
                        it.copy(
                            streamingMessageId = aiMessageId,
                            streamingContent = "" // Start with empty content
                        )
                    }
                    
                    var finalAccumulatedContent = ""
                    
                    llmRepository.generateResponse(
                        currentSession.modelId,
                        fullPrompt,
                        modelConfig
                    ).collect { cumulativeResponse ->
                        // MediaPipe tokens are now properly accumulated in the repository
                        // This receives the complete accumulated text: "A" -> "A light" -> "A light year" etc.
                        finalAccumulatedContent = cumulativeResponse
                        
                        // Debug logging
                        android.util.Log.d("ChatViewModel", "Streaming update: length=${cumulativeResponse.length}, content='${cumulativeResponse.take(100)}...'")
                        
                        // Update UI with the complete cumulative content
                        _uiState.update { currentState ->
                            currentState.copy(streamingContent = cumulativeResponse)
                        }
                    }
                    
                    // After streaming completes, use the final accumulated content
                    val accumulatedContent = finalAccumulatedContent
                    
                    // Create final AI message with complete content
                    val aiMessage = ChatMessage(
                        id = aiMessageId,
                        content = accumulatedContent,
                        isUser = false,
                        timestamp = System.currentTimeMillis(),
                        modelId = currentSession.modelId
                    )
                    
                    llmRepository.addMessageToSession(currentSession.id, aiMessage)
                    
                    // Clear streaming state and update session
                    val finalSession = llmRepository.getChatSession(currentSession.id)
                    _uiState.update {
                        it.copy(
                            currentChatSession = finalSession,
                            streamingMessageId = null,
                            streamingContent = ""
                        )
                    }
                } else {
                    // Handle non-streaming response (original behavior)
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
                }
                
                // Mark generation as complete
                _uiState.update {
                    it.copy(isGenerating = false)
                }
                
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        streamingMessageId = null,
                        streamingContent = "",
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
            it.copy(
                isGenerating = false,
                streamingMessageId = null,
                streamingContent = ""
            )
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
    
    fun editMessage(messageId: String, newContent: String) {
        val currentSession = _uiState.value.currentChatSession ?: return
        val currentConfig = _uiState.value.modelConfig
        
        viewModelScope.launch {
            try {
                // Find the message index
                val messageIndex = currentSession.messages.indexOfFirst { it.id == messageId }
                if (messageIndex == -1) return@launch
                
                // Create edited message
                val originalMessage = currentSession.messages[messageIndex]
                val editedMessage = originalMessage.copy(
                    content = newContent,
                    isEdited = true,
                    editedTimestamp = System.currentTimeMillis(),
                    originalContent = originalMessage.content
                )
                
                // Create new message list with messages up to and including the edited message
                val newMessages = currentSession.messages.take(messageIndex + 1).toMutableList()
                newMessages[messageIndex] = editedMessage
                
                // Update session with new messages
                val updatedSession = currentSession.copy(
                    messages = newMessages,
                    updatedAt = System.currentTimeMillis()
                )
                
                // Update repository and UI state
                llmRepository.updateChatSession(updatedSession)
                _uiState.update { it.copy(
                    currentChatSession = updatedSession,
                    currentMessage = "" // Clear input field
                ) }
                
                // If this was a user message, automatically trigger a new response
                if (editedMessage.isUser) {
                    // Don't send a new message, just trigger the response
                    val fullPrompt = buildPrompt(
                        systemPrompt = currentConfig.systemPrompt.ifBlank { currentSession.systemPrompt },
                        conversationHistory = newMessages,
                        currentMessage = "" // No new message needed
                    )
                    
                    _uiState.update { it.copy(isGenerating = true) }
                    
                    try {
                        if (currentConfig.enableStreaming) {
                            // Handle streaming response
                            val aiMessageId = UUID.randomUUID().toString()
                            
                            _uiState.update {
                                it.copy(
                                    streamingMessageId = aiMessageId,
                                    streamingContent = ""
                                )
                            }
                            
                            var finalAccumulatedContent = ""
                            
                            llmRepository.generateResponse(
                                currentSession.modelId,
                                fullPrompt,
                                currentConfig
                            ).collect { cumulativeResponse ->
                                finalAccumulatedContent = cumulativeResponse
                                _uiState.update { currentState ->
                                    currentState.copy(streamingContent = cumulativeResponse)
                                }
                            }
                            
                            // After streaming completes, add the AI message
                            val aiMessage = ChatMessage(
                                id = aiMessageId,
                                content = finalAccumulatedContent,
                                isUser = false,
                                timestamp = System.currentTimeMillis(),
                                modelId = currentSession.modelId
                            )
                            
                            val finalMessages = newMessages.toMutableList()
                            finalMessages.add(aiMessage)
                            
                            val finalSession = currentSession.copy(
                                messages = finalMessages,
                                updatedAt = System.currentTimeMillis()
                            )
                            
                            llmRepository.updateChatSession(finalSession)
                            
                            _uiState.update {
                                it.copy(
                                    currentChatSession = finalSession,
                                    streamingMessageId = null,
                                    streamingContent = "",
                                    isGenerating = false
                                )
                            }
                        } else {
                            // Handle non-streaming response
                            val response = llmRepository.generateResponse(
                                currentSession.modelId,
                                fullPrompt,
                                currentConfig
                            ).first()
                            
                            val aiMessage = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                content = response,
                                isUser = false,
                                timestamp = System.currentTimeMillis(),
                                modelId = currentSession.modelId
                            )
                            
                            val finalMessages = newMessages.toMutableList()
                            finalMessages.add(aiMessage)
                            
                            val finalSession = currentSession.copy(
                                messages = finalMessages,
                                updatedAt = System.currentTimeMillis()
                            )
                            
                            llmRepository.updateChatSession(finalSession)
                            
                            _uiState.update {
                                it.copy(
                                    currentChatSession = finalSession,
                                    isGenerating = false
                                )
                            }
                        }
                    } catch (e: Exception) {
                        _uiState.update {
                            it.copy(
                                isGenerating = false,
                                streamingMessageId = null,
                                streamingContent = "",
                                errorMessage = if (e is kotlinx.coroutines.CancellationException) {
                                    null
                                } else {
                                    "Error generating response: ${e.message}"
                                }
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Error editing message: ${e.message}")
                }
            }
        }
    }
    
    fun cancelEdit(messageId: String) {
        // No state changes needed for cancel, just UI reset
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
    val errorMessage: String? = null,
    val streamingMessageId: String? = null,
    val streamingContent: String = "",
    val modelConfig: ModelConfig = ModelConfig()
) 