package com.example.spark.network.api

import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Float = 0.8f,
    val max_tokens: Int = 1000,
    val top_k: Int = 40,
    val stream: Boolean = false
)

@Serializable
data class Message(
    val role: String, // "user", "assistant", "system"
    val content: String
)

@Serializable
data class ChatCompletionResponse(
    val id: String,
    val `object`: String = "chat.completion",
    val created: Long,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage? = null
)

@Serializable
data class Choice(
    val index: Int,
    val message: Message,
    val finish_reason: String? = null
)

@Serializable
data class Usage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)

@Serializable
data class ModelInfo(
    val id: String,
    val `object`: String = "model",
    val created: Long,
    val owned_by: String = "spark"
)

@Serializable
data class ModelsResponse(
    val `object`: String = "list",
    val data: List<ModelInfo>
)

@Serializable
data class ErrorResponse(
    val error: ErrorDetail
)

@Serializable
data class ErrorDetail(
    val message: String,
    val type: String,
    val code: String? = null
) 