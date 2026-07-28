package com.bunny.data.remote.socket

import android.util.Log
import com.bunny.domain.model.Message
import com.bunny.util.Constants
import com.bunny.util.BackendDiscovery
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocketService @Inject constructor(
    private val backendDiscovery: BackendDiscovery
) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .pingInterval(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val _incoming = MutableSharedFlow<Message>(extraBufferCapacity = 64)
    val incoming = _incoming.asSharedFlow()

    fun connect(accessToken: String) {
        if (webSocket != null) disconnect()

        val socketUrl = backendDiscovery.resolveSocketUrlSync()
        if (socketUrl.isBlank()) {
            Log.w("Socket", "Backend not discovered, skipping WebSocket connect")
            return
        }

        val request = Request.Builder()
            .url("$socketUrl/ws?token=$accessToken")
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d("Socket", "Connected")
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val type = json.optString("type")
                    if (type == "message_received") {
                        val messageId = json.optInt("messageId")
                        val channelId = json.optInt("channelId")
                        val userId = json.optInt("userId")
                        val content = json.optString("content")
                        val timestamp = json.optString("timestamp")
                        if (messageId > 0 && channelId > 0) {
                            _incoming.tryEmit(
                                Message(
                                    id = messageId,
                                    channelId = channelId,
                                    userId = userId,
                                    content = content,
                                    createdAt = timestamp
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Socket", "Failed to parse message", e)
                }
            }

            override fun onMessage(ws: WebSocket, bytes: okio.ByteString) {
                onMessage(ws, bytes.utf8())
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.d("Socket", "Closing: $code / $reason")
                ws.close(code, reason)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d("Socket", "Closed: $code / $reason")
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e("Socket", "Failure", t)
            }
        }

        webSocket = client.newWebSocket(request, listener)
    }

    fun joinChannel(channelId: Int) {
        sendJson(
            type = "join_channel",
            channelId = channelId
        )
    }

    fun leaveChannel(channelId: Int) {
        sendJson(
            type = "leave_channel",
            channelId = channelId
        )
    }

    fun sendMessage(channelId: Int, content: String, idempotencyKey: String? = null) {
        sendJson(
            type = "send_message",
            channelId = channelId,
            content = content,
            idempotencyKey = idempotencyKey
        )
    }

    fun disconnect() {
        try {
            webSocket?.close(1000, "Normal closure")
        } catch (e: Exception) {
            Log.e("Socket", "Disconnect error", e)
        }
        webSocket = null
    }

    private fun sendJson(
        type: String,
        channelId: Int? = null,
        content: String? = null,
        idempotencyKey: String? = null
    ) {
        val payload = JSONObject()
        payload.put("type", type)
        channelId?.let { payload.put("channelId", it) }
        content?.let { payload.put("content", it) }
        idempotencyKey?.let { payload.put("idempotencyKey", it) }
        webSocket?.send(payload.toString())
    }
}
