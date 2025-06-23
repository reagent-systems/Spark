package com.example.spark.network.server

import android.content.Context
import android.util.Log
import com.example.spark.domain.repository.LLMRepository
import com.example.spark.domain.models.ModelConfig
import com.example.spark.network.api.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.*

class ApiServer(
    private val context: Context,
    private val llmRepository: LLMRepository,
    private val port: Int = 8080
) {
    private var server: NettyApplicationEngine? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var defaultModelConfig: ModelConfig = ModelConfig()
    
    fun updateDefaultModelConfig(config: ModelConfig) {
        defaultModelConfig = config
        Log.d("ApiServer", "Updated default model config: $config")
    }
    
    fun getDefaultModelConfig(): ModelConfig = defaultModelConfig
    
    fun start() {
        scope.launch {
            try {
                Log.d("ApiServer", "Starting API server on port $port")
                server = embeddedServer(Netty, port = port, host = "0.0.0.0") {
                    install(ContentNegotiation) {
                        json(Json {
                            prettyPrint = true
                            isLenient = true
                            ignoreUnknownKeys = true
                        })
                    }
                    
                    install(CORS) {
                        anyHost()
                        allowMethod(HttpMethod.Options)
                        allowMethod(HttpMethod.Get)
                        allowMethod(HttpMethod.Post)
                        allowMethod(HttpMethod.Put)
                        allowMethod(HttpMethod.Delete)
                        allowHeader(HttpHeaders.ContentType)
                        allowHeader(HttpHeaders.Authorization)
                    }
                    
                    routing {
                        // Health check
                        get("/health") {
                            Log.d("ApiServer", "Health check endpoint called")
                            call.respond(mapOf("status" to "ok", "timestamp" to System.currentTimeMillis()))
                        }
                        
                        // OpenAI-compatible endpoints
                        get("/v1/models") {
                            try {
                                Log.d("ApiServer", "GET /v1/models endpoint called")
                                val models = llmRepository.getLoadedModels()
                                Log.d("ApiServer", "Found ${models.size} loaded models")
                                val modelInfos = models.map { model ->
                                    ModelInfo(
                                        id = model.id,
                                        created = System.currentTimeMillis() / 1000,
                                        owned_by = "spark"
                                    )
                                }
                                call.respond(ModelsResponse(data = modelInfos))
                            } catch (e: Exception) {
                                Log.e("ApiServer", "Error getting models", e)
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    ErrorResponse(ErrorDetail("Internal server error", "server_error"))
                                )
                            }
                        }
                        
                        post("/v1/chat/completions") {
                            try {
                                val request = call.receive<ChatCompletionRequest>()
                                
                                // Check if model is loaded
                                if (!llmRepository.isModelLoaded(request.model)) {
                                    call.respond(
                                        HttpStatusCode.BadRequest,
                                        ErrorResponse(ErrorDetail("Model not found or not loaded", "model_not_found"))
                                    )
                                    return@post
                                }
                                
                                // Build prompt from messages
                                val prompt = buildPromptFromMessages(request.messages)
                                
                                // Use request parameters, but if they're at default values, 
                                // prefer the server's configured defaults
                                val config = ModelConfig(
                                    maxTokens = if (request.max_tokens == 1000) defaultModelConfig.maxTokens else request.max_tokens,
                                    temperature = if (request.temperature == 0.8f) defaultModelConfig.temperature else request.temperature,
                                    topK = if (request.top_k == 40) defaultModelConfig.topK else request.top_k,
                                    randomSeed = defaultModelConfig.randomSeed,
                                    useGpu = defaultModelConfig.useGpu
                                )
                                
                                val result = llmRepository.generateResponseSync(
                                    request.model,
                                    prompt,
                                    config
                                )
                                
                                result.fold(
                                    onSuccess = { response ->
                                        val chatResponse = ChatCompletionResponse(
                                            id = "chatcmpl-${UUID.randomUUID()}",
                                            created = System.currentTimeMillis() / 1000,
                                            model = request.model,
                                            choices = listOf(
                                                Choice(
                                                    index = 0,
                                                    message = Message("assistant", response),
                                                    finish_reason = "stop"
                                                )
                                            )
                                        )
                                        call.respond(chatResponse)
                                    },
                                    onFailure = { error ->
                                        Log.e("ApiServer", "Error generating response", error)
                                        call.respond(
                                            HttpStatusCode.InternalServerError,
                                            ErrorResponse(ErrorDetail(error.message ?: "Unknown error", "generation_error"))
                                        )
                                    }
                                )
                            } catch (e: Exception) {
                                Log.e("ApiServer", "Error in chat completion", e)
                                call.respond(
                                    HttpStatusCode.BadRequest,
                                    ErrorResponse(ErrorDetail("Invalid request format", "bad_request"))
                                )
                            }
                        }
                        
                        // Custom endpoints for model management
                        get("/spark/models") {
                            try {
                                val models = llmRepository.getAvailableModels()
                                call.respond(models)
                            } catch (e: Exception) {
                                Log.e("ApiServer", "Error getting all models", e)
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    ErrorResponse(ErrorDetail("Internal server error", "server_error"))
                                )
                            }
                        }
                        
                        post("/spark/models/{modelId}/load") {
                            try {
                                val modelId = call.parameters["modelId"] ?: return@post call.respond(
                                    HttpStatusCode.BadRequest,
                                    ErrorResponse(ErrorDetail("Model ID required", "bad_request"))
                                )
                                
                                val result = llmRepository.loadModel(modelId)
                                result.fold(
                                    onSuccess = {
                                        call.respond(mapOf("status" to "loaded", "modelId" to modelId))
                                    },
                                    onFailure = { error ->
                                        call.respond(
                                            HttpStatusCode.BadRequest,
                                            ErrorResponse(ErrorDetail(error.message ?: "Failed to load model", "load_error"))
                                        )
                                    }
                                )
                            } catch (e: Exception) {
                                Log.e("ApiServer", "Error loading model", e)
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    ErrorResponse(ErrorDetail("Internal server error", "server_error"))
                                )
                            }
                        }
                        
                        post("/spark/models/{modelId}/unload") {
                            try {
                                val modelId = call.parameters["modelId"] ?: return@post call.respond(
                                    HttpStatusCode.BadRequest,
                                    ErrorResponse(ErrorDetail("Model ID required", "bad_request"))
                                )
                                
                                val result = llmRepository.unloadModel(modelId)
                                result.fold(
                                    onSuccess = {
                                        call.respond(mapOf("status" to "unloaded", "modelId" to modelId))
                                    },
                                    onFailure = { error ->
                                        call.respond(
                                            HttpStatusCode.BadRequest,
                                            ErrorResponse(ErrorDetail(error.message ?: "Failed to unload model", "unload_error"))
                                        )
                                    }
                                )
                            } catch (e: Exception) {
                                Log.e("ApiServer", "Error unloading model", e)
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    ErrorResponse(ErrorDetail("Internal server error", "server_error"))
                                )
                            }
                        }
                        
                        // Model configuration endpoints
                        get("/spark/config") {
                            try {
                                call.respond(defaultModelConfig)
                            } catch (e: Exception) {
                                Log.e("ApiServer", "Error getting model config", e)
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    ErrorResponse(ErrorDetail("Internal server error", "server_error"))
                                )
                            }
                        }
                        
                        post("/spark/config") {
                            try {
                                val newConfig = call.receive<ModelConfig>()
                                updateDefaultModelConfig(newConfig)
                                call.respond(mapOf("status" to "updated", "config" to newConfig))
                            } catch (e: Exception) {
                                Log.e("ApiServer", "Error updating model config", e)
                                call.respond(
                                    HttpStatusCode.BadRequest,
                                    ErrorResponse(ErrorDetail("Invalid config format", "bad_request"))
                                )
                            }
                        }
                    }
                }.start(wait = false)
                
                Log.i("ApiServer", "API Server started on port $port")
            } catch (e: Exception) {
                Log.e("ApiServer", "Failed to start API server", e)
            }
        }
    }
    
    fun stop() {
        server?.stop(1000, 2000)
        server = null
        Log.i("ApiServer", "API Server stopped")
    }
    
    fun isRunning(): Boolean {
        return server != null
    }
    
    fun getPort(): Int = port
    
    private fun buildPromptFromMessages(messages: List<Message>): String {
        return messages.joinToString("\n\n") { message ->
            when (message.role) {
                "system" -> "System: ${message.content}"
                "user" -> "User: ${message.content}"
                "assistant" -> "Assistant: ${message.content}"
                else -> "${message.role}: ${message.content}"
            }
        } + "\n\nAssistant:"
    }
} 