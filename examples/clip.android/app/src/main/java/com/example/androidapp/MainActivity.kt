package com.example.androidapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.androidapp.ui.theme.AndroidappTheme
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.clip.ClipExtractor
import com.example.clip.ClipResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import android.content.Context
class MainActivity : ComponentActivity() {

    private val clipExtractor = ClipExtractor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AndroidappTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ClipDemo(
                        modifier = Modifier.padding(innerPadding),
                        onProcessImages = { processImagesAndTexts() }
                    )
                }
            }
        }
    }

    /**
     * Example 1: Basic usage - extract embeddings from images and texts
     */
    private fun processImagesAndTexts() {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {

                    // 1. Get the model path
                    val modelPath = getAssetFilePath(applicationContext, "models/CLIP-ViT-B-32-laion2B-s34B-b79K_ggml-model-q8_0.gguf")

                    // 2. Get the image paths
                    val imagePaths = arrayOf(
                        getAssetFolderPath(applicationContext, "tests"),
                    )


                    val texts = arrayOf(
                        "a photo of a cat"
                    )

                    clipExtractor.extractVectors(
                        modelPath = modelPath,
                        imagePaths = imagePaths,
                        texts = texts,
                        nThreads = 4,
                        verbose = 1
                    )
                }

                handleResult(result)

            } catch (e: Exception) {
                Log.e(TAG, "Error processing: ${e.message}", e)
            }
        }
    }
    private fun handleResult(result: ClipResult) {
        if (result.success) {
            Log.d(TAG, "✓ Processing successful!")
            Log.d(TAG, "Processed ${result.processedImages} images")
            Log.d(TAG, "Processed ${result.processedTexts} texts")
            Log.d(TAG, "Total time: ${result.totalProcessingTime} seconds")

            Log.d(TAG, "\nEmbeddings:")
            Log.d(TAG, "- ${result.imageEmbeddings.size} image embeddings")
            Log.d(TAG, "- ${result.textEmbeddings.size} text embeddings")

            if (result.imageEmbeddings.isNotEmpty()) {
                Log.d(TAG, "- Image embedding dimension: ${result.imageEmbeddings[0].size}")
            }

            if (result.textEmbeddings.isNotEmpty()) {
                Log.d(TAG, "- Text embedding dimension: ${result.textEmbeddings[0].size}")
            }

        } else {
            Log.e(TAG, "✗ Processing failed: ${result.errorMessage}")
        }
    }

    private fun getAssetFilePath(context: Context, assetName: String): String {
        val file = File(context.filesDir, assetName)

        // Check if the file already exists in internal storage
        if (file.exists()) {
            Log.d(TAG, "File already exists: ${file.absolutePath}")
            return file.absolutePath
        }

        // Ensure parent directories exist
        file.parentFile?.mkdirs()

        try {
            // Open the asset stream
            val inputStream: InputStream = context.assets.open(assetName)

            // Create an output stream to the destination file
            val outputStream = FileOutputStream(file)

            // Copy the bytes
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            Log.d(TAG, "Copied asset '$assetName' to '${file.absolutePath}'")

            // Return the path to the new file
            return file.absolutePath

        } catch (e: Exception) {
            Log.e(TAG, "Error copying asset '$assetName': ${e.message}", e)
            // Re-throw the exception to be handled by the caller
            throw e
        }
    }

    private fun getAssetFolderPath(context: Context, assetFolderName: String): String {
        val destFolder = File(context.filesDir, assetFolderName)

        // Create the folder if it doesn't exist
        if (!destFolder.exists()) {
            destFolder.mkdirs()

            // Copy all files from the asset folder
            try {
                val assetManager = context.assets
                val files = assetManager.list(assetFolderName) ?: emptyArray()

                for (filename in files) {
                    val assetPath = "$assetFolderName/$filename"
                    val destFile = File(destFolder, filename)

                    assetManager.open(assetPath).use { inputStream ->
                        FileOutputStream(destFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return destFolder.absolutePath  // Returns the folder path
    }

    companion object {
        private const val TAG = "ClipExample"
    }
}

@Composable
fun ClipDemo(
    modifier: Modifier = Modifier,
    onProcessImages: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "CLIP Extractor Demo",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Test CLIP model functionality",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onProcessImages,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Process Images & Texts")
        }


        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Check Logcat for results",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}