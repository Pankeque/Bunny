package com.bunny.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: Int,
    val username: String,
    val avatarUrl: String?,
    val theme: String = "dark"
)

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val iconUrl: String?,
    val ownerId: Int,
    val inviteCode: String,
    val createdAt: String
)

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val id: Int,
    val serverId: Int,
    val name: String,
    val type: String,
    val createdAt: String
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: Int,
    val channelId: Int,
    val userId: Int,
    val content: String,
    val createdAt: String
)

@Entity(tableName = "refresh_tokens")
data class RefreshTokenEntity(
    @PrimaryKey val token: String,
    val userId: Int,
    val expiresAt: String
)
