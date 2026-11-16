package com.adesh.devjarvis.screen


class AgentUpgradeTest {
    private fun el(id: Int, text: String) = ScreenElement(id, text, "", "Button", "", true, false, false, false, false)

    @org.junit.Test fun verifierVerdicts() {
        org.junit.Assert.assertNull(AgentProtocol.notDone("YES"))
        org.junit.Assert.assertNull(AgentProtocol.notDone(""))
        org.junit.Assert.assertEquals("message not sent yet", AgentProtocol.notDone("NO, message not sent yet"))
    }

    @org.junit.Test fun fingerprintAndEyes() {
        val a = ScreenSnapshot("p", "App", listOf(el(1, "Send"), el(2, "Back")))
        val b = ScreenSnapshot("p", "App", listOf(el(1, "Sent"), el(2, "Back")))
        org.junit.Assert.assertNotEquals(AgentProtocol.fingerprint(a), AgentProtocol.fingerprint(b))
        org.junit.Assert.assertEquals(AgentProtocol.fingerprint(a), AgentProtocol.fingerprint(a.copy()))
        org.junit.Assert.assertTrue(AgentProtocol.needsEyes(a))
        org.junit.Assert.assertTrue(AgentProtocol.userPrompt("g", emptyList(), "s", plan = "1. open\n2. tap").contains("Plan: 1. open 2. tap"))
    }
}
