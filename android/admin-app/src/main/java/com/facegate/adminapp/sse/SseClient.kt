package com.facegate.adminapp.sse

import com.facegate.core.di.ApiBaseUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

data class SseEvent(
    val type: String,
    val data: String
)

@Singleton
class SseClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @ApiBaseUrl private val baseUrl: String
) {
    private val _events = MutableSharedFlow<SseEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<SseEvent> = _events.asSharedFlow()

    private var job: Job? = null

    // #96: backoff eksponensial 1s → 30s; reset setelah stream bertahan lama.
    private var retryDelayMs = 1_000L
    private var lastStableConnectAt = 0L

    fun connect(scope: CoroutineScope) {
        disconnect()
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val request = Request.Builder()
                        .url("${baseUrl.trimEnd('/')}/api/events/stream")
                        .header("Accept", "text/event-stream")
                        .build()

                    val response = okHttpClient.newCall(request).execute()
                    // #96: 401 = token kedaluwarsa → retry sia-sia & boros baterai.
                    if (response.code == 401) {
                        response.close()
                        break
                    }
                    if (!response.isSuccessful) {
                        response.close()
                        delay(retryDelayMs)
                        retryDelayMs = (retryDelayMs * 2).coerceAtMost(30_000L)
                        continue
                    }
                    val body = response.body ?: continue
                    val source = body.source()

                    var eventType = ""
                    val eventData = StringBuilder()

                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        when {
                            line.startsWith("event:") -> eventType = line.removePrefix("event:").trim()
                            line.startsWith("data:") -> {
                                // #96: data multi-baris digabung dengan newline (SSE spec)
                                if (eventData.isNotEmpty()) eventData.append('\n')
                                eventData.append(line.removePrefix("data:").trim())
                            }
                            line.isEmpty() -> {
                                if (eventData.isNotEmpty()) {
                                    _events.tryEmit(SseEvent(eventType.ifEmpty { "message" }, eventData.toString()))
                                }
                                eventType = ""
                                eventData.clear()
                            }
                        }
                    }
                    source.close()

                    // Koneksi stabil ≥30 detik → reset backoff.
                    val now = System.currentTimeMillis()
                    if (lastStableConnectAt != 0L && now - lastStableConnectAt >= 30_000L) {
                        retryDelayMs = 1_000L
                    }
                    lastStableConnectAt = now
                } catch (_: kotlinx.coroutines.CancellationException) {
                    break
                } catch (_: Exception) {
                    // #96: exponential backoff, cap 30s.
                    delay(retryDelayMs)
                    retryDelayMs = (retryDelayMs * 2).coerceAtMost(30_000L)
                }
            }
        }
    }

    fun disconnect() {
        job?.cancel()
        job = null
    }
}
