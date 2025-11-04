
package com.kkek.assistant.telecom

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.kkek.assistant.MainActivity
import com.kkek.assistant.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@RequiresApi(Build.VERSION_CODES.M)
class CallService : InCallService() {

    private var callCallback: Call.Callback? = null

    companion object {
        private var instance: CallService? = null

        private val _call = MutableStateFlow<Call?>(null)
        val call = _call.asStateFlow()

        private val _speakerOn = MutableStateFlow(false)
        val speakerOn = _speakerOn.asStateFlow()

        private val _muted = MutableStateFlow(false)
        val muted = _muted.asStateFlow()

        fun toggleSpeaker() {
            instance?.let {
                if (_speakerOn.value) {
                    it.setAudioRoute(CallAudioState.ROUTE_EARPIECE)
                } else {
                    it.setAudioRoute(CallAudioState.ROUTE_SPEAKER)
                }
            }
        }

        fun toggleMute() {
            instance?.setMuted(!_muted.value)
        }

        const val INCOMING_CALL_CHANNEL_ID = "INCOMING_CALL"
        const val INCOMING_CALL_NOTIFICATION_ID = 110
    }

    override fun onBind(intent: android.content.Intent?): android.os.IBinder? {
        instance = this
        return super.onBind(intent)
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onCallAdded(call: Call) {
        _call.value = call
        if (call.state == Call.STATE_RINGING) {
            showIncomingCallNotification(call)
        }

        callCallback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                if (state == Call.STATE_RINGING) {
                    showIncomingCallNotification(call)
                }
            }
            override fun onDetailsChanged(call: Call, details: Call.Details) {
                // This is a good place to update the UI with any call detail changes
            }
        }
        call.registerCallback(callCallback)
    }

    private fun showIncomingCallNotification(call: Call) {
        createNotificationChannel()

        val contentIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        val callerName = call.details.handle?.schemeSpecificPart ?: "Unknown Caller"

        val notificationBuilder = NotificationCompat.Builder(this, INCOMING_CALL_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Incoming Call")
            .setContentText(callerName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(INCOMING_CALL_NOTIFICATION_ID, notificationBuilder.build())
    }


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Incoming Calls"
            val descriptionText = "Channel for incoming call notifications"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(INCOMING_CALL_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }


    override fun onCallRemoved(call: Call) {
        _call.value = null
        call.unregisterCallback(callCallback)
        callCallback = null

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(INCOMING_CALL_NOTIFICATION_ID)

        // Reset states
        _speakerOn.value = false
        _muted.value = false
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        _speakerOn.value = audioState?.route == CallAudioState.ROUTE_SPEAKER
        _muted.value = audioState?.isMuted ?: false
    }
}
