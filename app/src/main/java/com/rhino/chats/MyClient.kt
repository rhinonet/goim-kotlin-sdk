package com.rhino.chats

import android.graphics.Color
import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import kotlin.concurrent.fixedRateTimer
import android.widget.TextView

class MyClient(
    private val statusTextView: TextView,
    private val messageBoxTextView: TextView,
    private val notify: (String) -> Unit
) {

    private val uri: URI = URI("ws://192.168.3.58:3102/sub")
    private var webSocketClient: WebSocketClient? = null
    private val rawHeaderLen = 16
    private var isConnected = false
    private val heartbeatIntervalMillis: Long = 30000 // 30 seconds

    init {
        connectWebSocket()
    }

    private fun connectWebSocket() {
        webSocketClient = object : WebSocketClient(uri) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                Log.d("WebSocket", "Connection opened")
                notify("Connection opened")
                isConnected = true
                authenticate()
            }

            override fun onMessage(message: String?) {
                Log.d("WebSocket", "Received message: $message")
                notify("Received message: $message")
            }

            override fun onMessage(bytes: ByteBuffer?) {
                bytes?.let {
                    handleBinaryMessage(it)
                }
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                Log.d("WebSocket", "Connection closed: $reason")
                notify("Connection closed: $reason")
                isConnected = false
                reconnect()
                statusTextView.post {
                    statusTextView.text = "Status: Failed"
                    statusTextView.setTextColor(Color.RED)
                }
            }

            override fun onError(ex: Exception?) {
                Log.e("WebSocket", "Error occurred: ${ex?.message}")
                notify("Error occurred: ${ex?.message}")
                isConnected = false
                reconnect()
            }
        }
        webSocketClient?.connect()
    }

    private fun reconnect() {
        Log.d("WebSocket", "Reconnecting...")
        notify("Reconnecting...")

        // Create a new thread to handle the reconnect
        Thread {
            try {
                // Sleep for a short time before reconnecting
                Thread.sleep(5000)
                webSocketClient?.reconnectBlocking()
            } catch (e: InterruptedException) {
                Log.e("WebSocket", "Reconnection attempt interrupted", e)
            }
        }.start()
    }

    private fun authenticate() {
        Log.d("Authenticate", "authenticate enter")

        val token = """{"mid":124, "room_id":"live://1000", "platform":"web", "accepts":[1000,1001,1002]}"""
        val bodyBuf = token.toByteArray(StandardCharsets.UTF_8)
        val headerBuf = ByteBuffer.allocate(rawHeaderLen)
        headerBuf.putInt(rawHeaderLen + bodyBuf.size)
        headerBuf.putShort(rawHeaderLen.toShort())
        headerBuf.putShort(1.toShort())
        headerBuf.putInt(7)
        headerBuf.putInt(1)
        val buf = mergeArrayBuffer(headerBuf.array(), bodyBuf)
        notify("Sent auth token: $token")
        Log.d("Authenticate", "authenticate out")
        webSocketClient?.send(buf)
        // Debug log for merged buffer length and value
        Log.d("Authenticate", "Merged buffer length: ${buf.capacity()}")
        Log.d("Authenticate", "Merged buffer values: ${buf.array().joinToString(", ")}")
    }

    private fun startHeartbeat() {
        fixedRateTimer("heartbeatTimer", initialDelay = 0, period = heartbeatIntervalMillis) {
            if (isConnected) {
                sendHeartbeat()
            }
        }
    }

    private fun sendHeartbeat() {
        Log.d("SendHeartbeat", "sendHeartbeat enter")
        val headerBuf = ByteBuffer.allocate(rawHeaderLen)
        headerBuf.putInt(rawHeaderLen)
        headerBuf.putShort(rawHeaderLen.toShort())
        headerBuf.putShort(1.toShort())
        headerBuf.putInt(2)
        headerBuf.putInt(1)
        headerBuf.flip()
        webSocketClient?.send(headerBuf.array())
        notify("Sent heartbeat")
        Log.d("SendHeartbeat", "sendHeartbeat out")
    }

    private fun handleBinaryMessage(buffer: ByteBuffer) {
        val packetLen = buffer.int
        val headerLen = buffer.short
        val ver = buffer.short
        val op = buffer.int
        val seq = buffer.int

        Log.d("WebSocket", "receiveHeader: packetLen=$packetLen, headerLen=$headerLen, ver=$ver, op=$op, seq=$seq")

        when (op) {
            8 -> {
                notify("receive: auth reply")
                Log.d("WebSocket", "receive: auth reply")
                statusTextView.post {
                    statusTextView.text = "Status: OK"
                    statusTextView.setTextColor(Color.GREEN)
                }
                startHeartbeat()
            }
            3 -> {
                notify("receive: heartbeat reply")
                Log.d("WebSocket", "receive: heartbeat reply")
            }
            else -> {
                val msgBody = StandardCharsets.UTF_8.decode(buffer).toString()
                notify("receive: ver=$ver, op=$op, seq=$seq, message=$msgBody")
                Log.d("WebSocket", "receive: ver=$ver, op=$op, seq=$seq, message=$msgBody")
            }
        }
    }

    private fun mergeArrayBuffer(ab1: ByteArray, ab2: ByteArray): ByteBuffer {
        val res = ByteBuffer.allocate(ab1.size + ab2.size)
        res.put(ab1)
        res.put(ab2)
        res.flip()
        return res
    }
}
