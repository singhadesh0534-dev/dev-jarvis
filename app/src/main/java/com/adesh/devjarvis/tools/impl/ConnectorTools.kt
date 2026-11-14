package com.adesh.devjarvis.tools.impl

import com.adesh.devjarvis.connectors.ConnectorStore
import com.adesh.devjarvis.connectors.McpClient
import com.adesh.devjarvis.connectors.McpProtocol
import com.adesh.devjarvis.tools.JarvisTool
import com.adesh.devjarvis.tools.SimpleTool
import com.adesh.devjarvis.tools.ToolArgs
import com.adesh.devjarvis.tools.ToolContext
import com.adesh.devjarvis.tools.ToolResult
import com.adesh.devjarvis.tools.params
import javax.inject.Inject
import javax.inject.Singleton

/** Sir's connected services (MCP), like connectors in Claude / ChatGPT. */
@Singleton
class ConnectorTools @Inject constructor(
    private val store: ConnectorStore,
    private val client: McpClient,
) {
    private fun which(args: ToolArgs) = args.string("connector")?.let { store.find(it) }
    private fun names() = store.all.value.joinToString { it.name }.ifBlank { "(none yet: Sir adds them in + > Connectors)" }

    private val listTools = object : SimpleTool(
        "connector_tools",
        "List what one of Sir's connected services can do (Gmail, Calendar, Notion… added in Connectors). Call this first, " +
            "then connector_call.",
        params { string("connector", "Connector name as Sir added it", required = true) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val c = which(args) ?: return ToolResult.fail("no connector like that. Connected: ${names()}")
            val tools = runCatching { client.listTools(c) }.getOrElse { return ToolResult.fail("${c.name} not reachable: ${it.message}") }
            if (tools.isEmpty()) return ToolResult.fail("${c.name} offers no tools")
            return ToolResult.ok("${c.name} tools:\n" + tools.take(60).joinToString("\n") { t ->
                "- ${t.name}(${t.params.joinToString()}): ${t.description.take(140)}"
            })
        }
    }

    private val call = object : SimpleTool(
        "connector_call",
        "Run one tool of a connected service. arguments = a JSON object with the tool's parameters (see connector_tools). " +
            "Anything that sends, posts, changes or deletes is confirmed with Sir first by the app.",
        params {
            string("connector", "Connector name", required = true)
            string("tool", "Tool name from connector_tools", required = true)
            string("arguments", "JSON object of arguments, e.g. {\"query\":\"invoice\"}")
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val c = which(args) ?: return ToolResult.fail("no connector like that. Connected: ${names()}")
            val tool = args.requireString("tool")
            val arguments = McpProtocol.arguments(args.string("arguments"))
            if (McpProtocol.risky(tool) && !ctx.confirm("Sir, ${c.name} pe '$tool' chalaun?")) return ToolResult.ok("Sir said no — nothing was done")
            val out = runCatching { client.call(c, tool, arguments) }.getOrElse { return ToolResult.fail("${c.name}: ${it.message}") }
            return if (out.startsWith("TOOL ERROR")) ToolResult.fail(out.take(1500)) else ToolResult.ok(out.take(4000))
        }
    }

    val tools: List<JarvisTool> = listOf(listTools, call)
}
