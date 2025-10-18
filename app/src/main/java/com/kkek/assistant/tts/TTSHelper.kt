package com.kkek.assistant.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * TTSHelper in tts subpackage: wraps Android TextToSpeech and exposes a simple speak() and shutdown() API.
 */
class TTSHelper(private val context: Context) : TextToSpeech.OnInitListener {
    private val TAG = "TTSHelper"
    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            if (!ready) {
                Log.e(TAG, "TTS language not supported")
            }
        } else {
            Log.e(TAG, "TTS initialization failed")
        }
    }

    fun speak(text: String) {
        if (!ready) {
            Log.w(TAG, "TTS not ready, skipping speak")
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts1")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}

