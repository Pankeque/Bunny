package com.bunny.backend.routes

import com.bunny.backend.dto.*
import com.bunny.backend.mapper.toResponse
import com.bunny.backend.model.*
import com.bunny.backend.service.*
import com.bunny.backend.util.PasswordUtils
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

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
                RefreshTokenEntity.new {
                    this.userId = EntityID(user.id.value, Users)
                    this.token = refreshToken
                    this.expiresAt = java.time.Instant.now().plusSeconds(604800)
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
                RefreshTokenEntity.new {
                    this.userId = EntityID(user.id.value, Users)
                    this.token = refreshToken
                    this.expiresAt = java.time.Instant.now().plusSeconds(604800)
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
            val stored = transaction { RefreshTokenEntity.find { RefreshTokens.token eq request.token }.firstOrNull() }
            if (stored != null) {
                val user = UserService.findById(stored.userId.value)
                if (user != null) {
                    val accessToken = generateToken(user.id.value)
                    call.respond(AuthResponse(accessToken = accessToken, refreshToken = request.token, user = user.toResponse()))
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
    authenticate {
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
                val request = call.receive<UpdateServerRequest>()
                val server = ServerService.update(serverId, null, request.iconUrl)
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
                val isMember = ServerMemberEntity.find { (ServerMembers.serverId eq serverId) and (ServerMembers.userId eq user.id.value) }.firstOrNull() != null
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

            delete("/roles/{roleId}") {
                val roleId = call.parameters["roleId"]?.toIntOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val user = call.principal<UserEntity>() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                val role = RoleService.findById(roleId)
                if (role == null) {
                    return@delete call.respond(HttpStatusCode.NotFound)
                }
                if (!ServerService.isOwner(role.serverId, user.id.value)) {
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
}

fun Route.channelRoutes() {
    authenticate {
        route("/api") {
            route("/servers/{serverId}") {
                get("/channels") {
                    val serverId = call.parameters["serverId"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val user = call.principal<UserEntity>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    val isMember = ServerMemberEntity.find { (ServerMembers.serverId eq serverId) and (ServerMembers.userId eq user.id.value) }.firstOrNull() != null
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
}

fun Route.messageRoutes() {
    authenticate {
        route("/api") {
            route("/channels/{channelId}") {
                get("/messages") {
                    val channelId = call.parameters["channelId"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                    val messages = MessageService.findByChannel(channelId, limit, (page - 1) * limit).map { it.toResponse() }
                    call.respond(messages)
                }
            }

            post("/messages") {
                val user = call.principal<UserEntity>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val request = call.receive<SendMessageRequest>()
                val message = MessageService.create(request.channelId, user.id.value, request.content)
                call.respond(HttpStatusCode.Created, message.toResponse())
            }
        }
    }
}

fun Route.userRoutes() {
    authenticate {
        route("/api/users") {
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
        }
    }
}
