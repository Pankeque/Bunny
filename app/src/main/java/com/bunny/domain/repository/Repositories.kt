package com.bunny.domain.repository

import com.bunny.domain.model.*

interface AuthRepository {
    suspend fun login(username: String, password: String): Result<AuthResponse>
    suspend fun register(username: String, password: String): Result<AuthResponse>
    suspend fun refreshToken(refreshToken: String): Result<AuthResponse>
    suspend fun logout()
}

interface ServerRepository {
    suspend fun getServers(): Result<List<Server>>
    suspend fun createServer(name: String, iconUrl: String?): Result<Server>
    suspend fun deleteServer(serverId: Int): Result<Unit>
    suspend fun updateServer(serverId: Int, name: String?, iconUrl: String?): Result<Server>
    suspend fun uploadServerIcon(serverId: Int, bytes: ByteArray, mimeType: String): Result<Server>
    suspend fun joinServer(inviteCode: String): Result<Server>
    suspend fun leaveServer(serverId: Int): Result<Unit>
    suspend fun regenerateInviteCode(serverId: Int): Result<String>
}

interface ChannelRepository {
    suspend fun getChannels(serverId: Int): Result<List<Channel>>
    suspend fun createChannel(name: String, serverId: Int, type: String = "text"): Result<Channel>
    suspend fun updateChannel(channelId: Int, name: String?, type: String? = null): Result<Channel>
    suspend fun deleteChannel(channelId: Int): Result<Unit>
}

interface MessageRepository {
    suspend fun getMessages(channelId: Int, page: Int = 1, limit: Int = 50): Result<List<Message>>
    suspend fun sendMessage(channelId: Int, content: String): Result<Message>
}

interface UserRepository {
    suspend fun updateProfile(username: String?, avatarUrl: String?, theme: String?): Result<User>
    suspend fun uploadAvatar(bytes: ByteArray, mimeType: String): Result<User>
    suspend fun getCurrentUser(userId: Int): Result<User?>
}

interface RoleRepository {
    suspend fun getRoles(serverId: Int): Result<List<Role>>
    suspend fun createRole(serverId: Int, name: String, color: String): Result<Role>
    suspend fun deleteRole(roleId: Int): Result<Unit>
}
