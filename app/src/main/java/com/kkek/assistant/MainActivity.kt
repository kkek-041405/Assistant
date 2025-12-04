package com.kkek.assistant

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kkek.assistant.System.VolumeCommandListener
import com.kkek.assistant.System.VolumeKeyListener
import com.kkek.assistant.model.ListItem
import com.kkek.assistant.ui.theme.AssistantTheme
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
@Suppress("RedundantQualifierName", "unused")
class MainActivity : ComponentActivity(), VolumeCommandListener {

    private val TAG = "MainActivity"
    private val viewModel: MainViewModel by viewModels()

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
        requestBluetoothPermissions()
        enableEdgeToEdge()

        @OptIn(ExperimentalMaterial3Api::class)
        setContent {
            AssistantTheme {


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

                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(viewModel.message) {
                    if (viewModel.message.isNotEmpty()) {
                        snackbarHostState.showSnackbar(viewModel.message)
                        viewModel.setUserMessage("") // Reset message after showing
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text("Assistant") }
                        )
                    },
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                ) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        ItemList(
                            items = viewModel.currentList,
                            selectedIndex = viewModel.selectedIndex,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }



    @Composable
    fun ItemList(items: List<ListItem>, selectedIndex: Int, modifier: Modifier = Modifier) {
        LazyColumn(modifier = modifier) {
            itemsIndexed(items) { index, item ->
                val isSelected = index == selectedIndex
                val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(backgroundColor)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = item.text ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
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

    override fun onResume() {
        super.onResume()
        viewModel.onAppOpen()
        VolumeKeyListener.setListener(this)
        VolumeKeyListener.reset()
        viewModel.updateDialerRoleState()
    }

    override fun onPause() {
        super.onPause()
        VolumeKeyListener.setListener(null)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (VolumeKeyListener.onKeyDown(keyCode, event)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (VolumeKeyListener.onKeyUp(keyCode, event)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onNext() = viewModel.onNext()
    override fun onPrevious() = viewModel.onPrevious()
    override fun onNextLongPress() = viewModel.onNextLongPress()
    override fun onPreviousLongPress() = viewModel.onPreviousLongPress()
    override fun onNextDoublePress() = viewModel.onNextDoublePress()
    override fun onPreviousDoublePress() = viewModel.onPreviousDoublePress()
}
