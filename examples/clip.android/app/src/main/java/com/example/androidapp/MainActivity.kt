package com.example.androidapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.androidapp.ui.theme.AndroidappTheme
import android.util.Log
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
import androidx.compose.runtime.*
import io.objectbox.Box
import io.objectbox.BoxStore
import org.json.JSONObject
import org.json.JSONArray
class MainActivity : ComponentActivity() {

    private val clipExtractor = ClipExtractor()
    private var isEncoding = mutableStateOf(false)
    private var isQuerying = mutableStateOf(false)
    private var encodingProgress = mutableStateOf("")
    private var searchResults = mutableStateOf<List<String>>(emptyList())
    private var availableFolders = mutableStateOf<List<String>>(emptyList())

    private lateinit var boxStore: BoxStore
    private lateinit var imageEmbeddingBox: Box<ImageEmbedding>

    // JSON file to track encoded files
    private val encodedFilesJsonName = "encoded_files.json"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize ObjectBox
        boxStore = MyObjectBox.builder()
            .androidContext(applicationContext)
            .build()
        imageEmbeddingBox = boxStore.boxFor(ImageEmbedding::class.java)

        // Load available folders from assets
        lifecycleScope.launch {
            loadAvailableFolders()
        }

        setContent {
            AndroidappTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ClipSearchUI(
                        modifier = Modifier.padding(innerPadding),
                        isEncoding = isEncoding.value,
                        encodingProgress = encodingProgress.value,
                        isQuerying = isQuerying.value,
                        searchResults = searchResults.value,
                        availableFolders = availableFolders.value,
                        onSelectFolder = { folderName -> encodeImagesFromFolder(folderName) },
                        onSearch = { query -> searchImages(query) }
                    )
                }
            }
        }
    }

    /**
     * Load available folders from assets directory
     */
    private suspend fun loadAvailableFolders() {
        withContext(Dispatchers.IO) {
            try {
                val assetManager = applicationContext.assets
                val folders = assetManager.list("") ?: emptyArray()

                // Filter for actual folders (exclude files)
                val validFolders = folders.filter { folder ->
                    try {
                        // Check if it's a folder by trying to list its contents
                        val contents = assetManager.list(folder)
                        contents != null && contents.isNotEmpty()
                    } catch (e: Exception) {
                        false
                    }
                }.filter { it != "models" } // Exclude models folder

                availableFolders.value = validFolders
                Log.d(TAG, "Found folders in assets: $validFolders")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading folders from assets: ${e.message}", e)
                availableFolders.value = emptyList()
            }
        }
    }

    /**
     * Load the encoded files registry from JSON
     */
    private fun loadEncodedFilesRegistry(): MutableMap<String, MutableSet<String>> {
        val file = File(applicationContext.filesDir, encodedFilesJsonName)
        if (!file.exists()) {
            return mutableMapOf()
        }

        return try {
            val jsonString = file.readText()
            val jsonObject = JSONObject(jsonString)
            val registry = mutableMapOf<String, MutableSet<String>>()

            jsonObject.keys().forEach { folder ->
                val filesArray = jsonObject.getJSONArray(folder)
                val filesSet = mutableSetOf<String>()
                for (i in 0 until filesArray.length()) {
                    filesSet.add(filesArray.getString(i))
                }
                registry[folder] = filesSet
            }

            registry
        } catch (e: Exception) {
            Log.e(TAG, "Error loading encoded files registry: ${e.message}", e)
            mutableMapOf()
        }
    }

    /**
     * Save the encoded files registry to JSON
     */
    private fun saveEncodedFilesRegistry(registry: Map<String, Set<String>>) {
        val file = File(applicationContext.filesDir, encodedFilesJsonName)

        try {
            val jsonObject = JSONObject()
            registry.forEach { (folder, files) ->
                val filesArray = JSONArray(files.toList())
                jsonObject.put(folder, filesArray)
            }

            file.writeText(jsonObject.toString(2)) // Pretty print with indent of 2
            Log.d(TAG, "Saved encoded files registry")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving encoded files registry: ${e.message}", e)
        }
    }

    /**
     * Encode all images from the selected folder and store in ObjectBox
     */
    private fun encodeImagesFromFolder(folderName: String) {
        lifecycleScope.launch {
            isEncoding.value = true
            encodingProgress.value = "Preparing to encode folder: $folderName"

            try {
                withContext(Dispatchers.IO) {
                    // Load the registry of already encoded files
                    val encodedRegistry = loadEncodedFilesRegistry()
                    val encodedFilesInFolder = encodedRegistry.getOrDefault(folderName, mutableSetOf())

                    // Get model path
                    val modelPath = getAssetFilePath(
                        applicationContext,
                        "models/CLIP-ViT-B-32-laion2B-s34B-b79K_ggml-model-q8_0.gguf"
                    )

                    // Get the selected folder path
                    val imagesFolderPath = getAssetFolderPath(applicationContext, folderName)
                    val imagesFolder = File(imagesFolderPath)
                    val allImageFiles = imagesFolder.listFiles { file ->
                        file.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp")
                    } ?: emptyArray()

                    if (allImageFiles.isEmpty()) {
                        encodingProgress.value = "No images found in folder: $folderName"
                        Log.w(TAG, "No images found in folder: $folderName")
                        return@withContext
                    }

                    // Filter out already encoded files
                    val imageFiles = allImageFiles.filter { file ->
                        file.absolutePath !in encodedFilesInFolder
                    }.toTypedArray()

                    val skippedCount = allImageFiles.size - imageFiles.size

                    if (imageFiles.isEmpty()) {
                        encodingProgress.value = "✓ All ${allImageFiles.size} images in '$folderName' are already encoded!"
                        Log.d(TAG, "All images in $folderName are already encoded")
                        return@withContext
                    }

                    Log.d(TAG, "Found ${imageFiles.size} new images to encode in $folderName (skipped $skippedCount already encoded)")
                    encodingProgress.value = if (skippedCount > 0) {
                        "Found ${imageFiles.size} new images (skipped $skippedCount already encoded)"
                    } else {
                        "Found ${imageFiles.size} images in $folderName"
                    }

                    // Process images in batches to avoid memory issues
                    val batchSize = 10
                    var totalEncoded = 0

                    imageFiles.toList().chunked(batchSize).forEachIndexed { batchIndex, batch ->
                        val startIdx = batchIndex * batchSize
                        encodingProgress.value = "Encoding images ${startIdx + 1}-${startIdx + batch.size} of ${imageFiles.size}..."

                        val imagePaths = batch.map { it.absolutePath }.toTypedArray()

                        val result = clipExtractor.extractVectors(
                            modelPath = modelPath,
                            imagePaths = imagePaths,
                            texts = emptyArray(),
                            nThreads = 4,
                            verbose = 0
                        )

                        if (result.success) {
                            // Store embeddings in ObjectBox
                            result.imageEmbeddings.forEachIndexed { index, embedding ->
                                val imageEmbedding = ImageEmbedding(
                                    imagePath = batch[index].absolutePath,
                                    imageName = batch[index].name,
                                    embedding = embedding
                                )
                                imageEmbeddingBox.put(imageEmbedding)

                                // Add to encoded files registry
                                encodedFilesInFolder.add(batch[index].absolutePath)
                            }
                            totalEncoded += batch.size
                            encodingProgress.value = "Encoded $totalEncoded of ${imageFiles.size} images..."
                            Log.d(TAG, "Stored batch ${batchIndex + 1} embeddings ($totalEncoded total)")
                        } else {
                            Log.e(TAG, "Failed to encode batch ${batchIndex + 1}: ${result.errorMessage}")
                            encodingProgress.value = "Error encoding batch ${batchIndex + 1}"
                        }
                    }

                    // Update and save the registry
                    encodedRegistry[folderName] = encodedFilesInFolder
                    saveEncodedFilesRegistry(encodedRegistry)

                    val statusMessage = if (skippedCount > 0) {
                        "✓ Completed! Encoded $totalEncoded new images from '$folderName' (skipped $skippedCount already encoded)"
                    } else {
                        "✓ Completed! Encoded $totalEncoded images from '$folderName'"
                    }

                    encodingProgress.value = statusMessage
                    Log.d(TAG, "Encoding completed. Total embeddings in DB: ${imageEmbeddingBox.count()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during encoding: ${e.message}", e)
                encodingProgress.value = "Error: ${e.message}"
            } finally {
                isEncoding.value = false
            }
        }
    }

    /**
     * Search for images using text query
     */
    private fun searchImages(query: String) {
        if (query.isBlank()) return

        lifecycleScope.launch {
            isQuerying.value = true

            try {
                // Check if there are any embeddings in the database
                val embeddingCount = withContext(Dispatchers.IO) {
                    imageEmbeddingBox.count()
                }

                if (embeddingCount == 0L) {
                    Log.w(TAG, "No embeddings in database. Please encode images first.")
                    encodingProgress.value = "Please add and encode a folder first!"
                    isQuerying.value = false
                    return@launch
                }

                val results = withContext(Dispatchers.IO) {
                    // Get model path
                    val modelPath = getAssetFilePath(
                        applicationContext,
                        "models/CLIP-ViT-B-32-laion2B-s34B-b79K_ggml-model-q8_0.gguf"
                    )

                    // Encode the query text
                    val result = clipExtractor.extractVectors(
                        modelPath = modelPath,
                        imagePaths = emptyArray(),
                        texts = arrayOf(query),
                        nThreads = 4,
                        verbose = 0
                    )

                    if (result.success && result.textEmbeddings.isNotEmpty()) {
                        val queryEmbedding = result.textEmbeddings[0]

                        // Perform vector search using ObjectBox
                        val nearest = imageEmbeddingBox.query()
                            .nearestNeighbors(
                                ImageEmbedding_.embedding,
                                queryEmbedding,
                                3  // Get top 3 results
                            )
                            .build()
                            .find()

                        Log.d(TAG, "Found ${nearest.size} similar images for query: '$query'")
                        nearest.map { it.imagePath }
                    } else {
                        Log.e(TAG, "Failed to encode query: ${result.errorMessage}")
                        emptyList()
                    }
                }

                searchResults.value = results

            } catch (e: Exception) {
                Log.e(TAG, "Error during search: ${e.message}", e)
            } finally {
                isQuerying.value = false
            }
        }
    }

    private fun getAssetFilePath(context: Context, assetName: String): String {
        val file = File(context.filesDir, assetName)

        if (file.exists()) {
            Log.d(TAG, "File already exists: ${file.absolutePath}")
            return file.absolutePath
        }

        file.parentFile?.mkdirs()

        try {
            val inputStream: InputStream = context.assets.open(assetName)
            val outputStream = FileOutputStream(file)

            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            Log.d(TAG, "Copied asset '$assetName' to '${file.absolutePath}'")
            return file.absolutePath

        } catch (e: Exception) {
            Log.e(TAG, "Error copying asset '$assetName': ${e.message}", e)
            throw e
        }
    }

    private fun getAssetFolderPath(context: Context, assetFolderName: String): String {
        val destFolder = File(context.filesDir, assetFolderName)

        if (!destFolder.exists()) {
            destFolder.mkdirs()

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
                Log.d(TAG, "Copied ${files.size} files from assets/$assetFolderName")
            } catch (e: Exception) {
                Log.e(TAG, "Error copying asset folder: ${e.message}", e)
                throw e
            }
        }

        return destFolder.absolutePath
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::boxStore.isInitialized) {
            boxStore.close()
        }
    }

    companion object {
        private const val TAG = "ClipExample"
    }
}