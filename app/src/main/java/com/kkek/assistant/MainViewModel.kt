package com.kkek.assistant

import android.app.Application
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
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
import com.kkek.assistant.notification.NotificationHelper as NotificationHelperNew
import com.kkek.assistant.contacts.ContactHelper as ContactsHelperNew
import com.kkek.assistant.tts.TTSHelper as TTSHelperNew
import com.kkek.assistant.telecom.CallHelper
import com.kkek.assistant.telecom.CallService
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

    var showDialerPrompt by mutableStateOf(false)

    var inSublist by mutableStateOf(false)
        private set

    // In-app custom contacts (used instead of querying device contacts)
    private val appContacts = listOf(
        ListItem.ContactItem("Dad", "+919391632589"),
        ListItem.ContactItem("MoM", "+916301638687"),
        ListItem.ContactItem("Charlie", "+10000000003")
    )

    private val defaultItems: List<ListItem> = listOf(
        ListItem.SimpleItem("Tell time"),
        ListItem.ToggleItem("Item #2 (Toggle)"),
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
                    ListItem.SimpleItem("Answer"),
                    ListItem.SimpleItem("Reject")
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
                    ListItem.SimpleItem("Hang up"),
                    ListItem.ToggleItem("Speaker", speakerOn),
                    ListItem.ToggleItem("Mute", muted)
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

    fun onPrevious() {
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

    fun onNextLongPress() {
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
                callHelper?.placeCall(selectedItem.phoneNumber)
                updateMessage("Calling ${selectedItem.name}")
            }
            is ListItem.SublistItem -> {
                currentList = selectedItem.sublist
                selectedIndex = 0
                VolumeKeyListener.reset()
                inSublist = true
                updateMessage("Opened ${selectedItem.text}")
            }
            is ListItem.SimpleItem -> {
                when (selectedItem.text) {
                    "Tell time" -> speakTime()
                    "Answer" -> CallService.call.value?.answer(0)
                    "Reject" -> CallService.call.value?.reject(false, "")
                    "Hang up" -> CallService.call.value?.disconnect()
                    else -> { }
                }
            }
            is ListItem.ToggleItem -> {
                when (selectedItem.text) {
                    "Speaker" -> CallService.toggleSpeaker()
                    "Mute" -> CallService.toggleMute()
                    else -> {
                        toggleItem(selectedIndex)
                        val updated = currentList[selectedIndex] as ListItem.ToggleItem
                        updateMessage("${updated.text} is now ${if (updated.isOn) "ON" else "OFF"}")
                    }
                }
            }
        }
    }

    fun onPreviousLongPress() {
        if (currentList != defaultItems) {
            currentList = defaultItems
            selectedIndex = 0
            inSublist = false
            updateMessage("")
        } else {
            updateMessage("")
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

    fun shutdown() {
        ttsHelper.shutdown()
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
