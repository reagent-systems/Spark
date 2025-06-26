package com.example.spark.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ModelFileManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "ModelFileManager"
    }
    
    suspend fun getActualFilePath(originalPath: String, modelName: String): String {
        return if (originalPath.startsWith("content://")) {
            Log.d(TAG, "Converting content URI to file path for: $modelName")
            copyContentUriToInternalStorage(originalPath, modelName)
        } else {
            Log.d(TAG, "Using existing file path for: $modelName")
            originalPath
        }
    }
    
    private suspend fun copyContentUriToInternalStorage(contentUri: String, modelName: String): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "Copying content URI to internal storage: $contentUri")
        val uri = Uri.parse(contentUri)
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) {
            Log.d(TAG, "Creating models directory: ${modelsDir.absolutePath}")
            modelsDir.mkdirs()
        }
        
        val fileName = "${modelName.replace(" ", "_")}.task"
        val targetFile = File(modelsDir, fileName)
        Log.d(TAG, "Target file path: ${targetFile.absolutePath}")
        
        // If file already exists, return its path
        if (targetFile.exists()) {
            Log.d(TAG, "File already exists, using existing copy: ${targetFile.absolutePath}")
            return@withContext targetFile.absolutePath
        }
        
        Log.d(TAG, "Starting file copy operation...")
        // Copy the file
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            targetFile.outputStream().use { outputStream ->
                val bytesCopied = inputStream.copyTo(outputStream)
                Log.d(TAG, "Successfully copied $bytesCopied bytes to: ${targetFile.absolutePath}")
            }
        } ?: throw Exception("Could not open input stream for content URI")
        
        Log.d(TAG, "File copy completed: ${targetFile.absolutePath}")
        return@withContext targetFile.absolutePath
    }
    
    fun getFileSize(filePath: String): Long {
        return if (filePath.startsWith("content://")) {
            Log.d(TAG, "Getting file size for content URI")
            try {
                val uri = Uri.parse(filePath)
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.available().toLong()
                } ?: 0L
            } catch (e: Exception) {
                Log.w(TAG, "Could not determine file size for content URI", e)
                0L
            }
        } else {
            Log.d(TAG, "Getting file size for regular file path")
            val file = File(filePath)
            if (file.exists()) {
                file.length()
            } else {
                0L
            }
        }
    }
    
    fun deleteModelFile(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (file.exists()) {
                val deleted = file.delete()
                if (deleted) {
                    Log.d(TAG, "Successfully deleted model file: $filePath")
                } else {
                    Log.e(TAG, "Failed to delete model file: $filePath")
                }
                deleted
            } else {
                Log.w(TAG, "Model file does not exist: $filePath")
                true // Consider it successful if file doesn't exist
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting model file: $filePath", e)
            false
        }
    }
    
    fun fileExists(filePath: String): Boolean {
        return File(filePath).exists()
    }
} 