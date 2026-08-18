package com.bunny.backend.service

import com.bunny.backend.model.*
import com.bunny.backend.util.PasswordUtils
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.security.SecureRandom
import java.time.Instant

object UserService {
    fun findByUsername(username: String): UserEntity? = transaction {
        UserEntity.find { Users.username eq username }.firstOrNull()
    }

    fun findById(id: Int): UserEntity? = transaction {
        UserEntity.findById(id)
    }

    fun create(username: String, password: String): UserEntity = transaction {
        val hash = PasswordUtils.hash(password)
        UserEntity.new {
            this.passwordHash = hash
            this.username = username
            this.avatarUrl = null
        }
    }

    fun validateCredentials(username: String, password: String): UserEntity? = transaction {
        val user = findByUsername(username)
        if (user != null && BCrypt.checkpw(password, user.passwordHash)) user else null
    }

    fun updateProfile(userId: Int, username: String?, avatarUrl: String?, theme: String?): UserEntity? = transaction {
        val user = findById(userId) ?: return@transaction null
        username?.let { user.username = it }
        avatarUrl?.let { user.avatarUrl = it }
        theme?.let { user.theme = it }
        user
    }

    fun search(query: String, excludeUserId: Int, limit: Int = 20): List<UserEntity> = transaction {
        UserEntity.find {
            (Users.username.lowerCase() like "%${query.lowercase()}%") and (Users.id neq excludeUserId)
        }.limit(limit).toList()
    }
}

object ServerService {
    private val INVITE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private val secureRandom = SecureRandom.getInstanceStrong()

    private fun generateInviteCode(): String = (1..12)
        .map { INVITE_CHARS[secureRandom.nextInt(INVITE_CHARS.length)] }
        .joinToString("")

    @Deprecated("Use role scoped queries instead.", replaceWith = ReplaceWith("findByUser(userId)"))
    fun findAll(): List<ServerEntity> = transaction {
        ServerEntity.all().toList()
    }

    fun findByUser(userId: Int): List<ServerEntity> = transaction {
        ServerMemberEntity.find { ServerMembers.userId eq userId }
            .map { it.serverId }
            .distinct()
            .mapNotNull { ServerEntity.findById(it.value) }
    }

    fun findById(id: Int): ServerEntity? = transaction {
        ServerEntity.findById(id)
    }

    fun findByInviteCode(code: String): ServerEntity? = transaction {
        ServerEntity.find {
            (Servers.inviteCode eq code) and
                (Servers.expiresAt.isNull() or (Servers.expiresAt greater Instant.now()))
        }.firstOrNull()
    }

    fun create(name: String, ownerId: Int): ServerEntity = transaction {
        val code = generateInviteCode()
        val server = ServerEntity.new {
            this.name = name
            this.iconUrl = null
            this.ownerId = EntityID(ownerId, Users)
            this.inviteCode = code
            this.expiresAt = Instant.now().plusSeconds(60 * 60 * 24 * 7)
        }
        val defaultRole = RoleEntity.new {
            this.serverId = server.id
            this.name = "member"
            this.color = "#99AAB5"
        }
        ServerMemberEntity.new {
            this.serverId = server.id
            this.userId = EntityID(ownerId, Users)
            this.roleId = defaultRole.id.value
        }
        server
    }

    fun isOwner(serverId: Int, userId: Int): Boolean = transaction {
        val server = findById(serverId) ?: return@transaction false
        server.ownerId.value == userId
    }

    fun regenerateInviteCode(serverId: Int): String? = transaction {
        val server = findById(serverId) ?: return@transaction null
        server.inviteCode = generateInviteCode()
        server.expiresAt = Instant.now().plusSeconds(60 * 60 * 24 * 7)
        server.inviteCode
    }

    fun update(serverId: Int, name: String?, iconUrl: String?): ServerEntity? = transaction {
        val server = findById(serverId) ?: return@transaction null
        name?.let { server.name = it }
        iconUrl?.let { server.iconUrl = it }
        server
    }

    fun delete(serverId: Int): Boolean = transaction {
        val server = findById(serverId)
        if (server != null) {
            ServerMembers.deleteWhere { it.run { ServerMembers.serverId eq serverId } }
            Channels.deleteWhere { it.run { Channels.serverId eq serverId } }
            Roles.deleteWhere { it.run { Roles.serverId eq serverId } }
            server.delete()
            true
        } else false
    }

    fun addMember(serverId: Int, userId: Int): Boolean = transaction {
        val alreadyMember = ServerMemberEntity.find {
            (ServerMembers.serverId eq serverId) and (ServerMembers.userId eq userId)
        }.firstOrNull()
        if (alreadyMember != null) {
            return@transaction false
        }
        val defaultRole = RoleEntity.find { (Roles.serverId eq serverId) and (Roles.name eq "member") }.firstOrNull()
            ?: RoleEntity.new {
                this.serverId = EntityID(serverId, Servers)
                this.name = "member"
                this.color = "#99AAB5"
            }
        try {
            ServerMemberEntity.new {
                this.serverId = EntityID(serverId, Servers)
                this.userId = EntityID(userId, Users)
                this.roleId = defaultRole.id.value
            }
            true
        } catch (e: org.jetbrains.exposed.exceptions.ExposedSQLException) {
            false
        }
    }

    fun removeMember(serverId: Int, userId: Int): Boolean = transaction {
        val server = findById(serverId)
        if (server != null && server.ownerId.value == userId) {
            return@transaction false
        }
        val member = ServerMemberEntity.find {
            (ServerMembers.serverId eq serverId) and (ServerMembers.userId eq userId)
        }.firstOrNull()
        member?.let {
            it.delete()
            true
        } ?: false
    }
}

object RoleService {
    fun findByServer(serverId: Int): List<RoleEntity> = transaction {
        RoleEntity.find { Roles.serverId eq serverId }.toList()
    }

    fun findById(id: Int): RoleEntity? = transaction {
        RoleEntity.findById(id)
    }

    fun create(serverId: Int, name: String, color: String = "#99AAB5"): RoleEntity = transaction {
        RoleEntity.new {
            this.serverId = EntityID(serverId, Servers)
            this.name = name
            this.color = color
        }
    }

    fun delete(roleId: Int): Boolean = transaction {
        val role = findById(roleId)
        if (role != null && role.name != "member") {
            val defaultRole = RoleEntity.find { (Roles.serverId eq role.serverId) and (Roles.name eq "member") }.firstOrNull()
            ServerMembers.update({ ServerMembers.roleId eq roleId }) {
                it[ServerMembers.roleId] = defaultRole?.id?.value
            }
            role.delete()
            true
        } else false
    }
}

object ChannelService {
    fun findByServer(serverId: Int): List<ChannelEntity> = transaction {
        ChannelEntity.find { Channels.serverId eq serverId }
            .orderBy(Channels.createdAt to SortOrder.ASC)
            .toList()
    }

    fun findById(id: Int): ChannelEntity? = transaction {
        ChannelEntity.findById(id)
    }

    fun create(name: String, type: String, serverId: Int): ChannelEntity = transaction {
        ChannelEntity.new {
            this.name = name
            this.serverId = EntityID(serverId, Servers)
            this.type = type
        }
    }

    fun update(channelId: Int, name: String?, type: String?): ChannelEntity? = transaction {
        val channel = findById(channelId) ?: return@transaction null
        name?.let { channel.name = it }
        type?.let { channel.type = it }
        channel
    }

    fun delete(channelId: Int): Boolean = transaction {
        val channel = findById(channelId)
        if (channel != null) {
            Messages.deleteWhere { it.run { Messages.channelId eq channelId } }
            channel.delete()
            true
        } else false
    }
}

object MessageService {
    fun findByChannel(channelId: Int, limit: Int = 50, offset: Int = 0): List<Pair<MessageEntity, UserEntity>> = transaction {
        (Messages innerJoin Users)
            .selectAll()
            .andWhere { Messages.channelId eq channelId }
            .orderBy(Messages.createdAt to SortOrder.DESC)
            .limit(limit, offset.toLong())
            .map {
                val msg = MessageEntity.wrapRow(it)
                val user = UserEntity.wrapRow(it)
                msg to user
            }
            .reversed()
    }

    fun create(channelId: Int, userId: Int, content: String): MessageEntity = transaction {
        MessageEntity.new {
            this.channelId = EntityID(channelId, Channels)
            this.userId = EntityID(userId, Users)
            this.content = content
        }
    }
}

object FriendService {
    private fun canonical(a: Int, b: Int): Pair<Int, Int> = if (a < b) a to b else b to a

    fun relationship(a: Int, b: Int): FriendshipEntity? = transaction {
        val (u1, u2) = canonical(a, b)
        FriendshipEntity.find {
            (Friendships.userOne eq u1) and (Friendships.userTwo eq u2)
        }.firstOrNull()
    }

    fun findById(id: Int): FriendshipEntity? = transaction {
        FriendshipEntity.findById(id)
    }

    fun otherUserId(friendship: FriendshipEntity, viewerId: Int): Int =
        if (friendship.userOne.value == viewerId) friendship.userTwo.value else friendship.userOne.value

    fun acceptedFriendIds(userId: Int): List<Int> = transaction {
        FriendshipEntity.find {
            (Friendships.status eq "accepted") and
                ((Friendships.userOne eq userId) or (Friendships.userTwo eq userId))
        }.map { otherUserId(it, userId) }
    }

    fun listFriends(userId: Int): List<Pair<FriendshipEntity, UserEntity>> = transaction {
        FriendshipEntity.find {
            (Friendships.status eq "accepted") and
                ((Friendships.userOne eq userId) or (Friendships.userTwo eq userId))
        }.orderBy(Friendships.createdAt to SortOrder.DESC).mapNotNull { f ->
            UserEntity.findById(otherUserId(f, userId))?.let { f to it }
        }
    }

    fun listPendingIncoming(userId: Int): List<Pair<FriendshipEntity, UserEntity>> = transaction {
        FriendshipEntity.find {
            (Friendships.status eq "pending") and
                (Friendships.initiatorId neq userId) and
                ((Friendships.userOne eq userId) or (Friendships.userTwo eq userId))
        }.orderBy(Friendships.createdAt to SortOrder.DESC).mapNotNull { f ->
            UserEntity.findById(otherUserId(f, userId))?.let { f to it }
        }
    }

    fun listPendingOutgoing(userId: Int): List<Pair<FriendshipEntity, UserEntity>> = transaction {
        FriendshipEntity.find {
            (Friendships.status eq "pending") and
                (Friendships.initiatorId eq userId)
        }.orderBy(Friendships.createdAt to SortOrder.DESC).mapNotNull { f ->
            UserEntity.findById(otherUserId(f, userId))?.let { f to it }
        }
    }

    // Creates a pending request, or auto-accepts a pending request that the
    // target already sent to the requester.
    fun sendRequest(requesterId: Int, targetId: Int): FriendshipEntity = transaction {
        val (u1, u2) = canonical(requesterId, targetId)
        val existing = FriendshipEntity.find {
            (Friendships.userOne eq u1) and (Friendships.userTwo eq u2)
        }.firstOrNull()
        if (existing != null) {
            if (existing.status == "pending" && existing.initiatorId != requesterId) {
                existing.status = "accepted"
            }
            return@transaction existing
        }
        FriendshipEntity.new {
            this.userOne = EntityID(u1, Users)
            this.userTwo = EntityID(u2, Users)
            this.initiatorId = requesterId
            this.status = "pending"
        }
    }

    fun acceptRequest(friendshipId: Int): FriendshipEntity? = transaction {
        val f = FriendshipEntity.findById(friendshipId) ?: return@transaction null
        f.status = "accepted"
        f
    }

    fun deleteRequest(friendshipId: Int): Boolean = transaction {
        val f = FriendshipEntity.findById(friendshipId) ?: return@transaction false
        f.delete()
        true
    }

    fun removeFriend(userId: Int, otherUserId: Int): Boolean = transaction {
        val f = relationship(userId, otherUserId) ?: return@transaction false
        if (f.status != "accepted") return@transaction false
        f.delete()
        true
    }

    fun blockUser(blockerId: Int, blockedId: Int) {
        transaction {
            val f = relationship(blockerId, blockedId)
            if (f != null) {
                if (f.status == "blocked" && f.initiatorId == blockerId) return@transaction
                f.delete()
            }
            val (u1, u2) = canonical(blockerId, blockedId)
            FriendshipEntity.new {
                this.userOne = EntityID(u1, Users)
                this.userTwo = EntityID(u2, Users)
                this.initiatorId = blockerId
                this.status = "blocked"
            }
            Unit
        }
    }

    fun unblockUser(blockerId: Int, blockedId: Int): Boolean = transaction {
        val f = relationship(blockerId, blockedId) ?: return@transaction false
        if (f.status == "blocked" && f.initiatorId == blockerId) {
            f.delete()
            true
        } else false
    }
}

object DirectMessageService {
    private fun canonical(a: Int, b: Int): Pair<Int, Int> = if (a < b) a to b else b to a

    fun findConversationBetween(a: Int, b: Int): DirectConversationEntity? = transaction {
        val (u1, u2) = canonical(a, b)
        DirectConversationEntity.find {
            (DirectConversations.userOne eq u1) and (DirectConversations.userTwo eq u2)
        }.firstOrNull()
    }

    fun getOrCreateConversation(a: Int, b: Int): DirectConversationEntity? = transaction {
        val friendship = FriendService.relationship(a, b)
        if (friendship == null || friendship.status != "accepted") return@transaction null
        findConversationBetween(a, b) ?: run {
            val (u1, u2) = canonical(a, b)
            DirectConversationEntity.new {
                this.userOne = EntityID(u1, Users)
                this.userTwo = EntityID(u2, Users)
            }
        }
    }

    fun findConversationById(id: Int): DirectConversationEntity? = transaction {
        DirectConversationEntity.findById(id)
    }

    fun isParticipant(conversationId: Int, userId: Int): Boolean = transaction {
        val conv = findConversationById(conversationId) ?: return@transaction false
        conv.userOne.value == userId || conv.userTwo.value == userId
    }

    fun otherParticipantId(conversationId: Int, userId: Int): Int? = transaction {
        val conv = findConversationById(conversationId) ?: return@transaction null
        when (userId) {
            conv.userOne.value -> conv.userTwo.value
            conv.userTwo.value -> conv.userOne.value
            else -> null
        }
    }

    fun listConversationsFor(userId: Int): List<Pair<DirectConversationEntity, UserEntity>> = transaction {
        DirectConversationEntity.find {
            (DirectConversations.userOne eq userId) or (DirectConversations.userTwo eq userId)
        }.mapNotNull { conv ->
            val otherId = if (conv.userOne.value == userId) conv.userTwo.value else conv.userOne.value
            UserEntity.findById(otherId)?.let { conv to it }
        }.sortedByDescending { lastMessage(it.first.id.value)?.createdAt ?: it.first.createdAt }
    }

    fun lastMessage(conversationId: Int): DirectMessageEntity? = transaction {
        DirectMessageEntity.find { DirectMessages.conversationId eq conversationId }
            .orderBy(DirectMessages.createdAt to SortOrder.DESC)
            .limit(1)
            .firstOrNull()
    }

    fun senderUser(message: DirectMessageEntity): UserEntity? = transaction {
        UserEntity.findById(message.senderId.value)
    }

    fun getMessages(conversationId: Int, limit: Int = 50, offset: Int = 0): List<Pair<DirectMessageEntity, UserEntity>> = transaction {
        (DirectMessages innerJoin Users)
            .selectAll()
            .andWhere { DirectMessages.conversationId eq conversationId }
            .orderBy(DirectMessages.createdAt to SortOrder.DESC)
            .limit(limit, offset.toLong())
            .map {
                val msg = DirectMessageEntity.wrapRow(it)
                val user = UserEntity.wrapRow(it)
                msg to user
            }
            .reversed()
    }

    fun create(conversationId: Int, senderId: Int, content: String): DirectMessageEntity = transaction {
        DirectMessageEntity.new {
            this.conversationId = EntityID(conversationId, DirectConversations)
            this.senderId = EntityID(senderId, Users)
            this.content = content
        }
    }
}
