package com.bunny.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bunny.domain.model.Channel
import com.bunny.domain.model.Server
import com.bunny.domain.repository.ChannelRepository
import com.bunny.domain.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChannelViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val serverRepository: ServerRepository
) : ViewModel() {

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels

    fun loadChannels(serverId: Int, onResult: (Result<List<Channel>>) -> Unit = {}) {
        viewModelScope.launch {
            val result = channelRepository.getChannels(serverId)
            if (result.isSuccess) {
                _channels.value = result.getOrDefault(emptyList())
            }
            onResult(result)
        }
    }

    fun createChannel(serverId: Int, name: String, type: String = "text", onResult: (Result<Channel>) -> Unit = {}) {
        viewModelScope.launch {
            val result = channelRepository.createChannel(name, serverId)
            if (result.isSuccess) {
                loadChannels(serverId)
            }
            onResult(result)
        }
    }

    fun deleteChannel(channelId: Int, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = channelRepository.deleteChannel(channelId)
            if (result.isSuccess) {
                _channels.value = _channels.value.filter { it.id != channelId }
            }
            onResult(result)
        }
    }

    fun updateChannel(channelId: Int, name: String?, type: String? = null, onResult: (Result<Channel>) -> Unit = {}) {
        viewModelScope.launch {
            val result = channelRepository.updateChannel(channelId, name, type)
            if (result.isSuccess) {
                _channels.value = _channels.value.map { if (it.id == channelId) result.getOrNull() ?: it else it }
            }
            onResult(result)
        }
    }

    fun getServerName(serverId: Int, onResult: (String) -> Unit = {}) {
        viewModelScope.launch {
            val result = serverRepository.getServers()
            if (result.isSuccess) {
                val server = result.getOrNull()?.find { it.id == serverId }
                onResult(server?.name ?: "")
            } else {
                onResult("")
            }
        }
    }
}
