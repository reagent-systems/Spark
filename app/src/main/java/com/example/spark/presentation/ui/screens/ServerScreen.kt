package com.example.spark.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.spark.domain.models.LLMModel
import com.example.spark.domain.models.ModelConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen(
    isServerRunning: Boolean,
    serverPort: Int,
    serverLocalIp: String = "",
    loadedModels: List<LLMModel>,
    modelConfig: ModelConfig,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onUpdateModelConfig: (ModelConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // Header
        Text(
            text = "API Server",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Server Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isServerRunning) 
                    MaterialTheme.colorScheme.primaryContainer 
                else 
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Server Status",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isServerRunning) {
                                if (serverLocalIp.isNotEmpty()) {
                                    "Running on $serverLocalIp:$serverPort"
                                } else {
                                    "Running on port $serverPort"
                                }
                            } else "Stopped",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isServerRunning) 
                                MaterialTheme.colorScheme.onPrimaryContainer 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Status indicator
                    Surface(
                        color = if (isServerRunning) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.outline,
                        shape = androidx.compose.foundation.shape.CircleShape,
                        modifier = Modifier.size(12.dp)
                    ) {}
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Control button
                Button(
                    onClick = if (isServerRunning) onStopServer else onStartServer,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isServerRunning) 
                            MaterialTheme.colorScheme.error 
                        else 
                            MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        if (isServerRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isServerRunning) "Stop Server" else "Start Server",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isServerRunning) "Stop Server" else "Start Server")
                }
            }
        }
        
        // Model Configuration Card
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Server Model Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    var showConfigDialog by remember { mutableStateOf(false) }
                    
                    IconButton(onClick = { showConfigDialog = true }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Configure Model Settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    if (showConfigDialog) {
                        ServerModelConfigDialog(
                            currentConfig = modelConfig,
                            onDismiss = { showConfigDialog = false },
                            onConfigUpdate = { newConfig ->
                                onUpdateModelConfig(newConfig)
                                showConfigDialog = false
                            }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Current configuration display
                Text(
                    text = "Current Settings:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Temperature: ${modelConfig.temperature}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Max Tokens: ${modelConfig.maxTokens}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Top-K: ${modelConfig.topK}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Random Seed: ${modelConfig.randomSeed}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "These settings will be used as defaults for API requests that don't specify their own parameters.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
        
        if (isServerRunning) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Base URL calculation
            val baseUrl = if (serverLocalIp.isNotEmpty()) {
                "http://$serverLocalIp:$serverPort"
            } else {
                "http://localhost:$serverPort"
            }
            
            // API Endpoints Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "API Endpoints",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    EndpointItem(
                        title = "Base URL",
                        url = baseUrl,
                        onCopy = { 
                            clipboardManager.setText(AnnotatedString(baseUrl))
                        }
                    )
                    
                    if (serverLocalIp.isNotEmpty()) {
                        EndpointItem(
                            title = "Local Network URL",
                            url = "http://$serverLocalIp:$serverPort",
                            onCopy = { 
                                clipboardManager.setText(AnnotatedString("http://$serverLocalIp:$serverPort"))
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // OpenAI Compatible Endpoints
                    Text(
                        text = "OpenAI Compatible",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    EndpointItem(
                        title = "List Models",
                        url = "GET /v1/models",
                        onCopy = { 
                            clipboardManager.setText(AnnotatedString("$baseUrl/v1/models"))
                        }
                    )
                    
                    EndpointItem(
                        title = "Chat Completions",
                        url = "POST /v1/chat/completions",
                        onCopy = { 
                            clipboardManager.setText(AnnotatedString("$baseUrl/v1/chat/completions"))
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Custom Endpoints
                    Text(
                        text = "Spark Custom",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    EndpointItem(
                        title = "All Models",
                        url = "GET /spark/models",
                        onCopy = { 
                            clipboardManager.setText(AnnotatedString("$baseUrl/spark/models"))
                        }
                    )
                    
                    EndpointItem(
                        title = "Load Model",
                        url = "POST /spark/models/{id}/load",
                        onCopy = { 
                            clipboardManager.setText(AnnotatedString("$baseUrl/spark/models/{id}/load"))
                        }
                    )
                    
                    EndpointItem(
                        title = "Unload Model",
                        url = "POST /spark/models/{id}/unload",
                        onCopy = { 
                            clipboardManager.setText(AnnotatedString("$baseUrl/spark/models/{id}/unload"))
                        }
                    )
                    
                    EndpointItem(
                        title = "Get Config",
                        url = "GET /spark/config",
                        onCopy = { 
                            clipboardManager.setText(AnnotatedString("$baseUrl/spark/config"))
                        }
                    )
                    
                    EndpointItem(
                        title = "Update Config",
                        url = "POST /spark/config",
                        onCopy = { 
                            clipboardManager.setText(AnnotatedString("$baseUrl/spark/config"))
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Example Usage Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Example Usage",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "cURL Example:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val curlExample = """
                        curl -X POST $baseUrl/v1/chat/completions \
                          -H "Content-Type: application/json" \
                          -d '{
                            "model": "${loadedModels.firstOrNull()?.id ?: "model-id"}",
                            "messages": [
                              {"role": "user", "content": "Hello, how are you?"}
                            ],
                            "temperature": 0.7,
                            "max_tokens": 100
                          }'
                    """.trimIndent()
                    
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = curlExample,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(curlExample))
                                }
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Model Configuration Examples:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val configExample = """
                        # Get current server config
                        curl $baseUrl/spark/config
                        
                        # Update server config
                        curl -X POST $baseUrl/spark/config \
                          -H "Content-Type: application/json" \
                          -d '{
                            "maxTokens": 1500,
                            "temperature": 0.8,
                            "topK": 40,
                            "randomSeed": 0
                          }'
                    """.trimIndent()
                    
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = configExample,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(configExample))
                                }
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Loaded Models Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "Loaded Models (${loadedModels.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (loadedModels.isEmpty()) {
                    Text(
                        text = "No models loaded",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    loadedModels.forEach { model ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = model.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "ID: ${model.id}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Loaded",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        if (model != loadedModels.last()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EndpointItem(
    title: String,
    url: String,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        IconButton(onClick = onCopy) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "Copy URL",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerModelConfigDialog(
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
                    text = "Server Model Configuration",
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
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Note: These settings will be used as defaults for API requests that don't specify their own parameters.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
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