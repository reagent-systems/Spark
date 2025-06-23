package com.example.spark.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class LLMModel(
    val id: String,
    val name: String,
    val description: String,
    val filePath: String,
    val size: Long,
    val isLoaded: Boolean = false,
    val modelType: ModelType = ModelType.TEXT_ONLY,
    val maxTokens: Int = 1000,
    val temperature: Float = 0.8f,
    val topK: Int = 40,
    val isMultimodal: Boolean = false
)

enum class ModelType {
    TEXT_ONLY,
    MULTIMODAL
}

@Serializable
data class ModelConfig(
    val maxTokens: Int = 1000,
    val temperature: Float = 0.8f,
    val topK: Int = 40,
    val randomSeed: Int = 0
)

@Serializable
data class ChatMessage(
    val id: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long,
    val modelId: String? = null
)

@Serializable
data class ChatSession(
    val id: String,
    val name: String,
    val messages: List<ChatMessage>,
    val modelId: String,
    val createdAt: Long,
    val updatedAt: Long
) 