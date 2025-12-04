package com.kkek.assistant.System

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.TelephonyManager
import com.kkek.assistant.data.AssistantRepository
import com.kkek.assistant.data.model.CallState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CallStateService : Service() {

    @Inject
    lateinit var repository: AssistantRepository

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

            val callState = when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> CallState(CallState.State.RINGING, phoneNumber)
                TelephonyManager.EXTRA_STATE_OFFHOOK -> CallState(CallState.State.OFFHOOK, phoneNumber)
                else -> CallState(CallState.State.IDLE)
            }
            repository.updateCallState(callState)
        }
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
