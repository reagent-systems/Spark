package com.example.spark.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.spark.domain.models.LLMModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen(
    isServerRunning: Boolean,
    serverPort: Int,
    serverLocalIp: String = "",
    loadedModels: List<LLMModel>,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
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
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )
        }
        IconButton(
            onClick = onCopy,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "Copy",
                modifier = Modifier.size(16.dp)
            )
        }
    }
} 