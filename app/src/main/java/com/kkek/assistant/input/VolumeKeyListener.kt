
package com.kkek.assistant.input

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent

/**
 * Volume input handling utilities.
 * Responsibility: listen to hardware volume key events and translate them into
 * high-level commands (next/previous and long-press variants).
 * This keeps input concerns separate from UI and call logic (SRP).
 */
interface VolumeCommandListener {
    fun onNext()
    fun onPrevious()
    fun onNextLongPress()
    fun onPreviousLongPress()
    fun onAnswer()
    fun onReject()
    fun onHangup()
    fun onEndCallLongPress()
    fun onToggleSpeakerLongPress()
}

object VolumeKeyListener {

    private var listener: VolumeCommandListener? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isVolumeUpLongPress = false
    private var isVolumeDownLongPress = false
    private const val LONG_PRESS_DURATION = 500L // ms
    private var callState: CallState = CallState.IDLE

    private val volumeUpLongPressRunnable = Runnable {
        isVolumeUpLongPress = true
        if (callState == CallState.ACTIVE) {
            listener?.onToggleSpeakerLongPress()
        } else {
            listener?.onPreviousLongPress()
        }
    }

    private val volumeDownLongPressRunnable = Runnable {
        isVolumeDownLongPress = true
        if (callState == CallState.ACTIVE) {
            listener?.onEndCallLongPress()
        } else {
            listener?.onNextLongPress()
        }
    }

    fun setListener(listener: VolumeCommandListener?) {
        this.listener = listener
    }

    fun setCallState(state: CallState) {
        callState = state
        // When the call state changes, reset any pending long-press actions
        // to avoid unintended navigation.
        if (state != CallState.IDLE) {
            reset()
        }
    }

    fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null || listener == null) {
            return false
        }

        if (event.repeatCount > 0) {
            // Ignore repeat events, long press is handled by the handler
            return true
        }

        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                isVolumeUpLongPress = false
                handler.postDelayed(volumeUpLongPressRunnable, LONG_PRESS_DURATION)
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                isVolumeDownLongPress = false
                handler.postDelayed(volumeDownLongPressRunnable, LONG_PRESS_DURATION)
                return true
            }
        }
        return false
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null || listener == null) {
            return false
        }

        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                handler.removeCallbacks(volumeUpLongPressRunnable)
                if (!isVolumeUpLongPress) {
                    when (callState) {
                        CallState.IDLE -> listener?.onPrevious()
                        CallState.INCOMING -> listener?.onReject()
                        CallState.ACTIVE -> { /* Do nothing for short press */ }
                    }
                }
                isVolumeUpLongPress = false // Reset for next press
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                handler.removeCallbacks(volumeDownLongPressRunnable)
                if (!isVolumeDownLongPress) {
                    when (callState) {
                        CallState.IDLE -> listener?.onNext()
                        CallState.INCOMING -> listener?.onAnswer()
                        CallState.ACTIVE -> { /* Do nothing */ }
                    }
                }
                isVolumeDownLongPress = false // Reset for next press
                return true
            }
        }
        return false
    }

    fun reset() {
        handler.removeCallbacksAndMessages(null)
        isVolumeUpLongPress = false
        isVolumeDownLongPress = false
    }
}
