package com.facegate.core.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF

class FaceDetectorWrapper(private val context: Context) {
    private var isInitialized = false

    fun init() {
        isInitialized = true
    }

    fun detect(bitmap: Bitmap): FaceDetectionResult? {
        if (!isInitialized) return null
        return FaceDetectionResult(bitmap.width.toFloat(), bitmap.height.toFloat())
    }

    fun release() {
        isInitialized = false
    }
}

data class FaceDetectionResult(
    val imageWidth: Float,
    val imageHeight: Float
)
