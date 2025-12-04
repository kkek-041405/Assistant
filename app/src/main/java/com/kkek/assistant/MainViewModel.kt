package com.kkek.assistant

import android.app.Application
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kkek.assistant.core.Command
import com.kkek.assistant.core.CommandQueue
import com.kkek.assistant.data.AssistantRepository
import com.kkek.assistant.data.repository.ContactRepository
import com.kkek.assistant.domain.model.ToolResult
import com.kkek.assistant.domain.store.ToolStore
import com.kkek.assistant.domain.usecase.ToolExecutor
import com.kkek.assistant.model.ListItem
import com.kkek.assistant.model.ToolAction
import com.kkek.assistant.music.SpotifyHelper
import com.kkek.assistant.repository.FirebaseRepository
import com.kkek.assistant.service.BubbleService
import com.kkek.assistant.System.notification.NotificationListener
import com.kkek.assistant.states.AppsState
import com.kkek.assistant.states.ContactsState
import com.kkek.assistant.states.DefaultState
import com.kkek.assistant.states.InCallState
import com.kkek.assistant.states.SpotifyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: AssistantRepository,
    private val contactRepository: ContactRepository,
    private val toolStore: ToolStore,
    private val spotifyHelper: SpotifyHelper,
    private val application: Application
) : ViewModel() {

    internal val TAG = "MainViewModel"
    private val toolExecutor = ToolExecutor(application)

    private val telephonyManager by lazy {
        application.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    private val audioManager by lazy {
        application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    var message by mutableStateOf("")
        private set

    var isDefaultDialer by mutableStateOf(true)
        private set

    var showDialerPrompt by mutableStateOf(false)

    var permissionRequest by mutableStateOf<Intent?>(null)
        private set

    var currentList by mutableStateOf<List<ListItem>>(emptyList())
    var selectedIndex by mutableStateOf(0)

    val batteryPercent = repository.batteryPercent.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = -1
    )

    val notifications = repository.notifications.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val contacts = contactRepository.getContacts().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    var isShowingContacts by mutableStateOf(false)
        private set

    init {
        updateDialerRoleState()
        if (!isDefaultDialer) showDialerPrompt = true
        repository.updateBatteryPercentage()
        checkNotificationListenerPermission()

        // 1. Upload capabilities to Firebase so Next.js knows what to show
        val allTools = toolStore.getAllTools()
        FirebaseRepository.uploadAvailableCommands(allTools)

        // 2. Start listening for incoming commands
        FirebaseRepository.listenForCommands { toolId, params ->
            viewModelScope.launch {
                val action = ToolAction(toolId, params)
                executeAiTool(action)
            }
        }
        observeContactUpdates()
    }

    private fun listenForCommands() {
        CommandQueue.commands.onEach { command ->
            when (command) {
                is Command.ExecutePrimaryAction -> onNextLongPress()
            }
        }.launchIn(viewModelScope)
    }

    private fun observeContactUpdates() {
        contacts.onEach { newContacts ->
            if (isShowingContacts) {
                val oldSelectedIndex = selectedIndex
                currentList = ContactsState.build(newContacts)
                if (oldSelectedIndex < newContacts.size) {
                    selectedIndex = oldSelectedIndex
                } else {
                    selectedIndex = 0
                }
            }
        }.launchIn(viewModelScope)
    }

    fun onAppOpen() {
        val isInCall = telephonyManager.callState != TelephonyManager.CALL_STATE_IDLE
        val isMusicPlaying = audioManager.isMusicActive

        when {
            isInCall -> showInCallList()
            isMusicPlaying -> showSpotifyList()
            else -> showDefaultList()
        }
    }

    // --- UI Action Handling ---

    fun onNext() = executeToolActions(currentList.getOrNull(selectedIndex)?.shortNext ?: listOf(ToolAction("next_item")))
    fun onPrevious() = executeToolActions(currentList.getOrNull(selectedIndex)?.shortPrevious ?: listOf(ToolAction("previous_item")))
    fun onNextLongPress() = executeToolActions(currentList.getOrNull(selectedIndex)?.longNext ?: emptyList())
    fun onPreviousLongPress() = executeToolActions(currentList.getOrNull(selectedIndex)?.longPrevious ?: listOf(ToolAction("show_default_list")))
    fun onNextDoublePress() = executeToolActions(currentList.getOrNull(selectedIndex)?.doubleNext ?: emptyList())
    fun onPreviousDoublePress() = executeToolActions(currentList.getOrNull(selectedIndex)?.doublePrevious ?: emptyList())

    private fun executeToolActions(actions: List<ToolAction>) {
        actions.forEach { action ->
            viewModelScope.launch {
                // Handle UI-specific pseudo-tools
                when (action.toolId) {
                    "show_default_list" -> showDefaultList()
                    "show_in_call_list" -> showInCallList()
                    "show_spotify_list" -> showSpotifyList()
                    "show_apps_list" -> showAppsList()
                    "show_contacts_list" -> showContactsList()
                    "next_item" -> selectNextItem()
                    "previous_item" -> selectPreviousItem()
                    "start_bubble_service" -> startBubbleService()
                    else -> executeAiTool(action) // Execute actual AI tool
                }
            }
        }
    }


    private suspend fun executeAiTool(action: ToolAction) {
        val tool = toolStore.getTool(action.toolId)
        if (tool == null) {
            setUserMessage("Tool not found: ${action.toolId}")
            return
        }

        val result = toolExecutor.execute(tool, action.params, viewModelScope)

        when (result) {
            is ToolResult.Success -> {
                result.data["speechOutput"]?.let { if (it is String) executeAiTool(ToolAction("tts", mapOf("text" to it))) }
                result.data["status"]?.let { if (it is String) setUserMessage(it) }
            }
            is ToolResult.Failure -> setUserMessage("Error: ${result.reason}")
        }
    }

    private fun showDefaultList() {
        isShowingContacts = false
        currentList = DefaultState.build()
        selectedIndex = 0
        setUserMessage("")
    }

    private fun showInCallList() {
        isShowingContacts = false
        currentList = InCallState.build()
        selectedIndex = 0
        setUserMessage("In-call actions")
    }

    private fun showSpotifyList() {
        isShowingContacts = false
        currentList = SpotifyState.build(spotifyHelper.isConnected.value)
        selectedIndex = 0
        setUserMessage("Opened Spotify controls")
    }

    private fun showAppsList() {
        isShowingContacts = false
        viewModelScope.launch {
            currentList = AppsState.build(application)
            selectedIndex = 0
            setUserMessage("Opened apps")
        }
    }

    private fun showContactsList() {
        isShowingContacts = true
        currentList = ContactsState.build(contacts.value)
        selectedIndex = 0
        setUserMessage("Opened contacts")
        viewModelScope.launch {
            refreshContacts()
        }
    }

    internal fun setUserMessage(newMessage: String) {
        message = newMessage
    }

    private fun selectNextItem() {
        if (currentList.isNotEmpty()) {
            selectedIndex = (selectedIndex + 1) % currentList.size
        }
    }

    private fun selectPreviousItem() {
        if (currentList.isNotEmpty()) {
            selectedIndex = (selectedIndex - 1 + currentList.size) % currentList.size
        }
    }

    private suspend fun refreshContacts() {
        contactRepository.refreshContacts()
    }

    // --- Permissions and System State ---

    fun onPermissionRequestHandled() {
        permissionRequest = null
    }

    fun updateDialerRoleState() {
        isDefaultDialer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = application.getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) ?: false
        } else {
            val telecom = application.getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
            telecom.defaultDialerPackage == application.packageName
        }
    }

    private fun checkNotificationListenerPermission() {
        val cn = ComponentName(application, NotificationListener::class.java)
        val flat = Settings.Secure.getString(application.contentResolver, "enabled_notification_listeners")
        if (flat == null || !flat.contains(cn.flattenToString())) {
            permissionRequest = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
    }

    private fun startBubbleService() {
        val intent = Intent(application, BubbleService::class.java)
        application.startService(intent)
    }

    override fun onCleared() {
        super.onCleared()
        spotifyHelper.disconnect()
    }
}
