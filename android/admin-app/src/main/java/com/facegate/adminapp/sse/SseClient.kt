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
                    val body = response.body ?: continue
                    val source = body.source()

                    var eventType = ""
                    val eventData = StringBuilder()

                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        when {
                            line.startsWith("event:") -> eventType = line.removePrefix("event:").trim()
                            line.startsWith("data:") -> eventData.append(line.removePrefix("data:").trim())
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
                } catch (_: kotlinx.coroutines.CancellationException) {
                    break
                } catch (_: Exception) {
                    delay(5000)
                }
            }
        }
    }

    fun disconnect() {
        job?.cancel()
        job = null
    }
}
