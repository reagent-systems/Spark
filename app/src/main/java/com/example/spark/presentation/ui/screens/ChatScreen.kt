package com.example.spark.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.example.spark.domain.models.ChatSession
import com.example.spark.domain.models.LLMModel
import com.example.spark.domain.models.ModelConfig
import com.example.spark.presentation.ui.components.ChatBubble
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

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
    
    // Auto-scroll to bottom when streaming content is being updated
    LaunchedEffect(streamingContent.length) {
        if (streamingMessageId != null && streamingContent.isNotEmpty()) {
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
    
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Main Chat Area
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top App Bar with Hamburger Menu
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
                        IconButton(onClick = { isDrawerOpen = !isDrawerOpen }) {
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
                            IconButton(onClick = { showModelConfigDialog = true }) {
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
                    IconButton(onClick = { showNewChatDialog = true }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "New Chat"
                        )
                    }
                }
            )
            
            if (currentChatSession == null) {
                // No chat selected state
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
                            onClick = { showNewChatDialog = true },
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
                                onClick = { isDrawerOpen = true },
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
            } else {
                // Chat content
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
                        // Performance optimizations
                        userScrollEnabled = true
                    ) {
                        currentChatSession?.messages?.let { messages ->
                            items(
                                items = messages,
                                key = { message -> "${message.id}_${message.timestamp}" }, // Stable key
                                contentType = { message -> if (message.isUser) "UserMessage" else "AIMessage" }
                            ) { message ->
                                // Use comprehensive key to prevent unnecessary recompositions
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
                                        modifier = Modifier.widthIn(max = 320.dp) // Slightly wider for better readability
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                text = streamingContent,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2 // Better line spacing
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Faster typing indicator for streaming
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
                        }
                        
                        // Show typing indicator when generating (non-streaming)
                        if (isGenerating && streamingMessageId == null) {
                            item(key = "typing_indicator", contentType = "TypingIndicator") {
                                key("typing_${isGenerating}") {
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
                            }
                        }
                    }
                    
                    // Message input
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
                                onValueChange = { newText ->
                                    localTextInput = newText
                                    // Immediately sync to ViewModel for fast typing support
                                    onUpdateCurrentMessage(newText)
                                },
                                placeholder = { Text("Type a message...") },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp),
                                maxLines = 4,
                                enabled = !isGenerating,
                                shape = RoundedCornerShape(24.dp),
                                // Optimize text input performance
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
                                        // Input field is cleared by the ViewModel
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
    }
    
    // Model Configuration Dialog
    if (showModelConfigDialog) {
        ModelConfigDialog(
            currentConfig = modelConfig,
            currentChatSession = currentChatSession,
            onDismiss = { showModelConfigDialog = false },
            onConfigUpdate = { newConfig ->
                onUpdateModelConfig(newConfig)
            },
            onChatSessionUpdate = { updatedSession ->
                onUpdateChatSession(updatedSession)
            }
        )
    }
    
    // New Chat Dialog
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

@Composable
fun ChatSessionsSidebar(
    chatSessions: List<ChatSession>,
    currentChatSession: ChatSession?,
    onSelectChatSession: (String) -> Unit,
    onDeleteChatSession: (String) -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Chat Sessions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    IconButton(onClick = onNewChat) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "New Chat",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            HorizontalDivider()
            
            // Chat Sessions List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(chatSessions) { session ->
                    ChatSessionItem(
                        session = session,
                        isSelected = currentChatSession?.id == session.id,
                        onSelect = { onSelectChatSession(session.id) },
                        onDelete = { onDeleteChatSession(session.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun ChatSessionItem(
    session: ChatSession,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = session.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
                Text(
                    text = "${session.messages.size} messages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            IconButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete Chat",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Chat") },
            text = { Text("Are you sure you want to delete \"${session.name}\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatDialog(
    availableModels: List<LLMModel>,
    loadedModels: List<LLMModel>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Boolean) -> Unit // name, modelId, systemPrompt, enableStreaming
) {
    var chatName by remember { mutableStateOf("") }
    var selectedModelId by remember { mutableStateOf(availableModels.firstOrNull()?.id ?: "") }
    var systemPrompt by remember { mutableStateOf("") }
    var enableStreaming by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val maxDialogHeight = (screenHeight * 0.9f).coerceAtMost(700.dp)
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxDialogHeight)
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "New Chat",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Tab Row
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Basic") },
                        icon = { Icon(Icons.Default.Chat, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("System Prompt") },
                        icon = { Icon(Icons.Default.Android, contentDescription = null) }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            // Basic Tab
                            OutlinedTextField(
                                value = chatName,
                                onValueChange = { chatName = it },
                                label = { Text("Chat Name") },
                                placeholder = { Text("My New Chat") },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = { Icon(Icons.Default.Title, contentDescription = null) }
                            )
                            
                            // Model Selection
                            Text(
                                text = "Select Model",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                            )
                            
                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = !expanded }
                            ) {
                                OutlinedTextField(
                                    value = availableModels.find { it.id == selectedModelId }?.name ?: "Select Model",
                                    onValueChange = { },
                                    readOnly = true,
                                    label = { Text("Model") },
                                    leadingIcon = { Icon(Icons.Default.Computer, contentDescription = null) },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor()
                                )
                                
                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    availableModels.forEach { model ->
                                        DropdownMenuItem(
                                            text = { 
                                                Column {
                                                    Text(
                                                        text = model.name,
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        if (loadedModels.any { it.id == model.id }) {
                                                            Icon(
                                                                Icons.Default.CheckCircle,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                            Text(
                                                                text = "Loaded",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                        } else {
                                                            Icon(
                                                                Icons.Default.Download,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                            Text(
                                                                text = "Will auto-load",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                            onClick = {
                                                selectedModelId = model.id
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            
                            // Streaming Toggle
                            Text(
                                text = "Response Options",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                            )
                            
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (enableStreaming) 
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    else 
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Stream,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Enable Streaming",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                            )
                                        }
                                        Text(
                                            text = "Show response as it's being generated",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 24.dp)
                                        )
                                    }
                                    Switch(
                                        checked = enableStreaming,
                                        onCheckedChange = { enableStreaming = it }
                                    )
                                }
                            }
                            
                            if (availableModels.isEmpty()) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Error,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "No models available. Please add a model first.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            } else {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Models will be automatically loaded when you start chatting.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        
                        1 -> {
                            // System Prompt Tab
                            Text(
                                text = "System Prompt",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                            )
                            
                            Text(
                                text = "Set a system prompt that will guide the AI's behavior for this chat. This prompt will be applied to all messages in the conversation.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            OutlinedTextField(
                                value = systemPrompt,
                                onValueChange = { systemPrompt = it },
                                label = { Text("System Prompt") },
                                placeholder = { Text("You are a helpful AI assistant...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 100.dp, max = 150.dp),
                                maxLines = 6,
                                supportingText = { 
                                    Text("${systemPrompt.length}/1000 characters") 
                                }
                            )
                            
                            // Quick system prompt presets
                            Text(
                                text = "Quick Presets:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                            )
                            
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 200.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                item {
                                    SystemPromptPresetCard(
                                        title = "Default Assistant",
                                        description = "Helpful and honest responses",
                                        prompt = "You are a helpful AI assistant. Be concise, accurate, and honest in your responses.",
                                        onSelect = { systemPrompt = it }
                                    )
                                }
                                item {
                                    SystemPromptPresetCard(
                                        title = "Creative Writer",
                                        description = "Imaginative and engaging content",
                                        prompt = "You are a creative writing assistant. Help craft engaging stories and creative content with imagination and flair.",
                                        onSelect = { systemPrompt = it }
                                    )
                                }
                                item {
                                    SystemPromptPresetCard(
                                        title = "Code Assistant",
                                        description = "Programming help and examples",
                                        prompt = "You are a programming assistant. Provide clear, well-commented code examples and explain technical concepts simply.",
                                        onSelect = { systemPrompt = it }
                                    )
                                }
                                item {
                                    SystemPromptPresetCard(
                                        title = "Tutor",
                                        description = "Patient teaching style",
                                        prompt = "You are a patient tutor. Explain concepts step-by-step and adapt explanations to the user's understanding level.",
                                        onSelect = { systemPrompt = it }
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = {
                            if (chatName.isNotBlank() && selectedModelId.isNotBlank()) {
                                onConfirm(chatName.trim(), selectedModelId, systemPrompt.take(1000), enableStreaming)
                            }
                        },
                        enabled = chatName.isNotBlank() && selectedModelId.isNotBlank() && availableModels.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Chat")
                    }
                }
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val animationDelay = index * 200
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = animationDelay),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$index"
            )
            
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(scale)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            )
        }
    }
}

@Composable
fun FastTypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "fast_typing")
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val animationDelay = index * 80 // Much faster staggered animation
            
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 1.3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 240, // Faster animation
                        delayMillis = animationDelay,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "fast_dot_scale_$index"
            )
            
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 240,
                        delayMillis = animationDelay
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "fast_dot_alpha_$index"
            )
            
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .scale(scale)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelConfigDialog(
    currentConfig: ModelConfig,
    currentChatSession: ChatSession?,
    onDismiss: () -> Unit,
    onConfigUpdate: (ModelConfig) -> Unit,
    onChatSessionUpdate: (ChatSession) -> Unit
) {
    var maxTokens by remember { mutableStateOf(currentConfig.maxTokens.toString()) }
    var temperature by remember { mutableStateOf(currentConfig.temperature.toString()) }
    var topK by remember { mutableStateOf(currentConfig.topK.toString()) }
    var randomSeed by remember { mutableStateOf(currentConfig.randomSeed.toString()) }
    var enableStreaming by remember { mutableStateOf(currentConfig.enableStreaming) }
    var systemPrompt by remember { 
        mutableStateOf(currentChatSession?.systemPrompt ?: currentConfig.systemPrompt) 
    }
    var selectedTab by remember { mutableStateOf(0) }
    
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val maxDialogHeight = (screenHeight * 0.9f).coerceAtMost(800.dp)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxDialogHeight)
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Model Configuration",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Tab Row
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("System Prompt") },
                        icon = { Icon(Icons.Default.Android, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Parameters") },
                        icon = { Icon(Icons.Default.Tune, contentDescription = null) }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Content based on selected tab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            // System Prompt Tab
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "System Prompt",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                )
                                
                                // Show indicator if this chat has a system prompt
                                if (currentChatSession?.systemPrompt?.isNotBlank() == true) {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                modifier = Modifier.size(12.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Active",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Text(
                                text = if (currentChatSession != null) {
                                    "This system prompt applies only to the current chat: \"${currentChatSession.name}\""
                                } else {
                                    "Set a system prompt that will apply to all future messages in this chat."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            OutlinedTextField(
                                value = systemPrompt,
                                onValueChange = { systemPrompt = it },
                                label = { Text("System Prompt") },
                                placeholder = { Text("You are a helpful AI assistant...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp, max = 200.dp),
                                maxLines = 8,
                                supportingText = { 
                                    Text("${systemPrompt.length}/2000 characters") 
                                }
                            )
                            
                            // System Prompt Presets
                            Text(
                                text = "Quick Presets:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                            )
                            
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 200.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    SystemPromptPresetCard(
                                        title = "Default Assistant",
                                        description = "Helpful, harmless, and honest AI assistant",
                                        prompt = "You are a helpful AI assistant. Be concise, accurate, and honest in your responses. If you don't know something, say so clearly.",
                                        onSelect = { systemPrompt = it }
                                    )
                                }
                                item {
                                    SystemPromptPresetCard(
                                        title = "Creative Writer",
                                        description = "Focuses on creative and engaging content",
                                        prompt = "You are a creative writing assistant. Help users craft engaging stories, poems, and creative content. Be imaginative and inspiring while maintaining quality.",
                                        onSelect = { systemPrompt = it }
                                    )
                                }
                                item {
                                    SystemPromptPresetCard(
                                        title = "Code Assistant",
                                        description = "Specialized in programming and technical help",
                                        prompt = "You are a programming assistant. Provide clear, well-commented code examples and explain technical concepts simply. Focus on best practices and security.",
                                        onSelect = { systemPrompt = it }
                                    )
                                }
                                item {
                                    SystemPromptPresetCard(
                                        title = "Tutor",
                                        description = "Educational and patient teaching style",
                                        prompt = "You are a patient tutor. Explain concepts step-by-step, ask clarifying questions, and adapt your explanations to the user's level of understanding.",
                                        onSelect = { systemPrompt = it }
                                    )
                                }
                                item {
                                    SystemPromptPresetCard(
                                        title = "Professional",
                                        description = "Formal and business-focused responses",
                                        prompt = "You are a professional business assistant. Provide formal, well-structured responses suitable for workplace communication. Be efficient and solution-oriented.",
                                        onSelect = { systemPrompt = it }
                                    )
                                }
                            }
                        }
                        
                        1 -> {
                            // Parameters Tab
                            Text(
                                text = "Generation Parameters",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                            )
                            
                            // Max Tokens with Slider
                            Text(
                                text = "Max Output Tokens: ${maxTokens}",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Slider(
                                value = maxTokens.toIntOrNull()?.toFloat() ?: 1000f,
                                onValueChange = { maxTokens = it.toInt().toString() },
                                valueRange = 100f..4000f,
                                steps = 38, // (4000-100)/100 steps
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Maximum number of tokens to generate",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Temperature with Slider
                            Text(
                                text = "Temperature: ${String.format("%.2f", temperature.toFloatOrNull() ?: 0.8f)}",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Slider(
                                value = temperature.toFloatOrNull() ?: 0.8f,
                                onValueChange = { temperature = String.format("%.2f", it) },
                                valueRange = 0.0f..2.0f,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Controls randomness: 0.0 = focused, 2.0 = very creative",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Top K with Slider
                            Text(
                                text = "Top K: ${topK}",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Slider(
                                value = topK.toIntOrNull()?.toFloat() ?: 40f,
                                onValueChange = { topK = it.toInt().toString() },
                                valueRange = 1f..100f,
                                steps = 98, // 99 steps for 1-100 range
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Consider top K most likely tokens",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Random Seed
                            OutlinedTextField(
                                value = randomSeed,
                                onValueChange = { randomSeed = it },
                                label = { Text("Random Seed") },
                                supportingText = { Text("0 = random, any number = reproducible results") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Streaming Toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Enable Streaming",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Text(
                                        text = "Show response as it's being generated",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = enableStreaming,
                                    onCheckedChange = { enableStreaming = it }
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Quick preset buttons
                            Text(
                                text = "Quick Presets:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                            )
                            
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    ParameterPresetCard(
                                        title = "Precise",
                                        description = "Focused & consistent",
                                        onClick = {
                                            maxTokens = "1000"
                                            temperature = "0.30"
                                            topK = "20"
                                        }
                                    )
                                }
                                item {
                                    ParameterPresetCard(
                                        title = "Balanced",
                                        description = "Good all-around",
                                        onClick = {
                                            maxTokens = "1500"
                                            temperature = "0.70"
                                            topK = "40"
                                        }
                                    )
                                }
                                item {
                                    ParameterPresetCard(
                                        title = "Creative",
                                        description = "Diverse & imaginative",
                                        onClick = {
                                            maxTokens = "2000"
                                            temperature = "1.20"
                                            topK = "60"
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = {
                            try {
                                val newConfig = ModelConfig(
                                    maxTokens = maxTokens.toIntOrNull()?.coerceIn(100, 4000) ?: currentConfig.maxTokens,
                                    temperature = temperature.toFloatOrNull()?.coerceIn(0.0f, 2.0f) ?: currentConfig.temperature,
                                    topK = topK.toIntOrNull()?.coerceIn(1, 100) ?: currentConfig.topK,
                                    randomSeed = randomSeed.toIntOrNull() ?: currentConfig.randomSeed,
                                    systemPrompt = currentConfig.systemPrompt, // Keep model config system prompt unchanged
                                    enableStreaming = enableStreaming
                                )
                                onConfigUpdate(newConfig)
                                
                                // Update chat session system prompt if we have a current session
                                currentChatSession?.let { session ->
                                    val updatedSession = session.copy(
                                        systemPrompt = systemPrompt.take(2000),
                                        updatedAt = System.currentTimeMillis()
                                    )
                                    onChatSessionUpdate(updatedSession)
                                }
                                
                                // Close dialog after successful update
                                onDismiss()
                            } catch (e: Exception) {
                                // Handle invalid input gracefully
                                onConfigUpdate(currentConfig)
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Apply")
                    }
                }
            }
        }
    }
}

@Composable
fun SystemPromptPresetCard(
    title: String,
    description: String,
    prompt: String,
    onSelect: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { onSelect(prompt) }) {
                    Text("Use")
                }
            }
        }
    }
}

@Composable
fun ParameterPresetCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.width(120.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
} 