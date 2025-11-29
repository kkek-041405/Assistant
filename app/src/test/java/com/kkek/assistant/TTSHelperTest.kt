package com.kkek.assistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kkek.assistant.System.TTSHelper
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TTSHelperTest {

    private lateinit var ttsHelper: TTSHelper

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        ttsHelper = TTSHelper(context)
    }

    @Test
    fun testSpeak() {
        // This test just ensures that the speak method can be called without crashing.
        ttsHelper.speak("Hello, world!")
    }
}
