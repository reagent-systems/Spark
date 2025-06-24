package com.example.spark.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
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
    modelConfig: ModelConfig,
    onCreateChatSession: (String, String) -> Unit,
    onSelectChatSession: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onUpdateCurrentMessage: (String) -> Unit,
    onLoadModel: (String) -> Unit,
    onDeleteChatSession: (String) -> Unit,
    onStopGeneration: () -> Unit,
    onUpdateModelConfig: (ModelConfig) -> Unit,
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
                            Icons.AutoMirrored.Filled.Send,
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
                        
                        // Show typing indicator when generating
                        if (isGenerating) {
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
                                            Icons.AutoMirrored.Filled.Send,
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
            onDismiss = { showModelConfigDialog = false },
            onConfigUpdate = { newConfig ->
                onUpdateModelConfig(newConfig)
                showModelConfigDialog = false
            }
        )
    }
    
    // New Chat Dialog
    if (showNewChatDialog) {
        NewChatDialog(
            availableModels = availableModels,
            loadedModels = loadedModels,
            onDismiss = { showNewChatDialog = false },
            onConfirm = { name, modelId ->
                // Model will be automatically loaded by the ViewModel
                onCreateChatSession(name, modelId)
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
    onConfirm: (String, String) -> Unit
) {
    var chatName by remember { mutableStateOf("") }
    var selectedModelId by remember { mutableStateOf(availableModels.firstOrNull()?.id ?: "") }
    var expanded by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("New Chat")
        },
        text = {
            Column {
                OutlinedTextField(
                    value = chatName,
                    onValueChange = { chatName = it },
                    label = { Text("Chat Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = availableModels.find { it.id == selectedModelId }?.name ?: "Select Model",
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Model") },
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
                                        Text(model.name)
                                        Text(
                                            text = if (loadedModels.any { it.id == model.id }) "Loaded" else "Will auto-load",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (loadedModels.any { it.id == model.id }) 
                                                MaterialTheme.colorScheme.primary 
                                            else 
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        )
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
                
                if (availableModels.isEmpty()) {
                    Text(
                        text = "No models available. Please add a model first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    Text(
                        text = "Models will be automatically loaded when you start chatting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (chatName.isNotBlank() && selectedModelId.isNotBlank()) {
                        onConfirm(chatName, selectedModelId)
                    }
                },
                enabled = chatName.isNotBlank() && selectedModelId.isNotBlank() && availableModels.isNotEmpty()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelConfigDialog(
    currentConfig: ModelConfig,
    onDismiss: () -> Unit,
    onConfigUpdate: (ModelConfig) -> Unit
) {
    var maxTokens by remember { mutableStateOf(currentConfig.maxTokens.toString()) }
    var temperature by remember { mutableStateOf(currentConfig.temperature.toString()) }
    var topK by remember { mutableStateOf(currentConfig.topK.toString()) }
    var randomSeed by remember { mutableStateOf(currentConfig.randomSeed.toString()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Text(
                    text = "Model Configuration",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                // Max Tokens
                OutlinedTextField(
                    value = maxTokens,
                    onValueChange = { maxTokens = it },
                    label = { Text("Max Output Tokens") },
                    supportingText = { Text("Maximum number of tokens to generate (100-4000)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                // Temperature
                OutlinedTextField(
                    value = temperature,
                    onValueChange = { temperature = it },
                    label = { Text("Temperature") },
                    supportingText = { Text("Creativity level (0.0-2.0). Higher = more creative") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                // Top K
                OutlinedTextField(
                    value = topK,
                    onValueChange = { topK = it },
                    label = { Text("Top K") },
                    supportingText = { Text("Consider top K tokens (1-100)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                // Random Seed
                OutlinedTextField(
                    value = randomSeed,
                    onValueChange = { randomSeed = it },
                    label = { Text("Random Seed") },
                    supportingText = { Text("Seed for reproducible results (0 = random)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                // Quick preset buttons
                Text(
                    text = "Quick Presets:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            maxTokens = "1000"
                            temperature = "0.3"
                            topK = "20"
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Precise", style = MaterialTheme.typography.bodySmall)
                    }
                    
                    OutlinedButton(
                        onClick = {
                            maxTokens = "1500"
                            temperature = "0.7"
                            topK = "40"
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Balanced", style = MaterialTheme.typography.bodySmall)
                    }
                    
                    OutlinedButton(
                        onClick = {
                            maxTokens = "2000"
                            temperature = "1.2"
                            topK = "60"
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Creative", style = MaterialTheme.typography.bodySmall)
                    }
                }
                }
                
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
                                    randomSeed = randomSeed.toIntOrNull() ?: currentConfig.randomSeed
                                )
                                onConfigUpdate(newConfig)
                            } catch (e: Exception) {
                                // Handle invalid input gracefully
                                onConfigUpdate(currentConfig)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
} 