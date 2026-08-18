package com.bunny.backend.routes

import com.bunny.backend.dto.*
import com.bunny.backend.mapper.toResponse
import com.bunny.backend.model.*
import com.bunny.backend.plugins.generateToken
import com.bunny.backend.plugins.WebSocketConnectionManager
import com.bunny.backend.plugins.WsMessage
import com.bunny.backend.plugins.WsData
import com.bunny.backend.service.*
import com.bunny.backend.util.PasswordUtils
import org.jetbrains.exposed.dao.id.EntityID
import io.ktor.http.*
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.utils.io.core.readBytes
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

private const val MAX_IMAGE_BYTES = 5 * 1024 * 1024

private suspend fun ApplicationCall.readImageBytes(): Pair<ByteArray, String>? {
    if (!(request.headers[HttpHeaders.ContentType]?.startsWith("multipart/form-data") == true)) return null
    val multipart = receiveMultipart()
    var bytes: ByteArray? = null
    var contentType: String? = null
    try {
        multipart.forEachPart { part ->
            if (part is PartData.FileItem) {
                bytes = part.provider().readBytes()
                contentType = part.contentType?.toString()
            }
            part.dispose()
        }
    } catch (e: Exception) {
        return null
    }
    val data = bytes ?: return null
    if (data.isEmpty() || data.size > MAX_IMAGE_BYTES) return null
    val mime = contentType?.substringBefore(";") ?: "image/png"
    if (!mime.startsWith("image/")) return null
    return data to mime
}

private fun toImageDataUri(bytes: ByteArray, mime: String): String =
    "data:$mime;base64," + Base64.getEncoder().encodeToString(bytes)

private fun isMember(serverId: Int, userId: Int): Boolean = transaction {
    ServerMemberEntity.find {
        (ServerMembers.serverId eq serverId) and (ServerMembers.userId eq userId)
    }.firstOrNull() != null
}

fun Route.authRoutes() {
    route("/api/auth") {
        post("/register") {
            val request = call.receive<RegisterRequest>()
            try {
                val existing = UserService.findByUsername(request.username)
                if (existing != null) {
                    call.respond(HttpStatusCode.Conflict, "Username already taken")
                    return@post
                }

                val user = UserService.create(request.username, request.password)
                val accessToken = generateToken(user.id.value)
                val refreshToken = UUID.randomUUID().toString()
                transaction {
                    RefreshTokenEntity.new {
                        this.userId = EntityID(user.id.value, Users)
                        this.token = refreshToken
                        this.expiresAt = java.time.Instant.now().plusSeconds(604800)
                    }
                }
                call.respond(
                    AuthResponse(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        user = user.toResponse()
                    )
                )
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, e.message ?: "Error")
            }
        }

        post("/login") {
            val request = call.receive<LoginRequest>()
            val user = UserService.validateCredentials(request.username, request.password)
            if (user != null) {
                val accessToken = generateToken(user.id.value)
                val refreshToken = UUID.randomUUID().toString()
                transaction {
                    RefreshTokenEntity.new {
                        this.userId = EntityID(user.id.value, Users)
                        this.token = refreshToken
                        this.expiresAt = java.time.Instant.now().plusSeconds(604800)
                    }
                }
                call.respond(
                    AuthResponse(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        user = user.toResponse()
                    )
                )
            } else {
                call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
            }
        }

        post("/refresh") {
            val request = call.receive<RefreshTokenRequest>()
            val stored = transaction { RefreshTokenEntity.find { RefreshTokens.token eq request.refreshToken }.firstOrNull() }
            if (stored != null) {
                val user = UserService.findById(stored.userId.value)
                if (user != null) {
                    val accessToken = generateToken(user.id.value)
                    call.respond(AuthResponse(accessToken = accessToken, refreshToken = request.refreshToken, user = user.toResponse()))
                } else {
                    call.respond(HttpStatusCode.Unauthorized, "Invalid token")
                }
            } else {
                call.respond(HttpStatusCode.Unauthorized, "Invalid token")
            }
        }
    }
}

fun Route.serverRoutes() {
        route("/api/servers") {
            get {
                val user = call.principal<UserEntity>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                try {
                    val servers = ServerService.findByUser(user.id.value).map { it.toResponse() }
                    call.respond(servers)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, e.message ?: "Error")
                }
            }

            post {
                val user = call.principal<UserEntity>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val request = call.receive<CreateServerRequest>()
                if (request.name.isBlank()) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Server name cannot be empty")
                }
                if (request.name.length > 100) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Server name must be at most 100 characters")
                }
                try {
                    val server = ServerService.create(request.name, user.id.value)
                    call.respond(HttpStatusCode.Created, server.toResponse())
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, e.message ?: "Error")
                }
            }

            delete("/{id}") {
                val serverId = call.parameters["id"]?.toIntOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val user = call.principal<UserEntity>() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                if (!ServerService.isOwner(serverId, user.id.value)) {
                    return@delete call.respond(HttpStatusCode.Forbidden)
                }
                if (ServerService.delete(serverId)) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            post("/join") {
                val user = call.principal<UserEntity>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val request = call.receive<JoinServerRequest>()
                try {
                    val server = ServerService.findByInviteCode(request.inviteCode)
                    if (server == null) {
                        call.respond(HttpStatusCode.NotFound, "Invalid or expired invite code")
                        return@post
                    }
                    val added = ServerService.addMember(server.id.value, user.id.value)
                    if (!added) {
                        call.respond(HttpStatusCode.Conflict, "You are already a member")
                    } else {
                        call.respond(server.toResponse())
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, e.message ?: "Error")
                }
            }

            post("/{id}/leave") {
                val serverId = call.parameters["id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                val user = call.principal<UserEntity>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                if (ServerService.isOwner(serverId, user.id.value)) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Owner cannot leave the server")
                }
                if (ServerService.removeMember(serverId, user.id.value)) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            put("/{id}") {
                val serverId = call.parameters["id"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)
                val user = call.principal<UserEntity>() ?: return@put call.respond(HttpStatusCode.Unauthorized)
                if (!ServerService.isOwner(serverId, user.id.value)) {
                    return@put call.respond(HttpStatusCode.Forbidden)
                }
                val request = call.receive<UpdateServerRequest>()
                val server = ServerService.update(serverId, request.name, request.iconUrl)
                if (server != null) {
                    call.respond(server.toResponse())
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            post("/{id}/icon") {
                val serverId = call.parameters["id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                val user = call.principal<UserEntity>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                if (!ServerService.isOwner(serverId, user.id.value)) {
                    return@post call.respond(HttpStatusCode.Forbidden)
                }
                val image = call.readImageBytes()
                if (image == null) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Image file required (max 5MB, image/* only)")
                }
                val dataUri = toImageDataUri(image.first, image.second)
                val server = ServerService.update(serverId, null, dataUri)
                if (server != null) {
                    call.respond(server.toResponse())
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            post("/{id}/regenerate-invite") {
                val serverId = call.parameters["id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                val user = call.principal<UserEntity>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                if (!ServerService.isOwner(serverId, user.id.value)) {
                    return@post call.respond(HttpStatusCode.Forbidden)
                }
                val newCode = ServerService.regenerateInviteCode(serverId)
                if (newCode != null) {
                    call.respond(mapOf("inviteCode" to newCode))
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            get("/{serverId}/roles") {
                val serverId = call.parameters["serverId"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
                val user = call.principal<UserEntity>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val isMember = isMember(serverId, user.id.value)
                if (!isMember) {
                    return@get call.respond(HttpStatusCode.Forbidden)
                }
                val roles = RoleService.findByServer(serverId).map { it.toResponse() }
                call.respond(roles)
            }

            post("/{serverId}/roles") {
                val serverId = call.parameters["serverId"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                val user = call.principal<UserEntity>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                if (!ServerService.isOwner(serverId, user.id.value)) {
                    return@post call.respond(HttpStatusCode.Forbidden)
                }
                val request = call.receive<com.bunny.backend.dto.CreateRoleRequest>()
                val role = RoleService.create(serverId, request.name, request.color)
                call.respond(HttpStatusCode.Created, role.toResponse())
            }

        }
        route("/api") {
            delete("/roles/{roleId}") {
                val roleId = call.parameters["roleId"]?.toIntOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val user = call.principal<UserEntity>() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                val role = RoleService.findById(roleId)
                if (role == null) {
                    return@delete call.respond(HttpStatusCode.NotFound)
                }
                if (!ServerService.isOwner(role.serverId.value, user.id.value)) {
                    return@delete call.respond(HttpStatusCode.Forbidden)
                }
                if (RoleService.delete(roleId)) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }
}

fun Route.channelRoutes() {
        route("/api") {
            route("/servers/{serverId}") {
                get("/channels") {
                    val serverId = call.parameters["serverId"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val user = call.principal<UserEntity>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    val isMember = isMember(serverId, user.id.value)
                    if (!isMember) {
                        return@get call.respond(HttpStatusCode.Forbidden)
                    }
                    val channels = ChannelService.findByServer(serverId).map { it.toResponse() }
                    call.respond(channels)
                }

                post("/channels") {
                    val serverId = call.parameters["serverId"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val user = call.principal<UserEntity>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                    if (!ServerService.isOwner(serverId, user.id.value)) {
                        return@post call.respond(HttpStatusCode.Forbidden)
                    }
                    val request = call.receive<CreateChannelRequest>()
                    if (request.name.isBlank()) {
                        return@post call.respond(HttpStatusCode.BadRequest, "Channel name cannot be empty")
                    }
                    val channel = ChannelService.create(request.name, request.type ?: "text", serverId)
                    call.respond(HttpStatusCode.Created, channel.toResponse())
                }
            }

            delete("/channels/{id}") {
                val channelId = call.parameters["id"]?.toIntOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val user = call.principal<UserEntity>() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                val channel = ChannelService.findById(channelId)
                if (channel == null) {
                    return@delete call.respond(HttpStatusCode.NotFound)
                }
                if (!ServerService.isOwner(channel.serverId.value, user.id.value)) {
                    return@delete call.respond(HttpStatusCode.Forbidden)
                }
                if (ChannelService.delete(channelId)) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            put("/channels/{id}") {
                val channelId = call.parameters["id"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)
                val user = call.principal<UserEntity>() ?: return@put call.respond(HttpStatusCode.Unauthorized)
                val request = call.receive<UpdateChannelRequest>()
                val channel = ChannelService.findById(channelId)
                if (channel == null) {
                    return@put call.respond(HttpStatusCode.NotFound)
                }
                if (!ServerService.isOwner(channel.serverId.value, user.id.value)) {
                    return@put call.respond(HttpStatusCode.Forbidden)
                }
                val updated = ChannelService.update(channelId, request.name, request.type)
                if (updated != null) {
                    call.respond(updated.toResponse())
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }
}

fun Route.messageRoutes() {
        route("/api") {
            route("/channels/{channelId}") {
                get("/messages") {
                    val channelId = call.parameters["channelId"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val user = call.principal<UserEntity>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    val channel = ChannelService.findById(channelId)
                    if (channel == null) {
                        return@get call.respond(HttpStatusCode.NotFound)
                    }
                    val isMember = isMember(channel.serverId.value, user.id.value)
                    if (!isMember) {
                        return@get call.respond(HttpStatusCode.Forbidden)
                    }
                    val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                    val messages = MessageService.findByChannel(channelId, limit, (page - 1) * limit).map { it.toResponse() }
                    call.respond(messages)
                }

                post("/messages") {
                    val channelId = call.parameters["channelId"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val user = call.principal<UserEntity>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                    val request = call.receive<SendMessageRequest>()
                    if (request.content.isBlank()) {
                        return@post call.respond(HttpStatusCode.BadRequest, "Message cannot be empty")
                    }
                    val channel = ChannelService.findById(channelId)
                    if (channel == null) {
                        return@post call.respond(HttpStatusCode.NotFound, "Channel not found")
                    }
                    val isMember = isMember(channel.serverId.value, user.id.value)
                    if (!isMember) {
                        return@post call.respond(HttpStatusCode.Forbidden)
                    }
                    val message = MessageService.create(channelId, user.id.value, request.content)
                    call.respond(HttpStatusCode.Created, (message to user).toResponse())
                }
            }
        }
}

fun Route.userRoutes() {
        route("/api/users") {
            get("/search") {
                val user = call.principal<UserEntity>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val query = call.request.queryParameters["q"]?.trim() ?: ""
                if (query.isBlank()) {
                    call.respond(emptyList<UserResponse>())
                    return@get
                }
                val results = UserService.search(query, user.id.value)
                call.respond(results.map { it.toResponse() })
            }

            put("/me") {
                val user = call.principal<UserEntity>() ?: return@put call.respond(HttpStatusCode.Unauthorized)
                val request = call.receive<UpdateUserRequest>()
                val updated = UserService.updateProfile(user.id.value, request.username, request.avatarUrl, request.theme)
                if (updated != null) {
                    call.respond(updated.toResponse())
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            post("/me/avatar") {
                val user = call.principal<UserEntity>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val image = call.readImageBytes()
                if (image == null) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Image file required (max 5MB, image/* only)")
                }
                val dataUri = toImageDataUri(image.first, image.second)
                val updated = UserService.updateProfile(user.id.value, null, dataUri, null)
                if (updated != null) {
                    call.respond(updated.toResponse())
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }
}

fun Route.friendRoutes() {
    route("/api/friends") {
        get {
            val user = call.principal<UserEntity>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val friends = FriendService.listFriends(user.id.value)
            call.respond(friends.map { it.toResponse(user.id.value) })
        }

        get("/requests") {
            val user = call.principal<UserEntity>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val incoming = FriendService.listPendingIncoming(user.id.value)
            call.respond(incoming.map { it.toResponse(user.id.value) })
        }

        get("/requests/sent") {
            val user = call.principal<UserEntity>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val outgoing = FriendService.listPendingOutgoing(user.id.value)
            call.respond(outgoing.map { it.toResponse(user.id.value) })
        }

        post("/requests") {
            val user = call.principal<UserEntity>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val request = call.receive<FriendRequestDto>()
            if (request.username.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, "Username cannot be empty")
            }
            val target = UserService.findByUsername(request.username)
                ?: return@post call.respond(HttpStatusCode.NotFound, "User not found")
            if (target.id.value == user.id.value) {
                return@post call.respond(HttpStatusCode.BadRequest, "You cannot add yourself")
            }
            val existing = FriendService.relationship(user.id.value, target.id.value)
            if (existing != null && existing.status == "blocked") {
                if (existing.initiatorId == user.id.value) {
                    return@post call.respond(HttpStatusCode.Conflict, "You have blocked this user")
                }
                return@post call.respond(HttpStatusCode.Forbidden, "This user has blocked you")
            }
            if (existing != null && existing.status == "accepted") {
                return@post call.respond(HttpStatusCode.Conflict, "You are already friends")
            }
            val autoAccepted = existing != null && existing.status == "pending" && existing.initiatorId != user.id.value
            val friendship = FriendService.sendRequest(user.id.value, target.id.value)
            val response = (friendship to target).toResponse(user.id.value)
            if (autoAccepted) {
                WebSocketConnectionManager.sendToUser(target.id.value, WsMessage(
                    op = "event",
                    type = "friend_request_accepted",
                    data = WsData(friendshipId = friendship.id.value, user = user.toResponse())
                ))
                call.respond(HttpStatusCode.OK, response)
            } else {
                WebSocketConnectionManager.sendToUser(target.id.value, WsMessage(
                    op = "event",
                    type = "friend_request_received",
                    data = WsData(friendshipId = friendship.id.value, user = user.toResponse())
                ))
                call.respond(HttpStatusCode.Created, response)
            }
        }

        post("/requests/{id}/accept") {
            val friendshipId = call.parameters["id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
            val user = call.principal<UserEntity>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val friendship = FriendService.findById(friendshipId)
                ?: return@post call.respond(HttpStatusCode.NotFound, "Request not found")
            if (friendship.status != "pending") {
                return@post call.respond(HttpStatusCode.Conflict, "Request is no longer pending")
            }
            if (friendship.initiatorId == user.id.value) {
                return@post call.respond(HttpStatusCode.BadRequest, "You cannot accept your own request")
            }
            FriendService.acceptRequest(friendshipId)
            WebSocketConnectionManager.sendToUser(friendship.initiatorId, WsMessage(
                op = "event",
                type = "friend_request_accepted",
                data = WsData(friendshipId = friendshipId, user = user.toResponse())
            ))
            val other = UserEntity.findById(friendship.initiatorId)
            if (other != null) {
                call.respond((friendship to other).toResponse(user.id.value))
            } else {
                call.respond(HttpStatusCode.OK)
            }
        }

        post("/requests/{id}/decline") {
            val friendshipId = call.parameters["id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
            val user = call.principal<UserEntity>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val friendship = FriendService.findById(friendshipId)
                ?: return@post call.respond(HttpStatusCode.NotFound, "Request not found")
            if (friendship.status != "pending") {
                return@post call.respond(HttpStatusCode.Conflict, "Request is no longer pending")
            }
            if (friendship.initiatorId == user.id.value) {
                return@post call.respond(HttpStatusCode.BadRequest, "You cannot decline your own request")
            }
            FriendService.deleteRequest(friendshipId)
            WebSocketConnectionManager.sendToUser(friendship.initiatorId, WsMessage(
                op = "event",
                type = "friend_request_declined",
                data = WsData(friendshipId = friendshipId)
            ))
            call.respond(HttpStatusCode.OK)
        }

        post("/requests/{id}/cancel") {
            val friendshipId = call.parameters["id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
            val user = call.principal<UserEntity>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val friendship = FriendService.findById(friendshipId)
                ?: return@post call.respond(HttpStatusCode.NotFound, "Request not found")
            if (friendship.status != "pending") {
                return@post call.respond(HttpStatusCode.Conflict, "Request is no longer pending")
            }
            if (friendship.initiatorId != user.id.value) {
                return@post call.respond(HttpStatusCode.BadRequest, "You cannot cancel this request")
            }
            val otherId = FriendService.otherUserId(friendship, user.id.value)
            FriendService.deleteRequest(friendshipId)
            WebSocketConnectionManager.sendToUser(otherId, WsMessage(
                op = "event",
                type = "friend_request_cancelled",
                data = WsData(friendshipId = friendshipId)
            ))
            call.respond(HttpStatusCode.OK)
        }

        delete("/{userId}") {
            val otherId = call.parameters["userId"]?.toIntOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest)
            val user = call.principal<UserEntity>() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
            if (!FriendService.removeFriend(user.id.value, otherId)) {
                return@delete call.respond(HttpStatusCode.NotFound, "Friendship not found")
            }
            WebSocketConnectionManager.sendToUser(otherId, WsMessage(
                op = "event",
                type = "friend_removed",
                data = WsData(userId = user.id.value)
            ))
            call.respond(HttpStatusCode.OK)
        }

        post("/{userId}/block") {
            val otherId = call.parameters["userId"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
            val user = call.principal<UserEntity>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            if (otherId == user.id.value) {
                return@post call.respond(HttpStatusCode.BadRequest, "You cannot block yourself")
            }
            FriendService.blockUser(user.id.value, otherId)
            WebSocketConnectionManager.sendToUser(otherId, WsMessage(
                op = "event",
                type = "friend_blocked",
                data = WsData(userId = user.id.value)
            ))
            call.respond(HttpStatusCode.OK)
        }

        post("/{userId}/unblock") {
            val otherId = call.parameters["userId"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
            val user = call.principal<UserEntity>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            FriendService.unblockUser(user.id.value, otherId)
            call.respond(HttpStatusCode.OK)
        }
    }
}

fun Route.dmRoutes() {
    route("/api/dms") {
        get("/conversations") {
            val user = call.principal<UserEntity>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val conversations = DirectMessageService.listConversationsFor(user.id.value)
            val responses = conversations.map { (conv, other) ->
                val last = DirectMessageService.lastMessage(conv.id.value)
                val lastResponse = last?.let { msg ->
                    val sender = DirectMessageService.senderUser(msg)
                    if (sender != null) (msg to sender).toResponse() else null
                }
                (conv to other).toResponse(user.id.value, lastResponse)
            }
            call.respond(responses)
        }

        post("/conversations/{userId}") {
            val otherId = call.parameters["userId"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
            val user = call.principal<UserEntity>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            if (otherId == user.id.value) {
                return@post call.respond(HttpStatusCode.BadRequest, "You cannot message yourself")
            }
            val conv = DirectMessageService.getOrCreateConversation(user.id.value, otherId)
                ?: return@post call.respond(HttpStatusCode.Forbidden, "You can only message friends")
            val other = UserService.findById(otherId) ?: return@post call.respond(HttpStatusCode.NotFound)
            call.respond((conv to other).toResponse(user.id.value))
        }

        get("/conversations/{id}/messages") {
            val conversationId = call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
            val user = call.principal<UserEntity>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            if (!DirectMessageService.isParticipant(conversationId, user.id.value)) {
                return@get call.respond(HttpStatusCode.Forbidden)
            }
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            val messages = DirectMessageService.getMessages(conversationId, limit, (page - 1) * limit)
            call.respond(messages.map { it.toResponse() })
        }

        post("/conversations/{id}/messages") {
            val conversationId = call.parameters["id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
            val user = call.principal<UserEntity>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val request = call.receive<SendDirectMessageRequest>()
            if (request.content.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, "Message cannot be empty")
            }
            if (!DirectMessageService.isParticipant(conversationId, user.id.value)) {
                return@post call.respond(HttpStatusCode.Forbidden)
            }
            val message = DirectMessageService.create(conversationId, user.id.value, request.content)
            val event = WsMessage(
                op = "event",
                type = "dm_message_received",
                data = WsData(
                    conversationId = message.conversationId.value,
                    messageId = message.id.value,
                    userId = message.senderId.value,
                    content = message.content,
                    timestamp = message.createdAt.toString(),
                    user = user.toResponse()
                )
            )
            val otherId = DirectMessageService.otherParticipantId(conversationId, user.id.value)
            if (otherId != null) {
                WebSocketConnectionManager.sendToUser(otherId, event)
            }
            call.respond(HttpStatusCode.Created, (message to user).toResponse())
        }
    }
}
