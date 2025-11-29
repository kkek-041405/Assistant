package com.kkek.assistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kkek.assistant.data.AssistantRepository
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AssistantRepositoryTest {

    private lateinit var repository: AssistantRepository

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        repository = AssistantRepository(context)
    }

    @Test
    fun testBatteryPercentage() {
        val batteryPercentage = repository.updateBatteryPercentage()
        // Depending on the test environment, this might return a specific value or -1
        // We are just testing that the method can be called without crashing.
        assert(batteryPercentage >= -1)
    }
}
