package com.kkek.assistant.System

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CallStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, CallStateService::class.java).apply {
            action = intent.action
            // Pass along the extras from the original intent
            intent.extras?.let { putExtras(it) }
        }
        context.startService(serviceIntent)
    }
}
