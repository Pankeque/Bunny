package com.bunny.backend.mapper

import com.bunny.backend.dto.*
import com.bunny.backend.model.*

fun UserEntity.toResponse() = UserResponse(
    id = id.value,
    username = username,
    avatarUrl = avatarUrl,
    theme = theme
)

fun ServerEntity.toResponse() = ServerResponse(
    id = id.value,
    name = name,
    iconUrl = iconUrl,
    ownerId = ownerId.value,
    inviteCode = inviteCode,
    createdAt = createdAt.toString()
)

fun ChannelEntity.toResponse() = ChannelResponse(
    id = id.value,
    serverId = serverId.value,
    name = name,
    type = type,
    createdAt = createdAt.toString()
)

fun Pair<MessageEntity, UserEntity>.toResponse() = MessageResponse(
    id = first.id.value,
    channelId = first.channelId.value,
    userId = first.userId.value,
    user = second.toResponse(),
    content = first.content,
    createdAt = first.createdAt.toString()
)

fun RoleEntity.toResponse() = RoleResponse(
    id = id.value,
    serverId = serverId.value,
    name = name,
    color = color,
    createdAt = createdAt.toString()
)
