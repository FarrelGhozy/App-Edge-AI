package com.facegate.core.face

import android.graphics.Bitmap

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
}
