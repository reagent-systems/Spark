package com.example.spark.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spark.domain.models.*
import com.example.spark.domain.repository.UpdateRepository
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class UpdateViewModel(
    private val updateRepository: UpdateRepository,
    private val context: Context
) : ViewModel() {
    
    companion object {
        private const val TAG = "UpdateViewModel"
        private const val AUTO_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours
    }
    
    data class UpdateUiState(
        val isCheckingForUpdates: Boolean = false,
        val updateStatus: UpdateStatus = UpdateStatus(),
        val showUpdateDialog: Boolean = false,
        val showChangelogDialog: Boolean = false,
        val availableUpdate: UpdateInfo? = null,
        val error: String? = null,
        val lastChecked: String = "Never",
        val isDownloading: Boolean = false,
        val downloadProgress: Float = 0f
    )
    
    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()
    
    init {
        // Observe update status from repository
        viewModelScope.launch {
            updateRepository.getUpdateStatusFlow().collect { status ->
                _uiState.update { currentState ->
                    currentState.copy(
                        updateStatus = status,
                        error = status.error,
                        isDownloading = status.isDownloading,
                        downloadProgress = status.downloadProgress
                    )
                }
            }
        }
        
        // Load last check time
        loadLastCheckTime()
        
        // Auto-check for updates if it's been more than 24 hours
        viewModelScope.launch {
            val lastCheck = updateRepository.getLastUpdateCheck()
            val now = System.currentTimeMillis()
            if (now - lastCheck > AUTO_CHECK_INTERVAL_MS) {
                Log.d(TAG, "Auto-checking for updates (last check was ${(now - lastCheck) / (60 * 60 * 1000)} hours ago)")
                checkForUpdatesInternal(showLoading = false)
            }
        }
    }
    
    fun checkForUpdates() {
        checkForUpdatesInternal(showLoading = true)
    }
    
    private fun checkForUpdatesInternal(showLoading: Boolean) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.update { it.copy(isCheckingForUpdates = true, error = null) }
            }
            
            try {
                when (val result = updateRepository.checkForUpdates()) {
                    is UpdateCheckResult.UpdateAvailable -> {
                        Log.d(TAG, "Update available: ${result.updateInfo.versionName}")
                        _uiState.update { currentState ->
                            currentState.copy(
                                isCheckingForUpdates = false,
                                availableUpdate = result.updateInfo,
                                showUpdateDialog = true,
                                updateStatus = currentState.updateStatus.copy(
                                    isUpdateAvailable = true,
                                    updateInfo = result.updateInfo
                                )
                            )
                        }
                    }
                    
                    is UpdateCheckResult.NoUpdateAvailable -> {
                        Log.d(TAG, "No update available")
                        _uiState.update { currentState ->
                            currentState.copy(
                                isCheckingForUpdates = false,
                                availableUpdate = null,
                                updateStatus = currentState.updateStatus.copy(
                                    isUpdateAvailable = false,
                                    updateInfo = null
                                )
                            )
                        }
                    }
                    
                    is UpdateCheckResult.Error -> {
                        Log.e(TAG, "Update check error: ${result.message}")
                        _uiState.update { currentState ->
                            currentState.copy(
                                isCheckingForUpdates = false,
                                error = result.message
                            )
                        }
                    }
                }
                
                loadLastCheckTime()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during update check", e)
                _uiState.update { currentState ->
                    currentState.copy(
                        isCheckingForUpdates = false,
                        error = "Failed to check for updates: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun downloadAndInstallUpdate() {
        val updateInfo = _uiState.value.availableUpdate ?: return
        
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting download and install for version: ${updateInfo.versionName}")
                
                val result = updateRepository.downloadAndInstallUpdate(updateInfo) { progress ->
                    // Progress is handled by the repository's status flow
                }
                
                result.fold(
                    onSuccess = {
                        Log.d(TAG, "Update download and installation initiated successfully")
                        hideUpdateDialog()
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Update download/installation failed", error)
                        _uiState.update { it.copy(error = "Update failed: ${error.message}") }
                    }
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during update download/installation", e)
                _uiState.update { it.copy(error = "Update failed: ${e.message}") }
            }
        }
    }
    
    fun cancelDownload() {
        viewModelScope.launch {
            updateRepository.cancelDownload()
        }
    }
    
    fun showUpdateDialog() {
        _uiState.update { it.copy(showUpdateDialog = true) }
    }
    
    fun hideUpdateDialog() {
        _uiState.update { it.copy(showUpdateDialog = false) }
    }
    
    fun showChangelogDialog() {
        _uiState.update { it.copy(showChangelogDialog = true) }
    }
    
    fun hideChangelogDialog() {
        _uiState.update { it.copy(showChangelogDialog = false) }
    }
    
    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
    
    private fun loadLastCheckTime() {
        viewModelScope.launch {
            val lastCheck = updateRepository.getLastUpdateCheck()
            val lastCheckedText = if (lastCheck > 0) {
                val formatter = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
                formatter.format(Date(lastCheck))
            } else {
                "Never"
            }
            
            _uiState.update { it.copy(lastChecked = lastCheckedText) }
        }
    }
    
    fun getChangeTypeDisplayName(type: ChangeType): String {
        return when (type) {
            ChangeType.NEW_FEATURE -> "New Feature"
            ChangeType.IMPROVEMENT -> "Improvement"
            ChangeType.BUG_FIX -> "Bug Fix"
            ChangeType.BREAKING_CHANGE -> "Breaking Change"
            ChangeType.SECURITY -> "Security"
            ChangeType.DEPRECATED -> "Deprecated"
        }
    }
    
    fun getChangeTypeColor(type: ChangeType): androidx.compose.ui.graphics.Color {
        return when (type) {
            ChangeType.NEW_FEATURE -> androidx.compose.ui.graphics.Color(0xFF4CAF50) // Green
            ChangeType.IMPROVEMENT -> androidx.compose.ui.graphics.Color(0xFF2196F3) // Blue
            ChangeType.BUG_FIX -> androidx.compose.ui.graphics.Color(0xFFFF9800) // Orange
            ChangeType.BREAKING_CHANGE -> androidx.compose.ui.graphics.Color(0xFFF44336) // Red
            ChangeType.SECURITY -> androidx.compose.ui.graphics.Color(0xFF9C27B0) // Purple
            ChangeType.DEPRECATED -> androidx.compose.ui.graphics.Color(0xFF795548) // Brown
        }
    }
    
    private fun getCurrentVersionDisplay(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "v${packageInfo.versionName}"
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Could not get version name", e)
            "v1.0.0" // Default fallback
        }
    }
} 