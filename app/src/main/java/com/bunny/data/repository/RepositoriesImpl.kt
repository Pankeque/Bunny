package com.bunny.data.repository

import android.content.SharedPreferences
import com.bunny.data.local.dao.RefreshTokenDao
import com.bunny.data.local.dao.UserDao
import com.bunny.data.local.dao.ServerDao
import com.bunny.data.local.dao.ChannelDao
import com.bunny.data.local.dao.MessageDao
import com.bunny.data.local.entity.RefreshTokenEntity
import com.bunny.data.local.entity.ServerEntity
import com.bunny.data.local.entity.ChannelEntity
import com.bunny.data.remote.api.BunnyApi
import com.bunny.data.remote.dto.AuthRequestDto
import com.bunny.data.remote.dto.AuthResponseDto
import com.bunny.data.remote.dto.RefreshTokenRequestDto
import com.bunny.data.remote.dto.CreateServerRequestDto
import com.bunny.data.remote.dto.JoinServerRequestDto
import com.bunny.data.remote.dto.UpdateServerRequestDto
import com.bunny.data.remote.dto.CreateChannelRequestDto
import com.bunny.data.remote.dto.UpdateChannelRequestDto
import com.bunny.data.remote.dto.SendMessageRequestDto
import com.bunny.data.remote.dto.UpdateUserRequestDto
import com.bunny.data.remote.dto.RoleDto
import com.bunny.data.remote.dto.CreateRoleRequestDto
import com.bunny.data.remote.mapper.toDomain
import com.bunny.domain.model.AuthResponse
import com.bunny.domain.model.Server
import com.bunny.domain.model.Channel
import com.bunny.domain.model.Message
import com.bunny.domain.model.User
import com.bunny.domain.model.Role
import com.bunny.domain.repository.AuthRepository
import com.bunny.domain.repository.ServerRepository
import com.bunny.domain.repository.ChannelRepository
import com.bunny.domain.repository.MessageRepository
import com.bunny.domain.repository.UserRepository
import com.bunny.domain.repository.RoleRepository
import com.bunny.util.Constants
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: BunnyApi,
    private val refreshTokenDao: RefreshTokenDao,
    private val userDao: UserDao,
    private val prefs: SharedPreferences
) : AuthRepository {
    override suspend fun login(username: String, password: String): Result<AuthResponse> {
        return try {
            val response = api.login(AuthRequestDto(username, password))
            if (response.isSuccessful) {
                val body = response.body()!!
                saveTokens(body)
                Result.success(body.toDomain())
            } else {
                Result.failure(Exception("Login failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun register(username: String, password: String): Result<AuthResponse> {
        return try {
            val response = api.register(AuthRequestDto(username, password))
            if (response.isSuccessful) {
                val body = response.body()!!
                saveTokens(body)
                Result.success(body.toDomain())
            } else {
                Result.failure(Exception("Registration failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun refreshToken(refreshToken: String): Result<AuthResponse> {
        return try {
            val response = api.refreshToken(RefreshTokenRequestDto(refreshToken))
            if (response.isSuccessful) {
                val body = response.body()!!
                saveTokens(body)
                Result.success(body.toDomain())
            } else {
                Result.failure(Exception("Token refresh failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun logout() {
        refreshTokenDao.clearTokens()
        userDao.clearUsers()
        prefs.edit().clear().apply()
    }

    private fun saveTokens(response: AuthResponseDto) {
        prefs.edit()
            .putString(Constants.KEY_ACCESS_TOKEN, response.accessToken)
            .putString(Constants.KEY_REFRESH_TOKEN, response.refreshToken)
            .putInt(Constants.KEY_USER_ID, response.user.id)
            .putString(Constants.KEY_USERNAME, response.user.username)
            .putString(Constants.KEY_THEME, response.user.theme ?: "dark")
            .apply()
        refreshTokenDao.insertToken(
            RefreshTokenEntity(
                token = response.refreshToken,
                userId = response.user.id,
                expiresAt = ""
            )
        )
    }
}

@Singleton
class ServerRepositoryImpl @Inject constructor(
    private val api: BunnyApi,
    private val serverDao: ServerDao
) : ServerRepository {
    override suspend fun getServers(): Result<List<Server>> {
        return try {
            val response = api.getServers()
            if (response.isSuccessful) {
                val servers = response.body()!!.map { it.toDomain() }
                serverDao.insertServers(servers.map { s ->
                    com.bunny.data.local.entity.ServerEntity(
                        id = s.id,
                        name = s.name,
                        iconUrl = s.iconUrl,
                        ownerId = s.ownerId,
                        inviteCode = s.inviteCode,
                        createdAt = s.createdAt
                    )
                })
                Result.success(servers)
            } else {
                Result.failure(Exception("Failed to fetch servers"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createServer(name: String, iconUrl: String?): Result<Server> {
        return try {
            val response = api.createServer(CreateServerRequestDto(name, iconUrl))
            if (response.isSuccessful) {
                Result.success(response.body()!!.toDomain())
            } else {
                Result.failure(Exception("Failed to create server"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteServer(serverId: Int): Result<Unit> {
        return try {
            val response = api.deleteServer(serverId)
            if (response.isSuccessful) {
                serverDao.deleteServer(serverId)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete server"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun joinServer(inviteCode: String): Result<Server> {
        return try {
            val response = api.joinServer(JoinServerRequestDto(inviteCode))
            if (response.isSuccessful) {
                Result.success(response.body()!!.toDomain())
            } else {
                Result.failure(Exception("Failed to join server"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun leaveServer(serverId: Int): Result<Unit> {
        return try {
            val response = api.leaveServer(serverId)
            if (response.isSuccessful) {
                serverDao.deleteServer(serverId)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to leave server"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateServer(serverId: Int, name: String?, iconUrl: String?): Result<Server> {
        return try {
            val response = api.updateServer(serverId, UpdateServerRequestDto(name, iconUrl))
            if (response.isSuccessful) {
                Result.success(response.body()!!.toDomain())
            } else {
                Result.failure(Exception("Failed to update server"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun regenerateInviteCode(serverId: Int): Result<String> {
        return try {
            val response = api.regenerateInviteCode(serverId)
            if (response.isSuccessful) {
                val newCode = response.body()?.get("inviteCode")
                if (newCode != null) {
                    Result.success(newCode)
                } else {
                    Result.failure(Exception("Empty invite code"))
                }
            } else {
                Result.failure(Exception("Failed to regenerate invite code"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

@Singleton
class ChannelRepositoryImpl @Inject constructor(
    private val api: BunnyApi,
    private val channelDao: ChannelDao
) : ChannelRepository {
    override suspend fun getChannels(serverId: Int): Result<List<Channel>> {
        return try {
            val response = api.getChannels(serverId)
            if (response.isSuccessful) {
                val channels = response.body()!!.map { it.toDomain() }
                channelDao.insertChannels(channels.map { c ->
                    com.bunny.data.local.entity.ChannelEntity(
                        id = c.id,
                        serverId = c.serverId,
                        name = c.name,
                        type = c.type,
                        createdAt = c.createdAt
                    )
                })
                Result.success(channels)
            } else {
                Result.failure(Exception("Failed to fetch channels"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createChannel(name: String, serverId: Int, type: String): Result<Channel> {
        return try {
            val response = api.createChannel(serverId, CreateChannelRequestDto(name, type))
            if (response.isSuccessful) {
                Result.success(response.body()!!.toDomain())
            } else {
                Result.failure(Exception("Failed to create channel"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteChannel(channelId: Int): Result<Unit> {
        return try {
            val response = api.deleteChannel(channelId)
            if (response.isSuccessful) {
                channelDao.deleteChannel(channelId)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete channel"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateChannel(channelId: Int, name: String?, type: String?): Result<Channel> {
        return try {
            val response = api.updateChannel(channelId, UpdateChannelRequestDto(name, type))
            if (response.isSuccessful) {
                Result.success(response.body()!!.toDomain())
            } else {
                Result.failure(Exception("Failed to update channel"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val api: BunnyApi,
    private val messageDao: MessageDao
) : MessageRepository {
    override suspend fun getMessages(channelId: Int, page: Int, limit: Int): Result<List<Message>> {
        return try {
            val response = api.getMessages(channelId, page, limit)
            if (response.isSuccessful) {
                val messages = response.body()!!.map { it.toDomain() }
                Result.success(messages)
            } else {
                Result.failure(Exception("Failed to fetch messages"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendMessage(channelId: Int, content: String): Result<Message> {
        return try {
            val response = api.sendMessage(channelId, SendMessageRequestDto(content))
            if (response.isSuccessful) {
                Result.success(response.body()!!.toDomain())
            } else {
                Result.failure(Exception("Failed to send message"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val api: BunnyApi
) : UserRepository {
    override suspend fun updateProfile(username: String?, avatarUrl: String?, theme: String?): Result<User> {
        return try {
            val response = api.updateProfile(UpdateUserRequestDto(username, avatarUrl, theme))
            if (response.isSuccessful) {
                Result.success(response.body()!!.toDomain())
            } else {
                Result.failure(Exception("Failed to update profile"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

@Singleton
class RoleRepositoryImpl @Inject constructor(
    private val api: BunnyApi
) : RoleRepository {
    override suspend fun getRoles(serverId: Int): Result<List<Role>> {
        return try {
            val response = api.getRoles(serverId)
            if (response.isSuccessful) {
                Result.success(response.body()!!.map { it.toDomain() })
            } else {
                Result.failure(Exception("Failed to fetch roles"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createRole(serverId: Int, name: String, color: String): Result<Role> {
        return try {
            val response = api.createRole(serverId, CreateRoleRequestDto(name, color))
            if (response.isSuccessful) {
                Result.success(response.body()!!.toDomain())
            } else {
                Result.failure(Exception("Failed to create role"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteRole(roleId: Int): Result<Unit> {
        return try {
            val response = api.deleteRole(roleId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete role"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
