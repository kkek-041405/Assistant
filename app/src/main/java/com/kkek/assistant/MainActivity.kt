package com.kkek.assistant

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kkek.assistant.ui.theme.AssistantTheme
import com.kkek.assistant.input.VolumeKeyListener
import com.kkek.assistant.input.VolumeCommandListener
import com.kkek.assistant.telecom.CallService

// Import moved model and input packages
import com.kkek.assistant.model.ListItem
import com.kkek.assistant.model.CallDetails

/**
 * MainActivity: orchestrates UI and delegates responsibilities to helper classes.
 * Responsibilities delegated:
 *  - NotificationHelper: notification channel and full-screen notifications
 *  - CallHelper: Telecom phone account registration and placing calls
 *  - ContactHelper: fetching contacts from ContentResolver
 *  - TTSHelper: text-to-speech lifecycle and speaking
 */
class MainActivity : ComponentActivity(), VolumeCommandListener {

    private val TAG = "MainActivity"

    // ViewModel
    private val viewModel: MainViewModel by viewModels()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                viewModel.setUserMessage("Permissions granted")
            } else {
                viewModel.setUserMessage("Some permissions were not granted")
            }
        }

    private val requestRoleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.setUserMessage("Dialer role granted")
        } else {
            viewModel.setUserMessage("Dialer role not granted")
        }
        // Re-evaluate dialer state after user action
        viewModel.updateDialerRoleState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Delegate initialization checks to ViewModel
        requestNotificationPermission()
        viewModel.checkFullScreenIntentPermission()
        viewModel.updateDialerRoleState()

        // Show prompt if not default dialer
        if (!viewModel.isDefaultDialer) {
            viewModel.showDialerPrompt = true
        }

        enableEdgeToEdge()

        setContent {
            AssistantTheme {
                val call by CallService.call.collectAsState()
                val speakerOn by CallService.speakerOn.collectAsState()
                val muted by CallService.muted.collectAsState()

                // Compose AlertDialog shown on app open when the app is not default dialer
                if (viewModel.showDialerPrompt) {
                    AlertDialog(
                        onDismissRequest = { viewModel.showDialerPrompt = false },
                        title = { Text("Set default dialer") },
                        text = { Text("This app can act as your default phone dialer. Would you like to set it as the default now?") },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.showDialerPrompt = false
                                requestDialerRoleOrChangeDefault()
                            }) { Text("Yes") }
                        },
                        dismissButton = {
                            TextButton(onClick = { viewModel.showDialerPrompt = false }) { Text("Later") }
                        }
                    )
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {

                        // If not default dialer, show a button so user can set it manually
                        if (!viewModel.isDefaultDialer) {
                            Button(onClick = { requestDialerRoleOrChangeDefault() }, modifier = Modifier.padding(16.dp)) {
                                Text("Set as Default Dialer")
                            }
                        }

                        // Let ViewModel update its state based on current call
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            viewModel.handleCallState(call, speakerOn, muted)
                        }

                        viewModel.callDetails?.let {
                            Text(text = "Caller: ${it.callerName ?: "Unknown"}")
                            Text(text = "Number: ${it.callerNumber ?: "Unknown"}")
                        }

                        ItemList(items = viewModel.currentList, selectedIndex = viewModel.selectedIndex, modifier = Modifier.weight(1f))

                        if (viewModel.message.isNotEmpty()) {
                            Text(text = viewModel.message, modifier = Modifier.padding(16.dp))
                        }

                        if (!viewModel.canScheduleFullScreenNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            Button(onClick = { openNotificationSettings() }) {
                                Text("Grant Full-Screen Notification Permission")
                            }
                        }

                        Button(onClick = { sendTestNotification() }) { Text("Test Full-Screen Notification") }

                    }
                }
            }
        }
    }

    private fun requestDialerRoleOrChangeDefault() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestDialerRole()
        } else {
            try {
                val intent = Intent("android.telecom.action.CHANGE_DEFAULT_DIALER")
                intent.putExtra("android.telecom.extra.CHANGE_DEFAULT_DIALER_PACKAGE_NAME", packageName)
                startActivity(intent)
            } catch (ignored: Exception) {
                viewModel.setUserMessage("Unable to open change default dialer settings")
            }
        }
    }

    private fun requestDialerRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(ROLE_SERVICE) as RoleManager
            if (!roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                requestRoleLauncher.launch(intent)
            }
        }
    }

    private fun openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply { data = ("package:$packageName").toUri() }
            startActivity(intent)
        }
    }

    private fun sendTestNotification() {
        // Intentionally left empty for now
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Requesting POST_NOTIFICATIONS permission.")
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        VolumeKeyListener.setListener(this)
        VolumeKeyListener.reset()
        // Re-evaluate whether we are the default dialer each time the activity resumes
        viewModel.updateDialerRoleState()
    }

    override fun onPause() {
        super.onPause()
        VolumeKeyListener.setListener(null)
    }

    override fun onDestroy() {
        viewModel.shutdown()
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (VolumeKeyListener.onKeyDown(keyCode, event)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (VolumeKeyListener.onKeyUp(keyCode, event)) return true
        return super.onKeyUp(keyCode, event)
    }

    // VolumeCommandListener implementations
    override fun onNext() {
        viewModel.onNext()
    }

    override fun onPrevious() {
        viewModel.onPrevious()
    }

    override fun onNextLongPress() {
        viewModel.onNextLongPress()
    }

    override fun onPreviousLongPress() {
        viewModel.onPreviousLongPress()
    }

    override fun onAnswer() {
        viewModel.answerCall()
    }

    override fun onReject() {
        viewModel.rejectCall()
    }

    override fun onHangup() {
        viewModel.hangupCall()
    }

    override fun onEndCallLongPress() {
        onHangup()
    }

    override fun onToggleSpeakerLongPress() {
        viewModel.toggleSpeaker()
    }

    // Note: business logic (speakTime/toggleItem/message) moved to ViewModel
}

@Composable
fun ItemList(
    items: List<ListItem>,
    selectedIndex: Int,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        itemsIndexed(items) { index, item ->
            val text = when (item) {
                is ListItem.SimpleItem -> item.text
                is ListItem.SublistItem -> "${item.text} >"
                is ListItem.ToggleItem -> "${item.text} [${if (item.isOn) "ON" else "OFF"}]"
                is ListItem.ContactItem -> "${item.name}: ${item.phoneNumber}"
            }
            Text(text = text, color = if (index == selectedIndex) Color.Red else Color.Unspecified)
        }
    }
}

@Composable
fun ItemListPreview() {
    AssistantTheme { ItemList(items = listOf(ListItem.SimpleItem("Item #1")), selectedIndex = 0) }
}
