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
import com.bunny.domain.repository.DirectMessageRepository
import com.bunny.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DmInboxViewModel @Inject constructor(
    private val dmRepository: DirectMessageRepository,
    private val socketService: SocketService,
    private val unreadStore: UnreadStore,
    private val prefs: SharedPreferences
) : ViewModel() {

    private val _conversations = MutableStateFlow<List<DirectConversation>>(emptyList())
    val conversations: StateFlow<List<DirectConversation>> = _conversations.asStateFlow()

    private val _onlineUserIds = MutableStateFlow<Set<Int>>(emptySet())
    val onlineUserIds: StateFlow<Set<Int>> = _onlineUserIds.asStateFlow()

    private val _totalUnread = MutableStateFlow(0)
    val totalUnread: StateFlow<Int> = _totalUnread.asStateFlow()

    private val _unreadByConversation = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val unreadByConversation: StateFlow<Map<Int, Int>> = _unreadByConversation.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var socketJob: kotlinx.coroutines.Job? = null

    init {
        load()
        ensureSocketConnected()
        collectSocketEvents()
        viewModelScope.launch {
            unreadStore.total.collect { _totalUnread.value = it }
        }
        viewModelScope.launch {
            unreadStore.byConversation.collect { _unreadByConversation.value = it }
        }
    }

    fun load() {
        viewModelScope.launch {
            dmRepository.getConversations()
                .onSuccess { convs ->
                    _conversations.value = convs
                }
                .onFailure { showError(it) }
        }
    }

    fun openConversation(userId: Int, onResult: (Result<DirectConversation>) -> Unit = {}) {
        viewModelScope.launch {
            val result = dmRepository.getOrCreateConversation(userId)
            result.onSuccess { onResult(Result.success(it)) }
                .onFailure { e -> showError(e); onResult(Result.failure(e)) }
        }
    }

    fun markRead(conversationId: Int) {
        unreadStore.markRead(conversationId)
    }

    fun unreadCountFor(conversationId: Int): Int = unreadStore.countFor(conversationId)

    fun dismissError() {
        _errorMessage.value = null
    }

    private fun currentUserId(): Int = prefs.getInt(Constants.KEY_USER_ID, 0)

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
                    is SocketEvent.DirectMessageReceived -> {
                        if (event.message.senderId != currentUserId()) {
                            unreadStore.increment(event.message.conversationId)
                        }
                        load()
                    }
                    is SocketEvent.FriendPresenceChanged -> {
                        val updated = _onlineUserIds.value.toMutableSet()
                        if (event.online) updated.add(event.userId) else updated.remove(event.userId)
                        _onlineUserIds.value = updated
                    }
                    is SocketEvent.FriendPresenceSnapshot -> {
                        _onlineUserIds.value = event.onlineUserIds.toSet()
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun showError(e: Throwable) {
        Log.w("DmInbox", "Error", e)
        _errorMessage.value = e.message ?: "Something went wrong"
    }

    override fun onCleared() {
        super.onCleared()
        socketJob?.cancel()
    }
}
