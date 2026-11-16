package com.adesh.devjarvis.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FactoryRestTest {
    @Test fun slugMatchesTheWorkflow() {
        assertEquals("todo-list", FactoryRest.slug("Todo List!"))
        assertEquals("app", FactoryRest.slug("!!!"))
        assertEquals("build: todo-list|Todo List", FactoryRest.commitMessage("todo-list", "Todo List"))
    }

    @Test fun putBodyCarriesTheHtmlAndBranch() {
        val b = FactoryRest.putBody("todo", "Todo", "<p>x</p>", "abc")
        assertTrue(b.contains("\"branch\":\"jarvis-apps\""))
        assertTrue(b.contains("\"sha\":\"abc\""))
        assertTrue(b.contains(java.util.Base64.getEncoder().encodeToString("<p>x</p>".toByteArray())))
        assertTrue(!FactoryRest.putBody("todo", "Todo", "x", null).contains("\"sha\""))
    }

    @Test fun parsesGithubAnswers() {
        assertEquals("s1", FactoryRest.refSha("""{"ref":"refs/heads/x","object":{"sha":"s1","type":"commit"}}"""))
        assertEquals("c1", FactoryRest.commitSha("""{"content":{"sha":"f"},"commit":{"sha":"c1"}}"""))
        val run = FactoryRest.factoryRun("""{"workflow_runs":[{"id":7,"name":"Build JARVIS APK","status":"queued"},{"id":9,"name":"JARVIS App Factory","status":"completed","conclusion":"success"}]}""")
        assertEquals(FactoryRest.Run(9, "completed", "success"), run)
        assertNull(FactoryRest.factoryRun("""{"workflow_runs":[]}"""))
        assertEquals("https://api.github.com/x/assets/5",
            FactoryRest.apkAssetUrl("""{"assets":[{"name":"other"},{"name":"app.apk","url":"https://api.github.com/x/assets/5"}]}"""))
    }
}
