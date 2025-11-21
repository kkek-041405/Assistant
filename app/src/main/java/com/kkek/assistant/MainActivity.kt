package com.kkek.assistant

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kkek.assistant.System.VolumeCommandListener
import com.kkek.assistant.System.VolumeKeyListener
import com.kkek.assistant.model.Kind
import com.kkek.assistant.model.ListItem
import com.kkek.assistant.modules.Phone
import com.kkek.assistant.ui.theme.AssistantTheme


@Suppress("RedundantQualifierName", "unused")
class MainActivity : ComponentActivity(), VolumeCommandListener {

    private val TAG = "MainActivity"

    private val viewModel: MainViewModel by viewModels()

    private var batteryPercent by mutableStateOf(-1)

    private var showNotificationListenerDialog by mutableStateOf(false)

    private val requestRoleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.setUserMessage("Dialer role granted")
        } else {
            viewModel.setUserMessage("Dialer role not granted")
        }
        viewModel.updateDialerRoleState()
    }

    private val requestBluetoothPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allPermissionsGranted = permissions.values.all { it }
            if (allPermissionsGranted) {
                viewModel.setUserMessage("Bluetooth permissions granted")
            } else {
                viewModel.setUserMessage("Some Bluetooth permissions were not granted")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action == Intent.ACTION_ASSIST) {
            Log.d(TAG, "MainActivity launched for ASSIST action")
        }

        VolumeKeyListener.init(this)

        viewModel.checkFullScreenIntentPermission()
        viewModel.updateDialerRoleState()

        requestBluetoothPermissions()

        if (!viewModel.isDefaultDialer) {
            viewModel.showDialerPrompt = true
        }

        if (!isNotificationServiceEnabled()) {
            showNotificationListenerDialog = true
        }

        enableEdgeToEdge()

        setContent {
            AssistantTheme {
                val call by Phone.call.collectAsState()
                val speakerOn by Phone.speakerOn.collectAsState()
                val muted by Phone.muted.collectAsState()

                if (showNotificationListenerDialog) {
                    AlertDialog(
                        onDismissRequest = { showNotificationListenerDialog = false },
                        title = { Text("Enable Notification Access") },
                        text = { Text("To upload notification details, this app requires notification access. Please enable it in the settings.") },
                        confirmButton = {
                            TextButton(onClick = {
                                showNotificationListenerDialog = false
                                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }) { Text("Open Settings") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showNotificationListenerDialog = false }) { Text("Later") }
                        }
                    )
                }

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

                        if (!viewModel.isDefaultDialer) {
                            Button(onClick = { requestDialerRoleOrChangeDefault() }, modifier = Modifier.padding(16.dp)) {
                                Text("Set as Default Dialer")
                            }
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            viewModel.handleCallState(call, speakerOn, muted)
                        }

                        viewModel.callDetails?.let {
                            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                Text(text = "Caller: ${it.callerName ?: "Unknown"}")
                                Text(text = "Number: ${it.callerNumber ?: "Unknown"}")
                            }
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

    private fun isNotificationServiceEnabled(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
    }

    private fun requestDialerRoleOrChangeDefault() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(ROLE_SERVICE) as RoleManager
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
            requestRoleLauncher.launch(intent)
        } else {
            try {
                val intent = Intent("android.telecom.action.CHANGE_DEFAULT_DIALER")
                intent.putExtra("android.telecom.extra.CHANGE_DEFAULT_DIALER_PACKAGE_NAME", packageName)
                startActivity(intent)
            } catch (_: Exception) {
                viewModel.setUserMessage("Unable to open change default dialer settings")
            }
        }
    }

    private fun requestBluetoothPermissions() {
        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }

        val permissionsNotGranted = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsNotGranted.isNotEmpty()) {
            requestBluetoothPermissionLauncher.launch(permissionsNotGranted.toTypedArray())
        }
    }

    private fun updateBatteryPercentage() {
        try {
            val bm = getSystemService(BATTERY_SERVICE) as? BatteryManager
            val capacity = if (bm != null) {
                val cap = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                if (cap != Int.MIN_VALUE) cap else null
            } else null

            if (capacity != null && capacity >= 0) {
                batteryPercent = capacity
                return
            }

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
        viewModel.updateDialerRoleState()
        updateBatteryPercentage()
        if (!isNotificationServiceEnabled()) {
            showNotificationListenerDialog = true
        }
    }

    override fun onPause() {
        super.onPause()
        VolumeKeyListener.setListener(null)
    }

    override fun onDestroy() {
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

    override fun onNextDoublePress() {
        viewModel.onNextDoublePress()
    }

    override fun onPreviousDoublePress() {
        viewModel.onPreviousDoublePress()
    }
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
