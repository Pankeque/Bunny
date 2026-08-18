package com.bunny.data.remote.dto

import com.google.gson.annotations.SerializedName

data class UserDto(
    @SerializedName("id") val id: Int,
    @SerializedName("username") val username: String,
    @SerializedName("avatar_url") val avatarUrl: String?,
    @SerializedName("theme") val theme: String? = "dark"
)

data class ServerDto(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("icon_url") val iconUrl: String?,
    @SerializedName("owner_id") val ownerId: Int,
    @SerializedName("invite_code") val inviteCode: String,
    @SerializedName("created_at") val createdAt: String
)

data class ChannelDto(
    @SerializedName("id") val id: Int,
    @SerializedName("server_id") val serverId: Int,
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String? = "text",
    @SerializedName("created_at") val createdAt: String
)

data class MessageDto(
    @SerializedName("id") val id: Int,
    @SerializedName("channel_id") val channelId: Int,
    @SerializedName("user_id") val userId: Int,
    val user: UserDto? = null,
    @SerializedName("content") val content: String,
    @SerializedName("created_at") val createdAt: String
)

data class AuthRequestDto(
    val username: String,
    val password: String
)

data class AuthResponseDto(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    val user: UserDto
)

data class CreateServerRequestDto(
    val name: String,
    @SerializedName("icon_url") val iconUrl: String? = null
)

data class CreateChannelRequestDto(
    val name: String,
    val type: String = "text"
)

data class SendMessageRequestDto(
    val content: String
)

data class JoinServerRequestDto(
    @SerializedName("invite_code") val inviteCode: String
)

data class RefreshTokenRequestDto(
    @SerializedName("refresh_token") val refreshToken: String
)

data class UpdateUserRequestDto(
    val username: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    val theme: String? = null
)

data class UpdateServerRequestDto(
    val name: String? = null,
    @SerializedName("icon_url") val iconUrl: String? = null
)

data class UpdateChannelRequestDto(
    val name: String? = null,
    val type: String? = null
)

data class RoleDto(
    @SerializedName("id") val id: Int,
    @SerializedName("server_id") val serverId: Int,
    val name: String,
    val color: String = "#99AAB5",
    @SerializedName("created_at") val createdAt: String
)

data class CreateRoleRequestDto(
    val name: String,
    val color: String = "#99AAB5"
)

data class FriendshipDto(
    @SerializedName("id") val id: Int,
    val user: UserDto,
    @SerializedName("status") val status: String,
    @SerializedName("initiator_id") val initiatorId: Int,
    @SerializedName("created_at") val createdAt: String
)

data class DirectConversationDto(
    @SerializedName("id") val id: Int,
    val user: UserDto,
    @SerializedName("last_message") val lastMessage: DirectMessageDto? = null
)

data class DirectMessageDto(
    @SerializedName("id") val id: Int,
    @SerializedName("conversation_id") val conversationId: Int,
    @SerializedName("sender_id") val senderId: Int,
    val user: UserDto? = null,
    @SerializedName("content") val content: String,
    @SerializedName("created_at") val createdAt: String
)

data class SendFriendRequestDto(
    val username: String
)

data class SendDirectMessageRequestDto(
    val content: String
)
