package com.facegate.core.face

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorResult

class FaceDetectorWrapper(private val context: Context) {
    private var detector: FaceDetector? = null

    fun init() {
        val optionsBuilder = FaceDetector.FaceDetectorOptions.builder()
            .setRunningMode(RunningMode.IMAGE)
            .setMinDetectionConfidence(0.5f)

        detector = FaceDetector.createFromOptions(context, optionsBuilder.build())
    }

    fun detect(bitmap: Bitmap): FaceDetectorResult? {
        val mpImage = BitmapImageBuilder(bitmap).build()
        val result = detector?.detect(mpImage)
        return result
    }

    fun release() {
        detector?.close()
        detector = null
    }
}
