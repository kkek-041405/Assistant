package com.kkek.assistant.System.touch

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TouchService : AccessibilityService() {

    @Inject
    lateinit var accessibilityHelper: AccessibilityHelper

    override fun onServiceConnected() {
        super.onServiceConnected()
        accessibilityHelper.setService(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                accessibilityHelper.inspectCurrentScreen()
            }
        }
    }

    override fun onInterrupt() {
        // Not needed for gesture dispatch
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        accessibilityHelper.setService(null)
        return super.onUnbind(intent)
    }
}
