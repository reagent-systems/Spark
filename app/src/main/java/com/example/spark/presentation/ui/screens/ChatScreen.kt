package com.example.spark.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    currentMessage: String,
    isGenerating: Boolean,
    onCreateChatSession: (String, String) -> Unit,
    onSelectChatSession: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onUpdateCurrentMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showNewChatDialog by remember { mutableStateOf(false) }
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
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = currentChatSession?.name ?: "Chat",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (currentChatSession != null) {
                        val modelName = loadedModels.find { it.id == currentChatSession.modelId }?.name ?: "Unknown Model"
                        Text(
                            text = "Model: $modelName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                FloatingActionButton(
                    onClick = { showNewChatDialog = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "New Chat"
                    )
                }
            }
        }
        
        if (currentChatSession == null) {
            // No chat selected state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No chat selected",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Create a new chat to get started",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Button(
                        onClick = { showNewChatDialog = true },
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("New Chat")
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
                            modifier = Modifier.weight(1f),
                            maxLines = 4,
                            enabled = !isGenerating
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        FloatingActionButton(
                            onClick = {
                                if (currentMessage.isNotBlank()) {
                                    onSendMessage(currentMessage)
                                    onUpdateCurrentMessage("")
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send"
                            )
                        }
                    }
                }
            }
        }
    }
    
    // New Chat Dialog
    if (showNewChatDialog) {
        NewChatDialog(
            loadedModels = loadedModels,
            onDismiss = { showNewChatDialog = false },
            onConfirm = { name, modelId ->
                onCreateChatSession(name, modelId)
                showNewChatDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatDialog(
    loadedModels: List<LLMModel>,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var chatName by remember { mutableStateOf("") }
    var selectedModelId by remember { mutableStateOf(loadedModels.firstOrNull()?.id ?: "") }
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
                        value = loadedModels.find { it.id == selectedModelId }?.name ?: "Select Model",
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
                        loadedModels.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.name) },
                                onClick = {
                                    selectedModelId = model.id
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                if (loadedModels.isEmpty()) {
                    Text(
                        text = "No models loaded. Please load a model first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
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
                enabled = chatName.isNotBlank() && selectedModelId.isNotBlank() && loadedModels.isNotEmpty()
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