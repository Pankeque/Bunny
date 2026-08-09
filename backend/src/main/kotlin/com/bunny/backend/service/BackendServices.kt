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
            true
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
