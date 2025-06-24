package com.example.spark.domain.repository

import com.example.spark.domain.models.LLMModel
import com.example.spark.domain.models.ChatMessage
import com.example.spark.domain.models.ChatSession
import com.example.spark.domain.models.ModelConfig
import kotlinx.coroutines.flow.Flow

interface LLMRepository {
    suspend fun getAvailableModels(): List<LLMModel>
    suspend fun loadModel(modelId: String, config: ModelConfig = ModelConfig()): Result<Unit>
    suspend fun unloadModel(modelId: String): Result<Unit>
    suspend fun generateResponse(
        modelId: String, 
        prompt: String, 
        config: ModelConfig
    ): Flow<String>
    suspend fun generateResponseSync(
        modelId: String, 
        prompt: String, 
        config: ModelConfig
    ): Result<String>
    suspend fun generateResponseStream(
        modelId: String, 
        prompt: String, 
        config: ModelConfig
    ): Flow<String>
    suspend fun addModel(filePath: String, name: String, description: String): Result<LLMModel>
    suspend fun removeModel(modelId: String): Result<Unit>
    suspend fun getLoadedModels(): List<LLMModel>
    suspend fun isModelLoaded(modelId: String): Boolean
    
    // Chat session management
    suspend fun createChatSession(name: String, modelId: String): ChatSession
    suspend fun getChatSessions(): List<ChatSession>
    suspend fun getChatSession(sessionId: String): ChatSession?
    suspend fun addMessageToSession(sessionId: String, message: ChatMessage): Result<Unit>
    suspend fun updateChatSession(session: ChatSession): Result<Unit>
    suspend fun deleteChatSession(sessionId: String): Result<Unit>
    
    // Model downloading
    suspend fun downloadModel(
        availableModel: com.example.spark.domain.models.AvailableModel,
        onProgress: (Float) -> Unit
    ): Result<LLMModel>
    
    // Cancel model download
    suspend fun cancelDownload(modelId: String): Result<Unit>
    
    // Model deletion
    suspend fun deleteModel(modelId: String): Result<Unit>
} 