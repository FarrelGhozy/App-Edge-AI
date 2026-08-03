package com.facegate.core.face

import android.graphics.Bitmap
import kotlin.math.sqrt

/**
 * Provider interface for face embedding extraction.
 * Implementations: OnnxFaceEmbedder (ONNX) primary, FaceEmbedder (TFLite) fallback.
 */
interface FaceEmbedderProvider {
    fun init(): Boolean
    fun embed(faceCrop: Bitmap): FloatArray
    val embeddingDim: Int
    val inputSize: Int
    fun release()
    fun isReady(): Boolean

    /**
     * Average multiple embeddings into one robust template vector.
     * Used by enrollment: embed top-K frames per pose, average → centroid,
     * L2-normalize. This is a shared default so every implementation benefits
     * (issue #66 — enrollment must not depend on a single noisy frame).
     */
    fun averageEmbeddings(embeddings: Array<FloatArray>): FloatArray {
        if (embeddings.isEmpty()) return FloatArray(embeddingDim)
        val result = FloatArray(embeddingDim)
        for (emb in embeddings) {
            for (i in result.indices) result[i] += emb[i]
        }
        for (i in result.indices) result[i] /= embeddings.size
        return l2Normalize(result)
    }

    /** In-place L2 normalization. Returns the same array for chaining. */
    private fun l2Normalize(v: FloatArray): FloatArray {
        var norm = 0.0
        for (x in v) norm += x * x
        norm = sqrt(norm)
        if (norm > 1e-8) {
            for (i in v.indices) v[i] = (v[i] / norm).toFloat()
        }
        return v
    }
}
