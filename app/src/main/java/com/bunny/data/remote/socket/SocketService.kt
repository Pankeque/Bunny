package com.bunny.data.remote.socket

import android.util.Log
import com.bunny.domain.model.DirectMessage
import com.bunny.domain.model.Message
import com.bunny.domain.model.User
import com.bunny.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    object Reconnecting : ConnectionState()
}

sealed class SocketEvent {
    data class MessageReceived(val message: Message, val nonce: String?) : SocketEvent()
    data class PresenceChanged(val channelId: Int, val userId: Int, val online: Boolean) : SocketEvent()
    data class PresenceSnapshot(val channelId: Int, val onlineUserIds: List<Int>) : SocketEvent()
    data class GatewayError(val code: String?, val message: String?) : SocketEvent()

    data class FriendRequestReceived(val friendshipId: Int, val user: User) : SocketEvent()
    data class FriendRequestAccepted(val friendshipId: Int, val user: User) : SocketEvent()
    data class FriendRequestDeclined(val friendshipId: Int) : SocketEvent()
    data class FriendRequestCancelled(val friendshipId: Int) : SocketEvent()
    data class FriendRemoved(val userId: Int) : SocketEvent()
    data class FriendBlocked(val userId: Int) : SocketEvent()
    data class FriendPresenceChanged(val userId: Int, val online: Boolean) : SocketEvent()
    data class FriendPresenceSnapshot(val onlineUserIds: List<Int>) : SocketEvent()

    data class DirectMessageReceived(val message: DirectMessage, val nonce: String?) : SocketEvent()
    data class DirectTyping(val conversationId: Int, val userId: Int, val isTyping: Boolean) : SocketEvent()
}

@Singleton
class SocketService @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var webSocket: WebSocket? = null
    private var accessToken: String? = null
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    private var explicitlyDisconnected = false
    private var reconnectAttempt = 0
    private var lastHeartbeatAckAt = 0L
    private var heartbeatIntervalMs = 30_000L

    private val joinedChannels = ConcurrentHashMap.newKeySet<Int>()
    private val pendingMessages = ConcurrentHashMap.newKeySet<JSONObject>()

    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incoming = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 256)
    val incoming: SharedFlow<SocketEvent> = _incoming.asSharedFlow()

    fun connect(accessToken: String) {
        this.accessToken = accessToken
        explicitlyDisconnected = false
        reconnectAttempt = 0
        reconnectJob?.cancel()
        reconnectJob = null
        _connectionState.value = ConnectionState.Connecting
        openSocket()
    }

    private fun openSocket() {
        val token = accessToken ?: return
        val request = Request.Builder()
            .url("${Constants.SOCKET_URL}/ws")
            .header("Authorization", "Bearer $token")
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("Socket", "Connected")
                reconnectAttempt = 0
                _connectionState.value = ConnectionState.Connected
                joinedChannels.forEach { channelId -> sendControl("join_channel", channelId) }
                flushPendingMessages()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleFrame(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleFrame(bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("Socket", "Closing: $code / $reason")
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("Socket", "Closed: $code / $reason")
                stopHeartbeat()
                if (explicitlyDisconnected) return
                if (isTerminalCode(code)) {
                    _connectionState.value = ConnectionState.Disconnected
                } else {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("Socket", "Failure", t)
                stopHeartbeat()
                if (!explicitlyDisconnected) {
                    scheduleReconnect()
                }
            }
        }

        webSocket = client.newWebSocket(request, listener)
    }

    private fun handleFrame(text: String) {
        try {
            val envelope = JSONObject(text)
            when (envelope.optString("op")) {
                "ready" -> {
                    _connectionState.value = ConnectionState.Connected
                }
                "hello" -> {
                    val interval = envelope.optJSONObject("data")?.optLong("heartbeatInterval") ?: 30_000L
                    startHeartbeat(interval)
                }
                "heartbeat_ack" -> {
                    lastHeartbeatAckAt = System.currentTimeMillis()
                }
                "event" -> handleEvent(envelope)
            }
        } catch (e: Exception) {
            Log.e("Socket", "Failed to parse message", e)
        }
    }

    private fun handleEvent(envelope: JSONObject) {
        val type = envelope.optString("type")
        val data = envelope.optJSONObject("data") ?: return
        when (type) {
            "message_received" -> {
                val messageId = data.optInt("messageId")
                val channelId = data.optInt("channelId")
                val userId = data.optInt("userId")
                val content = data.optString("content")
                val timestamp = data.optString("timestamp")
                val nonce = data.optString("nonce").takeIf { it.isNotBlank() }
                val roleName = data.optString("roleName").takeIf { it.isNotBlank() }
                val roleColor = data.optString("roleColor").takeIf { it.isNotBlank() }
                val user = data.optJSONObject("user")?.toUser()
                if (messageId > 0 && channelId > 0) {
                    _incoming.tryEmit(
                        SocketEvent.MessageReceived(
                            message = Message(
                                id = messageId,
                                channelId = channelId,
                                userId = userId,
                                user = user,
                                content = content,
                                createdAt = timestamp,
                                roleName = roleName,
                                roleColor = roleColor
                            ),
                            nonce = nonce
                        )
                    )
                }
            }
            "presence_update" -> {
                val channelId = data.optInt("channelId")
                val users = data.optJSONArray("users")
                if (users != null && users.length() > 0) {
                    val ids = (0 until users.length()).map { users.getInt(it) }
                    if (channelId > 0) {
                        _incoming.tryEmit(SocketEvent.PresenceSnapshot(channelId, ids))
                    }
                } else {
                    val userId = data.optInt("userId")
                    val online = data.optBoolean("online")
                    if (channelId > 0 && userId > 0) {
                        _incoming.tryEmit(SocketEvent.PresenceChanged(channelId, userId, online))
                    }
                }
            }
            "error" -> {
                _incoming.tryEmit(
                    SocketEvent.GatewayError(
                        code = data.optString("code").takeIf { it.isNotBlank() },
                        message = data.optString("error").takeIf { it.isNotBlank() }
                    )
                )
            }
            "friend_request_received" -> {
                val friendshipId = data.optInt("friendshipId")
                val user = data.optJSONObject("user")?.toUser() ?: return
                if (friendshipId > 0) {
                    _incoming.tryEmit(SocketEvent.FriendRequestReceived(friendshipId, user))
                }
            }
            "friend_request_accepted" -> {
                val friendshipId = data.optInt("friendshipId")
                val user = data.optJSONObject("user")?.toUser() ?: return
                if (friendshipId > 0) {
                    _incoming.tryEmit(SocketEvent.FriendRequestAccepted(friendshipId, user))
                }
            }
            "friend_request_declined" -> {
                val friendshipId = data.optInt("friendshipId")
                if (friendshipId > 0) {
                    _incoming.tryEmit(SocketEvent.FriendRequestDeclined(friendshipId))
                }
            }
            "friend_request_cancelled" -> {
                val friendshipId = data.optInt("friendshipId")
                if (friendshipId > 0) {
                    _incoming.tryEmit(SocketEvent.FriendRequestCancelled(friendshipId))
                }
            }
            "friend_removed" -> {
                val userId = data.optInt("userId")
                if (userId > 0) {
                    _incoming.tryEmit(SocketEvent.FriendRemoved(userId))
                }
            }
            "friend_blocked" -> {
                val userId = data.optInt("userId")
                if (userId > 0) {
                    _incoming.tryEmit(SocketEvent.FriendBlocked(userId))
                }
            }
            "friend_presence_update" -> {
                val userId = data.optInt("userId")
                if (userId > 0) {
                    _incoming.tryEmit(SocketEvent.FriendPresenceChanged(userId, data.optBoolean("online")))
                }
            }
            "friend_presence_snapshot" -> {
                val users = data.optJSONArray("users")
                if (users != null) {
                    val ids = (0 until users.length()).map { users.getInt(it) }
                    _incoming.tryEmit(SocketEvent.FriendPresenceSnapshot(ids))
                }
            }
            "dm_message_received" -> {
                val conversationId = data.optInt("conversationId")
                val messageId = data.optInt("messageId")
                val senderId = data.optInt("userId")
                val content = data.optString("content")
                val timestamp = data.optString("timestamp")
                val nonce = data.optString("nonce").takeIf { it.isNotBlank() }
                val user = data.optJSONObject("user")?.toUser()
                if (conversationId > 0 && messageId > 0) {
                    _incoming.tryEmit(
                        SocketEvent.DirectMessageReceived(
                            message = DirectMessage(
                                id = messageId,
                                conversationId = conversationId,
                                senderId = senderId,
                                user = user,
                                content = content,
                                createdAt = timestamp
                            ),
                            nonce = nonce
                        )
                    )
                }
            }
            "dm_typing" -> {
                val conversationId = data.optInt("conversationId")
                val userId = data.optInt("userId")
                if (conversationId > 0 && userId > 0) {
                    _incoming.tryEmit(SocketEvent.DirectTyping(conversationId, userId, data.optBoolean("isTyping")))
                }
            }
        }
    }

    private fun org.json.JSONObject.toUser(): User {
        return User(
            id = optInt("id"),
            username = optString("username"),
            avatarUrl = optString("avatarUrl").takeIf { it.isNotBlank() },
            theme = optString("theme").takeIf { it.isNotBlank() } ?: "dark"
        )
    }

    private fun startHeartbeat(intervalMs: Long) {
        stopHeartbeat()
        heartbeatIntervalMs = intervalMs.coerceAtLeast(5_000L)
        lastHeartbeatAckAt = System.currentTimeMillis()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(heartbeatIntervalMs)
                val ws = webSocket ?: break
                if (System.currentTimeMillis() - lastHeartbeatAckAt > heartbeatIntervalMs * 3) {
                    Log.w("Socket", "Heartbeat not acknowledged, reconnecting")
                    ws.close(1001, "Heartbeat timeout")
                    break
                }
                sendFrame(JSONObject().put("op", "heartbeat"))
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun scheduleReconnect() {
        if (explicitlyDisconnected || reconnectJob?.isActive == true) return
        _connectionState.value = ConnectionState.Reconnecting
        reconnectJob = scope.launch {
            val backoffMs = backoffFor(reconnectAttempt++)
            delay(backoffMs)
            if (explicitlyDisconnected) return@launch
            openSocket()
        }
    }

    private fun backoffFor(attempt: Int): Long {
        val capped = attempt.coerceAtMost(6)
        return (1_000L shl capped).coerceAtMost(30_000L)
    }

    private fun isTerminalCode(code: Int): Boolean = code == 1003 || code == 1008 || code >= 4000

    fun joinChannel(channelId: Int) {
        joinedChannels.add(channelId)
        sendControl("join_channel", channelId)
    }

    fun leaveChannel(channelId: Int) {
        joinedChannels.remove(channelId)
        sendControl("leave_channel", channelId)
    }

    fun sendMessage(channelId: Int, content: String, nonce: String?) {
        val payload = JSONObject()
            .put("op", "send_message")
            .put(
                "data",
                JSONObject()
                    .put("channelId", channelId)
                    .put("content", content)
                    .put("nonce", nonce ?: JSONObject.NULL)
            )
        if (_connectionState.value is ConnectionState.Connected) {
            sendFrame(payload)
        } else {
            pendingMessages.add(payload)
        }
    }

    fun sendDirectMessage(conversationId: Int, content: String, nonce: String?) {
        val payload = JSONObject()
            .put("op", "dm_send_message")
            .put(
                "data",
                JSONObject()
                    .put("conversationId", conversationId)
                    .put("content", content)
                    .put("nonce", nonce ?: JSONObject.NULL)
            )
        if (_connectionState.value is ConnectionState.Connected) {
            sendFrame(payload)
        } else {
            pendingMessages.add(payload)
        }
    }

    fun sendTyping(conversationId: Int, isTyping: Boolean) {
        val payload = JSONObject()
            .put("op", "dm_typing")
            .put(
                "data",
                JSONObject()
                    .put("conversationId", conversationId)
                    .put("isTyping", isTyping)
            )
        sendFrame(payload)
    }

    fun requestPresence() {
        sendFrame(JSONObject().put("op", "request_presence"))
    }

    private fun sendControl(op: String, channelId: Int) {
        sendFrame(
            JSONObject()
                .put("op", op)
                .put("data", JSONObject().put("channelId", channelId))
        )
    }

    private fun sendFrame(payload: JSONObject) {
        try {
            webSocket?.send(payload.toString())
        } catch (e: Exception) {
            Log.e("Socket", "Send failed", e)
        }
    }

    private fun flushPendingMessages() {
        pendingMessages.forEach { sendFrame(it) }
        pendingMessages.clear()
    }

    fun disconnect() {
        explicitlyDisconnected = true
        stopHeartbeat()
        reconnectJob?.cancel()
        reconnectJob = null
        pendingMessages.clear()
        joinedChannels.clear()
        try {
            webSocket?.close(1000, "Normal closure")
        } catch (e: Exception) {
            Log.e("Socket", "Disconnect error", e)
        }
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
    }
}
