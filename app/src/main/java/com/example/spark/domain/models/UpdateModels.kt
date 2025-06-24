package com.example.spark.domain.models

import kotlinx.serialization.Serializable

/**
 * Represents update information from the CDN
 */
@Serializable
data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val downloadUrl: String,
    val changelog: List<ChangelogItem>
)

/**
 * Individual changelog item
 */
@Serializable
data class ChangelogItem(
    val type: ChangeType,
    val description: String
)

/**
 * Types of changes in the changelog
 */
@Serializable
enum class ChangeType {
    NEW_FEATURE,
    IMPROVEMENT,
    BUG_FIX,
    BREAKING_CHANGE,
    SECURITY,
    DEPRECATED
}

/**
 * Current update status
 */
data class UpdateStatus(
    val isUpdateAvailable: Boolean = false,
    val updateInfo: UpdateInfo? = null,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val error: String? = null,
    val lastChecked: Long = 0L
)

/**
 * Update check result
 */
sealed class UpdateCheckResult {
    object NoUpdateAvailable : UpdateCheckResult()
    data class UpdateAvailable(val updateInfo: UpdateInfo) : UpdateCheckResult()
    data class Error(val message: String, val throwable: Throwable? = null) : UpdateCheckResult()
} 