package com.kkek.assistant.System

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TTSHelper @Inject constructor(@ApplicationContext context: Context) : TextToSpeech.OnInitListener {
    private val TAG = "TTSHelper"
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = ArrayList<String>()

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
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
                if (pending.isNotEmpty()) {
                    for (p in pending) {
                        tts?.speak(p, TextToSpeech.QUEUE_ADD, null, "tts_pending")
                    }
                    pending.clear()
                }
            }
        } else {
            Log.e(TAG, "TTS initialization failed")
        }
    }

    fun speak(text: String) {
        if (!ready) {
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
