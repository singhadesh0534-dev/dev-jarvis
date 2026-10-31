package com.adesh.devjarvis.live

import com.adesh.devjarvis.live.LiveMessages.ServerEvent
import com.adesh.devjarvis.tools.ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class LiveMessagesTest {

    @Test fun setupShape() {
        val decl = Json.parseToJsonElement("""{"name":"lock_screen","description":"Lock"}""").jsonObject
        val root = Json.parseToJsonElement(LiveMessages.setup("gemini-3.8-live", "Charon", "You are JARVIS", listOf(decl))).jsonObject["setup"]!!.jsonObject
        assertEquals("models/gemini-3.8-live", root["model"]!!.jsonPrimitive.content)
        val gen = root["generationConfig"]!!.jsonObject
        assertEquals("AUDIO", gen["responseModalities"]!!.jsonArray[0].jsonPrimitive.content)
        assertEquals("Charon", gen["speechConfig"]!!.jsonObject["voiceConfig"]!!.jsonObject["prebuiltVoiceConfig"]!!.jsonObject["voiceName"]!!.jsonPrimitive.content)
        assertEquals("lock_screen", root["tools"]!!.jsonArray[0].jsonObject["functionDeclarations"]!!.jsonArray[0].jsonObject["name"]!!.jsonPrimitive.content)
        assertTrue(root.containsKey("inputAudioTranscription"))
        assertTrue(root.containsKey("outputAudioTranscription"))
        // Already-prefixed model names are kept.
        assertTrue(LiveMessages.setup("models/x", "v", "s", emptyList()).contains("\"models/x\""))
    }

    @Test fun audioMessage() {
        val pcm = byteArrayOf(1, 2, 3, 4, 9, 9)
        val audio = Json.parseToJsonElement(LiveMessages.audio(pcm, 4, 16000)).jsonObject["realtimeInput"]!!.jsonObject["audio"]!!.jsonObject
        assertEquals("audio/pcm;rate=16000", audio["mimeType"]!!.jsonPrimitive.content)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), Base64.getDecoder().decode(audio["data"]!!.jsonPrimitive.content))
    }

    @Test fun parsesServerContentInOrder() {
        val b64 = Base64.getEncoder().encodeToString(byteArrayOf(5, 6))
        val raw = """{"serverContent":{"inputTranscription":{"text":"hello"},"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"$b64"}}]},"outputTranscription":{"text":"Good evening"},"turnComplete":true}}"""
        val ev = LiveMessages.parse(raw)
        assertEquals(4, ev.size)
        assertEquals(ServerEvent.InputText("hello"), ev[0])
        assertArrayEquals(byteArrayOf(5, 6), (ev[1] as ServerEvent.Audio).pcm)
        assertEquals(ServerEvent.OutputText("Good evening"), ev[2])
        assertEquals(ServerEvent.TurnComplete, ev[3])
    }

    @Test fun parsesSetupInterruptGoAway() {
        assertEquals(listOf(ServerEvent.SetupComplete), LiveMessages.parse("""{"setupComplete":{}}"""))
        assertEquals(listOf(ServerEvent.Interrupted), LiveMessages.parse("""{"serverContent":{"interrupted":true}}"""))
        assertEquals(listOf(ServerEvent.GoAway("10s")), LiveMessages.parse("""{"goAway":{"timeLeft":"10s"}}"""))
        assertTrue(LiveMessages.parse("not json").isEmpty())
    }

    @Test fun toolCallAndResponse() {
        val ev = LiveMessages.parse("""{"toolCall":{"functionCalls":[{"id":"f1","name":"set_timer","args":{"seconds":60}}]}}""")
        val calls = (ev.single() as ServerEvent.ToolCalls).calls
        assertEquals("set_timer", calls[0].name)
        val resp = Json.parseToJsonElement(LiveMessages.toolResponse(listOf(calls[0] to ToolResult.ok("timer started")))).jsonObject
        val fr = resp["toolResponse"]!!.jsonObject["functionResponses"]!!.jsonArray[0].jsonObject
        assertEquals("f1", fr["id"]!!.jsonPrimitive.content)
        assertEquals("timer started", fr["response"]!!.jsonObject["result"]!!.jsonPrimitive.content)
    }

    @Test fun classifiesCloseReasons() {
        assertEquals(LiveMessages.CloseKind.QUOTA, LiveMessages.classifyClose(1011, "You exceeded your current quota"))
        assertEquals(LiveMessages.CloseKind.AUTH, LiveMessages.classifyClose(1008, "API key not valid"))
        assertEquals(LiveMessages.CloseKind.MODEL, LiveMessages.classifyClose(1008, "models/foo is not found for API version v1beta"))
        assertEquals(LiveMessages.CloseKind.OTHER, LiveMessages.classifyClose(1000, "bye"))
    }
}
