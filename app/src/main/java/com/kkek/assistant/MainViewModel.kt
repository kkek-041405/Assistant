package com.kkek.assistant

import android.app.Application
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.telecom.Call
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.kkek.assistant.helpers.AppLauncher
import com.kkek.assistant.model.Actions
import com.kkek.assistant.model.Kind
import com.kkek.assistant.model.ListItem
import com.kkek.assistant.music.SpotifyHelper
import com.kkek.assistant.firebase.FirebaseRDHelper
import com.kkek.assistant.modules.CallDetails
import com.kkek.assistant.modules.Contact
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {
    internal val TAG = "MainViewModel"

    // UI state exposed to Compose
    var message by mutableStateOf("")
        private set

    var canScheduleFullScreenNotifications by mutableStateOf(false)
        private set

    var isDefaultDialer by mutableStateOf(true)
        private set

    var appContacts by mutableStateOf<List<ListItem>>(emptyList())
        private set

    var showDialerPrompt by mutableStateOf(false)

    var inSublist by mutableStateOf(false)

    var permissionRequest by mutableStateOf<Intent?>(null)
        private set

    internal val callItems: List<ListItem> = listOf(
        ListItem(kind = Kind.SIMPLE, text = "Hang up/Toggle Speaker", longNext = listOf(Actions.HANGUP), longPrevious = listOf(Actions.TOGGLE_SPEAKER)),
        ListItem(kind = Kind.TOGGLE, text = "Speaker", isOn = false),
        ListItem(kind = Kind.TOGGLE, text = "Mute", isOn = false)
    )

    internal suspend fun getContacts(): List<ListItem> {
        val contact: List<Contact> = FirebaseRDHelper.getContacts()
        Log.d("MainViewModel", "Fetched contacts: $contact")
        return contact.map {
            ListItem(kind = Kind.CONTACT, name = it.name, phoneNumber = it.phone, longNext = listOf(Actions.CALL_CONTACT))
        }
    }

    internal fun getSpotifySublist(): List<ListItem> {
        return listOf(
            ListItem(
                kind = Kind.SIMPLE,
                text = "Play/Pause",
                shortNext = listOf(Actions.SPOTIFY_NEXT),
                shortPrevious = listOf(Actions.SPOTIFY_PREVIOUS),
                longNext = listOf(Actions.SPOTIFY_SEEK_FORWARD),
                longPrevious = listOf(Actions.SPOTIFY_SEEK_BACKWARD),
                doubleNext = listOf(Actions.SPOTIFY_PLAY_PAUSE),
                doublePrevious = listOf(Actions.CLOSE_SUBLIST)
            )
        )
    }
    internal fun getBluetoothSublist(): List<ListItem> {
        val devices = actionExecutor.bluetoothHelper.getPairedDevices().map {
            ListItem(kind = Kind.SIMPLE, text = it.name ?: it.address, longNext = listOf(Actions.CONNECT_BLUETOOTH_DEVICE))
        }
        val disconnect = if (actionExecutor.bluetoothHelper.isConnected()) {
            listOf(ListItem(kind = Kind.SIMPLE, text = "Disconnect", longNext = listOf(Actions.DISCONNECT_BLUETOOTH_DEVICE)))
        } else {
            emptyList()
        }
        return listOf(
            ListItem(kind = Kind.TOGGLE, text = "Bluetooth", isOn = actionExecutor.bluetoothHelper.isBluetoothEnabled(), longNext = listOf(Actions.TOGGLE_BLUETOOTH)),
        ) + devices + disconnect
    }

    private val spotifyPlayer: List<ListItem> = listOf(
            ListItem(
                kind = Kind.SIMPLE,
                text = "Play/Pause",
                shortNext = listOf(Actions.SPOTIFY_NEXT),
                shortPrevious = listOf(Actions.SPOTIFY_PREVIOUS),
                longNext = listOf(Actions.SPOTIFY_SEEK_FORWARD),
                longPrevious = listOf(Actions.SPOTIFY_SEEK_BACKWARD),
                doubleNext = listOf(Actions.SPOTIFY_PLAY_PAUSE),
                doublePrevious = listOf(Actions.CLOSE_SUBLIST),
            ),
            ListItem(
                kind = Kind.SIMPLE,
                text = if (SpotifyHelper.isConnected.value) "Connected" else "Disconnected",
                isOn = SpotifyHelper.isConnected.value
            )
        )

    private val installedAppsList: List<ListItem>
        get() {
            val appLauncher = AppLauncher(getApplication())
            return appLauncher.getInstalledApps().map {
                ListItem(
                    kind = Kind.SIMPLE,
                    text = it.appName.toString(),
                    packageName = it.packageName,
                    longNext = listOf(Actions.LAUNCH_APP)
                )
            }
        }

    internal val defaultItems: List<ListItem>
        get() = listOf(
            ListItem(kind = Kind.SIMPLE, text = "Tell time", longNext = listOf(Actions.TELL_TIME), longPrevious = listOf(Actions.TELL_TIME), doubleNext = listOf(Actions.READ_SELECTION)),
            ListItem(kind = Kind.SUBLIST, text = "Make call", sublist = appContacts, longNext = listOf(Actions.OPEN_SUBLIST)),
            ListItem(kind = Kind.SUBLIST, text = "Spotify", sublist = spotifyPlayer, longNext = listOf(Actions.OPEN_SUBLIST)),
            ListItem(kind = Kind.SUBLIST, text = "Apps", sublist = installedAppsList, longNext = listOf(Actions.OPEN_SUBLIST)),
            ListItem(kind = Kind.SUBLIST, text = "Bluetooth", longNext = listOf(Actions.OPEN_SUBLIST)),
            ListItem(kind = Kind.SIMPLE, text = "Summarize Notifications", longNext = listOf(Actions.SUMMARIZE_NOTIFICATIONS), doubleNext = listOf(Actions.READ_SELECTION))
        )

    var currentList by mutableStateOf<List<ListItem>>(defaultItems)
    var selectedIndex by mutableStateOf(0)

    var callDetails by mutableStateOf<CallDetails?>(null)
        private set

    private lateinit var actionExecutor: ActionExecutor

    init {
        currentList = defaultItems
        actionExecutor = ActionExecutor(this, application)
        checkFullScreenIntentPermission()
        updateDialerRoleState()
        if (!isDefaultDialer) showDialerPrompt = true
    }

    fun onPermissionRequestHandled() {
        permissionRequest = null
    }

    fun updateDialerRoleState() {
        isDefaultDialer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getApplication<Application>().getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) ?: false
        } else {
            try {
                val telecom = getApplication<Application>().getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
                telecom.defaultDialerPackage == getApplication<Application>().packageName
            } catch (_: Exception) {
                false
            }
        }
    }

    fun checkFullScreenIntentPermission() {
        val notificationManager = getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        canScheduleFullScreenNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            notificationManager.canUseFullScreenIntent()
        } else {
            false
        }
    }

    fun handleCallState(call: Call?, speakerOn: Boolean, muted: Boolean) {
        if (call == null) {
            if (!inSublist) {
                currentList = defaultItems
                callDetails = null
            }
            return
        }

        when (call.state) {
            Call.STATE_RINGING -> {
                inSublist = false
                val details = call.details
                val callerName = details.extras?.getString("callerName")
                val callerNumber = call.details.handle?.schemeSpecificPart
                callDetails = CallDetails(callerName, callerNumber)
                currentList = listOf(
                    ListItem(kind = Kind.SIMPLE, text = "Answer/Reject", shortNext = listOf(Actions.ANSWER_CALL), shortPrevious = listOf(Actions.REJECT_CALL)),
                )
            }
            Call.STATE_ACTIVE, Call.STATE_DIALING, Call.STATE_CONNECTING -> {
                inSublist = false
                val details = call.details
                val callerName = details.extras?.getString("callerName")
                val callerNumber = call.details.handle?.schemeSpecificPart
                callDetails = CallDetails(callerName, callerNumber)
                currentList = listOf(
                    ListItem(kind = Kind.SIMPLE, text = "Hang up", longPrevious = listOf(Actions.TOGGLE_SPEAKER), longNext = listOf(Actions.HANGUP)),
                    ListItem(kind = Kind.TOGGLE, text = "Speaker", isOn = speakerOn),
                    ListItem(kind = Kind.TOGGLE, text = "Mute", isOn = muted)
                )
            }
            else -> {
                if (!inSublist) {
                    currentList = defaultItems
                    callDetails = null
                }
            }
        }
    }

    fun onNext() {
        val selectedItem = currentList[selectedIndex]
        val actions = selectedItem.shortNext.ifEmpty { listOf(Actions.NEXT) }
        actionExecutor.execute(selectedItem, actions)
    }

    fun onPrevious() {
        val selectedItem = currentList[selectedIndex]
        val actions = selectedItem.shortPrevious.ifEmpty { listOf(Actions.PREVIOUS) }
        actionExecutor.execute(selectedItem, actions)
    }

    fun onNextLongPress() {
        if (currentList.isEmpty()) return
        val selectedItem = currentList[selectedIndex]
        actionExecutor.execute(selectedItem, selectedItem.longNext)
    }

    fun onPreviousLongPress() {
        val selectedItem = currentList[selectedIndex]
        val actions = selectedItem.longPrevious.ifEmpty { listOf(Actions.CLOSE_SUBLIST) }
        actionExecutor.execute(selectedItem, actions)
    }

    fun onNextDoublePress() {
        val selectedItem = currentList[selectedIndex]
        actionExecutor.execute(selectedItem, selectedItem.doubleNext)
    }

    fun onPreviousDoublePress() {
        val selectedItem = currentList[selectedIndex]
        actionExecutor.execute(selectedItem, selectedItem.doublePrevious)
    }

    fun speakStatus() {
        Log.d("TTS", "Speaking Status")
        val sdf = SimpleDateFormat("h:mm a", Locale.US)
        val currentTime = sdf.format(Date())

        var batteryPercent: Int? = null
        try {
            val app = getApplication<Application>()
            val bm = app.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val cap = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (cap != null && cap != Int.MIN_VALUE && cap >= 0) {
                batteryPercent = cap
            } else {
                val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val status = app.registerReceiver(null, ifilter)
                status?.let {
                    val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (level >= 0 && scale > 0) {
                        batteryPercent = (level * 100) / scale
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read battery in speakStatus", e)
        }

        val text = if (batteryPercent != null) {
            "It's $currentTime with $batteryPercent percent"
        } else {
            "It's $currentTime. Battery percentage unknown"
        }
        actionExecutor.ttsHelper.speak(text)
        updateStatusMessage(currentTime, batteryPercent)
    }

    private fun updateStatusMessage(currentTime: String, batteryPercent: Int?) {
        setUserMessage("Spoken status: $currentTime${batteryPercent?.let { " with $it%" } ?: " (battery unknown)"}")
    }

    internal fun setUserMessage(newMessage: String) {
        message = newMessage
    }

    override fun onCleared() {
        super.onCleared()
        try {
            actionExecutor.ttsHelper.shutdown()
            SpotifyHelper.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "Error in onCleared", e)
        }
    }
}