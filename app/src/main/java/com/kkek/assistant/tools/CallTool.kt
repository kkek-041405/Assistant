package com.kkek.assistant.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.kkek.assistant.data.AssistantRepository
import com.kkek.assistant.domain.model.AiTool
import com.kkek.assistant.domain.model.ToolParameter
import com.kkek.assistant.domain.model.ToolResult
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

class CallTool @Inject constructor(private val repository: AssistantRepository) : AiTool {
    override val name = "call_contact"
    override val description = "Calls a contact by name."

    override val parameters = listOf(
        ToolParameter("contactName", "String", "The name of the contact to call.", isRequired = true)
    )

    override suspend fun execute(context: Context, params: Map<String, Any>, scope: CoroutineScope): ToolResult {
        val contactName = params["contactName"] as? String
            ?: return ToolResult.Failure("Contact name not provided.")

        val contacts = repository.getContacts()
        val contact = contacts.firstOrNull { it.name.equals(contactName, ignoreCase = true) }
            ?: return ToolResult.Failure("Contact not found: $contactName")

        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:${contact.phone}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            ToolResult.Success(mapOf("status" to "Calling ${contact.name}"))
        } catch (e: SecurityException) {
            ToolResult.Failure("Permission to make calls not granted.", e)
        }
    }
}
