package com.kkek.assistant.helpers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

class AppLauncher(private val context: Context) {
    fun launchApp(packageName: String) {
        val pm: PackageManager = context.packageManager
        val intent: Intent? = pm.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            context.startActivity(intent)
        }
    }

    data class AppDetails(
        val appName: CharSequence,
        val packageName: String,
        val icon: Drawable
    )

    fun getInstalledApps(): List<AppDetails> {
        val pm: PackageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfoList = pm.queryIntentActivities(intent, 0)
        val appDetailsList = mutableListOf<AppDetails>()
        for (resolveInfo in resolveInfoList) {
            val activityInfo = resolveInfo.activityInfo
            val appName = resolveInfo.loadLabel(pm)
            val packageName = activityInfo.packageName
            val icon = resolveInfo.loadIcon(pm)
            appDetailsList.add(AppDetails(appName, packageName, icon))
        }
        return appDetailsList
    }
}