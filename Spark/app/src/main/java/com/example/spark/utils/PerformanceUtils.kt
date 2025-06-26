package com.example.spark.utils

import android.os.Looper
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

object PerformanceUtils {
    private const val TAG = "PerformanceUtils"
    private val recompositionCount = AtomicLong(0)
    
    /**
     * Checks if we're on the main thread and logs a warning if heavy operations are detected
     */
    fun checkMainThread(operation: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "⚠️ PERFORMANCE WARNING: $operation is running on MAIN THREAD - this may cause UI lag!")
        }
    }
    
    /**
     * Tracks recomposition count for debugging excessive recompositions
     */
    @Composable
    fun TrackRecompositions(name: String) {
        val count = remember { recompositionCount.incrementAndGet() }
        SideEffect {
            Log.d(TAG, "🔄 Recomposition #$count in $name")
        }
    }
    
    /**
     * Stable wrapper for lists to prevent unnecessary recompositions
     */
    @Stable
    class StableList<T>(private val list: List<T>) : List<T> by list {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is StableList<*>) return false
            return list == other.list
        }
        
        override fun hashCode(): Int = list.hashCode()
    }
    
    /**
     * Creates a stable list wrapper
     */
    fun <T> List<T>.toStable(): StableList<T> = StableList(this)
    
    /**
     * Stable wrapper for model configurations
     */
    @Stable
    data class StableModelConfig(
        val maxTokens: Int,
        val temperature: Float,
        val topK: Int,
        val randomSeed: Int
    )
    
    /**
     * Debounced state holder to prevent excessive updates
     */
    @Composable
    fun <T> rememberDebouncedState(
        value: T,
        delayMillis: Long = 300
    ): State<T> {
        val debouncedValue = remember { mutableStateOf(value) }
        
        LaunchedEffect(value) {
            kotlinx.coroutines.delay(delayMillis)
            debouncedValue.value = value
        }
        
        return debouncedValue
    }
    
    /**
     * Optimized LaunchedEffect that runs on background dispatcher
     */
    @Composable
    fun LaunchedEffectBackground(
        key1: Any?,
        block: suspend CoroutineScope.() -> Unit
    ) {
        LaunchedEffect(key1) {
            withContext(Dispatchers.Default) {
                block()
            }
        }
    }
    
    /**
     * Optimized LaunchedEffect that runs on IO dispatcher
     */
    @Composable
    fun LaunchedEffectIO(
        key1: Any?,
        block: suspend CoroutineScope.() -> Unit
    ) {
        LaunchedEffect(key1) {
            withContext(Dispatchers.IO) {
                block()
            }
        }
    }
    
    /**
     * Memory-efficient remember for expensive calculations
     */
    @Composable
    fun <T> rememberCalculation(
        vararg keys: Any?,
        calculation: () -> T
    ): T {
        return remember(*keys) {
            Log.d(TAG, "💡 Performing expensive calculation with keys: ${keys.contentToString()}")
            calculation()
        }
    }
    
    /**
     * Logs performance metrics
     */
    fun logPerformanceMetrics() {
        Log.i(TAG, """
            📊 PERFORMANCE METRICS:
            - Total Recompositions: ${recompositionCount.get()}
            - Current Thread: ${Thread.currentThread().name}
            - Available Processors: ${Runtime.getRuntime().availableProcessors()}
            - Max Memory: ${Runtime.getRuntime().maxMemory() / 1024 / 1024} MB
            - Free Memory: ${Runtime.getRuntime().freeMemory() / 1024 / 1024} MB
        """.trimIndent())
    }
    
    /**
     * Wrapper for heavy operations that should run on background threads
     */
    suspend fun <T> runOnBackground(operation: suspend () -> T): T {
        checkMainThread("Heavy operation")
        return withContext(Dispatchers.Default) {
            operation()
        }
    }
    
    /**
     * Wrapper for file operations that should run on IO threads
     */
    suspend fun <T> runOnIO(operation: suspend () -> T): T {
        checkMainThread("IO operation")
        return withContext(Dispatchers.IO) {
            operation()
        }
    }
    
    /**
     * Optimized state holder that prevents unnecessary updates
     */
    class OptimizedStateHolder<T>(initialValue: T) {
        private var _value = initialValue
        private val _state = mutableStateOf(initialValue)
        
        val state: State<T> = _state
        
        fun update(newValue: T) {
            if (_value != newValue) {
                _value = newValue
                _state.value = newValue
            }
        }
        
        val value: T get() = _value
    }
    
    /**
     * Creates an optimized state holder
     */
    fun <T> createOptimizedState(initialValue: T) = OptimizedStateHolder(initialValue)
    
    /**
     * Stable wrapper for UI state to prevent unnecessary recompositions
     */
    @Stable
    data class StableUiState<T>(
        val value: T,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is StableUiState<*>) return false
            return value == other.value
        }
        
        override fun hashCode(): Int = value?.hashCode() ?: 0
    }
    
    /**
     * Creates a stable UI state wrapper
     */
    fun <T> T.toStableUiState(): StableUiState<T> = StableUiState(this)
    
    /**
     * Optimized remember function that reduces allocation overhead
     */
    @Composable
    inline fun <T> rememberOptimized(
        key1: Any?,
        crossinline calculation: () -> T
    ): T {
        return remember(key1) { calculation() }
    }
    
    /**
     * Batched state updater to reduce update frequency
     */
    class BatchedStateUpdater<T>(
        private val updateState: (T) -> Unit,
        private val batchDelayMs: Long = 16L // ~60fps
    ) {
        private var pendingUpdate: T? = null
        private var updateJob: Job? = null
        
        fun update(newValue: T) {
            pendingUpdate = newValue
            updateJob?.cancel()
            updateJob = CoroutineScope(Dispatchers.Main).launch {
                delay(batchDelayMs)
                pendingUpdate?.let { updateState(it) }
                pendingUpdate = null
            }
        }
        
        fun updateImmediate(newValue: T) {
            updateJob?.cancel()
            updateState(newValue)
            pendingUpdate = null
        }
    }
    
    /**
     * Creates a batched state updater
     */
    fun <T> createBatchedUpdater(
        updateState: (T) -> Unit,
        batchDelayMs: Long = 16L
    ) = BatchedStateUpdater(updateState, batchDelayMs)
    
    /**
     * Optimized LaunchedEffect that minimizes main thread work
     */
    @Composable
    fun OptimizedLaunchedEffect(
        key1: Any?,
        block: suspend CoroutineScope.() -> Unit
    ) {
        LaunchedEffect(key1) {
            // Run on background dispatcher by default
            withContext(Dispatchers.Default) {
                block()
            }
        }
    }
    
    /**
     * Memory-efficient state holder for lists
     */
    class MemoryEfficientListState<T>(
        private val initialList: List<T> = emptyList()
    ) {
        private var _list = initialList
        private var _listHashCode = initialList.hashCode()
        private val _state = mutableStateOf(initialList)
        
        val state: State<List<T>> = _state
        val value: List<T> get() = _list
        
        fun updateList(newList: List<T>) {
            val newHashCode = newList.hashCode()
            if (_listHashCode != newHashCode) {
                _list = newList
                _listHashCode = newHashCode
                _state.value = newList
            }
        }
    }
    
    /**
     * Creates a memory-efficient list state
     */
    fun <T> createMemoryEfficientListState(initialList: List<T> = emptyList()) = 
        MemoryEfficientListState(initialList)
    
    /**
     * Throttled function executor to prevent excessive calls
     */
    class ThrottledExecutor(
        private val intervalMs: Long = 16L // ~60fps
    ) {
        private var lastExecutionTime = 0L
        private var pendingExecution: Job? = null
        
        fun execute(action: suspend () -> Unit) {
            val currentTime = System.currentTimeMillis()
            val timeSinceLastExecution = currentTime - lastExecutionTime
            
            if (timeSinceLastExecution >= intervalMs) {
                // Execute immediately
                lastExecutionTime = currentTime
                CoroutineScope(Dispatchers.Main).launch {
                    action()
                }
            } else {
                // Throttle execution
                pendingExecution?.cancel()
                pendingExecution = CoroutineScope(Dispatchers.Main).launch {
                    delay(intervalMs - timeSinceLastExecution)
                    lastExecutionTime = System.currentTimeMillis()
                    action()
                }
            }
        }
    }
    
    /**
     * Creates a throttled executor
     */
    fun createThrottledExecutor(intervalMs: Long = 16L) = ThrottledExecutor(intervalMs)
} 