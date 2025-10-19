package com.kkek.assistant.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * TTSHelper in tts subpackage: wraps Android TextToSpeech and exposes a simple speak() and shutdown() API.
 */
class TTSHelper(private val appContext: Context) : TextToSpeech.OnInitListener {
    private val TAG = "TTSHelper"
    private var tts: TextToSpeech? = null
    private var ready = false
    // queue texts requested before TTS becomes ready
    private val pending = ArrayList<String>()

    init {
        // initialize with application context; this should survive Activity config changes
        tts = TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Try device default locale first, then fall back to US English
            val defaultLocale = Locale.getDefault()
            var result = tts?.setLanguage(defaultLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Default locale $defaultLocale not supported for TTS, trying US English")
                result = tts?.setLanguage(Locale.US)
            }
            ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            if (!ready) {
                Log.e(TAG, "TTS language not supported (default and fallback)")
            } else {
                Log.d(TAG, "TTS ready with locale: ${tts?.language}")
            }

            // speak any queued requests
            if (ready && pending.isNotEmpty()) {
                for (p in pending) {
                    tts?.speak(p, TextToSpeech.QUEUE_ADD, null, "tts_pending")
                }
                pending.clear()
            }
        } else {
            Log.e(TAG, "TTS initialization failed")
        }
    }

    fun speak(text: String) {
        // If the internal TTS has been cleared for some reason, recreate it and queue
        if (tts == null) {
            Log.w(TAG, "TTS instance null, re-initializing and queuing speak request")
            tts = TextToSpeech(appContext, this)
            pending.add(text)
            return
        }

        if (!ready) {
            // Queue early speak requests so they are not lost
            Log.w(TAG, "TTS not ready yet, queuing speak request")
            pending.add(text)
            return
        }

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts1")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        pending.clear()
    }
}
