package com.bunny.data.remote.mapper

import com.bunny.data.remote.dto.AuthResponseDto
import com.bunny.data.remote.dto.ChannelDto
import com.bunny.data.remote.dto.CreateRoleRequestDto
import com.bunny.data.remote.dto.MessageDto
import com.bunny.data.remote.dto.RoleDto
import com.bunny.data.remote.dto.ServerDto
import com.bunny.data.remote.dto.UserDto
import com.bunny.domain.model.AuthResponse
import com.bunny.domain.model.Channel
import com.bunny.domain.model.Message
import com.bunny.domain.model.Role
import com.bunny.domain.model.Server
import com.bunny.domain.model.User

fun UserDto.toDomain() = User(id, username, avatarUrl, theme ?: "dark")
fun ServerDto.toDomain() = Server(id, name, iconUrl, ownerId, inviteCode, createdAt)
fun ChannelDto.toDomain() = Channel(id, serverId, name, type, createdAt)
fun MessageDto.toDomain() = Message(id, channelId, userId, user?.toDomain(), content, createdAt)
fun AuthResponseDto.toDomain() = AuthResponse(accessToken, refreshToken, user.toDomain())
fun RoleDto.toDomain() = Role(id, serverId, name, color, createdAt)
