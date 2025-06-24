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
    val randomSeed: Int = 0,
    val enableStreaming: Boolean = false
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

// Add new data class for available models that can be downloaded
@Serializable
data class AvailableModel(
    val id: String,
    val name: String,
    val description: String,
    val downloadUrl: String,
    val modelType: ModelType,
    val size: String,
    val quantization: String,
    val contextLength: Int,
    val author: String = "LiteRT Community",
    val isRecommended: Boolean = false,
    val tags: List<String> = emptyList(),
    val requirements: ModelRequirements? = null,
    val needsHuggingFaceAuth: Boolean = false
)

@Serializable
data class ModelRequirements(
    val minRam: String,
    val recommendedRam: String
)

@Serializable
data class ModelCatalogResponse(
    val version: String,
    val lastUpdated: String,
    val models: List<AvailableModel>,
    val categories: List<ModelCategory>
)

@Serializable
data class ModelCategory(
    val id: String,
    val name: String,
    val description: String
) 