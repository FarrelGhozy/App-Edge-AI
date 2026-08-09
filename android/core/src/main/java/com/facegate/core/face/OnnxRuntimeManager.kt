package com.facegate.core.face

import android.content.Context
import android.util.Log
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession

/**
 * Manages ONNX Runtime environment and session lifecycle.
 * Singleton: one OrtEnvironment shared across all ONNX models (detection + recognition).
 */
class OnnxRuntimeManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "OnnxRuntime"
        private const val DET_MODEL = "det_500m.onnx"
        private const val EMB_MODEL = "w600k_mbf.onnx"

        @Volatile
        private var instance: OnnxRuntimeManager? = null

        fun getInstance(context: Context): OnnxRuntimeManager {
            return instance ?: synchronized(this) {
                instance ?: OnnxRuntimeManager(context.applicationContext).also { instance = it }
            }
        }
    }

    enum class RuntimeState {
        ONNX_READY,
        FALLBACK_TFLITE,
        FALLBACK_MLKIT,
        ERROR
    }

    var state: RuntimeState = RuntimeState.ERROR
        private set

    private var ortEnv: OrtEnvironment? = null
    private var detSession: OrtSession? = null
    private var embSession: OrtSession? = null
    private var initError: String? = null

    val detectorSession: OrtSession?
        get() = detSession

    val embedderSession: OrtSession?
        get() = embSession

    val environment: OrtEnvironment?
        get() = ortEnv

    fun init(): Boolean {
        return try {
            ortEnv = OrtEnvironment.getEnvironment()

            val sessionOpts = OrtSession.SessionOptions()
            // ONNX Runtime Android 1.20: addCPU(useArena) — CPU is the default fallback provider.
            sessionOpts.addCPU(true)

            // Load detection model
            try {
                val detBytes = context.assets.open(DET_MODEL).use { it.readBytes() }
                detSession = ortEnv!!.createSession(detBytes, sessionOpts)
                Log.d(TAG, "Detection model loaded: $DET_MODEL")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load $DET_MODEL: ${e.message}")
                throw e
            }

            // Load embedding model
            try {
                val embBytes = context.assets.open(EMB_MODEL).use { it.readBytes() }
                embSession = ortEnv!!.createSession(embBytes, sessionOpts)
                Log.d(TAG, "Embedding model loaded: $EMB_MODEL")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load $EMB_MODEL: ${e.message}")
                throw e
            }

            state = RuntimeState.ONNX_READY
            initError = null
            Log.d(TAG, "ONNX Runtime ready: ${state.name}")
            true
        } catch (e: UnsatisfiedLinkError) {
            state = RuntimeState.FALLBACK_TFLITE
            initError = "ONNX native library not found: ${e.message}"
            Log.w(TAG, initError!!)
            false
        } catch (e: Exception) {
            state = RuntimeState.FALLBACK_TFLITE
            initError = "ONNX init failed: ${e.message}"
            Log.w(TAG, initError!!, e)
            false
        }
    }

    fun getInitError(): String? = initError

    fun release() {
        try {
            detSession?.close()
            embSession?.close()
            ortEnv?.close()
        } catch (_: Exception) {}
        detSession = null
        embSession = null
        ortEnv = null
        state = RuntimeState.ERROR
        instance = null
    }
}
