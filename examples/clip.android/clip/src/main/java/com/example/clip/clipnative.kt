package com.example.clip
import android.util.Log

class ClipExtractor {

    companion object {
        private const val TAG = "ClipExtractor"

        // Load the native library
        init {
            try {
                System.loadLibrary("clip-android")
                Log.d(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library", e)
                throw e
            }
        }
    }

    /**
     * Extract CLIP embeddings for images and/or text
     *
     * @param modelPath Absolute path to the CLIP model file (.gguf)
     * @param imagePaths Array of image file paths or directory paths
     * @param texts Array of text strings to encode
     * @param nThreads Number of threads to use for processing (default: 4)
     * @param verbose Verbosity level (0 = quiet, 1+ = more verbose, default: 0)
     * @return ClipResult containing embeddings, statistics, and status
     */
    external fun extractVectors(
        modelPath: String,
        imagePaths: Array<String>,
        texts: Array<String>,
        nThreads: Int = 4,
        verbose: Int = 0
    ): ClipResult

//    /**
//     * Extract embeddings from images only
//     */
//    fun extractImageVectors(
//        modelPath: String,
//        imagePaths: Array<String>,
//        nThreads: Int = 4,
//        verbose: Int = 0
//    ): ClipResult {
//        return extractVectors(modelPath, imagePaths, emptyArray(), nThreads, verbose)
//    }
//
//    /**
//     * Extract embeddings from text only
//     */
//    fun extractTextVectors(
//        modelPath: String,
//        texts: Array<String>,
//        nThreads: Int = 4,
//        verbose: Int = 0
//    ): ClipResult {
//        return extractVectors(modelPath, emptyArray(), texts, nThreads, verbose)
//    }
}