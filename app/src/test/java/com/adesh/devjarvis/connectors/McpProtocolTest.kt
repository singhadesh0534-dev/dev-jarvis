package com.adesh.devjarvis.connectors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpProtocolTest {
    @Test fun readsPlainAndStreamedResponses() {
        val plain = """{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"gmail_send","description":"Send mail","inputSchema":{"type":"object","properties":{"to":{},"body":{}}}}]}}"""
        val tools = McpProtocol.tools(McpProtocol.response(plain, 2)!!)
        assertEquals("gmail_send", tools.single().name)
        assertEquals(listOf("to", "body"), tools.single().params)
        val sse = "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"3 new mails\"}]}}\n\n"
        assertEquals("3 new mails", McpProtocol.resultText(McpProtocol.response(sse, 3)!!))
    }

    @Test fun errorsAndRisk() {
        val e = McpProtocol.response("""{"jsonrpc":"2.0","id":1,"error":{"code":-32601,"message":"Method not found"}}""", 1)
        assertNotNull(e)
        assertEquals("Method not found (-32601)", McpProtocol.error(e!!))
        assertTrue(McpProtocol.risky("gmail_send_email"))
        assertTrue(McpProtocol.risky("delete_event"))
        assertFalse(McpProtocol.risky("search_emails"))
        assertEquals("x", McpProtocol.arguments("""{"q":"x"}""")["q"].toString().trim('"'))
        assertTrue(McpProtocol.arguments("broken").isEmpty())
    }
}
