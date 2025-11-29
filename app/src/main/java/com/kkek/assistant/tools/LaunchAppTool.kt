package com.kkek.assistant.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.kkek.assistant.domain.model.AiTool
import com.kkek.assistant.domain.model.ToolParameter
import com.kkek.assistant.domain.model.ToolResult
import kotlinx.coroutines.CoroutineScope

class LaunchAppTool : AiTool {
    override val name = "launch_app"
    override val description = "Launches an application on the device."

    override val parameters = listOf(
        ToolParameter("appName", "String", "The name of the application to launch", isRequired = true)
    )

    override suspend fun execute(context: Context, params: Map<String, Any>, scope: CoroutineScope): ToolResult {
        val appName = params["appName"] as? String
            ?: return ToolResult.Failure("App name was not provided or is not a string.")

        val packageName = getAppPackageName(context, appName)
            ?: return ToolResult.Failure("App not found: $appName")

        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(packageName)
            ?: return ToolResult.Failure("App cannot be launched: $appName")

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)

        return ToolResult.Success(mapOf("status" to "Launched $appName successfully."))
    }

    private fun getAppPackageName(context: Context, appName: String): String? {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return packages.firstOrNull { packageInfo ->
            pm.getApplicationLabel(packageInfo).toString().equals(appName, ignoreCase = true)
        }?.packageName
    }
}
