package com.bunny.domain.model

data class User(
    val id: Int,
    val username: String,
    val avatarUrl: String?,
    val theme: String = "dark"
)

data class Server(
    val id: Int,
    val name: String,
    val iconUrl: String?,
    val ownerId: Int,
    val inviteCode: String,
    val createdAt: String
)

data class Channel(
    val id: Int,
    val serverId: Int,
    val name: String,
    val type: String = "text",
    val createdAt: String
)

data class Message(
    val id: Int,
    val channelId: Int,
    val userId: Int,
    val user: User? = null,
    val content: String,
    val createdAt: String
)

data class AuthRequest(
    val username: String,
    val password: String
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: User
)

data class Role(
    val id: Int,
    val serverId: Int,
    val name: String,
    val color: String = "#99AAB5",
    val createdAt: String
)

data class CreateServerRequest(
    val name: String,
    val iconUrl: String? = null
)

data class CreateChannelRequest(
    val name: String,
    val serverId: Int,
    val type: String = "text"
)

data class SendMessageRequest(
    val channelId: Int,
    val content: String
)

enum class FriendshipStatus { Pending, Accepted, Blocked }

// A person on the friends list. `isIncoming` is true for pending requests
// directed at the current user; `friendshipId` identifies the request row.
data class FriendUser(
    val id: Int,
    val username: String,
    val avatarUrl: String?,
    val status: FriendshipStatus = FriendshipStatus.Pending,
    val isIncoming: Boolean = false,
    val friendshipId: Int = 0
)

data class DirectMessage(
    val id: Int,
    val conversationId: Int,
    val senderId: Int,
    val user: User? = null,
    val content: String,
    val createdAt: String
)

data class DirectConversation(
    val id: Int,
    val user: User,
    val lastMessage: DirectMessage? = null,
    val unreadCount: Int = 0
)
