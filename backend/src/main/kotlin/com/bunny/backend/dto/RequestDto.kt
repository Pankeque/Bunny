package com.bunny.backend.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class RegisterRequest(
    val username: String,
    val password: String
)

@Serializable
data class CreateServerRequest(
    val name: String,
    val iconUrl: String? = null
)

@Serializable
data class JoinServerRequest(
    val inviteCode: String
)

@Serializable
data class CreateChannelRequest(
    val name: String,
    val type: String = "text"
)

@Serializable
data class CreateRoleRequest(
    val name: String,
    val color: String = "#99AAB5"
)

@Serializable
data class SendMessageRequest(
    val content: String
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

@Serializable
data class UpdateUserRequest(
    val username: String? = null,
    val avatarUrl: String? = null,
    val theme: String? = null
)
