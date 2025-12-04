package com.kkek.assistant.tools

import android.content.Context
import com.kkek.assistant.data.AssistantRepository
import com.kkek.assistant.domain.model.AiTool
import com.kkek.assistant.domain.model.ToolParameter
import com.kkek.assistant.domain.model.ToolResult
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

class NotificationTool @Inject constructor(private val repository: AssistantRepository) : AiTool {
    override val name = "notification"
    override val description = "Reads and interacts with notifications."

    override val parameters = listOf(
        ToolParameter("action", "String", "The action to perform (e.g., 'summarize', 'read_latest_otp').", isRequired = true)
    )

    override suspend fun execute(context: Context, params: Map<String, Any>, scope: CoroutineScope): ToolResult {
        return when (val action = params["action"] as? String) {
            "summarize" -> {
                val latestNotification = repository.notifications.value.firstOrNull()
                val summary = if (latestNotification != null) {
                    "${latestNotification.appName}: ${latestNotification.title ?: ""}, ${latestNotification.text ?: ""}"
                } else {
                    "You have no new notifications."
                }
                ToolResult.Success(mapOf("speechOutput" to summary))
            }
            "read_latest_otp" -> {
                val latestOtpNotification = repository.notifications.value
                    .filter { it.otpCode != null }
                    .maxByOrNull { it.timestamp }

                val otpMessage = if (latestOtpNotification != null) {
                    "Your latest OTP is ${latestOtpNotification.otpCode}"
                } else {
                    "No recent OTP found."
                }
                ToolResult.Success(mapOf("speechOutput" to otpMessage))
            }
            else -> ToolResult.Failure("Invalid or missing action for NotificationTool: $action")
        }
    }
}
