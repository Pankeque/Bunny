package com.bunny.ui.chat

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bunny.data.remote.socket.ConnectionState
import com.bunny.data.remote.socket.SocketEvent
import com.bunny.data.remote.socket.SocketService
import com.bunny.domain.model.Message
import com.bunny.domain.model.User
import com.bunny.domain.repository.MessageRepository
import com.bunny.ui.common.MessageStatus
import com.bunny.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val socketService: SocketService,
    private val prefs: SharedPreferences
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isInitialLoading = MutableStateFlow(true)
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading.asStateFlow()

    private val _initialError = MutableStateFlow<String?>(null)
    val initialError: StateFlow<String?> = _initialError.asStateFlow()

    private val _isLoadingOlder = MutableStateFlow(false)
    val isLoadingOlder: StateFlow<Boolean> = _isLoadingOlder.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    /** Incremented whenever a new message is appended at the bottom of the list. */
    private val _bottomPoke = MutableStateFlow(0)
    val bottomPoke: StateFlow<Int> = _bottomPoke.asStateFlow()

    private val _messageStatuses = MutableStateFlow<Map<Int, MessageStatus>>(emptyMap())
    val messageStatuses: StateFlow<Map<Int, MessageStatus>> = _messageStatuses.asStateFlow()

    private val _onlineUserIds = MutableStateFlow<Set<Int>>(emptySet())
    val onlineUserIds: StateFlow<Set<Int>> = _onlineUserIds.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var currentChannelId: Int? = null
    private val knownMessageIds = mutableSetOf<Int>()
    private val nonceToTempId = mutableMapOf<String, Int>()
    private val sendTimeoutJobs = mutableMapOf<Int, Job>()
    private var tempIdCounter = 0
    private var socketJob: Job? = null
    private var stateJob: Job? = null

    private val sendTimeoutMs = 12_000L
    private val pageSize = 50
    private var currentPage = 0

    fun loadMessages(channelId: Int, onResult: (Result<List<Message>>) -> Unit = {}) {
        currentChannelId = channelId
        viewModelScope.launch {
            _isInitialLoading.value = true
            _initialError.value = null
            val result = messageRepository.getMessages(channelId, 1, pageSize)
            if (result.isSuccess) {
                val loaded = result.getOrDefault(emptyList())
                currentPage = 1
                knownMessageIds.clear()
                _messages.value = loaded.filter { knownMessageIds.add(it.id) }
                _hasMore.value = loaded.size >= pageSize
                val statuses = _messageStatuses.value.toMutableMap()
                loaded.forEach { statuses.remove(it.id) }
                _messageStatuses.value = statuses
            } else {
                _initialError.value = result.exceptionOrNull()?.message ?: "Failed to load messages"
            }
            _isInitialLoading.value = false
            onResult(result)
        }
    }

    /** Loads the next (older) page and prepends it to the list. */
    fun loadOlderMessages() {
        val channelId = currentChannelId ?: return
        if (_isLoadingOlder.value || !_hasMore.value || _isInitialLoading.value) return
        viewModelScope.launch {
            _isLoadingOlder.value = true
            val result = messageRepository.getMessages(channelId, currentPage + 1, pageSize)
            result.onSuccess { older ->
                val known = _messages.value.mapTo(mutableSetOf()) { it.id }
                val toPrepend = older.filterNot { known.contains(it.id) }
                if (toPrepend.isNotEmpty()) {
                    knownMessageIds.addAll(toPrepend.map { it.id })
                    _messages.value = toPrepend + _messages.value
                    currentPage += 1
                }
                if (older.size < pageSize) _hasMore.value = false
            }
            _isLoadingOlder.value = false
        }
    }

    fun sendMessage(channelId: Int, content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return

        val nonce = "msg_${System.currentTimeMillis()}_${(0..1000).random()}"
        val tempId = --tempIdCounter
        val optimistic = Message(
            id = tempId,
            channelId = channelId,
            userId = prefs.getInt(Constants.KEY_USER_ID, 0),
            content = trimmed,
            createdAt = ""
        )
        nonceToTempId[nonce] = tempId
        _messages.value = _messages.value + optimistic
        _messageStatuses.value = _messageStatuses.value + (tempId to MessageStatus.Sending)
        _bottomPoke.value += 1
        socketService.sendMessage(channelId, trimmed, nonce)
        scheduleSendTimeout(tempId)
    }

    fun retryMessage(channelId: Int, message: Message) {
        _messages.value = _messages.value.filterNot { it.id == message.id }
        _messageStatuses.value = _messageStatuses.value - message.id
        sendTimeoutJobs.remove(message.id)?.cancel()
        sendMessage(channelId, message.content)
    }

    private fun scheduleSendTimeout(tempId: Int) {
        sendTimeoutJobs.remove(tempId)?.cancel()
        sendTimeoutJobs[tempId] = viewModelScope.launch {
            delay(sendTimeoutMs)
            if (_messageStatuses.value[tempId] == MessageStatus.Sending) {
                _messageStatuses.value = _messageStatuses.value + (tempId to MessageStatus.Failed)
            }
        }
    }

    fun currentUser(onResult: (User) -> Unit = {}) {
        viewModelScope.launch {
            val userId = prefs.getInt(Constants.KEY_USER_ID, 0)
            val username = prefs.getString(Constants.KEY_USERNAME, "") ?: ""
            onResult(User(userId, username, null, "dark"))
        }
    }

    fun subscribeToChannel(channelId: Int) {
        currentChannelId?.takeIf { it != channelId }?.let { previous ->
            socketService.leaveChannel(previous)
            _onlineUserIds.value = emptySet()
        }
        currentChannelId = channelId
        ensureSocketConnected()

        socketJob?.cancel()
        socketJob = viewModelScope.launch {
            socketService.incoming.collect { event ->
                when (event) {
                    is SocketEvent.MessageReceived -> onMessageReceived(event, channelId)
                    is SocketEvent.PresenceChanged -> {
                        if (event.channelId == channelId) {
                            val updated = _onlineUserIds.value.toMutableSet()
                            if (event.online) updated.add(event.userId) else updated.remove(event.userId)
                            _onlineUserIds.value = updated
                        }
                    }
                    is SocketEvent.PresenceSnapshot -> {
                        if (event.channelId == channelId) {
                            _onlineUserIds.value = event.onlineUserIds.toSet()
                        }
                    }
                    is SocketEvent.GatewayError -> {
                        Log.w("Chat", "Gateway error: ${event.code} / ${event.message}")
                    }
                    is SocketEvent.FriendRequestReceived,
                    is SocketEvent.FriendRequestAccepted,
                    is SocketEvent.FriendRequestDeclined,
                    is SocketEvent.FriendRequestCancelled,
                    is SocketEvent.FriendRemoved,
                    is SocketEvent.FriendBlocked,
                    is SocketEvent.FriendPresenceChanged,
                    is SocketEvent.FriendPresenceSnapshot,
                    is SocketEvent.DirectMessageReceived,
                    is SocketEvent.DirectTyping -> {
                        // Handled by the friends / direct-message collectors.
                    }
                }
            }
        }

        stateJob?.cancel()
        stateJob = viewModelScope.launch {
            socketService.connectionState.collect { _connectionState.value = it }
        }

        socketService.joinChannel(channelId)
    }

    private fun ensureSocketConnected() {
        if (socketService.connectionState.value is ConnectionState.Connected) return
        val token = prefs.getString(Constants.KEY_ACCESS_TOKEN, null)
        if (!token.isNullOrBlank()) {
            socketService.connect(token)
        }
    }

    private fun onMessageReceived(event: SocketEvent.MessageReceived, channelId: Int) {
        val message = event.message
        if (message.channelId != channelId) return

        val tempId = event.nonce?.let { nonceToTempId.remove(it) }
        if (tempId != null) {
            _messages.value = _messages.value.filterNot { it.id == tempId }
            sendTimeoutJobs.remove(tempId)?.cancel()
            val statuses = _messageStatuses.value.toMutableMap()
            statuses.remove(tempId)
            if (knownMessageIds.add(message.id)) {
                _messages.value = _messages.value + message
                statuses[message.id] = MessageStatus.Delivered
            }
            _messageStatuses.value = statuses
            _bottomPoke.value += 1
            return
        }
        if (knownMessageIds.add(message.id)) {
            _messages.value = _messages.value + message
            _bottomPoke.value += 1
        }
    }

    override fun onCleared() {
        super.onCleared()
        socketJob?.cancel()
        stateJob?.cancel()
        sendTimeoutJobs.values.forEach { it.cancel() }
        sendTimeoutJobs.clear()
        currentChannelId?.let { socketService.leaveChannel(it) }
    }
}
