package com.example.spark.utils

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class HuggingFaceAuth(private val context: Context) {
    
    companion object {
        private const val TAG = "HuggingFaceAuth"
        private const val PREFS_NAME = "huggingface_auth"
        private const val KEY_ACCESS_TOKEN = "access_token"
        
        // HuggingFace URLs
        private const val HUGGINGFACE_TOKEN_URL = "https://huggingface.co/settings/tokens"
        private const val HUGGINGFACE_LOGIN_URL = "https://huggingface.co/login"
    }
    
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    suspend fun authenticate(): Result<String> = suspendCancellableCoroutine { continuation ->
        try {
            // Check if we already have a valid token
            val existingToken = getAccessToken()
            if (existingToken != null) {
                Log.d(TAG, "Using existing access token")
                continuation.resume(Result.success(existingToken))
                return@suspendCancellableCoroutine
            }
            
            openHuggingFaceTokenPage()
            
            continuation.resume(Result.failure(Exception("Please create a HuggingFace access token manually")))
            
        } catch (e: Exception) {
            Log.e(TAG, "Authentication failed", e)
            continuation.resume(Result.failure(e))
        }
    }
    
    private fun openHuggingFaceTokenPage() {
        try {
            val intent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            
            intent.launchUrl(context, Uri.parse(HUGGINGFACE_TOKEN_URL))
            Log.d(TAG, "Opened HuggingFace token page")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open HuggingFace token page", e)
            // Fallback to regular browser
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(HUGGINGFACE_TOKEN_URL))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to open browser", ex)
            }
        }
    }
    
    fun saveAccessToken(token: String) {
        sharedPrefs.edit()
            .putString(KEY_ACCESS_TOKEN, token)
            .apply()
        Log.d(TAG, "Access token stored successfully")
    }
    
    fun getAccessToken(): String? {
        return sharedPrefs.getString(KEY_ACCESS_TOKEN, null)
    }
    
    fun clearAccessToken() {
        sharedPrefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .apply()
        Log.d(TAG, "Access token cleared")
    }
    
    fun isAuthenticated(): Boolean {
        return getAccessToken() != null
    }
    
    fun signOut() {
        sharedPrefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .apply()
        Log.d(TAG, "User signed out")
    }
    
    // Simplified initialization - no longer needs activity registration
    fun initialize(activity: ComponentActivity) {
        Log.d(TAG, "HuggingFace auth initialized")
    }
} 