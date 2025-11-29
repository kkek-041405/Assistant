package com.kkek.assistant.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.kkek.assistant.System.notification.AppNotification
import com.kkek.assistant.repository.FirebaseRepository
import com.kkek.assistant.data.CallDetails
import com.kkek.assistant.data.Contact
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssistantRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val TAG = "AssistantRepository"
    private val firebaseRepository = FirebaseRepository

    private val _batteryPercent = MutableStateFlow(-1)
    val batteryPercent = _batteryPercent.asStateFlow()

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications = _notifications.asStateFlow()

    fun handleNewNotification(notification: AppNotification) {
        _notifications.value = _notifications.value + notification
        firebaseRepository.uploadNotification(notification)
    }

    fun updateBatteryPercentage(): Int {
        try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            if (batteryManager != null) {
                val capacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                if (capacity in 0..100) {
                    _batteryPercent.value = capacity
                    return capacity
                }
            }

            // If BatteryManager failed, try the old IntentFilter method as a fallback
            val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, iFilter)
            batteryStatus?.let { intent ->
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    val percentage = (level * 100) / scale
                    _batteryPercent.value = percentage
                    return percentage
                }
            }

            // If both methods fail
            Log.w(TAG, "Failed to read battery percentage using all available methods.")
            _batteryPercent.value = -1
            return -1

        } catch (e: Exception) {
            Log.e(TAG, "An exception occurred while reading battery percentage", e)
            _batteryPercent.value = -1
            return -1
        }
    }

    fun uploadCallDetails(callDetails: CallDetails) {
        firebaseRepository.uploadCallDetails(callDetails)
    }

    suspend fun getContacts(): List<Contact> {
        return firebaseRepository.getContacts()
    }
}
