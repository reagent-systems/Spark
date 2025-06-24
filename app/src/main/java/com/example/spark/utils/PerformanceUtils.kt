package com.example.spark.utils

import android.util.Log
import androidx.compose.runtime.*

object PerformanceUtils {
    
    // Simple logging utilities
    fun logDebug(tag: String, message: String) {
        Log.d(tag, message)
    }
    
    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
    
    fun logWarning(tag: String, message: String) {
        Log.w(tag, message)
    }
    
    // Performance timing utility
    inline fun <T> measureTime(tag: String, operation: String, block: () -> T): T {
        val startTime = System.currentTimeMillis()
        val result = block()
        val endTime = System.currentTimeMillis()
        Log.d(tag, "$operation took ${endTime - startTime}ms")
        return result
    }
    
    // Composable performance tracker
    @Composable
    fun TrackComposition(name: String) {
        val recompositionCount = remember { mutableIntStateOf(0) }
        SideEffect {
            recompositionCount.intValue++
            if (recompositionCount.intValue > 5) {
                Log.w("Composition", "$name recomposed ${recompositionCount.intValue} times")
            }
        }
    }
} 