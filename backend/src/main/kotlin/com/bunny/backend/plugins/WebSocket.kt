package com.bunny.backend.plugins

import com.bunny.backend.service.MessageService
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.concurrent.ConcurrentHashMap

object WebSocketConnectionManager {
    private val connections = ConcurrentHashMap<Int, MutableSet<DefaultWebSocketSession>>()
    private val channelSubscriptions = ConcurrentHashMap<Int, MutableSet<Int>>()

    fun addConnection(userId: Int, session: DefaultWebSocketSession) {
        connections.getOrPut(userId) { ConcurrentHashMap.newKeySet() }.add(session)
    }

    fun removeConnection(userId: Int, session: DefaultWebSocketSession) {
        connections[userId]?.remove(session)
        if (connections[userId]?.isEmpty() == true) {
            connections.remove(userId)
            unsubscribeAllChannels(userId, session)
        }
    }

    fun subscribeToChannel(userId: Int, channelId: Int, session: DefaultWebSocketSession) {
        channelSubscriptions.getOrPut(channelId) { ConcurrentHashMap.newKeySet() }.add(userId)
    }

    fun unsubscribeFromChannel(userId: Int, channelId: Int) {
        channelSubscriptions[channelId]?.remove(userId)
        if (channelSubscriptions[channelId]?.isEmpty() == true) {
            channelSubscriptions.remove(channelId)
        }
    }

    private fun unsubscribeAllChannels(userId: Int, session: DefaultWebSocketSession) {
        channelSubscriptions.forEach { (channelId, subscribers) ->
            subscribers.remove(userId)
            if (subscribers.isEmpty()) {
                channelSubscriptions.remove(channelId)
            }
        }
    }

    suspend fun broadcastToChannel(channelId: Int, message: WsMessage, senderId: Int) {
        val subscribers = channelSubscriptions[channelId] ?: emptySet()
        subscribers.forEach { userId ->
            if (userId != senderId) {
                connections[userId]?.forEach { session ->
                    try {
                        session.send(kotlinx.serialization.json.Json.Default.encodeToString(message))
                    } catch (e: Exception) {
                        // Ignore failed sends
                    }
                }
            }
        }
    }

    suspend fun sendToUser(userId: Int, message: WsMessage) {
        connections[userId]?.forEach { session ->
            try {
                session.send(kotlinx.serialization.json.Json.Default.encodeToString(message))
            } catch (e: Exception) {
                // Ignore failed sends
            }
        }
    }
}

fun Application.configureWebSockets() {
    install(WebSockets) {
        pingPeriod = 15_000
        timeout = 15_000
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        val jwtSecret = System.getenv("JWT_SECRET") ?: "your-secret-key-change-in-production"
        val jwtIssuer = System.getenv("JWT_ISSUER") ?: "bunny"
        val jwtAlgorithm = Algorithm.HMAC256(jwtSecret)
        val verifier = JWT.require(jwtAlgorithm).withIssuer(jwtIssuer).build()

        webSocket("/ws") {
            val token = call.request.queryParameters["token"]
                ?: call.request.headers["Authorization"]?.removePrefix("Bearer ")

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

            val connectionManager = WebSocketConnectionManager
            connectionManager.addConnection(userId, this)

            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        try {
                            val text = frame.readText()
                            val message = kotlinx.serialization.json.Json.Default.decodeFromString<WsMessage>(text)
                            when (message.type) {
                                "join_channel" -> {
                                    message.channelId?.let { channelId ->
                                        connectionManager.subscribeToChannel(userId, channelId, this)
                                    }
                                }
                                "leave_channel" -> {
                                    message.channelId?.let { channelId ->
                                        connectionManager.unsubscribeFromChannel(userId, channelId)
                                    }
                                }
                                "send_message" -> {
                                    if (message.channelId != null && !message.content.isNullOrBlank()) {
                                        val savedMessage = transaction {
                                            MessageService.create(
                                                channelId = message.channelId,
                                                userId = userId,
                                                content = message.content
                                            )
                                        }

                                        val response = WsMessage(
                                            type = "message_received",
                                            channelId = savedMessage.channelId.value,
                                            messageId = savedMessage.id.value,
                                            content = savedMessage.content,
                                            userId = savedMessage.userId.value,
                                            sequence = savedMessage.id.value.toLong(),
                                            idempotencyKey = message.idempotencyKey,
                                            timestamp = savedMessage.createdAt.toString()
                                        )
                                        connectionManager.broadcastToChannel(
                                            savedMessage.channelId.value,
                                            response,
                                            senderId = userId
                                        )
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Skip malformed messages
                        }
                    }
                }
            } catch (e: Exception) {
                // Connection error
            } finally {
                connectionManager.removeConnection(userId, this)
                close(CloseReason(CloseReason.Codes.NORMAL, "Bye"))
            }
        }
    }
}
