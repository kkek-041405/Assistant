package com.kkek.assistant.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.kkek.assistant.data.AssistantRepository
import com.kkek.assistant.data.model.ScreenCapture
import com.kkek.assistant.tools.TtsTool
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AssistantAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var repository: AssistantRepository
    @Inject
    lateinit var ttsTool: TtsTool

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // State for the latest OTP
    private val _latestOtp = MutableStateFlow<String?>(null)
    val latestOtp = _latestOtp.asStateFlow()

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "onServiceConnected: Accessibility Service is connected and running.")

        // --- OTP State Handling ---
        serviceScope.launch {
            repository.latestOtp.collect { otp ->
                _latestOtp.value = otp
            }
        }

        // --- Smart Screen Capture Trigger ---
        // Listens for a request from the repository (triggered by the bubble)
        serviceScope.launch {
            repository.captureRequest.collect { requested ->
                Log.d(TAG, "Capture request collector fired. Value: $requested")
                if (requested) {
                    captureAndUploadScreenContent()
                    repository.onCaptureRequestCompleted() // Reset the trigger
                }
            }
        }
    }

    /**
     * Captures screen content when triggered.
     */
    private fun captureAndUploadScreenContent() {
        Log.d(TAG, "captureAndUploadScreenContent: Attempting to capture screen.")
        val rootNode = rootInActiveWindow ?: run {
            Log.e(TAG, "captureAndUploadScreenContent: rootInActiveWindow is null. Cannot capture screen.")
            serviceScope.launch { ttsTool.execute(this@AssistantAccessibilityService, mapOf("text" to "Could not access screen content."), serviceScope) }
            return
        }

        val sourceApp = rootNode.packageName?.toString() ?: "unknown"
        val contentBuilder = StringBuilder()

        extractTextFromNodeRecursive(rootNode, contentBuilder)

        // Important: Recycle the root node that we explicitly acquired.
        rootNode.recycle()

        val content = contentBuilder.toString().trim()

        if (content.isNotBlank()) {
            val screenCapture = ScreenCapture(
                sourceApp = sourceApp,
                content = content
            )
            Log.d(TAG, "captureAndUploadScreenContent: Content captured. App: $sourceApp, Size: ${content.length}")
            serviceScope.launch {
                repository.uploadScreenContent(screenCapture)
                ttsTool.execute(this@AssistantAccessibilityService, mapOf("text" to "Screen content saved."), serviceScope)
            }
        } else {
            Log.w(TAG, "captureAndUploadScreenContent: No text content found on screen.")
             serviceScope.launch { ttsTool.execute(this@AssistantAccessibilityService, mapOf("text" to "No text found on screen."), serviceScope) }
        }
    }

    /**
     * Recursively traverses the node tree to extract visible text.
     */
    private fun extractTextFromNodeRecursive(node: AccessibilityNodeInfo?, builder: StringBuilder) {
        if (node == null) return

        if (node.isVisibleToUser && !node.text.isNullOrBlank()) {
            builder.append(node.text).appendLine()
        }

        for (i in 0 until node.childCount) {
            val childNode = node.getChild(i)
            extractTextFromNodeRecursive(childNode, builder)
            // It's crucial not to recycle child nodes obtained during traversal here, 
            // as it can lead to unpredictable crashes.
        }
    }

    // --- OTP Public Functions ---
    fun speakLatestOtp() {
        val message = latestOtp.value?.let { "Your latest OTP is $it" } ?: "OTP not received"
        serviceScope.launch { ttsTool.execute(this@AssistantAccessibilityService, mapOf("text" to message), serviceScope) }
    }

    fun resetLatestOtp() {
        repository.resetLatestOtp()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Not used for this feature, but required to be implemented.
    }

    override fun onInterrupt() {
        Log.e(TAG, "onInterrupt: Accessibility Service was interrupted.")
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: Accessibility Service is being destroyed.")
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "AssistantAccessibility"
    }
}
