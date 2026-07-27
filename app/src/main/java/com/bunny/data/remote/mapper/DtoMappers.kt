package com.bunny.data.remote.mapper

import com.bunny.data.remote.dto.*
import com.bunny.domain.model.*

fun UserDto.toDomain() = User(id, username, avatarUrl, theme ?: "dark")
fun ServerDto.toDomain() = Server(id, name, iconUrl, ownerId, inviteCode, createdAt)
fun ChannelDto.toDomain() = Channel(id, serverId, name, type, createdAt)
fun MessageDto.toDomain() = Message(id, channelId, userId, user?.toDomain(), content, createdAt)
fun AuthResponseDto.toDomain() = AuthResponse(accessToken, refreshToken, user.toDomain())
fun RoleDto.toDomain() = Role(id, serverId, name, color, createdAt)
