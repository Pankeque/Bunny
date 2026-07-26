package com.bunny.backend.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserResponse(
    val id: Int,
    val username: String,
    val avatarUrl: String? = null,
    val theme: String = "dark"
)

@Serializable
data class ServerResponse(
    val id: Int,
    val name: String,
    val iconUrl: String? = null,
    val ownerId: Int,
    val inviteCode: String,
    val createdAt: String
)

@Serializable
data class ChannelResponse(
    val id: Int,
    val serverId: Int,
    val name: String,
    val type: String = "text",
    val createdAt: String
)

@Serializable
data class MessageResponse(
    val id: Int,
    val channelId: Int,
    val userId: Int,
    val user: UserResponse? = null,
    val content: String,
    val createdAt: String
)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserResponse
)

@Serializable
data class RoleResponse(
    val id: Int,
    val serverId: Int,
    val name: String,
    val color: String = "#99AAB5",
    val createdAt: String
)

@Serializable
data class UpdateServerRequest(
    val name: String? = null,
    val iconUrl: String? = null
)

@Serializable
data class UpdateChannelRequest(
    val name: String? = null,
    val type: String? = null
)
