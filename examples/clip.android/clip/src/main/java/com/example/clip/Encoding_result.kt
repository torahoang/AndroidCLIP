package com.example.clip

data class ClipResult(
    var success: Boolean = false,
    var errorMessage: String = "",
    var processedImages: Int = 0,
    var processedTexts: Int = 0,

    // Timing statistics
    var folderScanTime: Double = 0.0,
    var totalImageLoadTime: Double = 0.0,
    var totalImageEncodeTime: Double = 0.0,
    var totalTextEncodeTime: Double = 0.0,
    var totalProcessingTime: Double = 0.0,

    // Embeddings (2D arrays where each row is an embedding vector)
    var imageEmbeddings: Array<FloatArray> = emptyArray(),
    var textEmbeddings: Array<FloatArray> = emptyArray(),

    // Corresponding paths and texts
    var imagePathsProcessed: Array<String> = emptyArray(),
    var textsProcessed: Array<String> = emptyArray()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ClipResult

        if (success != other.success) return false
        if (processedImages != other.processedImages) return false
        if (processedTexts != other.processedTexts) return false
        if (folderScanTime != other.folderScanTime) return false
        if (totalImageLoadTime != other.totalImageLoadTime) return false
        if (totalImageEncodeTime != other.totalImageEncodeTime) return false
        if (totalTextEncodeTime != other.totalTextEncodeTime) return false
        if (totalProcessingTime != other.totalProcessingTime) return false
        if (errorMessage != other.errorMessage) return false
        if (!imageEmbeddings.contentDeepEquals(other.imageEmbeddings)) return false
        if (!textEmbeddings.contentDeepEquals(other.textEmbeddings)) return false
        if (!imagePathsProcessed.contentEquals(other.imagePathsProcessed)) return false
        if (!textsProcessed.contentEquals(other.textsProcessed)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = success.hashCode()
        result = 31 * result + processedImages
        result = 31 * result + processedTexts
        result = 31 * result + folderScanTime.hashCode()
        result = 31 * result + totalImageLoadTime.hashCode()
        result = 31 * result + totalImageEncodeTime.hashCode()
        result = 31 * result + totalTextEncodeTime.hashCode()
        result = 31 * result + totalProcessingTime.hashCode()
        result = 31 * result + errorMessage.hashCode()
        result = 31 * result + imageEmbeddings.contentDeepHashCode()
        result = 31 * result + textEmbeddings.contentDeepHashCode()
        result = 31 * result + imagePathsProcessed.contentHashCode()
        result = 31 * result + textsProcessed.contentHashCode()
        return result
    }
}
