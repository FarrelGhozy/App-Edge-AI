package com.facegate.kioskscanner.scanner

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceFeedback @Inject constructor(
    private val context: Context
) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    fun init() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("id", "ID")
                isInitialized = true
            }
        }
    }

    fun speak(text: String) {
        if (!isInitialized) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    fun speakSuccess(name: String, action: String) {
        val message = when (action) {
            "keluar" -> "Silakan keluar, $name"
            "kembali" -> "Selamat datang kembali, $name"
            else -> "$name"
        }
        speak(message)
    }

    fun speakError() {
        speak("Wajah tidak dikenal, silakan hubungi admin")
    }

    fun speakWarning(message: String) {
        speak("Perhatian! $message")
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
