package com.bunny.ui.chat

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bunny.data.remote.socket.SocketService
import com.bunny.domain.model.Message
import com.bunny.domain.model.User
import com.bunny.domain.repository.MessageRepository
import com.bunny.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    private var currentChannelId: Int? = null
    private val knownMessageIds = mutableSetOf<Int>()
    private var socketJob: Job? = null

    fun loadMessages(channelId: Int, onResult: (Result<List<Message>>) -> Unit = {}) {
        currentChannelId = channelId
        viewModelScope.launch {
            val result = messageRepository.getMessages(channelId)
            if (result.isSuccess) {
                val loaded = result.getOrDefault(emptyList())
                knownMessageIds.clear()
                val filtered = loaded.filter { knownMessageIds.add(it.id) }
                _messages.value = filtered
            }
            onResult(result)
        }
    }

    fun sendMessage(channelId: Int, content: String, onResult: (Result<Message>) -> Unit = {}) {
        viewModelScope.launch {
            val idempotencyKey = "msg_${System.currentTimeMillis()}_${(0..1000).random()}"
            socketService.sendMessage(channelId, content, idempotencyKey)

            val savedResult = messageRepository.sendMessage(channelId, content)
            savedResult.onSuccess { message ->
                if (knownMessageIds.add(message.id)) {
                    _messages.value = _messages.value + message
                }
            }
            onResult(savedResult)
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
        }
        socketJob?.cancel()
        socketJob = viewModelScope.launch {
            socketService.incoming.collect { message ->
                if (message.channelId == channelId && knownMessageIds.add(message.id)) {
                    _messages.value = _messages.value + message
                }
            }
        }
        socketService.joinChannel(channelId)
        currentChannelId = channelId
    }

    override fun onCleared() {
        super.onCleared()
        socketJob?.cancel()
    }
}