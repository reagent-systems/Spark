package com.example.spark.presentation.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.spark.domain.models.LLMModel
import com.example.spark.presentation.ui.components.SystemPromptPresetCard

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
    
    val configuration = LocalConfiguration.current
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
                            BasicTabContent(
                                chatName = chatName,
                                onChatNameChange = { chatName = it },
                                selectedModelId = selectedModelId,
                                onModelSelect = { selectedModelId = it },
                                availableModels = availableModels,
                                loadedModels = loadedModels,
                                expanded = expanded,
                                onExpandedChange = { expanded = it },
                                enableStreaming = enableStreaming,
                                onStreamingToggle = { enableStreaming = it }
                            )
                        }
                        1 -> {
                            SystemPromptTabContent(
                                systemPrompt = systemPrompt,
                                onSystemPromptChange = { systemPrompt = it }
                            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BasicTabContent(
    chatName: String,
    onChatNameChange: (String) -> Unit,
    selectedModelId: String,
    onModelSelect: (String) -> Unit,
    availableModels: List<LLMModel>,
    loadedModels: List<LLMModel>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    enableStreaming: Boolean,
    onStreamingToggle: (Boolean) -> Unit
) {
    // Chat Name Input
    OutlinedTextField(
        value = chatName,
        onValueChange = onChatNameChange,
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
        onExpandedChange = onExpandedChange
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
            onDismissRequest = { onExpandedChange(false) }
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
                        onModelSelect(model.id)
                        onExpandedChange(false)
                    }
                )
            }
        }
    }
    
    // Streaming Toggle
    StreamingToggleCard(
        enableStreaming = enableStreaming,
        onStreamingToggle = onStreamingToggle
    )
    
    // Status Cards
    if (availableModels.isEmpty()) {
        StatusCard(
            icon = Icons.Default.Error,
            text = "No models available. Please add a model first.",
            isError = true
        )
    } else {
        StatusCard(
            icon = Icons.Default.Info,
            text = "Models will be automatically loaded when you start chatting.",
            isError = false
        )
    }
}

@Composable
private fun SystemPromptTabContent(
    systemPrompt: String,
    onSystemPromptChange: (String) -> Unit
) {
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
        onValueChange = onSystemPromptChange,
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
                onSelect = onSystemPromptChange
            )
        }
        item {
            SystemPromptPresetCard(
                title = "Creative Writer",
                description = "Imaginative and engaging content",
                prompt = "You are a creative writing assistant. Help craft engaging stories and creative content with imagination and flair.",
                onSelect = onSystemPromptChange
            )
        }
        item {
            SystemPromptPresetCard(
                title = "Code Assistant",
                description = "Programming help and examples",
                prompt = "You are a programming assistant. Provide clear, well-commented code examples and explain technical concepts simply.",
                onSelect = onSystemPromptChange
            )
        }
        item {
            SystemPromptPresetCard(
                title = "Tutor",
                description = "Patient teaching style",
                prompt = "You are a patient tutor. Explain concepts step-by-step and adapt explanations to the user's understanding level.",
                onSelect = onSystemPromptChange
            )
        }
    }
}

@Composable
private fun StreamingToggleCard(
    enableStreaming: Boolean,
    onStreamingToggle: (Boolean) -> Unit
) {
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
                onCheckedChange = onStreamingToggle
            )
        }
    }
}

@Composable
private fun StatusCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    isError: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError) 
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

 