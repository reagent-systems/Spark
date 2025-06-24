package com.example.spark.presentation.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
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
import com.example.spark.domain.models.ChatSession
import com.example.spark.domain.models.ModelConfig
import com.example.spark.presentation.ui.components.SystemPromptPresetCard
import com.example.spark.presentation.ui.components.ParameterPresetCard

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
    
    val configuration = LocalConfiguration.current
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
                            SystemPromptTab(
                                currentChatSession = currentChatSession,
                                systemPrompt = systemPrompt,
                                onSystemPromptChange = { systemPrompt = it }
                            )
                        }
                        1 -> {
                            ParametersTab(
                                maxTokens = maxTokens,
                                onMaxTokensChange = { maxTokens = it },
                                temperature = temperature,
                                onTemperatureChange = { temperature = it },
                                topK = topK,
                                onTopKChange = { topK = it },
                                randomSeed = randomSeed,
                                onRandomSeedChange = { randomSeed = it },
                                enableStreaming = enableStreaming,
                                onStreamingToggle = { enableStreaming = it }
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
private fun SystemPromptTab(
    currentChatSession: ChatSession?,
    systemPrompt: String,
    onSystemPromptChange: (String) -> Unit
) {
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
        onValueChange = onSystemPromptChange,
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
                onSelect = onSystemPromptChange
            )
        }
        item {
            SystemPromptPresetCard(
                title = "Creative Writer",
                description = "Focuses on creative and engaging content",
                prompt = "You are a creative writing assistant. Help users craft engaging stories, poems, and creative content. Be imaginative and inspiring while maintaining quality.",
                onSelect = onSystemPromptChange
            )
        }
        item {
            SystemPromptPresetCard(
                title = "Code Assistant",
                description = "Specialized in programming and technical help",
                prompt = "You are a programming assistant. Provide clear, well-commented code examples and explain technical concepts simply. Focus on best practices and security.",
                onSelect = onSystemPromptChange
            )
        }
        item {
            SystemPromptPresetCard(
                title = "Tutor",
                description = "Educational and patient teaching style",
                prompt = "You are a patient tutor. Explain concepts step-by-step, ask clarifying questions, and adapt your explanations to the user's level of understanding.",
                onSelect = onSystemPromptChange
            )
        }
        item {
            SystemPromptPresetCard(
                title = "Professional",
                description = "Formal and business-focused responses",
                prompt = "You are a professional business assistant. Provide formal, well-structured responses suitable for workplace communication. Be efficient and solution-oriented.",
                onSelect = onSystemPromptChange
            )
        }
    }
}

@Composable
private fun ParametersTab(
    maxTokens: String,
    onMaxTokensChange: (String) -> Unit,
    temperature: String,
    onTemperatureChange: (String) -> Unit,
    topK: String,
    onTopKChange: (String) -> Unit,
    randomSeed: String,
    onRandomSeedChange: (String) -> Unit,
    enableStreaming: Boolean,
    onStreamingToggle: (Boolean) -> Unit
) {
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
        onValueChange = { onMaxTokensChange(it.toInt().toString()) },
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
        onValueChange = { onTemperatureChange(String.format("%.2f", it)) },
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
        onValueChange = { onTopKChange(it.toInt().toString()) },
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
        onValueChange = onRandomSeedChange,
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
            onCheckedChange = onStreamingToggle
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
                    onMaxTokensChange("1000")
                    onTemperatureChange("0.30")
                    onTopKChange("20")
                }
            )
        }
        item {
            ParameterPresetCard(
                title = "Balanced",
                description = "Good all-around",
                onClick = {
                    onMaxTokensChange("1500")
                    onTemperatureChange("0.70")
                    onTopKChange("40")
                }
            )
        }
        item {
            ParameterPresetCard(
                title = "Creative",
                description = "Diverse & imaginative",
                onClick = {
                    onMaxTokensChange("2000")
                    onTemperatureChange("1.20")
                    onTopKChange("60")
                }
            )
        }
    }
}

 