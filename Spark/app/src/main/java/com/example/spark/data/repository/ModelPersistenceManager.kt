package com.example.spark.data.repository

import android.content.Context
import android.util.Log
import com.example.spark.domain.models.LLMModel
import com.example.spark.domain.models.ChatSession
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class ModelPersistenceManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "ModelPersistenceManager"
        private const val MODELS_FILE = "models_list.json"
        private const val CHAT_SESSIONS_FILE = "chat_sessions.json"
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    
    fun loadPersistedModels(): List<LLMModel> {
        return try {
            val modelsFile = File(context.filesDir, MODELS_FILE)
            if (modelsFile.exists()) {
                Log.d(TAG, "Loading persisted models from: ${modelsFile.absolutePath}")
                val jsonString = modelsFile.readText()
                val models = json.decodeFromString<List<LLMModel>>(jsonString)
                
                // Verify files still exist and filter out missing ones
                val validModels = models.filter { model ->
                    val file = File(model.filePath)
                    if (file.exists()) {
                        Log.d(TAG, "Found persisted model: ${model.name}")
                        true
                    } else {
                        Log.w(TAG, "Persisted model file no longer exists: ${model.filePath}")
                        false
                    }
                }
                
                Log.d(TAG, "Loaded ${validModels.size} persisted models")
                validModels
            } else {
                Log.d(TAG, "No persisted models file found")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load persisted models", e)
            emptyList()
        }
    }
    
    fun savePersistedModels(models: List<LLMModel>) {
        try {
            val modelsFile = File(context.filesDir, MODELS_FILE)
            val jsonString = json.encodeToString(models)
            modelsFile.writeText(jsonString)
            Log.d(TAG, "Saved ${models.size} models to persistence")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save persisted models", e)
        }
    }
    
    fun loadPersistedChatSessions(): List<ChatSession> {
        return try {
            val chatSessionsFile = File(context.filesDir, CHAT_SESSIONS_FILE)
            if (chatSessionsFile.exists()) {
                Log.d(TAG, "Loading persisted chat sessions from: ${chatSessionsFile.absolutePath}")
                val jsonString = chatSessionsFile.readText()
                val sessions = json.decodeFromString<List<ChatSession>>(jsonString)
                Log.d(TAG, "Loaded ${sessions.size} persisted chat sessions")
                sessions
            } else {
                Log.d(TAG, "No persisted chat sessions file found")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load persisted chat sessions", e)
            emptyList()
        }
    }
    
    fun savePersistedChatSessions(sessions: List<ChatSession>) {
        try {
            val chatSessionsFile = File(context.filesDir, CHAT_SESSIONS_FILE)
            val jsonString = json.encodeToString(sessions)
            chatSessionsFile.writeText(jsonString)
            Log.d(TAG, "Saved ${sessions.size} chat sessions to persistence")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save persisted chat sessions", e)
        }
    }
    
    fun initializeDefaultModels(): List<LLMModel> {
        val foundModels = mutableListOf<LLMModel>()
        
        // Check for models in the default directory
        val modelsDir = File(context.filesDir, "models")
        if (modelsDir.exists()) {
            modelsDir.listFiles()?.forEach { file ->
                if (file.extension == "task") {
                    // Use filename without extension as ID for consistency
                    val modelId = file.nameWithoutExtension
                    val model = LLMModel(
                        id = modelId,
                        name = file.nameWithoutExtension.replace("_", " ").replace("-", " "),
                        description = "Local model",
                        filePath = file.absolutePath,
                        size = file.length()
                    )
                    foundModels.add(model)
                    Log.d(TAG, "Found model in directory: ${model.name}")
                }
            }
        }
        
        return foundModels
    }
} 