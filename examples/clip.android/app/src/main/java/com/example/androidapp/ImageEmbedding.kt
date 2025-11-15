package com.example.androidapp
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.HnswIndex
@Entity
data class ImageEmbedding(
    @Id var id: Long = 0,
    var imagePath: String = "",
    var imageName: String = "",
    @HnswIndex(dimensions = 512)  // Adjust dimensions based on your CLIP model
    var embedding: FloatArray = floatArrayOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImageEmbedding

        if (id != other.id) return false
        if (imagePath != other.imagePath) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + imagePath.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}