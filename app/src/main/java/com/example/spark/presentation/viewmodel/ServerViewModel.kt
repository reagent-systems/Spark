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
                apiServer.start()
                val localIp = NetworkUtils.getLocalIpAddress() ?: "localhost"
                _uiState.update {
                    it.copy(
                        isServerRunning = true,
                        serverPort = apiServer.getPort(),
                        serverLocalIp = localIp
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Failed to start server: ${e.message}")
                }
            }
        }
    }
    
    fun stopServer() {
        viewModelScope.launch {
            try {
                apiServer.stop()
                _uiState.update {
                    it.copy(
                        isServerRunning = false,
                        serverLocalIp = ""
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Failed to stop server: ${e.message}")
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