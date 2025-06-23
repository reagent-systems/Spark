package com.example.spark.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import com.example.spark.domain.models.ChatSession
import com.example.spark.domain.models.LLMModel
import com.example.spark.presentation.ui.components.ChatBubble
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
    onCreateChatSession: (String, String) -> Unit,
    onSelectChatSession: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onUpdateCurrentMessage: (String) -> Unit,
    onLoadModel: (String) -> Unit,
    onDeleteChatSession: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showNewChatDialog by remember { mutableStateOf(false) }
    var isDrawerOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(currentChatSession?.messages?.size) {
        if (currentChatSession?.messages?.isNotEmpty() == true) {
            coroutineScope.launch {
                listState.animateScrollToItem(currentChatSession.messages.size - 1)
            }
        }
    }
    
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Main Chat Area
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            // Handle swipe gestures
                        }
                    ) { _, dragAmount ->
                        // Open drawer on right swipe from left edge
                        if (dragAmount > 50 && !isDrawerOpen) {
                            isDrawerOpen = true
                        }
                        // Close drawer on left swipe when open
                        else if (dragAmount < -50 && isDrawerOpen) {
                            isDrawerOpen = false
                        }
                    }
                }
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
                            val modelName = (loadedModels + availableModels).find { it.id == currentChatSession.modelId }?.name ?: "Unknown Model"
                            Text(
                                text = modelName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(currentChatSession.messages) { message ->
                            ChatBubble(message = message)
                        }
                        
                        if (isGenerating) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Generating response...",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
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
                                value = currentMessage,
                                onValueChange = onUpdateCurrentMessage,
                                placeholder = { Text("Type a message...") },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp),
                                maxLines = 4,
                                enabled = !isGenerating,
                                shape = RoundedCornerShape(24.dp)
                            )
                            
                            FloatingActionButton(
                                onClick = {
                                    if (currentMessage.isNotBlank()) {
                                        onSendMessage(currentMessage)
                                        onUpdateCurrentMessage("")
                                    }
                                },
                                modifier = Modifier.size(48.dp),
                                containerColor = if (currentMessage.isBlank() || isGenerating) 
                                    MaterialTheme.colorScheme.surfaceVariant 
                                else 
                                    MaterialTheme.colorScheme.primary
                            ) {
                                if (isGenerating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send",
                                        tint = if (currentMessage.isBlank()) 
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