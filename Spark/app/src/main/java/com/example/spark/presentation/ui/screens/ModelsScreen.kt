package com.example.spark.presentation.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.spark.domain.models.LLMModel
import com.example.spark.domain.models.AvailableModel
import com.example.spark.domain.models.ModelCategory
import com.example.spark.presentation.ui.components.ModelCard
import com.example.spark.presentation.ui.components.AvailableModelCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    models: List<LLMModel>,
    downloadableModels: List<AvailableModel> = emptyList(),
    categories: List<ModelCategory> = emptyList(),
    isLoading: Boolean,
    loadingModelId: String? = null,
    unloadingModelId: String? = null,
    downloadingModelId: String? = null,
    downloadProgress: Float = 0f,
    isHuggingFaceAuthenticated: Boolean = false,
    onLoadModel: (String) -> Unit,
    onUnloadModel: (String) -> Unit,
    onAddModel: (String, String, String) -> Unit,
    onDeleteModel: (String) -> Unit,
    onDownloadModel: (AvailableModel) -> Unit,
    onCancelDownload: (AvailableModel) -> Unit,
    onShowHuggingFaceSettings: () -> Unit = {},
    onShowCustomUrlDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showAddModelDialog by remember { mutableStateOf(false) }
    var selectedFilePath by remember { mutableStateOf("") }
    var selectedFileName by remember { mutableStateOf("") }
    var selectedTabIndex by remember { mutableStateOf(0) }
    var selectedCategory by remember { mutableStateOf("all") }
    val context = LocalContext.current
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            // Get the file name from URI
            val fileName = getFileName(context, uri)
            selectedFileName = fileName ?: "Unknown Model"
            selectedFilePath = uri.toString()
            showAddModelDialog = true
        }
    }
    
    val tabs = listOf("Local Models", "Download Models")
    
    // Filter downloadable models based on selected category (optimized for performance)
    val filteredModels = remember(downloadableModels, selectedCategory) {
        when (selectedCategory) {
            "all" -> downloadableModels
            "recommended" -> downloadableModels.filter { it.isRecommended }
            "lightweight" -> downloadableModels.filter { 
                it.size.contains("0.5", ignoreCase = true) || 
                it.size.contains("1.5", ignoreCase = true) ||
                it.tags.contains("mobile-optimized") 
            }
            "reasoning" -> downloadableModels.filter { it.tags.contains("reasoning") }
            "multilingual" -> downloadableModels.filter { it.tags.contains("multilingual") }
            "multimodal" -> downloadableModels.filter { it.tags.contains("multimodal") }
            "google" -> downloadableModels.filter { it.author.equals("Google", ignoreCase = true) }
            else -> downloadableModels.filter { it.tags.contains(selectedCategory) }
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Models",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Settings button
                IconButton(
                    onClick = onShowHuggingFaceSettings
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "HuggingFace Settings"
                    )
                }
                
                // Add model button
                FloatingActionButton(
                    onClick = { filePickerLauncher.launch("*/*") },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add Model"
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Tabs
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title) },
                    icon = {
                        Icon(
                            if (index == 0) Icons.Default.Add else Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Content based on selected tab
        when (selectedTabIndex) {
            0 -> {
                // Local Models Tab
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (models.isEmpty()) {
                    // Empty state
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No models available",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Add a model to get started",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            Row(
                                modifier = Modifier.padding(top = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { filePickerLauncher.launch("*/*") }
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Add Model")
                                }
                                OutlinedButton(
                                    onClick = { selectedTabIndex = 1 }
                                ) {
                                    Icon(
                                        Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Download")
                                }
                            }
                        }
                    }
                } else {
                    // Models list
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        userScrollEnabled = true,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(
                            items = models,
                            key = { model -> model.id },
                            contentType = { "LocalModel" }
                        ) { model ->
                            ModelCard(
                                model = model,
                                isLoading = loadingModelId == model.id,
                                isUnloading = unloadingModelId == model.id,
                                onLoadClick = { onLoadModel(model.id) },
                                onUnloadClick = { onUnloadModel(model.id) },
                                onDeleteClick = { onDeleteModel(model.id) }
                            )
                        }
                    }
                }
            }
            1 -> {
                // Download Models Tab - Use LazyColumn for the entire content to enable proper scrolling
                if (downloadableModels.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        modifier = Modifier.fillMaxSize(),
                        // Performance optimizations
                        userScrollEnabled = true,
                        reverseLayout = false
                    ) {
                        // Custom URL button
                        item {
                            OutlinedButton(
                                onClick = onShowCustomUrlDialog,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Default.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Download from Custom URL")
                            }
                        }
                        
                        // Category filters (simplified)
                        if (categories.isNotEmpty()) {
                            item {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    item {
                                        FilterChip(
                                            onClick = { selectedCategory = "all" },
                                            label = { Text("All", style = MaterialTheme.typography.labelMedium) },
                                            selected = selectedCategory == "all"
                                        )
                                    }
                                    
                                    items(categories) { category ->
                                        FilterChip(
                                            onClick = { selectedCategory = category.id },
                                            label = { Text(category.name, style = MaterialTheme.typography.labelMedium) },
                                            selected = selectedCategory == category.id
                                        )
                                    }
                                }
                            }
                        }
                        
                        // HuggingFace authentication status (simplified)
                        if (!isHuggingFaceAuthenticated && downloadableModels.any { it.needsHuggingFaceAuth }) {
                            item {
                                OutlinedCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.outlinedCardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.1f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "⚠️ Some models need HuggingFace login",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(
                                            onClick = onShowHuggingFaceSettings
                                        ) {
                                            Text("Login", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Show filtered count (only if filtered)
                        if (selectedCategory != "all" && filteredModels.size != downloadableModels.size) {
                            item {
                                Text(
                                    text = "${filteredModels.size} models",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        // Model cards
                        items(
                            items = filteredModels,
                            key = { model -> model.id },
                            contentType = { "AvailableModel" }
                        ) { availableModel ->
                            AvailableModelCard(
                                model = availableModel,
                                isDownloading = downloadingModelId == availableModel.id,
                                downloadProgress = if (downloadingModelId == availableModel.id) downloadProgress else 0f,
                                isAlreadyDownloaded = models.any { it.id == availableModel.id },
                                isHuggingFaceAuthenticated = isHuggingFaceAuthenticated,
                                onDownload = onDownloadModel,
                                onCancelDownload = onCancelDownload,
                                onShowHuggingFaceAuth = onShowHuggingFaceSettings
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Add Model Dialog
    if (showAddModelDialog) {
        AddModelDialog(
            initialFilePath = selectedFilePath,
            initialName = selectedFileName,
            onDismiss = { 
                showAddModelDialog = false
                selectedFilePath = ""
                selectedFileName = ""
            },
            onConfirm = { name, description, filePath ->
                onAddModel(filePath, name, description)
                showAddModelDialog = false
                selectedFilePath = ""
                selectedFileName = ""
            }
        )
    }
}

@Composable
fun AddModelDialog(
    initialFilePath: String = "",
    initialName: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf("") }
    var filePath by remember { mutableStateOf(initialFilePath.ifEmpty { "/data/local/tmp/llm/model.task" }) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add Model")
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Model Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = filePath,
                    onValueChange = { filePath = it },
                    label = { Text("File Path") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && filePath.isNotBlank()) {
                        onConfirm(name, description, filePath)
                    }
                },
                enabled = name.isNotBlank() && filePath.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val displayNameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    result = it.getString(displayNameIndex)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != -1 && cut != null) {
            result = result?.substring(cut + 1)
        }
    }
    return result?.removeSuffix(".task") // Remove .task extension for cleaner name
}