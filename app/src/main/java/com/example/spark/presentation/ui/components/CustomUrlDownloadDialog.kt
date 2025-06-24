package com.example.spark.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomUrlDownloadDialog(
    urlInput: String,
    isDownloading: Boolean,
    downloadProgress: Float,
    onDismiss: () -> Unit,
    onUrlChange: (String) -> Unit,
    onDownload: (String, String, String) -> Unit // url, name, description
) {
    var modelName by remember { mutableStateOf("") }
    var modelDescription by remember { mutableStateOf("") }
    
    // Auto-generate name from URL
    LaunchedEffect(urlInput) {
        if (urlInput.isNotBlank() && modelName.isEmpty()) {
            val fileName = urlInput.substringAfterLast("/").substringBeforeLast(".")
            if (fileName.isNotBlank()) {
                modelName = fileName.replace("-", " ").replace("_", " ")
            }
        }
    }

    Dialog(
        onDismissRequest = if (isDownloading) ({}) else onDismiss,
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Download from URL",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (!isDownloading) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                if (isDownloading) {
                    // Download progress
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Downloading... ${(downloadProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    // URL input
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = onUrlChange,
                        label = { Text("Model URL") },
                        placeholder = { Text("https://huggingface.co/...") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(Icons.Default.Link, contentDescription = null)
                        },
                        supportingText = {
                            Text("Enter a direct download link to a .task or .tflite model file")
                        }
                    )

                    // Model name
                    OutlinedTextField(
                        value = modelName,
                        onValueChange = { modelName = it },
                        label = { Text("Model Name") },
                        placeholder = { Text("My Custom Model") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(Icons.Default.Title, contentDescription = null)
                        }
                    )

                    // Model description
                    OutlinedTextField(
                        value = modelDescription,
                        onValueChange = { modelDescription = it },
                        label = { Text("Description (Optional)") },
                        placeholder = { Text("Custom model downloaded from URL") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(Icons.Default.Description, contentDescription = null)
                        },
                        maxLines = 3
                    )

                    // Instructions
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Supported URLs:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "• MediaPipe .task files (recommended)\n• HuggingFace model files (.tflite may not work)\n• Direct download links\n\nExample: SmolLM .task files from:\nhttps://huggingface.co/litert-community/SmolLM-135M-Instruct",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        
                        Button(
                            onClick = {
                                val finalDescription = modelDescription.ifBlank { "Custom model downloaded from URL" }
                                onDownload(urlInput.trim(), modelName.trim(), finalDescription)
                            },
                            modifier = Modifier.weight(1f),
                            enabled = urlInput.isNotBlank() && 
                                     modelName.isNotBlank() && 
                                     (urlInput.startsWith("http://") || urlInput.startsWith("https://"))
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Download")
                        }
                    }
                }
            }
        }
    }
} 