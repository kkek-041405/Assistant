package com.kkek.assistant.System

import android.annotation.SuppressLint
import android.content.Context
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
    fun onNextDoublePress()
    fun onPreviousDoublePress()
}

object VolumeKeyListener {

    private var listener: VolumeCommandListener? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isVolumeUpLongPress = false
    private var isVolumeDownLongPress = false
    // When a long-press action executes it may arrive before the physical key-up event.
    // Record which key's up event should be ignored so we don't trigger a short-press
    // action for the same physical press.
    private var ignoredUpKey: Int? = null
    private const val LONG_PRESS_DURATION = 500L // ms
    @SuppressLint("StaticFieldLeak")
    private var vibrationHelper: VibrationHelper? = null

    // Double-press detection
    private const val DOUBLE_PRESS_TIMEOUT = 300L // ms
    private var lastVolumeUpPending = false
    private var lastVolumeDownPending = false

    private val volumeUpLongPressRunnable = Runnable {
        isVolumeUpLongPress = true
        // Mark that the next VOLUME_UP key-up should be ignored (consumed) because this was a long-press
        ignoredUpKey = KeyEvent.KEYCODE_VOLUME_UP
        vibrationHelper?.vibrateLongPress()
        listener?.onPreviousLongPress()
    }

    private val volumeDownLongPressRunnable = Runnable {
        isVolumeDownLongPress = true
        // Mark that the next VOLUME_DOWN key-up should be ignored (consumed) because this was a long-press
        ignoredUpKey = KeyEvent.KEYCODE_VOLUME_DOWN
        vibrationHelper?.vibrateLongPress()
        listener?.onNextLongPress()
    }

    // Single-press runnables scheduled to allow double-press detection
    private val volumeUpSingleRunnable = Runnable {
        // Execute single short-press action for VOLUME_UP
        listener?.onPrevious()
        vibrationHelper?.vibrateNavigation()
        lastVolumeUpPending = false
    }

    private val volumeDownSingleRunnable = Runnable {
        listener?.onNext()
        vibrationHelper?.vibrateNavigation()

        lastVolumeDownPending = false
    }

    fun init(context: Context) {
        // Use applicationContext to avoid leaking an Activity context from the static object
        vibrationHelper = VibrationHelper(context.applicationContext)
    }

    fun setListener(listener: VolumeCommandListener?) {
        this.listener = listener
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
                // If a long-press was processed for this key, consume the following key-up and do nothing
                if (ignoredUpKey == KeyEvent.KEYCODE_VOLUME_UP) {
                    ignoredUpKey = null
                    isVolumeUpLongPress = false
                    return true
                }
                if (!isVolumeUpLongPress) {
                    if (lastVolumeUpPending) {
                        // Second press within timeout -> double-press
                        handler.removeCallbacks(volumeUpSingleRunnable)
                        vibrationHelper?.vibrateDoublePress()
                        listener?.onPreviousDoublePress()
                        lastVolumeUpPending = false
                    } else {
                        // First short press: schedule single action and wait for potential second press
                        lastVolumeUpPending = true
                        handler.postDelayed(volumeUpSingleRunnable, DOUBLE_PRESS_TIMEOUT)
                    }
                }
                isVolumeUpLongPress = false // Reset for next press
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                handler.removeCallbacks(volumeDownLongPressRunnable)
                // If a long-press was processed for this key, consume the following key-up and do nothing
                if (ignoredUpKey == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    ignoredUpKey = null
                    isVolumeDownLongPress = false
                    return true
                }
                if (!isVolumeDownLongPress) {
                    if (lastVolumeDownPending) {
                        // Second press within timeout -> double-press
                        handler.removeCallbacks(volumeDownSingleRunnable)
                        vibrationHelper?.vibrateDoublePress()
                        listener?.onNextDoublePress()
                        lastVolumeDownPending = false
                    } else {
                        // First short press: schedule single action and wait for potential second press
                        lastVolumeDownPending = true
                        handler.postDelayed(volumeDownSingleRunnable, DOUBLE_PRESS_TIMEOUT)
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
        lastVolumeUpPending = false
        lastVolumeDownPending = false
        // Do NOT clear ignoredUpKey here — if a long-press action already fired we want to
        // consume the upcoming key-up event. reset() is often called by UI code immediately
        // after opening sublists, so keep the consume behavior intact.
    }
}
