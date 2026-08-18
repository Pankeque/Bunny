package com.bunny.backend.model

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import io.ktor.server.auth.Principal
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object Users : IntIdTable("users") {
    val passwordHash = varchar("password_hash", 255)
    val username = varchar("username", 50).uniqueIndex()
    val avatarUrl = text("avatar_url").nullable()
    val theme = varchar("theme", 20).default("dark")
    val createdAt = timestamp("created_at").default(Instant.now())
}

object RefreshTokens : IntIdTable("refresh_tokens") {
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val token = varchar("token", 255).uniqueIndex()
    val expiresAt = timestamp("expires_at")
}

object Servers : IntIdTable("servers") {
    val name = varchar("name", 100)
    val iconUrl = text("icon_url").nullable()
    val ownerId = reference("owner_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val inviteCode = varchar("invite_code", 20).uniqueIndex()
    val expiresAt = timestamp("expires_at").nullable()
    val createdAt = timestamp("created_at").default(Instant.now())
}

object ServerMembers : IntIdTable("server_members") {
    val serverId = reference("server_id", Servers.id, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val roleId = integer("role_id").nullable()
    val joinedAt = timestamp("joined_at").default(Instant.now())
    init { uniqueIndex(serverId, userId) }
}

object Roles : IntIdTable("roles") {
    val serverId = reference("server_id", Servers.id, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 50)
    val color = varchar("color", 20).default("#99AAB5")
    val createdAt = timestamp("created_at").default(Instant.now())
    init { uniqueIndex(serverId, name) }
}

object Channels : IntIdTable("channels") {
    val serverId = reference("server_id", Servers.id, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 100)
    val type = varchar("type", 20).default("text")
    val createdAt = timestamp("created_at").default(Instant.now())
}

object Messages : IntIdTable("messages") {
    val channelId = reference("channel_id", Channels.id, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val content = text("content")
    val createdAt = timestamp("created_at").default(Instant.now())
}

// Friendship rows use canonical ordering: user_one is always the lower user id,
// so the unique index prevents duplicate relationships regardless of direction.
object Friendships : IntIdTable("friendships") {
    val userOne = reference("user_one", Users.id, onDelete = ReferenceOption.CASCADE)
    val userTwo = reference("user_two", Users.id, onDelete = ReferenceOption.CASCADE)
    val initiatorId = integer("initiator_id")
    val status = varchar("status", 20).default("pending") // pending | accepted | blocked
    val createdAt = timestamp("created_at").default(Instant.now())
    init { uniqueIndex(userOne, userTwo) }
}

object DirectConversations : IntIdTable("direct_conversations") {
    val userOne = reference("user_one", Users.id, onDelete = ReferenceOption.CASCADE)
    val userTwo = reference("user_two", Users.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at").default(Instant.now())
    init { uniqueIndex(userOne, userTwo) }
}

object DirectMessages : IntIdTable("direct_messages") {
    val conversationId = reference("conversation_id", DirectConversations.id, onDelete = ReferenceOption.CASCADE)
    val senderId = reference("sender_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val content = text("content")
    val createdAt = timestamp("created_at").default(Instant.now())
}

class UserEntity(id: EntityID<Int>) : IntEntity(id), Principal {
    companion object : IntEntityClass<UserEntity>(Users)
    var passwordHash by Users.passwordHash
    var username by Users.username
    var avatarUrl by Users.avatarUrl
    var theme by Users.theme
    var createdAt by Users.createdAt
}

class RefreshTokenEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<RefreshTokenEntity>(RefreshTokens)
    var userId by RefreshTokens.userId
    var token by RefreshTokens.token
    var expiresAt by RefreshTokens.expiresAt
}

class ServerEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ServerEntity>(Servers)
    var name by Servers.name
    var iconUrl by Servers.iconUrl
    var ownerId by Servers.ownerId
    var inviteCode by Servers.inviteCode
    var expiresAt by Servers.expiresAt
    var createdAt by Servers.createdAt
}

class ServerMemberEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ServerMemberEntity>(ServerMembers)
    var serverId by ServerMembers.serverId
    var userId by ServerMembers.userId
    var roleId by ServerMembers.roleId
    var joinedAt by ServerMembers.joinedAt
}

class RoleEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<RoleEntity>(Roles)
    var serverId by Roles.serverId
    var name by Roles.name
    var color by Roles.color
    var createdAt by Roles.createdAt
}

class ChannelEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ChannelEntity>(Channels)
    var serverId by Channels.serverId
    var name by Channels.name
    var type by Channels.type
    var createdAt by Channels.createdAt
}

class MessageEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<MessageEntity>(Messages)
    var channelId by Messages.channelId
    var userId by Messages.userId
    var content by Messages.content
    var createdAt by Messages.createdAt
}

class FriendshipEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<FriendshipEntity>(Friendships)
    var userOne by Friendships.userOne
    var userTwo by Friendships.userTwo
    var initiatorId by Friendships.initiatorId
    var status by Friendships.status
    var createdAt by Friendships.createdAt
}

class DirectConversationEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<DirectConversationEntity>(DirectConversations)
    var userOne by DirectConversations.userOne
    var userTwo by DirectConversations.userTwo
    var createdAt by DirectConversations.createdAt
}

class DirectMessageEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<DirectMessageEntity>(DirectMessages)
    var conversationId by DirectMessages.conversationId
    var senderId by DirectMessages.senderId
    var content by DirectMessages.content
    var createdAt by DirectMessages.createdAt
}
