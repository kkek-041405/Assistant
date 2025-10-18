package com.kkek.assistant

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telecom.Call
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.kkek.assistant.ui.theme.AssistantTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Use aliased imports to reference the new helper classes in subpackages
import com.kkek.assistant.notification.NotificationHelper as NotificationHelperNew
import com.kkek.assistant.contacts.ContactHelper as ContactsHelperNew
import com.kkek.assistant.tts.TTSHelper as TTSHelperNew
import com.kkek.assistant.telecom.CallHelper
import com.kkek.assistant.telecom.CallService

// Import moved model and input packages
import com.kkek.assistant.model.ListItem
import com.kkek.assistant.model.CallDetails
import com.kkek.assistant.input.VolumeKeyListener
import com.kkek.assistant.input.VolumeCommandListener
import com.kkek.assistant.input.CallState

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

    // Helpers (use implementations from the organized subpackages)
    private lateinit var notificationHelper: NotificationHelperNew
    private lateinit var contactHelper: ContactsHelperNew
    private lateinit var ttsHelper: TTSHelperNew
    private lateinit var callHelper: CallHelper

    // UI state
    private var message by mutableStateOf("")
    private var canScheduleFullScreenNotifications by mutableStateOf(false)
    // Track whether this app is the default dialer; prompt if not
    private var isDefaultDialer by mutableStateOf(true)
    // Whether we should show a prompt dialog on app open to set default dialer
    private var showDialerPrompt by mutableStateOf(false)
    // Whether the UI is currently showing a sublist (so we don't overwrite it from call-state logic)
    private var inSublist by mutableStateOf(false)
    // In-app custom contacts (used instead of querying device contacts)
    private val appContacts = listOf(
        ListItem.ContactItem("Dad", "+919391632589"),
        ListItem.ContactItem("MoM", "+916301638687"),
        ListItem.ContactItem("Charlie", "+10000000003")
    )
    private var currentList by mutableStateOf<List<ListItem>>(emptyList())
    private var selectedIndex by mutableStateOf(0)
    private var callDetails by mutableStateOf<CallDetails?>(null)

    private val defaultItems = listOf(
        ListItem.SimpleItem("Tell time"),
        ListItem.ToggleItem("Item #2 (Toggle)"),
        // Pre-populate the "Make call" sublist with the in-app contacts so we never load device contacts at runtime
        ListItem.SublistItem("Make call", appContacts.map { it as ListItem }),
        ListItem.SublistItem(
            "Item #3",
            listOf(
                ListItem.SimpleItem("Sub-item #3.1"),
                ListItem.SimpleItem("Sub-item #3.2"),
                ListItem.SimpleItem("Sub-item #3.3")
            )
        ),
        ListItem.SimpleItem("Item #4"),
        ListItem.SimpleItem("Item #5")
    )

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                updateMessage("Permissions granted")
                // We no longer request READ_CONTACTS; nothing special to do here
            } else {
                updateMessage("Some permissions were not granted")
            }
        }

    private val requestRoleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            updateMessage("Dialer role granted")
        } else {
            updateMessage("Dialer role not granted")
        }
        // Re-evaluate dialer state after user action
        updateDialerRoleState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentList = defaultItems

        // Initialize helpers (now from subpackages)
        notificationHelper = NotificationHelperNew(this)
        // Provide the in-app contacts so the helper does not query the device
        contactHelper = ContactsHelperNew(appContacts)
        ttsHelper = TTSHelperNew(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            callHelper = CallHelper(this)
        }

        requestNotificationPermission()
        checkFullScreenIntentPermission()

        // Update dialer role state and prompt if not the default dialer
        updateDialerRoleState()
        // Show an in-app confirmation dialog asking the user whether to set this app as default
        if (!isDefaultDialer) {
            showDialerPrompt = true
        }

        enableEdgeToEdge()

        setContent {
            AssistantTheme {
                val call by CallService.call.collectAsState()
                val speakerOn by CallService.speakerOn.collectAsState()
                val muted by CallService.muted.collectAsState()

                // Compose AlertDialog shown on app open when the app is not default dialer
                if (showDialerPrompt) {
                    AlertDialog(
                        onDismissRequest = { showDialerPrompt = false },
                        title = { Text("Set default dialer") },
                        text = { Text("This app can act as your default phone dialer. Would you like to set it as the default now?") },
                        confirmButton = {
                            TextButton(onClick = {
                                showDialerPrompt = false
                                requestDialerRoleOrChangeDefault()
                            }) { Text("Yes") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDialerPrompt = false }) { Text("Later") }
                        }
                    )
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {

                        // If not default dialer, show a button so user can set it manually
                        if (!isDefaultDialer) {
                            Button(onClick = { requestDialerRoleOrChangeDefault() }, modifier = Modifier.padding(16.dp)) {
                                Text("Set as Default Dialer")
                            }
                        }

                        // Update the VolumeKeyListener with the current call state
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            when {
                                call?.state == Call.STATE_RINGING -> {
                                    VolumeKeyListener.setCallState(CallState.INCOMING)
                                    // entering call UI — clear sublist flag
                                    inSublist = false
                                    val details = call?.details
                                    val callerName = details?.extras?.getString("callerName")
                                    val callerNumber = call?.details?.handle?.schemeSpecificPart
                                    callDetails = CallDetails(callerName, callerNumber)
                                    currentList = listOf(
                                        ListItem.SimpleItem("Answer"),
                                        ListItem.SimpleItem("Reject")
                                    )
                                }
                                call?.state in setOf(Call.STATE_ACTIVE, Call.STATE_DIALING, Call.STATE_CONNECTING) -> {
                                    VolumeKeyListener.setCallState(CallState.ACTIVE)
                                    // entering call UI — clear sublist flag
                                    inSublist = false
                                    val details = call?.details
                                    val callerName = details?.extras?.getString("callerName")
                                    val callerNumber = call?.details?.handle?.schemeSpecificPart
                                    callDetails = CallDetails(callerName, callerNumber)
                                    currentList = listOf(
                                        ListItem.SimpleItem("Hang up"),
                                        ListItem.ToggleItem("Speaker", speakerOn),
                                        ListItem.ToggleItem("Mute", muted)
                                    )
                                }
                                else -> {
                                    VolumeKeyListener.setCallState(CallState.IDLE)
                                    // Only reset back to the default items when not viewing a sublist
                                    if (!inSublist) {
                                        currentList = defaultItems
                                        callDetails = null
                                    }
                                }
                            }
                        }

                        callDetails?.let {
                            Text(text = "Caller: ${it.callerName ?: "Unknown"}")
                            Text(text = "Number: ${it.callerNumber ?: "Unknown"}")
                        }

                        ItemList(items = currentList, selectedIndex = selectedIndex, modifier = Modifier.weight(1f))

                        if (message.isNotEmpty()) {
                            Text(text = message, modifier = Modifier.padding(16.dp))
                        }

                        if (!canScheduleFullScreenNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
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
                updateMessage("Unable to open change default dialer settings")
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

    private fun updateDialerRoleState() {
        isDefaultDialer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(ROLE_SERVICE) as RoleManager
            roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
        } else {
            try {
                val telecom = getSystemService(TELECOM_SERVICE) as android.telecom.TelecomManager
                telecom.defaultDialerPackage == packageName
            } catch (ignored: Exception) {
                false
            }
        }
    }

    private fun checkFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            canScheduleFullScreenNotifications = notificationManager.canUseFullScreenIntent()
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
        checkFullScreenIntentPermission()
        // Re-evaluate whether we are the default dialer each time the activity resumes
        updateDialerRoleState()
    }

    override fun onPause() {
        super.onPause()
        VolumeKeyListener.setListener(null)
    }

    override fun onDestroy() {
        ttsHelper.shutdown()
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
        if (currentList.isNotEmpty()) {
            selectedIndex = (selectedIndex + 1) % currentList.size
            val item = currentList.getOrNull(selectedIndex)
            Log.d(TAG, "onNext -> index=$selectedIndex item=${item?.let { it::class.simpleName }}")
            updateMessage("Selected: ${when (item) {
                is ListItem.SimpleItem -> item.text
                is ListItem.ToggleItem -> item.text
                is ListItem.ContactItem -> item.name
                is ListItem.SublistItem -> item.text
                else -> "Unknown"
            }}")
        }
    }

    override fun onPrevious() {
        if (currentList.isNotEmpty()) {
            selectedIndex = if (selectedIndex > 0) selectedIndex - 1 else currentList.size - 1
            val item = currentList.getOrNull(selectedIndex)
            Log.d(TAG, "onPrevious -> index=$selectedIndex item=${item?.let { it::class.simpleName }}")
            updateMessage("Selected: ${when (item) {
                is ListItem.SimpleItem -> item.text
                is ListItem.ToggleItem -> item.text
                is ListItem.ContactItem -> item.name
                is ListItem.SublistItem -> item.text
                else -> "Unknown"
            }}")
        }
    }

    override fun onNextLongPress() {
        if (currentList.isEmpty()) {
            updateMessage("List empty")
            return
        }
        val safeIndex = selectedIndex.coerceIn(0, currentList.size - 1)
        if (safeIndex != selectedIndex) selectedIndex = safeIndex
        val selectedItem = currentList.getOrNull(safeIndex) ?: run {
            updateMessage("No item at $safeIndex")
            return
        }
        Log.d(TAG, "onNextLongPress - index=$safeIndex item=${selectedItem::class.simpleName}")
        updateMessage("Long-pressed: ${selectedItem::class.simpleName} at $safeIndex")
        Log.d(TAG, "Opening item at $safeIndex: ${selectedItem}")
        when (selectedItem) {
            is ListItem.ContactItem -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    callHelper.placeCall(selectedItem.phoneNumber)
                }
                updateMessage("Calling ${selectedItem.name}")
            }
            is ListItem.SublistItem -> {
                // Open any sublist directly — we pre-populate the "Make call" sublist with contacts
                currentList = selectedItem.sublist
                selectedIndex = 0
                // Reset any pending key state (avoid blocked navigation)
                com.kkek.assistant.input.VolumeKeyListener.reset()
                // mark that we're viewing a sublist so call-state code won't overwrite it
                inSublist = true
                updateMessage("Opened ${selectedItem.text}")
            }
            is ListItem.SimpleItem -> {
                when (selectedItem.text) {
                    "Tell time" -> speakTime()
                    "Answer" -> onAnswer()
                    "Reject" -> onReject()
                    "Hang up" -> onHangup()
                    else -> { }
                }
            }
            is ListItem.ToggleItem -> {
                when (selectedItem.text) {
                    "Speaker" -> onToggleSpeakerLongPress()
                    "Mute" -> onToggleMute()
                    else -> {
                        toggleItem(selectedIndex)
                        val updated = currentList[selectedIndex] as ListItem.ToggleItem
                        updateMessage("${updated.text} is now ${if (updated.isOn) "ON" else "OFF"}")
                    }
                }
            }
        }
    }

    override fun onPreviousLongPress() {
        if (currentList != defaultItems) {
            currentList = defaultItems
            selectedIndex = 0
            inSublist = false
            updateMessage("")
        } else {
            updateMessage("")
        }
    }

    override fun onAnswer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            CallService.call.value?.answer(0)
        }
    }

    override fun onReject() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            CallService.call.value?.reject(false, "")
        }
    }

    override fun onHangup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            CallService.call.value?.disconnect()
        }
    }

    override fun onEndCallLongPress() {
        onHangup()
    }

    override fun onToggleSpeakerLongPress() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            CallService.toggleSpeaker()
        }
    }

    private fun onToggleMute() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            CallService.toggleMute()
        }
    }

    private fun speakTime() {
        val sdf = SimpleDateFormat("h:mm a", Locale.US)
        val currentTime = sdf.format(Date())
        ttsHelper.speak("The time is $currentTime")
        updateMessage("Telling time: $currentTime")
    }

    private fun toggleItem(index: Int) {
        val list = currentList.toMutableList()
        val item = list.getOrNull(index) as? ListItem.ToggleItem ?: return
        list[index] = item.copy(isOn = !item.isOn)
        currentList = list
    }

    private fun updateMessage(newMessage: String) {
        message = newMessage
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
