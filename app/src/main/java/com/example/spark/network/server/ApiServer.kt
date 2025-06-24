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
import kotlinx.coroutines.flow.collect
import io.ktor.http.ContentType
import io.ktor.server.response.respondTextWriter
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class ApiServer(
    private val context: Context,
    private val llmRepository: LLMRepository,
    private val port: Int = 8080
) {
    private var server: NettyApplicationEngine? = null
    
    // Use dedicated scope for server operations to prevent blocking
    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val serverMutex = Mutex()
    private val isStarting = AtomicBoolean(false)
    private val isStopping = AtomicBoolean(false)
    
    private var defaultModelConfig: ModelConfig = ModelConfig()
    
    fun updateDefaultModelConfig(config: ModelConfig) {
        defaultModelConfig = config
        Log.d("ApiServer", "Updated default model config: $config")
    }
    
    fun getDefaultModelConfig(): ModelConfig = defaultModelConfig
    
    suspend fun start() = withContext(Dispatchers.IO) {
        serverMutex.withLock {
            if (server != null) {
                Log.d("ApiServer", "Server already running")
                return@withLock
            }
            
            if (isStarting.getAndSet(true)) {
                Log.d("ApiServer", "Server already starting")
                return@withLock
            }
            
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
                                
                                // Use background dispatcher for model operations
                                val models = withContext(Dispatchers.Default) {
                                    llmRepository.getLoadedModels()
                                }
                                
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
                                
                                // Use background dispatcher for model operations
                                val isModelLoaded = withContext(Dispatchers.Default) {
                                    llmRepository.isModelLoaded(request.model)
                                }
                                
                                if (!isModelLoaded) {
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
                                    enableStreaming = request.stream
                                )
                                
                                if (request.stream) {
                                    // Handle streaming response with Server-Sent Events
                                    call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                                        val chatId = "chatcmpl-${UUID.randomUUID()}"
                                        val created = System.currentTimeMillis() / 1000
                                        
                                        try {
                                            withContext(Dispatchers.Default) {
                                                llmRepository.generateResponse(request.model, prompt, config).collect { chunk ->
                                                    val chunkResponse = ChatCompletionChunk(
                                                        id = chatId,
                                                        created = created,
                                                        model = request.model,
                                                        choices = listOf(
                                                            ChoiceChunk(
                                                                index = 0,
                                                                delta = MessageDelta(content = chunk)
                                                            )
                                                        )
                                                    )
                                                    
                                                    val jsonString = Json.encodeToString(ChatCompletionChunk.serializer(), chunkResponse)
                                                    write("data: $jsonString\n\n")
                                                    flush()
                                                }
                                            }
                                            
                                            // Send final chunk to indicate completion
                                            val finalChunk = ChatCompletionChunk(
                                                id = chatId,
                                                created = created,
                                                model = request.model,
                                                choices = listOf(
                                                    ChoiceChunk(
                                                        index = 0,
                                                        delta = MessageDelta(),
                                                        finish_reason = "stop"
                                                    )
                                                )
                                            )
                                            
                                            val finalJsonString = Json.encodeToString(ChatCompletionChunk.serializer(), finalChunk)
                                            write("data: $finalJsonString\n\n")
                                            write("data: [DONE]\n\n")
                                            flush()
                                            
                                        } catch (e: Exception) {
                                            Log.e("ApiServer", "Error in streaming response", e)
                                            write("data: {\"error\": \"${e.message}\"}\n\n")
                                            flush()
                                        }
                                    }
                                } else {
                                    // Handle non-streaming response (original behavior)
                                    val result = withContext(Dispatchers.Default) {
                                        llmRepository.generateResponseSync(
                                            request.model,
                                            prompt,
                                            config
                                        )
                                    }
                                    
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
                                }
                            } catch (e: Exception) {
                                Log.e("ApiServer", "Error in chat completion", e)
                                call.respond(
                                    HttpStatusCode.BadRequest,
                                    ErrorResponse(ErrorDetail("Invalid request format", "bad_request"))
                                )
                            }
                        }
                        
                        // Spark-specific endpoints
                        get("/spark/models") {
                            try {
                                // Use background dispatcher for model operations
                                val models = withContext(Dispatchers.Default) {
                                    llmRepository.getAvailableModels()
                                }
                                call.respond(models)
                            } catch (e: Exception) {
                                Log.e("ApiServer", "Error getting available models", e)
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    ErrorResponse(ErrorDetail("Internal server error", "server_error"))
                                )
                            }
                        }
                        
                        post("/spark/models/{modelId}/load") {
                            try {
                                val modelId = call.parameters["modelId"] ?: throw IllegalArgumentException("Missing modelId")
                                
                                // Load model on background dispatcher
                                val result = withContext(Dispatchers.Default) {
                                    llmRepository.loadModel(modelId, defaultModelConfig)
                                }
                                
                                result.fold(
                                    onSuccess = {
                                        call.respond(mapOf("status" to "loaded", "modelId" to modelId))
                                    },
                                    onFailure = { error ->
                                        Log.e("ApiServer", "Error loading model: $modelId", error)
                                        call.respond(
                                            HttpStatusCode.InternalServerError,
                                            ErrorResponse(ErrorDetail(error.message ?: "Failed to load model", "load_error"))
                                        )
                                    }
                                )
                            } catch (e: Exception) {
                                Log.e("ApiServer", "Error in model loading endpoint", e)
                                call.respond(
                                    HttpStatusCode.BadRequest,
                                    ErrorResponse(ErrorDetail("Invalid request", "bad_request"))
                                )
                            }
                        }
                        
                        post("/spark/models/{modelId}/unload") {
                            try {
                                val modelId = call.parameters["modelId"] ?: throw IllegalArgumentException("Missing modelId")
                                
                                // Unload model on background dispatcher
                                val result = withContext(Dispatchers.Default) {
                                    llmRepository.unloadModel(modelId)
                                }
                                
                                result.fold(
                                    onSuccess = {
                                        call.respond(mapOf("status" to "unloaded", "modelId" to modelId))
                                    },
                                    onFailure = { error ->
                                        Log.e("ApiServer", "Error unloading model: $modelId", error)
                                        call.respond(
                                            HttpStatusCode.InternalServerError,
                                            ErrorResponse(ErrorDetail(error.message ?: "Failed to unload model", "unload_error"))
                                        )
                                    }
                                )
                            } catch (e: Exception) {
                                Log.e("ApiServer", "Error in model unloading endpoint", e)
                                call.respond(
                                    HttpStatusCode.BadRequest,
                                    ErrorResponse(ErrorDetail("Invalid request", "bad_request"))
                                )
                            }
                        }
                        
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
                server = null
                throw e
            } finally {
                isStarting.set(false)
            }
        }
    }
    
    suspend fun stop() = withContext(Dispatchers.IO) {
        serverMutex.withLock {
            if (server == null) {
                Log.d("ApiServer", "Server not running")
                return@withLock
            }
            
            if (isStopping.getAndSet(true)) {
                Log.d("ApiServer", "Server already stopping")
                return@withLock
            }
            
            try {
                Log.d("ApiServer", "Stopping API server")
                server?.stop(1000, 2000)
                server = null
                Log.i("ApiServer", "API Server stopped")
            } catch (e: Exception) {
                Log.e("ApiServer", "Error stopping server", e)
                throw e
            } finally {
                isStopping.set(false)
            }
        }
    }
    
    fun isRunning(): Boolean {
        return server != null && !isStarting.get() && !isStopping.get()
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