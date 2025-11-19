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
import androidx.lifecycle.viewModelScope
import com.kkek.assistant.model.Actions
import com.kkek.assistant.model.CallDetails
import com.kkek.assistant.model.Kind
import com.kkek.assistant.model.ListItem
import com.kkek.assistant.spotify.SpotifyHelper
import com.kkek.assistant.telecom.CallHelper
import com.kkek.assistant.telecom.CallService
import com.kkek.assistant.telecom.Contact
import com.kkek.assistant.firebase.FirebaseRDHelper
import com.kkek.assistant.input.CallState
import com.kkek.assistant.input.VolumeKeyListener
import com.kkek.assistant.tts.TTSHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MainViewModel"

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
        private set

    var permissionRequest by mutableStateOf<Intent?>(null)
        private set

    // In-app custom contacts (used instead of querying device contacts)
    private val callItems: List<ListItem> = listOf(
        ListItem(kind = Kind.SIMPLE, text = "Hang up/Toggle Speaker", longNext = Actions.HANGUP, longPrevious = Actions.TOGGLE_SPEAKER),
        ListItem(kind = Kind.TOGGLE, text = "Speaker", isOn = false),
        ListItem(kind = Kind.TOGGLE, text = "Mute", isOn = false)
    )

    private suspend fun getContacts(): List<ListItem> {
        val contact: List<Contact> = FirebaseRDHelper.getContacts()
        Log.d("MainViewModel", "Fetched contacts: $contact")
        return contact.map {
            ListItem(kind = Kind.CONTACT, name = it.name, phoneNumber = it.phone, longNext = Actions.CALL_CONTACT)
        }
    }

    private fun getSpotifySublist(): List<ListItem> {
        return listOf(
            ListItem(
                kind = Kind.SIMPLE,
                text = "Play/Pause",
                shortNext = Actions.SPOTIFY_NEXT,
                shortPrevious = Actions.SPOTIFY_PREVIOUS,
                longNext = Actions.SPOTIFY_SEEK_FORWARD,
                longPrevious = Actions.SPOTIFY_SEEK_BACKWARD,
                doubleNext = Actions.SPOTIFY_PLAY_PAUSE
            )
        )
    }

    private val spotifyPlayer: List<ListItem> = listOf(
            ListItem(
                kind = Kind.SIMPLE,
                text = "Play/Pause",
                shortNext = Actions.SPOTIFY_NEXT,
                shortPrevious = Actions.SPOTIFY_PREVIOUS,
                longNext = Actions.SPOTIFY_SEEK_FORWARD,
                longPrevious = Actions.SPOTIFY_SEEK_BACKWARD,
                doubleNext = Actions.SPOTIFY_PLAY_PAUSE,
                doublePrevious = Actions.CLOSE_SUBLIST,
            )
        )

    private val defaultItems: List<ListItem>
        get() = listOf(
            ListItem(kind = Kind.SIMPLE, text = "Tell time", longNext = Actions.TELL_TIME, longPrevious = Actions.TELL_TIME, doubleNext = Actions.READ_SELECTION),
            ListItem(kind = Kind.SUBLIST, text = "Make call", sublist = appContacts, longNext = Actions.OPEN_SUBLIST),
            ListItem(kind = Kind.SUBLIST, text = "Spotify", sublist = spotifyPlayer, longNext = Actions.OPEN_SUBLIST),
            ListItem(kind = Kind.SIMPLE, text = "Summarize Notifications", longNext = Actions.SUMMARIZE_NOTIFICATIONS, doubleNext = Actions.READ_SELECTION)
        )

    var currentList by mutableStateOf<List<ListItem>>(defaultItems)
        private set

    var selectedIndex by mutableStateOf(0)
        private set

    var callDetails by mutableStateOf<CallDetails?>(null)
        private set

    private val ttsHelper = TTSHelper(getApplication())
    private val callHelper: CallHelper? = CallHelper(getApplication())

    init {
        currentList = defaultItems
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
            VolumeKeyListener.setCallState(CallState.IDLE)
            if (!inSublist) {
                currentList = defaultItems
                callDetails = null
            }
            return
        }

        when (call.state) {
            Call.STATE_RINGING -> {
                VolumeKeyListener.setCallState(CallState.INCOMING)
                inSublist = false
                val details = call.details
                val callerName = details.extras?.getString("callerName")
                val callerNumber = call.details.handle?.schemeSpecificPart
                callDetails = CallDetails(callerName, callerNumber)
                currentList = listOf(
                    ListItem(kind = Kind.SIMPLE, text = "Answer/Reject", shortNext = Actions.ANSWER_CALL, shortPrevious = Actions.REJECT_CALL),
                )
            }
            Call.STATE_ACTIVE, Call.STATE_DIALING, Call.STATE_CONNECTING -> {
                VolumeKeyListener.setCallState(CallState.ACTIVE)
                inSublist = false
                val details = call.details
                val callerName = details.extras?.getString("callerName")
                val callerNumber = call.details.handle?.schemeSpecificPart
                callDetails = CallDetails(callerName, callerNumber)
                currentList = listOf(
                    ListItem(kind = Kind.SIMPLE, text = "Hang up", shortNext = Actions.HANGUP, shortPrevious = Actions.HANGUP),
                    ListItem(kind = Kind.TOGGLE, text = "Speaker", isOn = speakerOn),
                    ListItem(kind = Kind.TOGGLE, text = "Mute", isOn = muted)
                )
            }
            else -> {
                VolumeKeyListener.setCallState(CallState.IDLE)
                if (!inSublist) {
                    currentList = defaultItems
                    callDetails = null
                }
            }
        }
    }

    fun onNext() {
        val selectedItem = currentList[selectedIndex]
        activateItem(selectedItem, selectedIndex, selectedItem.shortNext ?: Actions.NEXT)
    }

    fun onPrevious() {
        val selectedItem = currentList[selectedIndex]
        activateItem(selectedItem, selectedIndex, selectedItem.shortPrevious ?: Actions.PREVIOUS)
    }

    fun onNextLongPress() {
        if (currentList.isEmpty()) return
        val selectedItem = currentList[selectedIndex]
        activateItem(selectedItem, selectedIndex, selectedItem.longNext)
    }

    fun onPreviousLongPress() {
        val selectedItem = currentList[selectedIndex]
        activateItem(selectedItem, selectedIndex, selectedItem.longPrevious ?: Actions.CLOSE_SUBLIST)
    }

    fun onNextDoublePress() {
        val selectedItem = currentList[selectedIndex]
        activateItem(selectedItem, selectedIndex, selectedItem.doubleNext)
    }

    fun onPreviousDoublePress() {
        val selectedItem = currentList[selectedIndex]
        activateItem(selectedItem, selectedIndex, selectedItem.doublePrevious)
    }



    private fun activateItem(selectedItem: ListItem, index: Int, actionId: Actions?) {
        if (actionId == null) {
            updateMessage("No action assigned")
            return
        }
        when (actionId) {
            Actions.TELL_TIME -> speakStatus()
            Actions.SUMMARIZE_NOTIFICATIONS -> {
                ttsHelper.speak("Notification summary not available")
                updateMessage("Summarize notifications (not implemented)")
            }
            Actions.ANSWER_CALL -> {
                CallService.call.value?.answer(0)
                currentList = callItems
                selectedIndex = 0
                inSublist = true
                updateMessage("Call answered")
            }
            Actions.REJECT_CALL -> {
                CallService.call.value?.reject(false, "")
                currentList = defaultItems
                selectedIndex = 0
                inSublist = false
                updateMessage("Call rejected")
            }
            Actions.HANGUP -> {
                CallService.call.value?.disconnect()
                currentList = defaultItems
                selectedIndex = 0
                inSublist = false
                updateMessage("Call ended")
            }
            Actions.TOGGLE_SPEAKER -> {
                CallService.toggleSpeaker()
                updateMessage("Toggled speaker")
            }
            Actions.TOGGLE_MUTE -> {
                CallService.toggleMute()
                updateMessage("Toggled mute")
            }
            Actions.CALL_CONTACT -> {
                val phoneNumber = selectedItem.phoneNumber
                if (phoneNumber != null) {
                    callHelper?.placeCall(phoneNumber)
                    updateMessage("Calling ${selectedItem.name ?: phoneNumber}")
                    currentList = callItems
                    selectedIndex = 0
                    inSublist = true
                } else {
                    updateMessage("No phone number for contact")
                }
            }
            Actions.OPEN_SUBLIST -> {
                if (selectedItem.text == "Spotify") {
                    currentList = getSpotifySublist()
                    selectedIndex = 0
                    inSublist = true
                    updateMessage("Opened Spotify controls")
                } else if (selectedItem.text == "Make call") {
                    viewModelScope.launch {
                        updateMessage("Fetching contacts...")
                        currentList = getContacts()
                        selectedIndex = 0
                        inSublist = true
                        updateMessage("Opened contacts")
                    }
                } else {
                    selectedItem.sublist?.let {
                        currentList = it
                        selectedIndex = 0
                        inSublist = true
                        updateMessage("Opened sublist: ${selectedItem.text ?: "Unknown"}")
                    } ?: updateMessage("No sublist available for this item")
                }
            }
            Actions.CLOSE_SUBLIST -> {
                if (currentList != defaultItems) {
                    currentList = defaultItems
                    selectedIndex = 0
                    inSublist = false
                    updateMessage("")
                }
            }
            Actions.READ_SELECTION -> {
                selectedItem.let {
                    val textToSpeak = when (it.kind) {
                        Kind.SIMPLE -> it.text
                        Kind.TOGGLE -> it.text
                        Kind.CONTACT -> it.name
                        Kind.SUBLIST -> it.text
                    }
                    ttsHelper.speak(textToSpeak ?: "Unknown item")
                    updateMessage("Spoken: ${textToSpeak ?: "Unknown item"}")
                }
            }
            Actions.PREVIOUS -> {
                if (currentList.isNotEmpty()) {
                    selectedIndex = if (selectedIndex > 0) selectedIndex - 1 else selectedIndex
                }
            }
            Actions.NEXT -> {
                if (currentList.isNotEmpty()) {
                    selectedIndex = if (selectedIndex < currentList.size - 1) selectedIndex + 1 else selectedIndex
                }
            }
            Actions.SPOTIFY_PLAY_PAUSE -> {
                try {
                    SpotifyHelper.playPause(getApplication())
                    updateMessage("Toggled Play/Pause")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to toggle play/pause", e)
                    updateMessage("Play/Pause failed")
                }
            }
            Actions.SPOTIFY_NEXT -> {
                try {
                    SpotifyHelper.next(getApplication())
                    updateMessage("Skipping to next track")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to skip to next", e)
                    updateMessage("Skip next failed")
                }
            }
            Actions.SPOTIFY_PREVIOUS -> {
                try {
                    SpotifyHelper.previous(getApplication())
                    updateMessage("Skipping to previous track")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to skip to previous", e)
                    updateMessage("Skip previous failed")
                }
            }
            Actions.SPOTIFY_SEEK_FORWARD -> {
                try {
                    SpotifyHelper.seekForward(getApplication())
                    updateMessage("Seeked forward")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to seek forward", e)
                    updateMessage("Seek forward failed")
                }
            }
            Actions.SPOTIFY_SEEK_BACKWARD -> {
                try {
                    SpotifyHelper.seekBackward(getApplication())
                    updateMessage("Seeked backward")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to seek backward", e)
                    updateMessage("Seek backward failed")
                }
            }
            else -> {
                updateMessage("")
            }
        }
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
        ttsHelper.speak(text)
        updateMessage("Spoken status: $currentTime${batteryPercent?.let { " with $it%" } ?: " (battery unknown)"}")
    }

    private fun updateMessage(newMessage: String) {
        message = newMessage
    }

    fun shutdown() {
        ttsHelper.shutdown()
        SpotifyHelper.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        try {
            ttsHelper.shutdown()
            SpotifyHelper.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "Error in onCleared", e)
        }
    }

    fun speak(text: String) {
        ttsHelper.speak(text)
    }

    fun setUserMessage(newMessage: String) {
        updateMessage(newMessage)
    }

    fun answerCall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            CallService.call.value?.answer(0)
        }
    }

    fun rejectCall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            CallService.call.value?.reject(false, "")
        }
    }

    fun hangupCall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            CallService.call.value?.disconnect()
        }
    }

    fun toggleSpeaker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            CallService.toggleSpeaker()
        }
    }

    fun toggleMute() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            CallService.toggleMute()
        }
    }
}
