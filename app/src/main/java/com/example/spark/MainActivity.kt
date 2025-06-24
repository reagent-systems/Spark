package com.example.spark

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.spark.data.repository.LLMRepositoryImpl
import com.example.spark.data.repository.UpdateRepositoryImpl
import com.example.spark.network.server.ApiServer
import com.example.spark.presentation.ui.screens.*
import com.example.spark.presentation.ui.components.ModelLoadingDialog
import com.example.spark.presentation.ui.components.HuggingFaceTokenDialog
import com.example.spark.presentation.ui.components.HuggingFaceSettingsDialog
import com.example.spark.presentation.ui.components.CustomUrlDownloadDialog
import com.example.spark.presentation.ui.components.DeleteModelConfirmationDialog
import com.example.spark.presentation.ui.components.UpdateDialog
import com.example.spark.presentation.ui.components.ChangelogDialog
import com.example.spark.presentation.viewmodel.MainViewModel
import com.example.spark.ui.theme.SparkTheme
import com.example.spark.utils.HuggingFaceAuth

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MainViewModel
    private lateinit var huggingFaceAuth: HuggingFaceAuth
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize HuggingFace authentication
        huggingFaceAuth = HuggingFaceAuth(this)
        huggingFaceAuth.initialize(this)
        
        // Initialize dependencies
        val llmRepository = LLMRepositoryImpl(this, huggingFaceAuth)
        val updateRepository = UpdateRepositoryImpl(this)
        val apiServer = ApiServer(this, llmRepository)
        viewModel = MainViewModel(llmRepository, updateRepository, apiServer, this, huggingFaceAuth)
        
        setContent {
            SparkTheme {
                            SparkApp(
                viewModel = viewModel,
                huggingFaceAuth = huggingFaceAuth,
                onOpenUrl = { url ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                },
                context = this
            )
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up server if running
        if (::viewModel.isInitialized) {
            viewModel.stopServer()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SparkApp(
    viewModel: MainViewModel,
    huggingFaceAuth: HuggingFaceAuth,
    onOpenUrl: (String) -> Unit,
    context: Context
) {
    val navController = rememberNavController()
    
    // Use more efficient state collection with lifecycle awareness
    val uiState by viewModel.uiState.collectAsStateWithLifecycle(
        minActiveState = androidx.lifecycle.Lifecycle.State.STARTED
    )
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Optimize error handling with batching and background processing
    val errorMessage = uiState.errorMessage
    LaunchedEffect(errorMessage) {
        errorMessage?.let { error ->
            // Batch error processing on background thread
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                // Small delay to batch multiple errors
                kotlinx.coroutines.delay(100)
                
                // Only show snackbar on main thread
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                    try {
                        snackbarHostState.showSnackbar(
                            message = error,
                            duration = SnackbarDuration.Long
                        )
                    } catch (e: Exception) {
                        // Ignore snackbar errors
                    }
                    viewModel.clearError()
                }
            }
        }
    }
    
    // Pre-calculate navigation items to avoid recreation
    val navigationItems = remember {
        listOf(
            BottomNavItem("models", "Models", Icons.Default.Storage),
            BottomNavItem("chat", "Chat", Icons.AutoMirrored.Filled.Chat),
            BottomNavItem("server", "Server", Icons.Default.Api)
        )
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                
                navigationItems.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        selected = currentRoute == item.route,
                        onClick = {
                            if (currentRoute != item.route) {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        // Model Loading Dialog
        if (uiState.loadingModelId != null) {
            ModelLoadingDialog(
                modelName = uiState.loadingModelName ?: "",
                isVisible = true
            )
        }
        
        // HuggingFace Token Dialog
        if (uiState.showHuggingFaceTokenDialog) {
            HuggingFaceTokenDialog(
                isVisible = true,
                onDismiss = viewModel::hideHuggingFaceTokenDialog,
                onTokenSubmit = viewModel::submitHuggingFaceToken,
                onOpenTokenPage = { onOpenUrl("https://huggingface.co/settings/tokens") }
            )
        }

        // HuggingFace Settings Dialog
        if (uiState.showHuggingFaceSettingsDialog) {
            HuggingFaceSettingsDialog(
                isAuthenticated = uiState.isHuggingFaceAuthenticated,
                currentToken = if (uiState.isHuggingFaceAuthenticated) huggingFaceAuth.getAccessToken() else null,
                currentVersion = remember {
                    try {
                        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                        "v${packageInfo.versionName}"
                    } catch (e: Exception) {
                        "v1.0.0"
                    }
                },
                lastChecked = uiState.lastUpdateCheck,
                isCheckingForUpdates = uiState.isCheckingForUpdates,
                onDismiss = viewModel::hideHuggingFaceSettingsDialog,
                onSaveToken = viewModel::saveHuggingFaceToken,
                onRemoveToken = viewModel::removeHuggingFaceToken,
                onOpenTokenPage = { onOpenUrl("https://huggingface.co/settings/tokens") },
                onCheckForUpdates = viewModel::checkForUpdates
            )
        }

        // Custom URL Download Dialog
        if (uiState.showCustomUrlDialog) {
            CustomUrlDownloadDialog(
                urlInput = uiState.customUrlInput,
                isDownloading = uiState.isDownloadingCustomUrl,
                downloadProgress = uiState.downloadProgress,
                onDismiss = viewModel::hideCustomUrlDialog,
                onUrlChange = viewModel::updateCustomUrlInput,
                onDownload = viewModel::downloadFromCustomUrl
            )
        }

        // Delete Model Confirmation Dialog
        if (uiState.showDeleteConfirmationDialog) {
            DeleteModelConfirmationDialog(
                isVisible = true,
                modelName = uiState.modelToDelete?.name ?: "",
                onDismiss = viewModel::cancelDeleteModel,
                onConfirm = viewModel::confirmDeleteModel
            )
        }

        // Update Dialog
        if (uiState.showUpdateDialog) {
            uiState.availableUpdate?.let { updateInfo ->
                UpdateDialog(
                    updateInfo = updateInfo,
                    isDownloading = uiState.isDownloadingUpdate,
                    downloadProgress = uiState.updateDownloadProgress,
                    onUpdate = viewModel::downloadAndInstallUpdate,
                    onSkip = viewModel::hideUpdateDialog,
                    onViewChangelog = viewModel::showChangelogDialog,
                    onDismiss = viewModel::hideUpdateDialog
                )
            }
        }

        // Changelog Dialog
        if (uiState.showChangelogDialog) {
            uiState.availableUpdate?.let { updateInfo ->
                ChangelogDialog(
                    updateInfo = updateInfo,
                    onDismiss = viewModel::hideChangelogDialog
                )
            }
        }
        
        NavHost(
            navController = navController,
            startDestination = "models",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(
                route = "models",
                content = {
                    ModelsScreen(
                        models = uiState.availableModels,
                        downloadableModels = uiState.downloadableModels,
                        isLoading = uiState.isLoading,
                        loadingModelId = uiState.loadingModelId,
                        unloadingModelId = uiState.unloadingModelId,
                        downloadingModelId = uiState.downloadingModelId,
                        downloadProgress = uiState.downloadProgress,
                        onLoadModel = viewModel::loadModel,
                        onUnloadModel = viewModel::unloadModel,
                        onAddModel = viewModel::addModel,
                        onDeleteModel = viewModel::deleteModel,
                        onDownloadModel = viewModel::downloadModel,
                        onCancelDownload = viewModel::cancelDownload,
                        onShowHuggingFaceSettings = viewModel::showHuggingFaceSettingsDialog,
                        onShowCustomUrlDialog = viewModel::showCustomUrlDialog
                    )
                }
            )
            
            composable(
                route = "chat",
                content = {
                    ChatScreen(
                        chatSessions = uiState.chatSessions,
                        currentChatSession = uiState.currentChatSession,
                        loadedModels = uiState.loadedModels,
                        availableModels = uiState.availableModels,
                        currentMessage = uiState.currentMessage,
                        isGenerating = uiState.isGenerating,
                        modelConfig = uiState.modelConfig,
                        onCreateChatSession = viewModel::createChatSession,
                        onSelectChatSession = viewModel::selectChatSession,
                        onSendMessage = viewModel::sendMessage,
                        onUpdateCurrentMessage = viewModel::updateCurrentMessage,
                        onLoadModel = viewModel::loadModel,
                        onDeleteChatSession = viewModel::deleteChatSession,
                        onStopGeneration = viewModel::stopGeneration,
                        onUpdateModelConfig = viewModel::updateModelConfig
                    )
                }
            )
            
            composable(
                route = "server",
                content = {
                    ServerScreen(
                        isServerRunning = uiState.isServerRunning,
                        serverPort = uiState.serverPort,
                        serverLocalIp = uiState.serverLocalIp,
                        loadedModels = uiState.loadedModels,
                        modelConfig = uiState.modelConfig,
                        onStartServer = viewModel::startServer,
                        onStopServer = viewModel::stopServer,
                        onUpdateModelConfig = viewModel::updateModelConfig
                    )
                }
            )
        }
    }
}

data class BottomNavItem(
    val route: String,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)