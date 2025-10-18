
package com.kkek.assistant.telecom

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

@RequiresApi(Build.VERSION_CODES.M)
class CallHelper(private val context: Context) {

    private val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

    fun registerPhoneAccount() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.MANAGE_OWN_CALLS) != PackageManager.PERMISSION_GRANTED) {
            // Request permission
            ActivityCompat.requestPermissions(context as Activity, arrayOf(Manifest.permission.MANAGE_OWN_CALLS), 1)
            return
        }

        val componentName = ComponentName(context, CallConnectionService::class.java)
        val phoneAccountHandle = PhoneAccountHandle(componentName, "KKek Assistant")
        val phoneAccount = PhoneAccount.builder(phoneAccountHandle, "KKek Assistant")
            .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
            .build()

        telecomManager.registerPhoneAccount(phoneAccount)
    }

    fun placeCall(number: String) {
        val uri = Uri.fromParts("tel", number, null)
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            // Request permission
            ActivityCompat.requestPermissions(context as Activity, arrayOf(Manifest.permission.CALL_PHONE), 1)
            return
        }
        telecomManager.placeCall(uri, null)
    }
}
