package com.example.spark.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spark.domain.models.ModelConfig
import com.example.spark.domain.repository.LLMRepository
import com.example.spark.network.server.ApiServer
import com.example.spark.utils.NetworkUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.content.Context
import java.io.File

class ServerViewModel(
    private val llmRepository: LLMRepository,
    private val apiServer: ApiServer,
    private val context: Context
) : ViewModel() {
    
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    
    private val _uiState = MutableStateFlow(ServerUiState())
    val uiState: StateFlow<ServerUiState> = _uiState.asStateFlow()
    
    init {
        loadModelConfigAsync()
        updateServerStatus()
        
        // Periodically check server status to ensure UI stays in sync
        viewModelScope.launch {
            kotlinx.coroutines.flow.flow {
                while (true) {
                    emit(Unit)
                    delay(500) // Check every 500ms for more responsive UI
                }
            }.collect {
                val actualServerRunning = apiServer.isRunning()
                val currentState = _uiState.value
                
                android.util.Log.d("ServerViewModel", "Periodic check - server running: $actualServerRunning, UI state: ${currentState.isServerRunning}, loading: ${currentState.isLoading}")
                
                // Update if there's a mismatch (regardless of loading state to catch state changes)
                if (currentState.isServerRunning != actualServerRunning) {
                    _uiState.update { state ->
                        state.copy(
                            isServerRunning = actualServerRunning,
                            serverLocalIp = if (actualServerRunning) {
                                NetworkUtils.getLocalIpAddress() ?: "localhost"
                            } else "",
                            isLoading = false // Clear loading state when we detect a state change
                        )
                    }
                }
            }
        }
    }
    
    private fun updateServerStatus() {
        _uiState.update {
            it.copy(
                isServerRunning = apiServer.isRunning(),
                serverPort = apiServer.getPort()
            )
        }
    }
    
    fun startServer() {
        viewModelScope.launch {
            try {
                // Update UI to show starting state
                _uiState.update {
                    it.copy(isLoading = true, errorMessage = null)
                }
                
                // Start the server
                apiServer.start()
                
                // Wait for server to be fully ready with retries
                var isServerReady = false
                var attempts = 0
                while (!isServerReady && attempts < 10) {
                    delay(200) // Wait 200ms between checks
                    isServerReady = apiServer.isRunning()
                    android.util.Log.d("ServerViewModel", "Server ready check attempt $attempts: $isServerReady")
                    attempts++
                }
                
                // Get local IP
                val localIp = NetworkUtils.getLocalIpAddress() ?: "localhost"
                
                // Update UI with running state
                _uiState.update {
                    it.copy(
                        isServerRunning = isServerReady,
                        serverPort = apiServer.getPort(),
                        serverLocalIp = if (isServerReady) localIp else "",
                        isLoading = false,
                        errorMessage = if (!isServerReady) "Server started but not responding" else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to start server: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun stopServer() {
        viewModelScope.launch {
            try {
                // Update UI to show stopping state
                _uiState.update {
                    it.copy(isLoading = true, errorMessage = null)
                }
                
                // Stop the server
                apiServer.stop()
                
                // Small delay to ensure server is fully stopped
                delay(100)
                
                // Update UI with stopped state
                _uiState.update {
                    it.copy(
                        isServerRunning = apiServer.isRunning(),
                        serverLocalIp = "",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to stop server: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun updateModelConfig(newConfig: ModelConfig) {
        _uiState.update { it.copy(modelConfig = newConfig) }
        saveModelConfigAsync(newConfig)
        
        // Update the API server's default config
        apiServer.updateDefaultModelConfig(newConfig)
    }
    
    private fun loadModelConfigAsync() {
        viewModelScope.launch {
            try {
                val config = withContext(Dispatchers.IO) {
                    val configFile = File(context.filesDir, "model_config.json")
                    if (configFile.exists()) {
                        val configJson = configFile.readText()
                        json.decodeFromString<ModelConfig>(configJson)
                    } else {
                        ModelConfig() // Default config
                    }
                }
                
                _uiState.update { it.copy(modelConfig = config) }
                // Initialize API server with the loaded config
                apiServer.updateDefaultModelConfig(config)
            } catch (e: Exception) {
                // If loading fails, use default config
                val defaultConfig = ModelConfig()
                _uiState.update { it.copy(modelConfig = defaultConfig) }
                apiServer.updateDefaultModelConfig(defaultConfig)
            }
        }
    }
    
    private fun saveModelConfigAsync(config: ModelConfig) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val configFile = File(context.filesDir, "model_config.json")
                    val jsonString = json.encodeToString(config)
                    configFile.writeText(jsonString)
                }
            } catch (e: Exception) {
                // Handle save error silently
            }
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

data class ServerUiState(
    val isServerRunning: Boolean = false,
    val serverPort: Int = 8080,
    val serverLocalIp: String = "",
    val modelConfig: ModelConfig = ModelConfig(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) 