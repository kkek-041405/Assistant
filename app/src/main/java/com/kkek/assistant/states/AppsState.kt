package com.kkek.assistant.states

import android.app.Application
import android.content.Intent
import com.kkek.assistant.model.Kind
import com.kkek.assistant.model.ListItem
import com.kkek.assistant.model.ToolAction

object AppsState {
    fun build(application: Application): List<ListItem> {
        val pm = application.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0)
        return apps.map { app ->
            val appName = app.loadLabel(pm).toString()
            ListItem(
                kind = Kind.SIMPLE,
                text = appName,
                longNext = listOf(ToolAction("launch_app", mapOf("appName" to appName))),
                shortNext = listOf(ToolAction("next_item")),
                shortPrevious = listOf(ToolAction("previous_item")),
                longPrevious = listOf(ToolAction("show_default_list"))
            )
        }
    }
}