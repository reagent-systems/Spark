package com.example.spark.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import com.example.spark.domain.models.ChatSession
import com.example.spark.domain.models.LLMModel
import com.example.spark.domain.models.ModelConfig
import com.example.spark.presentation.ui.components.ChatBubble
import com.example.spark.presentation.ui.components.ChatSessionsSidebar
import com.example.spark.presentation.ui.components.TypingIndicator
import com.example.spark.presentation.ui.components.FastTypingIndicator
import com.example.spark.presentation.ui.dialogs.NewChatDialog
import com.example.spark.presentation.ui.dialogs.ModelConfigDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatSessions: List<ChatSession>,
    currentChatSession: ChatSession?,
    loadedModels: List<LLMModel>,
    availableModels: List<LLMModel>,
    currentMessage: String,
    isGenerating: Boolean,
    streamingMessageId: String?,
    streamingContent: String,
    modelConfig: ModelConfig,
    onCreateChatSession: (String, String, String) -> Unit,
    onSelectChatSession: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onUpdateCurrentMessage: (String) -> Unit,
    onLoadModel: (String) -> Unit,
    onDeleteChatSession: (String) -> Unit,
    onStopGeneration: () -> Unit,
    onUpdateModelConfig: (ModelConfig) -> Unit,
    onUpdateChatSession: (ChatSession) -> Unit,
    modifier: Modifier = Modifier
) {
    var showNewChatDialog by remember { mutableStateOf(false) }
    var showModelConfigDialog by remember { mutableStateOf(false) }
    var isDrawerOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Local text state to prevent cursor jumping issues
    var localTextInput by remember { mutableStateOf("") }
    
    // Sync local state with ViewModel state when currentMessage changes (like when cleared after sending)
    LaunchedEffect(currentMessage) {
        localTextInput = currentMessage
    }
    
    // Sync local state when chat session changes (when loading past chats)
    LaunchedEffect(currentChatSession?.id) {
        localTextInput = currentMessage
    }
    
    // Auto-scroll to bottom when new messages arrive (but not on content changes)
    LaunchedEffect(currentChatSession?.messages?.size) {
        if (currentChatSession?.messages?.isNotEmpty() == true) {
            // Use background dispatcher for scroll calculation
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val messageCount = currentChatSession.messages.size
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                    coroutineScope.launch {
                        try {
                            // Use instant scroll instead of animation for better performance
                            listState.scrollToItem(messageCount - 1)
                        } catch (e: Exception) {
                            // Ignore scroll errors
                        }
                    }
                }
            }
        }
    }
    
    // Track user scroll interactions to prevent auto-scroll fighting with user
    var userIsScrolling by remember { mutableStateOf(false) }
    var lastUserScrollTime by remember { mutableStateOf(0L) }
    
    // Check if user has scrolled away from bottom during streaming
    val isAtBottom = remember(listState.firstVisibleItemIndex, listState.layoutInfo.totalItemsCount) {
        val totalItems = listState.layoutInfo.totalItemsCount
        if (totalItems == 0) true
        else {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex >= totalItems - 2 // Allow some tolerance
        }
    }
    
    val showScrollToBottomButton = streamingMessageId != null && !isAtBottom && !userIsScrolling
    
    // Auto-scroll to bottom when streaming content is being updated, but only if user isn't actively scrolling
    LaunchedEffect(streamingContent.length) {
        if (streamingMessageId != null && streamingContent.isNotEmpty()) {
            val currentTime = System.currentTimeMillis()
            // Only auto-scroll if user hasn't scrolled in the last 2 seconds
            if (currentTime - lastUserScrollTime > 2000 && !userIsScrolling) {
                coroutineScope.launch {
                    try {
                        // Calculate total items including streaming message
                        val totalItems = (currentChatSession?.messages?.size ?: 0) + 1
                        listState.animateScrollToItem(totalItems - 1)
                    } catch (e: Exception) {
                        // Ignore scroll errors
                    }
                }
            }
        }
    }
    
    // Detect when user starts/stops scrolling
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            userIsScrolling = true
            lastUserScrollTime = System.currentTimeMillis()
        } else {
            // Delay before allowing auto-scroll again
            kotlinx.coroutines.delay(500)
            userIsScrolling = false
        }
    }
    
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Main Chat Area
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top App Bar
            ChatTopAppBar(
                currentChatSession = currentChatSession,
                loadedModels = loadedModels,
                availableModels = availableModels,
                modelConfig = modelConfig,
                chatSessions = chatSessions,
                onMenuClick = { isDrawerOpen = !isDrawerOpen },
                onSettingsClick = { showModelConfigDialog = true },
                onNewChatClick = { showNewChatDialog = true }
            )
            
            if (currentChatSession == null) {
                // No chat selected state
                EmptyChatState(
                    chatSessions = chatSessions,
                    onNewChatClick = { showNewChatDialog = true },
                    onDrawerOpen = { isDrawerOpen = true },
                    onSelectChatSession = onSelectChatSession
                )
            } else {
                // Chat content
                ChatContent(
                    currentChatSession = currentChatSession,
                    listState = listState,
                    streamingMessageId = streamingMessageId,
                    streamingContent = streamingContent,
                    isGenerating = isGenerating,
                    localTextInput = localTextInput,
                    onTextInputChange = { newText ->
                        localTextInput = newText
                        onUpdateCurrentMessage(newText)
                    },
                    onSendMessage = onSendMessage,
                    onStopGeneration = onStopGeneration
                )
            }
        }
        
        // Sliding Drawer Overlay
        if (isDrawerOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                    .clickable { isDrawerOpen = false }
                    .zIndex(1f)
            )
        }
        
        // Sliding Chat Sessions Drawer
        AnimatedVisibility(
            visible = isDrawerOpen && chatSessions.isNotEmpty(),
            enter = slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(300)
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(300)
            ),
            modifier = Modifier.zIndex(2f)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(280.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                ChatSessionsSidebar(
                    chatSessions = chatSessions,
                    currentChatSession = currentChatSession,
                    onSelectChatSession = { sessionId ->
                        onSelectChatSession(sessionId)
                        isDrawerOpen = false // Close drawer after selection
                    },
                    onDeleteChatSession = onDeleteChatSession,
                    onNewChat = { 
                        showNewChatDialog = true
                        isDrawerOpen = false // Close drawer after opening dialog
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        
        // Floating scroll to bottom button during streaming
        if (showScrollToBottomButton) {
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        val totalItems = (currentChatSession?.messages?.size ?: 0) + 
                                       (if (streamingMessageId != null) 1 else 0)
                        if (totalItems > 0) {
                            listState.animateScrollToItem(totalItems - 1)
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset { IntOffset(x = -16.dp.roundToPx(), y = -80.dp.roundToPx()) }
                    .size(48.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Scroll to bottom",
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "New",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp
                    )
                }
            }
        }
    }
    
    // Dialogs
    if (showModelConfigDialog) {
        ModelConfigDialog(
            currentConfig = modelConfig,
            currentChatSession = currentChatSession,
            onDismiss = { showModelConfigDialog = false },
            onConfigUpdate = onUpdateModelConfig,
            onChatSessionUpdate = onUpdateChatSession
        )
    }
    
    if (showNewChatDialog) {
        NewChatDialog(
            availableModels = availableModels,
            loadedModels = loadedModels,
            onDismiss = { showNewChatDialog = false },
            onConfirm = { name, modelId, systemPrompt, enableStreaming ->
                // Update model config with streaming setting
                val newConfig = modelConfig.copy(enableStreaming = enableStreaming)
                onUpdateModelConfig(newConfig)
                
                // Model will be automatically loaded by the ViewModel
                onCreateChatSession(name, modelId, systemPrompt)
                showNewChatDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopAppBar(
    currentChatSession: ChatSession?,
    loadedModels: List<LLMModel>,
    availableModels: List<LLMModel>,
    modelConfig: ModelConfig,
    chatSessions: List<ChatSession>,
    onMenuClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNewChatClick: () -> Unit
) {
    TopAppBar(
        title = { 
            Column {
                Text(
                    text = currentChatSession?.name ?: "Spark Chat",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                if (currentChatSession != null) {
                    val modelName = remember(currentChatSession.modelId, loadedModels.size, availableModels.size) {
                        (loadedModels + availableModels).find { it.id == currentChatSession.modelId }?.name ?: "Unknown Model"
                    }
                    Text(
                        text = modelName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    val configText = remember(modelConfig.temperature, modelConfig.topK, modelConfig.maxTokens) {
                        "T:${modelConfig.temperature} • K:${modelConfig.topK} • ${modelConfig.maxTokens}tok • CPU"
                    }
                    Text(
                        text = configText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
            }
        },
        navigationIcon = {
            if (chatSessions.isNotEmpty()) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = "Open chat sessions"
                    )
                }
            }
        },
        actions = {
            if (currentChatSession != null) {
                Box {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Model Settings"
                        )
                    }
                    // Show indicator if config is modified from defaults
                    val isConfigModified = remember(modelConfig.temperature, modelConfig.topK, modelConfig.maxTokens, modelConfig.randomSeed) {
                        val defaultConfig = ModelConfig()
                        modelConfig.temperature != defaultConfig.temperature ||
                        modelConfig.topK != defaultConfig.topK ||
                        modelConfig.maxTokens != defaultConfig.maxTokens ||
                        modelConfig.randomSeed != defaultConfig.randomSeed
                    }
                    if (isConfigModified) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                                .align(androidx.compose.ui.Alignment.TopEnd)
                                .offset(x = (-2).dp, y = 2.dp)
                        )
                    }
                }
            }
            IconButton(onClick = onNewChatClick) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "New Chat"
                )
            }
        }
    )
}

@Composable
private fun EmptyChatState(
    chatSessions: List<ChatSession>,
    onNewChatClick: () -> Unit,
    onDrawerOpen: () -> Unit,
    onSelectChatSession: (String) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.Send,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Welcome to Spark Chat",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Start a conversation with your AI assistant",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Button(
                onClick = onNewChatClick,
                modifier = Modifier.padding(top = 24.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start New Chat")
            }
            
            if (chatSessions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Or continue a previous conversation:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Show hamburger menu button to access all sessions
                OutlinedButton(
                    onClick = onDrawerOpen,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View All Chat Sessions")
                }
                
                // Show recent sessions as quick access
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(chatSessions.take(2)) { session ->
                        OutlinedButton(
                            onClick = { onSelectChatSession(session.id) },
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Text(session.name)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatContent(
    currentChatSession: ChatSession,
    listState: androidx.compose.foundation.lazy.LazyListState,
    streamingMessageId: String?,
    streamingContent: String,
    isGenerating: Boolean,
    localTextInput: String,
    onTextInputChange: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onStopGeneration: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = true
        ) {
            currentChatSession.messages.let { messages ->
                items(
                    items = messages,
                    key = { message -> "${message.id}_${message.timestamp}" },
                    contentType = { message -> if (message.isUser) "UserMessage" else "AIMessage" }
                ) { message ->
                    key("msg_${message.id}_${message.content.hashCode()}_${message.isUser}") {
                        ChatBubble(
                            message = message,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            // Show streaming message when streaming is active
            if (streamingMessageId != null && streamingContent.isNotEmpty()) {
                // Debug logging for streaming display
                android.util.Log.d("ChatScreen", "Displaying streaming content: length=${streamingContent.length}, id=$streamingMessageId")
                
                item(key = "streaming_message_$streamingMessageId", contentType = "StreamingMessage") {
                    StreamingMessageBubble(streamingContent = streamingContent)
                }
            }
            
            // Show typing indicator when generating (non-streaming)
            if (isGenerating && streamingMessageId == null) {
                item(key = "typing_indicator", contentType = "TypingIndicator") {
                    key("typing_${isGenerating}") {
                        TypingMessageBubble()
                    }
                }
            }
        }
        
        // Message input
        MessageInput(
            localTextInput = localTextInput,
            isGenerating = isGenerating,
            onTextInputChange = onTextInputChange,
            onSendMessage = onSendMessage,
            onStopGeneration = onStopGeneration
        )
    }
}

@Composable
private fun StreamingMessageBubble(streamingContent: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = 6.dp,
                bottomEnd = 18.dp
            ),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = streamingContent,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FastTypingIndicator()
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "streaming...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun TypingMessageBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = 6.dp,
                bottomEnd = 18.dp
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TypingIndicator()
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI is thinking...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MessageInput(
    localTextInput: String,
    isGenerating: Boolean,
    onTextInputChange: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onStopGeneration: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = localTextInput,
                onValueChange = onTextInputChange,
                placeholder = { Text("Type a message...") },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                maxLines = 4,
                enabled = !isGenerating,
                shape = RoundedCornerShape(24.dp),
                singleLine = false,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                    keyboardType = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.Sentences
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (!isGenerating && localTextInput.isNotBlank()) {
                            onSendMessage(localTextInput)
                        }
                    }
                )
            )
            
            FloatingActionButton(
                onClick = {
                    if (isGenerating) {
                        onStopGeneration()
                    } else if (localTextInput.isNotBlank()) {
                        onSendMessage(localTextInput)
                    }
                },
                modifier = Modifier.size(48.dp),
                containerColor = when {
                    isGenerating -> MaterialTheme.colorScheme.error
                    localTextInput.isBlank() -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.primary
                }
            ) {
                when {
                    isGenerating -> {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Stop Generation",
                            tint = MaterialTheme.colorScheme.onError
                        )
                    }
                    else -> {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (localTextInput.isBlank()) 
                                MaterialTheme.colorScheme.onSurfaceVariant 
                            else 
                                MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
} 