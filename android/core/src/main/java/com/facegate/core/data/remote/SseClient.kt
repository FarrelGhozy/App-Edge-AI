package com.facegate.core.data.remote

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Server-Sent Events (SSE) client untuk realtime update.
 * Connect: GET /api/events/stream  →  listen for "change", "ping", "metrics" events.
 */
class SseClient(
    private val baseUrl: String,
    private val authInterceptor: AuthInterceptor
) {
    private val _events = MutableSharedFlow<SseEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<SseEvent> = _events.asSharedFlow()
    private var job: Job? = null
    private var currentCall: Call? = null

    companion object {
        private const val TAG = "SseClient"
        private const val INITIAL_RETRY_MS = 2000L
        private const val MAX_RETRY_MS = 30_000L
        private const val BACKOFF_MULTIPLIER = 3
    }

    /** Mulai listen SSE events. */
    fun start(scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            var retryDelay = INITIAL_RETRY_MS
            while (isActive) {
                try {
                    connect()
                    retryDelay = INITIAL_RETRY_MS
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "SSE disconnected: ${e.message}. Reconnecting in ${retryDelay}ms")
                    delay(retryDelay)
                    retryDelay = (retryDelay * BACKOFF_MULTIPLIER).coerceAtMost(MAX_RETRY_MS)
                }
            }
        }
    }

    /** Stop listening. */
    fun stop() {
        currentCall?.cancel()
        job?.cancel()
        job = null
    }

    private suspend fun connect() {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor) // Reuse the existing AuthInterceptor
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Accept", "text/event-stream")
                    .addHeader("Cache-Control", "no-cache")
                    .build()
                chain.proceed(request)
            }
            .build()

        val request = Request.Builder()
            .url("$baseUrl/api/events/stream")
            .get()
            .build()

        currentCall = client.newCall(request)
        val response = currentCall!!.execute()
        val body = response.body ?: throw IllegalStateException("Empty response body")

        val reader = BufferedReader(InputStreamReader(body.byteStream()))
        var eventType = ""
        val data = StringBuilder()

        reader.useLines { lines ->
            lines.forEach { line ->
                when {
                    line.startsWith("event:") -> eventType = line.removePrefix("event:").trim()
                    line.startsWith("data:") -> {
                        data.append(line.removePrefix("data:").trim())
                    }
                    line.isBlank() && data.isNotEmpty() -> {
                        val eventData = data.toString()
                        Log.d(TAG, "SSE event: $eventType | ${eventData.take(100)}")
                        _events.tryEmit(SseEvent(eventType, eventData))
                        eventType = ""
                        data.clear()
                    }
                }
            }
        }
    }
}

data class SseEvent(
    val type: String,
    val data: String
) {
    val isChangeEvent: Boolean get() = type == "change"
    val isPing: Boolean get() = type == "ping"
    val isMetrics: Boolean get() = type == "metrics"
}
