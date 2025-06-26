package com.example.spark.domain.repository

import com.example.spark.domain.models.UpdateCheckResult
import com.example.spark.domain.models.UpdateInfo
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for handling app updates
 */
interface UpdateRepository {
    
    /**
     * Check for available updates from the CDN
     */
    suspend fun checkForUpdates(): UpdateCheckResult
    
    /**
     * Download and install an update
     * @param updateInfo The update information
     * @param onProgress Progress callback (0.0 to 1.0)
     */
    suspend fun downloadAndInstallUpdate(
        updateInfo: UpdateInfo,
        onProgress: (Float) -> Unit
    ): Result<Unit>
    
    /**
     * Cancel ongoing download
     */
    suspend fun cancelDownload(): Result<Unit>
    
    /**
     * Get update status as a flow
     */
    fun getUpdateStatusFlow(): Flow<com.example.spark.domain.models.UpdateStatus>
    
    /**
     * Check if an update is currently being downloaded
     */
    suspend fun isDownloading(): Boolean
    
    /**
     * Get the last update check timestamp
     */
    suspend fun getLastUpdateCheck(): Long
    
    /**
     * Set the last update check timestamp
     */
    suspend fun setLastUpdateCheck(timestamp: Long)
} 