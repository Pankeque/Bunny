package com.bunny.backend.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.bunny.backend.model.ServerMemberEntity
import com.bunny.backend.model.ServerMembers
import com.bunny.backend.service.ChannelService
import com.bunny.backend.service.MessageService
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class WsData(
    val channelId: Int? = null,
    val messageId: Int? = null,
    val userId: Int? = null,
    val content: String? = null,
    val timestamp: String? = null,
    val nonce: String? = null,
    val sequence: Long? = null,
    val heartbeatInterval: Long? = null,
    val online: Boolean? = null,
    val users: List<Int>? = null,
    val code: String? = null,
    val error: String? = null
)

@Serializable
data class WsMessage(
    val op: String,
    val type: String? = null,
    val data: WsData? = null
)

object WebSocketConnectionManager {
    // userId -> (session -> channels the session is subscribed to)
    private val sessions = ConcurrentHashMap<Int, ConcurrentHashMap<DefaultWebSocketSession, MutableSet<Int>>>()
    // channelId -> userIds subscribed
    private val channelSubscriptions = ConcurrentHashMap<Int, MutableSet<Int>>()
    // userId -> channels the user is present in (across sessions)
    private val userChannels = ConcurrentHashMap<Int, MutableSet<Int>>()
    // "userId:nonce" -> timestamp of last send
    private val sentNonces = ConcurrentHashMap<String, Long>()
    private const val NONCE_TTL_MS = 60_000L

    fun addConnection(userId: Int, session: DefaultWebSocketSession) {
        sessions.getOrPut(userId) { ConcurrentHashMap() }[session] = ConcurrentHashMap.newKeySet()
    }

    fun subscribeToChannel(userId: Int, channelId: Int, session: DefaultWebSocketSession) {
        sessions[userId]?.get(session)?.add(channelId)
        userChannels.getOrPut(userId) { ConcurrentHashMap.newKeySet() }.add(channelId)
        channelSubscriptions.getOrPut(channelId) { ConcurrentHashMap.newKeySet() }.add(userId)
    }

    fun unsubscribeFromChannel(userId: Int, channelId: Int, session: DefaultWebSocketSession): Boolean {
        sessions[userId]?.get(session)?.remove(channelId)
        val stillIn = sessions[userId]?.values?.any { it.contains(channelId) } == true
        if (!stillIn) {
            channelSubscriptions[channelId]?.remove(userId)
            if (channelSubscriptions[channelId]?.isEmpty() == true) channelSubscriptions.remove(channelId)
            userChannels[userId]?.remove(channelId)
            if (userChannels[userId]?.isEmpty() == true) userChannels.remove(userId)
        }
        return !stillIn
    }

    fun removeConnection(userId: Int, session: DefaultWebSocketSession): List<Int> {
        val joined = sessions[userId]?.remove(session) ?: emptySet()
        if (sessions[userId]?.isEmpty() == true) sessions.remove(userId)
        return joined.filter { channelId ->
            val stillIn = sessions[userId]?.values?.any { it.contains(channelId) } == true
            if (!stillIn) {
                channelSubscriptions[channelId]?.remove(userId)
                if (channelSubscriptions[channelId]?.isEmpty() == true) channelSubscriptions.remove(channelId)
                userChannels[userId]?.remove(channelId)
                if (userChannels[userId]?.isEmpty() == true) userChannels.remove(userId)
            }
            !stillIn
        }
    }

    fun onlineUsersInChannel(channelId: Int): Set<Int> = channelSubscriptions[channelId] ?: emptySet()

    fun isDuplicateSend(userId: Int, nonce: String?): Boolean {
        if (nonce.isNullOrBlank()) return false
        val key = "$userId:$nonce"
        val now = System.currentTimeMillis()
        if (sentNonces.size > 10_000) {
            sentNonces.entries.removeIf { now - it.value > NONCE_TTL_MS }
        }
        return sentNonces.putIfAbsent(key, now) != null
    }

    suspend fun sendToChannel(channelId: Int, message: WsMessage) {
        val subscribers = channelSubscriptions[channelId] ?: return
        subscribers.forEach { userId ->
            sessions[userId]?.forEach { (session, _) ->
                try {
                    session.send(Json.encodeToString(message))
                } catch (e: Exception) {
                    // Ignore failed sends
                }
            }
        }
    }

    suspend fun sendToUser(userId: Int, message: WsMessage) {
        sessions[userId]?.forEach { (session, _) ->
            try {
                session.send(Json.encodeToString(message))
            } catch (e: Exception) {
                // Ignore failed sends
            }
        }
    }
}

fun Application.configureWebSockets() {
    install(WebSockets) {
        pingPeriod = java.time.Duration.ofSeconds(15)
        timeout = java.time.Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        val jwtSecret = System.getenv("JWT_SECRET") ?: "your-secret-key-change-in-production"
        val jwtIssuer = System.getenv("JWT_ISSUER") ?: "bunny"
        val jwtAlgorithm = Algorithm.HMAC256(jwtSecret)
        val verifier = JWT.require(jwtAlgorithm).withIssuer(jwtIssuer).build()

        webSocket("/ws") {
            val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
                ?: call.request.queryParameters["token"]

            if (token == null) {
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Token required"))
                return@webSocket
            }

            val userId = try {
                val decoded = JWT.decode(token)
                val userIdClaim = decoded.getClaim("userId").asInt()
                val expiresAt = decoded.expiresAt.time
                if (System.currentTimeMillis() > expiresAt) {
                    close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Token expired"))
                    return@webSocket
                }
                userIdClaim ?: throw IllegalArgumentException("Invalid token")
            } catch (e: Exception) {
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Invalid token"))
                return@webSocket
            }

            val manager = WebSocketConnectionManager
            manager.addConnection(userId, this)

            send(Json.encodeToString(WsMessage(op = "ready", data = WsData(userId = userId))))
            send(Json.encodeToString(WsMessage(op = "hello", data = WsData(heartbeatInterval = 30_000L))))

            fun isChannelMember(channelId: Int): Boolean = transaction {
                val channel = ChannelService.findById(channelId) ?: return@transaction false
                ServerMemberEntity.find {
                    (ServerMembers.serverId eq channel.serverId.value) and (ServerMembers.userId eq userId)
                }.firstOrNull() != null
            }

            fun errorEvent(code: String, error: String) = WsMessage(
                op = "event",
                type = "error",
                data = WsData(code = code, error = error)
            )

            try {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue

                    val envelope = try {
                        Json.decodeFromString<WsMessage>(frame.readText())
                    } catch (e: Exception) {
                        continue
                    }

                    if (envelope.op == "heartbeat") {
                        send(Json.encodeToString(WsMessage(op = "heartbeat_ack")))
                        continue
                    }

                    val data = envelope.data ?: continue

                    when (envelope.op) {
                        "join_channel" -> {
                            val channelId = data.channelId ?: continue
                            if (!isChannelMember(channelId)) {
                                send(Json.encodeToString(errorEvent("forbidden", "Not a member of this channel")))
                                continue
                            }
                            manager.subscribeToChannel(userId, channelId, this)
                            val online = manager.onlineUsersInChannel(channelId).sorted()
                            send(Json.encodeToString(WsMessage(
                                op = "event",
                                type = "presence_update",
                                data = WsData(channelId = channelId, users = online)
                            )))
                            manager.sendToChannel(channelId, WsMessage(
                                op = "event",
                                type = "presence_update",
                                data = WsData(channelId = channelId, userId = userId, online = true)
                            ))
                        }

                        "leave_channel" -> {
                            val channelId = data.channelId ?: continue
                            if (manager.unsubscribeFromChannel(userId, channelId, this)) {
                                manager.sendToChannel(channelId, WsMessage(
                                    op = "event",
                                    type = "presence_update",
                                    data = WsData(channelId = channelId, userId = userId, online = false)
                                ))
                            }
                        }

                        "send_message" -> {
                            val channelId = data.channelId ?: continue
                            val content = data.content?.trim() ?: continue
                            if (content.isEmpty()) {
                                send(Json.encodeToString(errorEvent("invalid_message", "Message cannot be empty")))
                                continue
                            }
                            if (!isChannelMember(channelId)) {
                                send(Json.encodeToString(errorEvent("forbidden", "Not a member of this channel")))
                                continue
                            }
                            if (manager.isDuplicateSend(userId, data.nonce)) {
                                continue
                            }
                            val savedMessage = transaction {
                                MessageService.create(
                                    channelId = channelId,
                                    userId = userId,
                                    content = content
                                )
                            }
                            val event = WsMessage(
                                op = "event",
                                type = "message_received",
                                data = WsData(
                                    channelId = savedMessage.channelId.value,
                                    messageId = savedMessage.id.value,
                                    userId = savedMessage.userId.value,
                                    content = savedMessage.content,
                                    nonce = data.nonce,
                                    sequence = savedMessage.id.value.toLong(),
                                    timestamp = savedMessage.createdAt.toString()
                                )
                            )
                            manager.sendToChannel(channelId, event)
                        }
                    }
                }
            } catch (e: Exception) {
                // Connection error
            } finally {
                val vacated = manager.removeConnection(userId, this)
                vacated.forEach { channelId ->
                    manager.sendToChannel(channelId, WsMessage(
                        op = "event",
                        type = "presence_update",
                        data = WsData(channelId = channelId, userId = userId, online = false)
                    ))
                }
                try {
                    close(CloseReason(CloseReason.Codes.NORMAL, "Bye"))
                } catch (e: Exception) {
                    // Already closed
                }
            }
        }
    }
}
