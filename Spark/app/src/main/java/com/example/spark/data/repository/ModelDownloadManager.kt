package com.example.spark.data.repository

import android.content.Context
import android.util.Log
import com.example.spark.domain.models.AvailableModel
import com.example.spark.domain.models.LLMModel
import com.example.spark.utils.HuggingFaceAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class ModelDownloadManager(
    private val context: Context,
    private val huggingFaceAuth: HuggingFaceAuth
) {
    companion object {
        private const val TAG = "ModelDownloadManager"
    }
    
    private val downloadJobs = ConcurrentHashMap<String, Job>()
    
    suspend fun downloadModel(
        availableModel: AvailableModel,
        onProgress: (Float) -> Unit
    ): Result<LLMModel> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting download for model: ${availableModel.name}")
        
        // Store the current job for cancellation
        currentCoroutineContext()[Job]?.let { job ->
            downloadJobs[availableModel.id] = job
        }
        
        try {
            // Create models directory if it doesn't exist
            val modelsDir = File(context.filesDir, "models")
            if (!modelsDir.exists()) {
                Log.d(TAG, "Creating models directory: ${modelsDir.absolutePath}")
                modelsDir.mkdirs()
            }
            
            // Create target file
            val originalFileName = availableModel.downloadUrl.substringAfterLast("/")
            val fileExtension = if (originalFileName.contains(".")) {
                originalFileName.substringAfterLast(".")
            } else {
                "task" // Default to .task if no extension found
            }
            val fileName = "${availableModel.id}.$fileExtension"
            val targetFile = File(modelsDir, fileName)
            
            // Check if model already exists
            if (targetFile.exists()) {
                Log.d(TAG, "Model already exists: ${targetFile.absolutePath}")
                val existingModel = LLMModel(
                    id = availableModel.id,
                    name = availableModel.name,
                    description = availableModel.description,
                    filePath = targetFile.absolutePath,
                    size = targetFile.length(),
                    modelType = availableModel.modelType
                )
                return@withContext Result.success(existingModel)
            }
            
            Log.d(TAG, "Downloading from URL: ${availableModel.downloadUrl}")
            
            // Check if authentication is required for this model
            if (availableModel.needsHuggingFaceAuth) {
                Log.d(TAG, "Model requires HuggingFace authentication")
                val authResult = huggingFaceAuth.authenticate()
                if (authResult.isFailure) {
                    Log.e(TAG, "Authentication failed for model: ${availableModel.name}")
                    return@withContext Result.failure(authResult.exceptionOrNull() ?: Exception("Authentication failed"))
                }
                
                val accessToken = authResult.getOrNull()
                if (accessToken.isNullOrEmpty()) {
                    Log.e(TAG, "No access token available for authenticated download")
                    return@withContext Result.failure(Exception("No access token available"))
                }
                
                // Use OkHttp for authenticated download
                downloadWithAuthentication(availableModel, targetFile, accessToken, onProgress)
            } else {
                // Use standard download for non-authenticated models
                downloadWithoutAuthentication(availableModel, targetFile, onProgress)
            }
            
            Log.d(TAG, "Download completed: ${targetFile.absolutePath}")
            
            // Create LLMModel
            val model = LLMModel(
                id = availableModel.id,
                name = availableModel.name,
                description = availableModel.description,
                filePath = targetFile.absolutePath,
                size = targetFile.length(),
                modelType = availableModel.modelType
            )
            
            Log.d(TAG, "Successfully downloaded model: ${model.name}")
            Result.success(model)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download model: ${availableModel.name}", e)
            
            // Clean up partial file if download was cancelled or failed
            val modelsDir = File(context.filesDir, "models")
            val partialFiles = modelsDir.listFiles { file ->
                file.name.startsWith("${availableModel.id}.") && file.length() == 0L
            }
            
            partialFiles?.forEach { file ->
                if (file.exists()) {
                    file.delete()
                    Log.d(TAG, "Cleaned up empty file after failed download: ${file.absolutePath}")
                }
            }
            
            Result.failure(e)
        } finally {
            // Clean up the download job reference
            downloadJobs.remove(availableModel.id)
        }
    }
    
    private suspend fun downloadWithAuthentication(
        availableModel: AvailableModel,
        targetFile: File,
        accessToken: String,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(availableModel.downloadUrl)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }
            
            val contentLength = response.body?.contentLength() ?: -1L
            Log.d(TAG, "Authenticated download content length: $contentLength bytes")
            
            response.body?.byteStream()?.use { inputStream ->
                targetFile.outputStream().use { outputStream ->
                    val buffer = ByteArray(8192)
                    var totalBytesRead = 0L
                    var bytesRead: Int
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        // Check for cancellation
                        currentCoroutineContext().ensureActive()
                        
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        // Update progress
                        if (contentLength > 0) {
                            val progress = totalBytesRead.toFloat() / contentLength.toFloat()
                            onProgress(progress)
                        }
                    }
                }
            } ?: throw Exception("No response body")
        }
    }
    
    private suspend fun downloadWithoutAuthentication(
        availableModel: AvailableModel,
        targetFile: File,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        val url = URL(availableModel.downloadUrl)
        val connection = url.openConnection()
        val contentLength = connection.contentLength
        
        Log.d(TAG, "Standard download content length: $contentLength bytes")
        
        connection.getInputStream().use { inputStream ->
            targetFile.outputStream().use { outputStream ->
                val buffer = ByteArray(8192)
                var totalBytesRead = 0L
                var bytesRead: Int
                
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    // Check for cancellation
                    currentCoroutineContext().ensureActive()
                    
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    
                    // Update progress
                    if (contentLength > 0) {
                        val progress = totalBytesRead.toFloat() / contentLength.toFloat()
                        onProgress(progress)
                    }
                }
            }
        }
    }
    
    suspend fun cancelDownload(modelId: String): Result<Unit> {
        return try {
            Log.d(TAG, "Cancelling download for model: $modelId")
            val job = downloadJobs[modelId]
            if (job != null) {
                job.cancel()
                downloadJobs.remove(modelId)
                
                // Clean up partial file - search for files with this model ID
                val modelsDir = File(context.filesDir, "models")
                val partialFiles = modelsDir.listFiles { file ->
                    file.name.startsWith("${modelId}.")
                }
                
                partialFiles?.forEach { file ->
                    if (file.exists()) {
                        file.delete()
                        Log.d(TAG, "Cleaned up partial file after cancellation: ${file.absolutePath}")
                    }
                }
                
                Log.d(TAG, "Successfully cancelled download for model: $modelId")
                Result.success(Unit)
            } else {
                Log.w(TAG, "No active download found for model: $modelId")
                Result.failure(Exception("No active download found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel download for model: $modelId", e)
            Result.failure(e)
        }
    }
} 