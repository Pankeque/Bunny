package com.bunny.ui.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bunny.domain.repository.ServerRepository
import com.bunny.domain.repository.RoleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServerViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    private val roleRepository: RoleRepository
) : ViewModel() {

    private val _servers = MutableStateFlow<List<com.bunny.domain.model.Server>>(emptyList())
    val servers: StateFlow<List<com.bunny.domain.model.Server>> = _servers

    fun loadServers(onResult: (Result<List<com.bunny.domain.model.Server>>) -> Unit = {}) {
        viewModelScope.launch {
            val result = serverRepository.getServers()
            if (result.isSuccess) {
                _servers.value = result.getOrDefault(emptyList())
            }
            onResult(result)
        }
    }

    fun createServer(name: String, onResult: (Result<com.bunny.domain.model.Server>) -> Unit = {}) {
        viewModelScope.launch {
            val result = serverRepository.createServer(name, null)
            if (result.isSuccess) {
                loadServers()
            }
            onResult(result)
        }
    }

    fun joinServer(inviteCode: String, onResult: (Result<com.bunny.domain.model.Server>) -> Unit = {}) {
        viewModelScope.launch {
            val result = serverRepository.joinServer(inviteCode)
            if (result.isSuccess) {
                loadServers()
            }
            onResult(result)
        }
    }

    fun leaveServer(serverId: Int, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = serverRepository.leaveServer(serverId)
            if (result.isSuccess) {
                _servers.value = _servers.value.filter { it.id != serverId }
            }
            onResult(result)
        }
    }

    fun deleteServer(serverId: Int, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = serverRepository.deleteServer(serverId)
            if (result.isSuccess) {
                _servers.value = _servers.value.filter { it.id != serverId }
            }
            onResult(result)
        }
    }

    fun regenerateInviteCode(serverId: Int, onResult: (Result<String>) -> Unit = {}) {
        viewModelScope.launch {
            val result = serverRepository.regenerateInviteCode(serverId)
            onResult(result)
        }
    }

    fun updateServer(serverId: Int, name: String?, iconUrl: String?, onResult: (Result<com.bunny.domain.model.Server>) -> Unit = {}) {
        viewModelScope.launch {
            val result = serverRepository.updateServer(serverId, name, iconUrl)
            onResult(result)
        }
    }

    fun uploadServerIcon(serverId: Int, bytes: ByteArray, mimeType: String, onResult: (Result<com.bunny.domain.model.Server>) -> Unit = {}) {
        viewModelScope.launch {
            val result = serverRepository.uploadServerIcon(serverId, bytes, mimeType)
            onResult(result)
        }
    }

    fun loadRoles(serverId: Int, onResult: (Result<List<com.bunny.domain.model.Role>>) -> Unit = {}) {
        viewModelScope.launch {
            val result = roleRepository.getRoles(serverId)
            onResult(result)
        }
    }

    fun createRole(serverId: Int, name: String, color: String, onResult: (Result<com.bunny.domain.model.Role>) -> Unit = {}) {
        viewModelScope.launch {
            val result = roleRepository.createRole(serverId, name, color)
            onResult(result)
        }
    }

    fun deleteRole(roleId: Int, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = roleRepository.deleteRole(roleId)
            onResult(result)
        }
    }
}
