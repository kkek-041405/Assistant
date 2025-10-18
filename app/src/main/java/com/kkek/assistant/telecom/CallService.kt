
package com.kkek.assistant.telecom

import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import androidx.annotation.RequiresApi
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
        callCallback = object : Call.Callback() {
            override fun onDetailsChanged(call: Call, details: Call.Details) {
                // This is a good place to update the UI with any call detail changes
            }
        }
        call.registerCallback(callCallback)
    }

    override fun onCallRemoved(call: Call) {
        _call.value = null
        call.unregisterCallback(callCallback)
        callCallback = null
        // Reset states
        _speakerOn.value = false
        _muted.value = false
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        _speakerOn.value = audioState?.route == CallAudioState.ROUTE_SPEAKER
        _muted.value = audioState?.isMuted ?: false
    }
}
