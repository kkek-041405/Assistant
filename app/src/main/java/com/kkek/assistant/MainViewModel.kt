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
import com.kkek.assistant.input.CallState
import com.kkek.assistant.input.VolumeKeyListener
import com.kkek.assistant.model.CallDetails
import com.kkek.assistant.model.ListItem
import com.kkek.assistant.model.Actions
import com.kkek.assistant.tts.TTSHelper as TTSHelperNew
import com.kkek.assistant.telecom.CallHelper
import com.kkek.assistant.telecom.CallService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.kkek.assistant.model.Kind

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MainViewModel"

    // Trigger for activations
    private enum class Trigger { SHORT, LONG, DOUBLE }

    // UI state exposed to Compose
    var message by mutableStateOf("")
        private set

    var canScheduleFullScreenNotifications by mutableStateOf(false)
        private set

    var isDefaultDialer by mutableStateOf(true)
        private set

    var showDialerPrompt by mutableStateOf(false)

    var inSublist by mutableStateOf(false)
        private set

    var permissionRequest by mutableStateOf<Intent?>(null)
        private set

    // In-app custom contacts (used instead of querying device contacts)
    private  val callItems: List<ListItem> = listOf(
        ListItem(kind = Kind.SIMPLE, text = "Hang up/Toggle Speaker", longNext = Actions.HANGUP, longPrevious = Actions.TOGGLE_SPEAKER),
        ListItem(kind = Kind.TOGGLE, text = "Speaker", isOn = false),
        ListItem(kind = Kind.TOGGLE, text = "Mute", isOn = false)
    )
    private val appContacts = listOf(
        ListItem(kind = Kind.CONTACT, name = "Dad", phoneNumber = "+919391632589", longNext = Actions.CALL_CONTACT),
        ListItem(kind = Kind.CONTACT, name = "MoM", phoneNumber = "+916301638687", longNext = Actions.CALL_CONTACT),
        ListItem(kind = Kind.CONTACT, name = "Sai Kiran", phoneNumber = "+917659835677", longNext = Actions.CALL_CONTACT),
        ListItem(kind = Kind.CONTACT, name = "Revanth", phoneNumber = "+916302415703", longNext = Actions.CALL_CONTACT)

    )

    private val defaultItems: List<ListItem> = listOf(
        // Use Actions IDs for behavior; default to long-press -> speak status
        // preserve previous single-action behavior by assigning both Next and Previous to the same action
        ListItem(kind = Kind.SIMPLE, text = "Tell time", longNext = Actions.TELL_TIME, longPrevious = Actions.TELL_TIME, doubleNext = Actions.READ_SELECTION),
        ListItem(kind = Kind.SUBLIST, text = "Make call", sublist = appContacts, longNext = Actions.OPEN_SUBLIST),
        ListItem(kind = Kind.SIMPLE, text = "Summarize Notifications", longNext = Actions.SUMMARIZE_NOTIFICATIONS, doubleNext = Actions.READ_SELECTION)
    )

    var currentList by mutableStateOf<List<ListItem>>(defaultItems)
        private set

    var selectedIndex by mutableStateOf(0)
        private set

    var callDetails by mutableStateOf<CallDetails?>(null)
        private set

    // Helpers
    private val ttsHelper = TTSHelperNew(getApplication())
    private val callHelper: CallHelper? = CallHelper(getApplication())

    init {
        // Initialize any VM-level state here
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
        // Guard nullable call first so we can safely access its properties
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
                    // Assign both Next and Previous to preserve previous single-action semantics
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
        val selectedItem = currentList.get(selectedIndex)
        activateItem(selectedItem, selectedIndex, selectedItem.shortNext?: Actions.NEXT)
    }

    fun onPrevious() {
        val selectedItem = currentList.get(selectedIndex)
        activateItem(selectedItem,selectedIndex, selectedItem.shortPrevious?: Actions.PREVIOUS)
    }

    fun onNextLongPress() {
        if (currentList.isEmpty()) return
        val selectedItem = currentList.get(selectedIndex)
        // Execute long-press activation for the selected item (Next direction)
        activateItem(selectedItem, selectedIndex, selectedItem.longNext)
    }

    fun onPreviousLongPress() {
        val selectedItem = currentList.get(selectedIndex)
        activateItem(selectedItem, selectedIndex, selectedItem.longPrevious?:Actions.CLOSE_SUBLIST)
    }

    fun onNextDoublePress() {
        val selectedItem = currentList.get(selectedIndex)
        activateItem(selectedItem, selectedIndex, selectedItem.doubleNext)
    }

    fun onPreviousDoublePress() {
        val selectedItem = currentList.get(selectedIndex)
        activateItem(selectedItem, selectedIndex, selectedItem.doublePrevious)
    }
    // Shared activation helper used by different triggers
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
                // Open sublist if available
                selectedItem.sublist?.let { sublist ->
                    currentList = sublist
                    selectedIndex = 0
                    inSublist = true
                    updateMessage("Opened sublist: ${selectedItem.text ?: "Unknown"}")
                } ?: run {
                    updateMessage("No sublist available for this item")
                }
            }
            Actions.CLOSE_SUBLIST -> {
                if (currentList != defaultItems) {
                    currentList = defaultItems
                    selectedIndex = 0
                    inSublist = false
                    updateMessage("")
                } else {
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
        }
    }

    fun speakStatus() {
        Log.d("TTS", "Speaking Status")
        // Format time
        val sdf = SimpleDateFormat("h:mm a", Locale.US)
        val currentTime = sdf.format(Date())

        // Read battery percentage via BatteryManager first, then fallback to ACTION_BATTERY_CHANGED
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
                status?.let { intent ->
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (level >= 0 && scale > 0) {
                        batteryPercent = (level * 100) / scale
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read battery in speakStatus", e)
        }

        // Compose speak text
        val text = if (batteryPercent != null) {
            "It's $currentTime with $batteryPercent percent"
        } else {
            "It's $currentTime. Battery percentage unknown"
        }
        ttsHelper.speak(text)
        updateMessage("Spoken status: $currentTime${batteryPercent?.let { " with $it%" } ?: " (battery unknown)"}")
    }

    private fun toggleItem(index: Int) {
        val list = currentList.toMutableList()
        val item = list.getOrNull(index) ?: return
        if (item.kind != Kind.TOGGLE) return
        list[index] = item.copy(isOn = !item.isOn)
        currentList = list
    }

    private fun updateMessage(newMessage: String) {
        message = newMessage
    }

    fun shutdown() {
        ttsHelper.shutdown()
    }

    override fun onCleared() {
        super.onCleared()
        // ViewModel is being destroyed for good (not a config change) — clean up TTS
        try {
            ttsHelper.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TTS in onCleared", e)
        }
    }

    // Expose TTS via ViewModel so Activities/fragments can request speech without creating their own TTS.
    fun speak(text: String) {
        ttsHelper.speak(text)
    }

    // Public helpers used by Activity
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
