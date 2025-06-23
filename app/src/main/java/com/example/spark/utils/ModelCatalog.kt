package com.example.spark.utils

import android.util.Log
import com.example.spark.domain.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.URL

object ModelCatalog {
    private const val TAG = "ModelCatalog"
    private const val CDN_URL = "https://cdn.bentlybro.com/Spark/models.json"
    
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    
    private var cachedCatalog: ModelCatalogResponse? = null
    private var lastFetchTime = 0L
    private const val CACHE_DURATION = 5 * 60 * 1000L // 5 minutes
    
    suspend fun fetchAvailableModels(): Result<List<AvailableModel>> = withContext(Dispatchers.IO) {
        try {
            // Check cache first
            val currentTime = System.currentTimeMillis()
            if (cachedCatalog != null && (currentTime - lastFetchTime) < CACHE_DURATION) {
                Log.d(TAG, "Returning cached models")
                return@withContext Result.success(cachedCatalog!!.models)
            }
            
            Log.d(TAG, "Fetching models from CDN: $CDN_URL")
            val jsonString = URL(CDN_URL).readText()
            Log.d(TAG, "Received JSON response: ${jsonString.take(200)}...")
            
            val catalog = json.decodeFromString<ModelCatalogResponse>(jsonString)
            cachedCatalog = catalog
            lastFetchTime = currentTime
            
            Log.d(TAG, "Successfully parsed ${catalog.models.size} models")
            Result.success(catalog.models)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch models from CDN", e)
            // Return fallback models if CDN fails
            Result.success(getFallbackModels())
        }
    }
    
    suspend fun getAvailableModels(): List<AvailableModel> {
        return fetchAvailableModels().getOrElse { 
            Log.w(TAG, "Using fallback models due to fetch failure")
            getFallbackModels() 
        }
    }
    
    private fun getFallbackModels(): List<AvailableModel> = listOf(
        // Fallback model in case CDN is unreachable
        AvailableModel(
            id = "gemma3-1b-it-int4",
            name = "Gemma 3 1B Instruct (INT4)",
            description = "Latest Gemma 3 model, optimized for mobile devices. Best balance of performance and quality.",
            downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task",
            modelType = ModelType.TEXT_ONLY,
            size = "555 MB",
            quantization = "INT4",
            contextLength = 2048,
            author = "Google",
            isRecommended = true,
            tags = listOf("recommended", "efficient", "mobile-optimized"),
            requirements = ModelRequirements("2GB", "4GB")
        )
    )
    
    suspend fun getModelById(id: String): AvailableModel? {
        return getAvailableModels().find { it.id == id }
    }
    
    suspend fun getRecommendedModels(): List<AvailableModel> {
        return getAvailableModels().filter { it.isRecommended }
    }
    
    suspend fun getModelsByType(type: ModelType): List<AvailableModel> {
        return getAvailableModels().filter { it.modelType == type }
    }
    
    suspend fun getModelsByTag(tag: String): List<AvailableModel> {
        return getAvailableModels().filter { it.tags.contains(tag) }
    }
    
    suspend fun getCategories(): List<ModelCategory> {
        return cachedCatalog?.categories ?: emptyList()
    }
    
    fun clearCache() {
        cachedCatalog = null
        lastFetchTime = 0L
        Log.d(TAG, "Cache cleared")
    }
} 