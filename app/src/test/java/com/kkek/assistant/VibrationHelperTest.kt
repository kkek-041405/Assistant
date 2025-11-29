package com.kkek.assistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kkek.assistant.System.VibrationHelper
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VibrationHelperTest {

    private lateinit var vibrationHelper: VibrationHelper

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        vibrationHelper = VibrationHelper(context)
    }

    @Test
    fun testVibrate() {
        // This test just ensures that the vibrate method can be called without crashing.
        vibrationHelper.vibrate(100)
    }
}
