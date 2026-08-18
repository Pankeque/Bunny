package com.bunny.ui.dms

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bunny.data.local.UnreadStore
import com.bunny.data.remote.socket.ConnectionState
import com.bunny.data.remote.socket.SocketEvent
import com.bunny.data.remote.socket.SocketService
import com.bunny.domain.model.DirectConversation
import com.bunny.domain.model.DirectMessage
import com.bunny.domain.repository.DirectMessageRepository
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
class DmChatViewModel @Inject constructor(
    private val dmRepository: DirectMessageRepository,
    private val socketService: SocketService,
    private val unreadStore: UnreadStore,
    private val prefs: SharedPreferences
) : ViewModel() {

    private val _conversation = MutableStateFlow<DirectConversation?>(null)
    val conversation: StateFlow<DirectConversation?> = _conversation.asStateFlow()

    private val _messages = MutableStateFlow<List<DirectMessage>>(emptyList())
    val messages: StateFlow<List<DirectMessage>> = _messages.asStateFlow()

    private val _messageStatuses = MutableStateFlow<Map<Int, MessageStatus>>(emptyMap())
    val messageStatuses: StateFlow<Map<Int, MessageStatus>> = _messageStatuses.asStateFlow()

    private val _peerTyping = MutableStateFlow(false)
    val peerTyping: StateFlow<Boolean> = _peerTyping.asStateFlow()

    private val _peerOnline = MutableStateFlow(false)
    val peerOnline: StateFlow<Boolean> = _peerOnline.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var currentConversationId: Int? = null
    private val knownMessageIds = mutableSetOf<Int>()
    private val nonceToTempId = mutableMapOf<String, Int>()
    private val sendTimeoutJobs = mutableMapOf<Int, Job>()
    private var tempIdCounter = 0
    private var socketJob: Job? = null
    private var stateJob: Job? = null
    private var typingJob: Job? = null
    private var peerId: Int? = null

    private val sendTimeoutMs = 12_000L
    private val typingDebounceMs = 1_200L

    fun loadConversation(conversationId: Int, userId: Int) {
        currentConversationId = conversationId
        peerId = userId
        unreadStore.activeConversationId = conversationId
        unreadStore.markRead(conversationId)
        ensureSocketConnected()
        viewModelScope.launch {
            dmRepository.getMessages(conversationId)
                .onSuccess { loaded ->
                    knownMessageIds.clear()
                    _messages.value = loaded.filter { knownMessageIds.add(it.id) }
                    val statuses = _messageStatuses.value.toMutableMap()
                    loaded.forEach { statuses.remove(it.id) }
                    _messageStatuses.value = statuses
                }
                .onFailure { showError(it) }
            dmRepository.getOrCreateConversation(userId)
                .onSuccess { _conversation.value = it }
                .onFailure { showError(it) }
        }
        collectSocketEvents()
    }

    fun sendMessage(content: String) {
        val conversationId = currentConversationId ?: return
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return

        val nonce = "dm_${System.currentTimeMillis()}_${(0..1000).random()}"
        val tempId = --tempIdCounter
        val optimistic = DirectMessage(
            id = tempId,
            conversationId = conversationId,
            senderId = prefs.getInt(Constants.KEY_USER_ID, 0),
            content = trimmed,
            createdAt = ""
        )
        nonceToTempId[nonce] = tempId
        _messages.value = _messages.value + optimistic
        _messageStatuses.value = _messageStatuses.value + (tempId to MessageStatus.Sending)
        socketService.sendDirectMessage(conversationId, trimmed, nonce)
        scheduleSendTimeout(tempId)
    }

    fun retryMessage(message: DirectMessage) {
        _messages.value = _messages.value.filterNot { it.id == message.id }
        _messageStatuses.value = _messageStatuses.value - message.id
        sendTimeoutJobs.remove(message.id)?.cancel()
        sendMessage(message.content)
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

    fun notifyTyping(isTyping: Boolean) {
        val conversationId = currentConversationId ?: return
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            socketService.sendTyping(conversationId, isTyping)
            if (isTyping) {
                delay(typingDebounceMs)
                socketService.sendTyping(conversationId, false)
            }
        }
    }

    fun markRead(conversationId: Int) {
        unreadStore.markRead(conversationId)
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    private fun ensureSocketConnected() {
        if (socketService.connectionState.value is ConnectionState.Connected) return
        val token = prefs.getString(Constants.KEY_ACCESS_TOKEN, null)
        if (!token.isNullOrBlank()) {
            socketService.connect(token)
        }
    }

    private fun collectSocketEvents() {
        socketJob?.cancel()
        socketJob = viewModelScope.launch {
            socketService.incoming.collect { event ->
                when (event) {
                    is SocketEvent.DirectMessageReceived -> onMessageReceived(event)
                    is SocketEvent.DirectTyping -> {
                        if (event.conversationId == currentConversationId) {
                            _peerTyping.value = event.isTyping
                        }
                    }
                    is SocketEvent.FriendPresenceChanged -> {
                        if (event.userId == peerId) {
                            _peerOnline.value = event.online
                        }
                    }
                    is SocketEvent.FriendPresenceSnapshot -> {
                        _peerOnline.value = event.onlineUserIds.contains(peerId)
                    }
                    is SocketEvent.GatewayError -> {
                        Log.w("DmChat", "Gateway error: ${event.code} / ${event.message}")
                    }
                    else -> Unit
                }
            }
        }
        stateJob?.cancel()
        stateJob = viewModelScope.launch {
            socketService.connectionState.collect { _connectionState.value = it }
        }
        socketService.requestPresence()
    }

    private fun onMessageReceived(event: SocketEvent.DirectMessageReceived) {
        val conversationId = currentConversationId
        if (conversationId == null || event.message.conversationId != conversationId) return
        unreadStore.markRead(conversationId)

        val tempId = event.nonce?.let { nonceToTempId.remove(it) }
        if (tempId != null) {
            _messages.value = _messages.value.filterNot { it.id == tempId }
            sendTimeoutJobs.remove(tempId)?.cancel()
            val statuses = _messageStatuses.value.toMutableMap()
            statuses.remove(tempId)
            if (knownMessageIds.add(event.message.id)) {
                _messages.value = _messages.value + event.message
                statuses[event.message.id] = MessageStatus.Delivered
            }
            _messageStatuses.value = statuses
            return
        }
        if (knownMessageIds.add(event.message.id)) {
            _messages.value = _messages.value + event.message
        }
    }

    private fun showError(e: Throwable) {
        Log.w("DmChat", "Error", e)
        _errorMessage.value = e.message ?: "Something went wrong"
    }

    override fun onCleared() {
        super.onCleared()
        socketJob?.cancel()
        stateJob?.cancel()
        typingJob?.cancel()
        sendTimeoutJobs.values.forEach { it.cancel() }
        sendTimeoutJobs.clear()
        if (unreadStore.activeConversationId == currentConversationId) {
            unreadStore.activeConversationId = null
        }
        currentConversationId?.let { socketService.sendTyping(it, false) }
    }
}
