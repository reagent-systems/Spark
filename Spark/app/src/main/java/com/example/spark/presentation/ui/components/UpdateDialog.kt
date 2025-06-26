package com.example.spark.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.spark.domain.models.ChangeType
import com.example.spark.domain.models.ChangelogItem
import com.example.spark.domain.models.UpdateInfo
import androidx.compose.ui.platform.LocalContext
import android.content.pm.PackageManager

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    isDownloading: Boolean = false,
    downloadProgress: Float = 0f,
    onUpdate: () -> Unit,
    onSkip: () -> Unit,
    onViewChangelog: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isDownloading,
            dismissOnClickOutside = !isDownloading
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Update icon
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = "Update Available",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Title
                Text(
                    text = "Update Available",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Version info
                Text(
                    text = "Version ${updateInfo.versionName} is now available",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                val context = LocalContext.current
                val currentVersion = remember {
                    try {
                        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                        "v${packageInfo.versionName}"
                    } catch (e: PackageManager.NameNotFoundException) {
                        "v1.0.0"
                    }
                }
                
                Text(
                    text = "Current version: $currentVersion",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Quick changelog preview (first 3 items)
                if (updateInfo.changelog.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "What's New:",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            updateInfo.changelog.take(3).forEach { change ->
                                ChangelogItemRow(
                                    change = change,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                            
                            if (updateInfo.changelog.size > 3) {
                                Spacer(modifier = Modifier.height(4.dp))
                                TextButton(
                                    onClick = onViewChangelog,
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("View Full Changelog")
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .padding(start = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // Download progress
                if (isDownloading) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Downloading update...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = "${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
                

                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (!isDownloading) {
                        TextButton(
                            onClick = onSkip
                        ) {
                            Text("Skip")
                        }
                    }
                    
                    Button(
                        onClick = onUpdate,
                        enabled = !isDownloading
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isDownloading) "Downloading..." else "Update Now")
                    }
                }
            }
        }
    }
}

@Composable
fun ChangelogDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "What's New",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Version ${updateInfo.versionName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Changelog list
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(updateInfo.changelog) { change ->
                        ChangelogItemCard(change = change)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ChangelogItemRow(
    change: ChangelogItem,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(getChangeTypeColor(change.type))
                .padding(top = 6.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = change.description,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ChangelogItemCard(
    change: ChangelogItem
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(getChangeTypeColor(change.type))
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = getChangeTypeDisplayName(change.type),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = getChangeTypeColor(change.type)
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = change.description,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun getChangeTypeColor(type: ChangeType): Color {
    return when (type) {
        ChangeType.NEW_FEATURE -> Color(0xFF4CAF50) // Green
        ChangeType.IMPROVEMENT -> Color(0xFF2196F3) // Blue
        ChangeType.BUG_FIX -> Color(0xFFFF9800) // Orange
        ChangeType.BREAKING_CHANGE -> Color(0xFFF44336) // Red
        ChangeType.SECURITY -> Color(0xFF9C27B0) // Purple
        ChangeType.DEPRECATED -> Color(0xFF795548) // Brown
    }
}

private fun getChangeTypeDisplayName(type: ChangeType): String {
    return when (type) {
        ChangeType.NEW_FEATURE -> "New Feature"
        ChangeType.IMPROVEMENT -> "Improvement"
        ChangeType.BUG_FIX -> "Bug Fix"
        ChangeType.BREAKING_CHANGE -> "Breaking Change"
        ChangeType.SECURITY -> "Security"
        ChangeType.DEPRECATED -> "Deprecated"
    }
} 