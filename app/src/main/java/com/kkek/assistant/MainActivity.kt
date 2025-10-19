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
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kkek.assistant.ui.theme.AssistantTheme
import com.kkek.assistant.input.VolumeKeyListener
import com.kkek.assistant.input.VolumeCommandListener
import com.kkek.assistant.telecom.CallService
import android.content.Context
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.mutableStateOf
import com.kkek.assistant.model.Kind


// Import moved model and input packages
import com.kkek.assistant.model.ListItem

class MainActivity : ComponentActivity(), VolumeCommandListener {

    private val TAG = "MainActivity"

    // ViewModel
    private val viewModel: MainViewModel by viewModels()

    // Battery percentage state (observed by Compose)
    private var batteryPercent by mutableStateOf(-1)

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

        VolumeKeyListener.init(this)

        // Delegate initialization checks to ViewModel
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

                // Handle permission requests from the ViewModel
                val permissionRequest = viewModel.permissionRequest
                LaunchedEffect(permissionRequest) {
                    permissionRequest?.let {
                        try {
                            startActivity(it)
                        } catch (e: Exception) {
                            Log.e(TAG, "Could not launch settings intent", e)
                        } finally {
                            viewModel.onPermissionRequestHandled()
                        }
                    }
                }

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

    private fun updateBatteryPercentage() {
        try {
            val bm = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val capacity = if (bm != null) {
                // BATTERY_PROPERTY_CAPACITY returns battery level in percent on supported devices
                val cap = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                if (cap != Int.MIN_VALUE) cap else null
            } else null

            if (capacity != null && capacity >= 0) {
                batteryPercent = capacity
                return
            }

            // Fallback: query ACTION_BATTERY_CHANGED
            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = registerReceiver(null, ifilter)
            batteryStatus?.let { intent ->
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    batteryPercent = (level * 100) / scale
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read battery percentage", e)
        }
    }

    override fun onResume() {
        super.onResume()
        VolumeKeyListener.setListener(this)
        VolumeKeyListener.reset()
        // Re-evaluate whether we are the default dialer each time the activity resumes
        viewModel.updateDialerRoleState()
        // Refresh battery percentage when returning to the activity
        updateBatteryPercentage()
    }

    override fun onPause() {
        super.onPause()
        VolumeKeyListener.setListener(null)
    }

    override fun onDestroy() {
        // Don't call viewModel.shutdown() here; Activity onDestroy is called on configuration changes
        // and we want the ViewModel (and its TTS) to survive rotations. The ViewModel will clean up
        // in onCleared() when it is actually destroyed.
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



    // New double-press callbacks: map to same behavior as long-press handlers in ViewModel
    override fun onNextDoublePress() {
        viewModel.onNextDoublePress()
    }

    override fun onPreviousDoublePress() {
        viewModel.onPreviousDoublePress()
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
            val text = when (item.kind) {
                Kind.SIMPLE -> item.text ?: ""
                Kind.SUBLIST -> "${item.text ?: ""} >"
                Kind.TOGGLE -> "${item.text ?: ""} [${if (item.isOn) "ON" else "OFF"}]"
                Kind.CONTACT -> "${item.name ?: ""}: ${item.phoneNumber ?: ""}"
                else -> ""
            }
            Text(text = text, color = if (index == selectedIndex) Color.Red else Color.Unspecified)
        }
    }
}

@Composable
fun ItemListPreview() {
    AssistantTheme { ItemList(items = listOf(ListItem(kind = Kind.SIMPLE, text = "Item #1")), selectedIndex = 0) }
}
