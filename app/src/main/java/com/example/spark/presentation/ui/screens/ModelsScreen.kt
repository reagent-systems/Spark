package com.example.spark.presentation.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.spark.domain.models.LLMModel
import com.example.spark.presentation.ui.components.ModelCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    models: List<LLMModel>,
    isLoading: Boolean,
    loadingModelId: String? = null,
    onLoadModel: (String) -> Unit,
    onUnloadModel: (String) -> Unit,
    onAddModel: (String, String, String) -> Unit,
    onDeleteModel: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddModelDialog by remember { mutableStateOf(false) }
    var selectedFilePath by remember { mutableStateOf("") }
    var selectedFileName by remember { mutableStateOf("") }
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
        
        Spacer(modifier = Modifier.height(16.dp))
        
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
                    Button(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Model")
                    }
                }
            }
        } else {
            // Models list
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(models) { model ->
                    ModelCard(
                        model = model,
                        isLoading = loadingModelId == model.id,
                        onLoadClick = { onLoadModel(model.id) },
                        onUnloadClick = { onUnloadModel(model.id) },
                        onDeleteClick = { onDeleteModel(model.id) }
                    )
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