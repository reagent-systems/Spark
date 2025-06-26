package com.example.spark.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.example.spark.domain.models.*
import com.example.spark.domain.repository.UpdateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class UpdateRepositoryImpl(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder().build()
) : UpdateRepository {
    
    companion object {
        private const val TAG = "UpdateRepositoryImpl"
        private const val UPDATE_CHECK_URL = "https://cdn.bentlybro.com/Spark/update.json"
        private const val PREFS_NAME = "update_prefs"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
        private const val DOWNLOAD_TIMEOUT_MS = 30_000L
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    
    private val _updateStatus = MutableStateFlow(UpdateStatus())
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private var currentCall: Call? = null
    
    override suspend fun checkForUpdates(): UpdateCheckResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Checking for updates...")
        
        try {
            val request = Request.Builder()
                .url(UPDATE_CHECK_URL)
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "Update check failed with code: ${response.code}")
                return@withContext UpdateCheckResult.Error("Failed to check for updates: HTTP ${response.code}")
            }
            
            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                Log.e(TAG, "Empty response from update server")
                return@withContext UpdateCheckResult.Error("Empty response from update server")
            }
            
            Log.d(TAG, "Update response: $responseBody")
            
            val updateInfo = json.decodeFromString<UpdateInfo>(responseBody)
            Log.d(TAG, "Parsed update info: $updateInfo")
            
            // Compare versions
            val currentVersionCode = getCurrentVersionCode()
            val isUpdateAvailable = updateInfo.versionCode > currentVersionCode
            
            Log.d(TAG, "Current version code: $currentVersionCode, Remote version code: ${updateInfo.versionCode}")
            Log.d(TAG, "Update available: $isUpdateAvailable")
            
            // Update last check timestamp
            setLastUpdateCheck(System.currentTimeMillis())
            
            return@withContext if (isUpdateAvailable) {
                UpdateCheckResult.UpdateAvailable(updateInfo)
            } else {
                UpdateCheckResult.NoUpdateAvailable
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            return@withContext UpdateCheckResult.Error("Failed to check for updates: ${e.message}", e)
        }
    }
    
    override suspend fun downloadAndInstallUpdate(
        updateInfo: UpdateInfo,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting download of update: ${updateInfo.versionName}")
        
        try {
            // Update status to downloading
            _updateStatus.value = _updateStatus.value.copy(
                isDownloading = true,
                downloadProgress = 0f,
                error = null
            )
            
            val request = Request.Builder()
                .url(updateInfo.downloadUrl)
                .build()
            
            currentCall = httpClient.newCall(request)
            val response = currentCall!!.execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "Download failed with code: ${response.code}")
                _updateStatus.value = _updateStatus.value.copy(
                    isDownloading = false,
                    error = "Download failed: HTTP ${response.code}"
                )
                return@withContext Result.failure(Exception("Download failed: HTTP ${response.code}"))
            }
            
            val body = response.body
            if (body == null) {
                Log.e(TAG, "Response body is null")
                _updateStatus.value = _updateStatus.value.copy(
                    isDownloading = false,
                    error = "Download failed: Empty response"
                )
                return@withContext Result.failure(Exception("Download failed: Empty response"))
            }
            
            val contentLength = body.contentLength()
            Log.d(TAG, "Content length: $contentLength bytes")
            
            // Create download directory
            val downloadDir = File(context.getExternalFilesDir(null), "updates")
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            
            val apkFile = File(downloadDir, "spark-update-${updateInfo.versionName}.apk")
            
            // Download with progress tracking
            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(apkFile)
            
            val buffer = ByteArray(8192)
            var downloadedBytes = 0L
            var bytesRead: Int
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead
                
                if (contentLength > 0) {
                    val progress = downloadedBytes.toFloat() / contentLength.toFloat()
                    onProgress(progress)
                    
                    _updateStatus.value = _updateStatus.value.copy(
                        downloadProgress = progress
                    )
                }
            }
            
            outputStream.close()
            inputStream.close()
            
            Log.d(TAG, "Download completed. File size: ${apkFile.length()} bytes")
            
            // Install the APK
            installApk(apkFile)
            
            _updateStatus.value = _updateStatus.value.copy(
                isDownloading = false,
                downloadProgress = 1f
            )
            
            Log.d(TAG, "Update download and installation initiated successfully")
            return@withContext Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading update", e)
            _updateStatus.value = _updateStatus.value.copy(
                isDownloading = false,
                error = "Download failed: ${e.message}"
            )
            return@withContext Result.failure(e)
        }
    }
    
    override suspend fun cancelDownload(): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            currentCall?.cancel()
            currentCall = null
            
            _updateStatus.value = _updateStatus.value.copy(
                isDownloading = false,
                downloadProgress = 0f,
                error = "Download cancelled"
            )
            
            Log.d(TAG, "Download cancelled")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling download", e)
            Result.failure(e)
        }
    }
    
    override fun getUpdateStatusFlow(): Flow<UpdateStatus> = _updateStatus.asStateFlow()
    
    override suspend fun isDownloading(): Boolean = _updateStatus.value.isDownloading
    
    override suspend fun getLastUpdateCheck(): Long {
        return prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
    }
    
    override suspend fun setLastUpdateCheck(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, timestamp).apply()
    }
    
    private fun installApk(apkFile: File) {
        Log.d(TAG, "Installing APK: ${apkFile.absolutePath}")
        
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // Use FileProvider for Android N and above
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        apkFile
                    )
                    setDataAndType(uri, "application/vnd.android.package-archive")
                } else {
                    setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
                }
            }
            
            context.startActivity(intent)
            Log.d(TAG, "Installation intent started")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting APK installation", e)
            _updateStatus.value = _updateStatus.value.copy(
                error = "Failed to start installation: ${e.message}"
            )
        }
    }
    
    private fun getCurrentVersionCode(): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Could not get version code", e)
            1 // Default fallback
        }
    }
    
    private fun getCurrentVersionName(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Could not get version name", e)
            "1.0.0" // Default fallback
        }
    }

} 