package com.bunny.data.remote.mapper

import com.bunny.data.remote.dto.AuthResponseDto
import com.bunny.data.remote.dto.ChannelDto
import com.bunny.data.remote.dto.CreateRoleRequestDto
import com.bunny.data.remote.dto.DirectConversationDto
import com.bunny.data.remote.dto.DirectMessageDto
import com.bunny.data.remote.dto.FriendshipDto
import com.bunny.data.remote.dto.MessageDto
import com.bunny.data.remote.dto.RoleDto
import com.bunny.data.remote.dto.ServerDto
import com.bunny.data.remote.dto.UserDto
import com.bunny.domain.model.AuthResponse
import com.bunny.domain.model.Channel
import com.bunny.domain.model.DirectConversation
import com.bunny.domain.model.DirectMessage
import com.bunny.domain.model.FriendUser
import com.bunny.domain.model.FriendshipStatus
import com.bunny.domain.model.Message
import com.bunny.domain.model.Role
import com.bunny.domain.model.Server
import com.bunny.domain.model.User

fun UserDto.toDomain() = User(id, username, avatarUrl, theme ?: "dark")
fun ServerDto.toDomain() = Server(id, name, iconUrl, ownerId, inviteCode, createdAt)
fun ChannelDto.toDomain() = Channel(id, serverId, name, type ?: "text", createdAt)
fun MessageDto.toDomain() = Message(id, channelId, userId, user?.toDomain(), content, createdAt, roleName, roleColor)
fun AuthResponseDto.toDomain() = AuthResponse(accessToken, refreshToken, user.toDomain())
fun RoleDto.toDomain() = Role(id, serverId, name, color, createdAt)
fun DirectMessageDto.toDomain() = DirectMessage(id, conversationId, senderId, user?.toDomain(), content, createdAt)
fun DirectConversationDto.toDomain() = DirectConversation(id, user.toDomain(), lastMessage?.toDomain())
fun FriendshipDto.toDomain(isIncoming: Boolean = false): FriendUser {
    val status = when (status) {
        "accepted" -> FriendshipStatus.Accepted
        "blocked" -> FriendshipStatus.Blocked
        else -> FriendshipStatus.Pending
    }
    return FriendUser(
        id = user.id,
        username = user.username,
        avatarUrl = user.avatarUrl,
        status = status,
        isIncoming = isIncoming && status == FriendshipStatus.Pending,
        friendshipId = id
    )
}
