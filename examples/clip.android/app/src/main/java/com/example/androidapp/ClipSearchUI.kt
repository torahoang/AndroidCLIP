package com.example.androidapp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipSearchUI(
    modifier: Modifier = Modifier,
    isEncoding: Boolean,
    encodingProgress: String,
    isQuerying: Boolean,
    searchResults: List<String>,
    availableFolders: List<String>,
    onSelectFolder: (String) -> Unit,
    onSearch: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showFolderDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Encoding Status
        if (isEncoding) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = encodingProgress)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Add Folder Button
        Button(
            onClick = { showFolderDialog = true },
            enabled = !isEncoding && !isQuerying
        ) {
            Text("Add Folder to Encode")
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search images...") },
            trailingIcon = {
                IconButton(
                    onClick = { onSearch(searchQuery) },
                    enabled = !isQuerying && !isEncoding && searchQuery.isNotBlank()
                ) {
                    Icon(Icons.Default.Search, "Search")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isQuerying && !isEncoding
        )

        // Folder Selection Dialog
        if (showFolderDialog) {
            AlertDialog(
                onDismissRequest = { showFolderDialog = false },
                title = { Text("Select Folder from Assets") },
                text = {
                    Column {
                        Text("Choose a folder to encode:")
                        Spacer(modifier = Modifier.height(16.dp))
                        availableFolders.forEach { folder ->
                            TextButton(
                                onClick = {
                                    onSelectFolder(folder)
                                    showFolderDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(folder)
                            }
                        }
                        if (availableFolders.isEmpty()) {
                            Text("No folders found in assets")
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showFolderDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Query Status
        if (isQuerying) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text("Searching...")
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Search Results
        if (searchResults.isNotEmpty()) {
            Text(
                text = "Top ${searchResults.size} Results:",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(searchResults) { imagePath ->
                    Card(
                        modifier = Modifier.size(200.dp)
                    ) {
                        Column {
                            Image(
                                painter = rememberAsyncImagePainter(File(imagePath)),
                                contentDescription = "Search result",
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentScale = ContentScale.Crop
                            )
                            Text(
                                text = File(imagePath).name,
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}