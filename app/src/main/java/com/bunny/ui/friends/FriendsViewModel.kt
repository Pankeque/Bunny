package com.bunny.ui.friends

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bunny.data.remote.socket.ConnectionState
import com.bunny.data.remote.socket.SocketEvent
import com.bunny.data.remote.socket.SocketService
import com.bunny.domain.model.FriendUser
import com.bunny.domain.model.FriendshipStatus
import com.bunny.domain.model.User
import com.bunny.domain.repository.FriendRepository
import com.bunny.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val friendRepository: FriendRepository,
    private val socketService: SocketService,
    private val prefs: SharedPreferences
) : ViewModel() {

    private val _friends = MutableStateFlow<List<FriendUser>>(emptyList())
    val friends: StateFlow<List<FriendUser>> = _friends.asStateFlow()

    private val _incoming = MutableStateFlow<List<FriendUser>>(emptyList())
    val incoming: StateFlow<List<FriendUser>> = _incoming.asStateFlow()

    private val _outgoing = MutableStateFlow<List<FriendUser>>(emptyList())
    val outgoing: StateFlow<List<FriendUser>> = _outgoing.asStateFlow()

    private val _onlineUserIds = MutableStateFlow<Set<Int>>(emptySet())
    val onlineUserIds: StateFlow<Set<Int>> = _onlineUserIds.asStateFlow()

    private val _searchResults = MutableStateFlow<List<User>>(emptyList())
    val searchResults: StateFlow<List<User>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var socketJob: kotlinx.coroutines.Job? = null

    init {
        load()
        ensureSocketConnected()
        collectSocketEvents()
    }

    fun load() {
        viewModelScope.launch {
            val friendsResult = friendRepository.getFriends()
            friendsResult.onSuccess { _friends.value = it }.onFailure { showError(it) }

            val incomingResult = friendRepository.getPendingIncoming()
            incomingResult.onSuccess { _incoming.value = it }.onFailure { showError(it) }

            val outgoingResult = friendRepository.getPendingOutgoing()
            outgoingResult.onSuccess { _outgoing.value = it }.onFailure { showError(it) }

            socketService.requestPresence()
        }
    }

    private fun refresh() = load()

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            friendRepository.searchUsers(trimmed)
                .onSuccess { _searchResults.value = it }
                .onFailure { showError(it) }
            _isSearching.value = false
        }
    }

    fun clearSearch() {
        _searchResults.value = emptyList()
    }

    fun sendRequest(username: String, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = friendRepository.sendRequest(username)
            result
                .onSuccess { refresh() }
                .onFailure { showError(it) }
            onResult(result.map { Unit })
        }
    }

    fun acceptRequest(friend: FriendUser) {
        viewModelScope.launch {
            friendRepository.acceptRequest(friend.friendshipId)
                .onSuccess { refresh() }
                .onFailure { showError(it) }
        }
    }

    fun declineRequest(friend: FriendUser) {
        viewModelScope.launch {
            friendRepository.declineRequest(friend.friendshipId)
                .onSuccess { refresh() }
                .onFailure { showError(it) }
        }
    }

    fun cancelRequest(friend: FriendUser) {
        viewModelScope.launch {
            friendRepository.cancelRequest(friend.friendshipId)
                .onSuccess { refresh() }
                .onFailure { showError(it) }
        }
    }

    fun removeFriend(friend: FriendUser) {
        viewModelScope.launch {
            friendRepository.removeFriend(friend.id)
                .onSuccess { refresh() }
                .onFailure { showError(it) }
        }
    }

    fun blockUser(userId: Int) {
        viewModelScope.launch {
            friendRepository.blockUser(userId)
                .onSuccess { refresh() }
                .onFailure { showError(it) }
        }
    }

    fun currentUserId(): Int = prefs.getInt(Constants.KEY_USER_ID, 0)

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
                    is SocketEvent.FriendPresenceChanged -> {
                        val updated = _onlineUserIds.value.toMutableSet()
                        if (event.online) updated.add(event.userId) else updated.remove(event.userId)
                        _onlineUserIds.value = updated
                    }
                    is SocketEvent.FriendPresenceSnapshot -> {
                        _onlineUserIds.value = event.onlineUserIds.toSet()
                    }
                    is SocketEvent.FriendRequestReceived,
                    is SocketEvent.FriendRequestAccepted,
                    is SocketEvent.FriendRequestDeclined,
                    is SocketEvent.FriendRequestCancelled,
                    is SocketEvent.FriendRemoved,
                    is SocketEvent.FriendBlocked -> {
                        refresh()
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun showError(e: Throwable) {
        Log.w("Friends", "Error", e)
        _errorMessage.value = e.message ?: "Something went wrong"
    }

    override fun onCleared() {
        super.onCleared()
        socketJob?.cancel()
    }
}
