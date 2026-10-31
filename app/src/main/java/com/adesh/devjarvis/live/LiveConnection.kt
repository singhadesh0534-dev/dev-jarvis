package com.adesh.devjarvis.live

import com.adesh.devjarvis.core.JarvisConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Ek Live conversation = ek WebSocket. Setup bhejta hai, mic audio stream karta hai, server events deta hai.
 * Setup complete hone tak ka audio (wake ke turant baad bola gaya) buffer hota hai, khoya nahi jaata.
 */
class LiveConnection(
    baseClient: OkHttpClient,
    private val apiKey: String,
    private val setupJson: String,
) {
    sealed interface Event {
        data class Server(val event: LiveMessages.ServerEvent) : Event
        data class Closed(val kind: LiveMessages.CloseKind, val detail: String) : Event
    }

    private val client = baseClient.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val channel = Channel<Event>(Channel.UNLIMITED)
    val events: ReceiveChannel<Event> = channel

    @Volatile private var ready = false
    @Volatile private var closed = false
    private val pending = ArrayDeque<String>()
    private var socket: WebSocket? = null

    fun connect() {
        val url = JarvisConfig.GEMINI_LIVE_URL.toHttpUrl().newBuilder().addQueryParameter("key", apiKey).build()
        socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(setupJson)
            }

            override fun onMessage(webSocket: WebSocket, text: String) = handle(text)
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handle(bytes.utf8())

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                finish(LiveMessages.classifyClose(code, reason), "close $code: $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                finish(LiveMessages.classifyClose(code, reason), "closed $code: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val code = response?.code ?: 0
                val kind = when {
                    code == 429 -> LiveMessages.CloseKind.QUOTA
                    code == 401 || code == 403 -> LiveMessages.CloseKind.AUTH
                    code == 404 -> LiveMessages.CloseKind.MODEL
                    t is UnknownHostException || (t is IOException && response == null) -> LiveMessages.CloseKind.NETWORK
                    else -> LiveMessages.CloseKind.OTHER
                }
                finish(kind, "failure ${t.javaClass.simpleName} ${response?.code ?: ""}")
            }
        })
    }

    private fun handle(raw: String) {
        for (ev in LiveMessages.parse(raw)) {
            if (ev == LiveMessages.ServerEvent.SetupComplete) {
                synchronized(pending) {
                    ready = true
                    while (pending.isNotEmpty()) socket?.send(pending.removeFirst())
                }
            }
            channel.trySend(Event.Server(ev))
        }
    }

    fun sendAudio(pcm: ByteArray, length: Int) {
        if (closed) return
        val msg = LiveMessages.audio(pcm, length, JarvisConfig.LIVE_INPUT_RATE)
        synchronized(pending) {
            if (!ready) {
                pending.addLast(msg)
                while (pending.size > MAX_PENDING) pending.removeFirst()
                return
            }
        }
        socket?.send(msg)
    }

    fun sendToolResponse(json: String) {
        if (!closed) socket?.send(json)
    }

    fun close() {
        if (closed) return
        socket?.close(1000, "bye")
        finish(LiveMessages.CloseKind.OTHER, "client closed")
    }

    private fun finish(kind: LiveMessages.CloseKind, detail: String) {
        if (closed) return
        closed = true
        channel.trySend(Event.Closed(kind, detail))
        channel.close()
    }

    companion object {
        /** ~3 s of 50 ms chunks spoken before the server was ready. */
        private const val MAX_PENDING = 60

        fun setup(model: String, voice: String, system: String, tools: List<JsonObject>) =
            LiveMessages.setup(model, voice, system, tools)
    }
}
