package com.example.spark.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spark.domain.models.AvailableModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvailableModelCard(
    model: AvailableModel,
    isDownloading: Boolean = false,
    downloadProgress: Float = 0f,
    isAlreadyDownloaded: Boolean = false,
    onDownload: (AvailableModel) -> Unit,
    onCancelDownload: (AvailableModel) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Use actual requirements data or calculate from model name/size
    val ramRequirement = remember(model.requirements, model.name, model.size) {
        model.requirements?.recommendedRam ?: run {
            // Fallback calculation based on model name and size
            when {
                model.name.contains("7B", ignoreCase = true) || model.name.contains("8B", ignoreCase = true) -> "8GB"
                model.name.contains("3B", ignoreCase = true) || model.name.contains("4B", ignoreCase = true) -> "4GB"
                model.name.contains("1B", ignoreCase = true) || model.name.contains("2B", ignoreCase = true) -> "2GB"
                model.name.contains("13B", ignoreCase = true) -> "16GB"
                model.size.contains("555", ignoreCase = true) -> "2GB" // Gemma 3 1B
                model.size.contains("1.5", ignoreCase = true) -> "4GB"
                model.size.contains("2.9", ignoreCase = true) -> "8GB"
                else -> "4GB" // Default reasonable requirement
            }
        }
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header: Model name, author, and status - match ModelCard layout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = model.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = "by ${model.author}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                // Status indicator - match ModelCard style
                Surface(
                    color = when {
                        isDownloading -> MaterialTheme.colorScheme.secondary
                        isAlreadyDownloaded -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSecondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = when {
                                isDownloading -> "Downloading..."
                                isAlreadyDownloaded -> "Downloaded"
                                else -> "Available"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                isDownloading -> MaterialTheme.colorScheme.onSecondary
                                isAlreadyDownloaded -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Model info - Storage and RAM in simple text format like ModelCard
            Text(
                text = "Size: ${model.size} • RAM: ~${ramRequirement}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons - match ModelCard layout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when {
                    isAlreadyDownloaded -> {
                        OutlinedButton(
                            onClick = { },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = false,
                            colors = ButtonDefaults.outlinedButtonColors(
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Downloaded")
                        }
                    }
                    isDownloading -> {
                        // Download progress
                        Column {
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.fillMaxWidth(),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Downloading... ${(downloadProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = { onCancelDownload(model) },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Stop,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Cancel", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    else -> {
                        Button(
                            onClick = { onDownload(model) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Download",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Download")
                        }
                    }
                }
            }
        }
    }
} 