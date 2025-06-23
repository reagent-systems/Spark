package com.example.spark

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
import com.example.spark.network.server.ApiServer
import com.example.spark.presentation.ui.screens.*
import com.example.spark.presentation.viewmodel.MainViewModel
import com.example.spark.ui.theme.SparkTheme

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MainViewModel
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize dependencies
        val llmRepository = LLMRepositoryImpl(this)
        val apiServer = ApiServer(this, llmRepository)
        viewModel = MainViewModel(llmRepository, apiServer)
        
        setContent {
            SparkTheme {
                SparkApp(viewModel = viewModel)
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
fun SparkApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Handle errors
    uiState.errorMessage?.let { error ->
        LaunchedEffect(error) {
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Long
            )
            viewModel.clearError()
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                val items = listOf(
                    BottomNavItem("models", "Models", Icons.Default.Storage),
                    BottomNavItem("chat", "Chat", Icons.AutoMirrored.Filled.Chat),
                    BottomNavItem("server", "Server", Icons.Default.Api)
                )
                
                items.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "models",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("models") {
                ModelsScreen(
                    models = uiState.availableModels,
                    isLoading = uiState.isLoading,
                    loadingModelId = uiState.loadingModelId,
                    onLoadModel = viewModel::loadModel,
                    onUnloadModel = viewModel::unloadModel,
                    onAddModel = viewModel::addModel,
                    onDeleteModel = { modelId ->
                        // TODO: Implement delete model
                    }
                )
            }
            
            composable("chat") {
                ChatScreen(
                    chatSessions = uiState.chatSessions,
                    currentChatSession = uiState.currentChatSession,
                    loadedModels = uiState.loadedModels,
                    currentMessage = uiState.currentMessage,
                    isGenerating = uiState.isGenerating,
                    onCreateChatSession = viewModel::createChatSession,
                    onSelectChatSession = viewModel::selectChatSession,
                    onSendMessage = viewModel::sendMessage,
                    onUpdateCurrentMessage = viewModel::updateCurrentMessage
                )
            }
            
            composable("server") {
                ServerScreen(
                    isServerRunning = uiState.isServerRunning,
                    serverPort = uiState.serverPort,
                    serverLocalIp = uiState.serverLocalIp,
                    loadedModels = uiState.loadedModels,
                    onStartServer = viewModel::startServer,
                    onStopServer = viewModel::stopServer
                )
            }
        }
    }
}

data class BottomNavItem(
    val route: String,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)