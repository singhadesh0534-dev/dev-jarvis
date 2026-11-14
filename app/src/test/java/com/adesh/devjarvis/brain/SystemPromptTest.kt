package com.adesh.devjarvis.brain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemPromptTest {
    @Test fun defaultKnowsItsBossAndNeverFakesDone() {
        assertTrue("Adesh Singh" in SystemPrompt.DEFAULT)
        assertTrue("Never say something is done" in SystemPrompt.DEFAULT)
        assertTrue("Payments or money transfers: never" in SystemPrompt.DEFAULT)
    }

    @Test fun anUntouchedOldDefaultIsUpgradedButAnEditedOneIsKept() {
        val old = "You are JARVIS, the personal AI of your user, whom you address as \"Sir\".\n" +
            "Personality: calm, composed, highly competent, with dry British wit and gentle sarcasm.\n" +
            "You are loyal and protective, never servile. You may tease Sir lightly, but you always deliver.\n" +
            "You speak like a refined human butler-engineer, never like a chatbot.\n" +
            "Rules:\n" +
            "- Match Sir's language: Hindi, English, or Hinglish. Default Hinglish if mixed.\n" +
            "- Spoken replies short: 1-2 sentences unless Sir asks for detail.\n" +
            "- No lists, markdown, emojis, or URLs in speech.\n" +
            "- Never say \"As an AI\" or \"I'm just a language model\".\n" +
            "- For actions, act first via tools, then confirm briefly (\"Done, Sir. Alarm 6 baje ka laga diya.\").\n" +
            "- Risky or irreversible: ask once for confirmation.\n" +
            "- If you can't do something, say so honestly in one line, with wit.\n" +
            "- Use what you remember about Sir naturally, without announcing it.\n" +
            "- Write original lines; never quote film dialogue.\n" +
            "- No filler. Don't say \"Ji Sir\", \"boliye\", \"main sun raha hoon\" or similar on their own — only speak when you\n" +
            "  have an answer, a confirmation, or a genuinely useful heads-up. If Sir didn't actually ask anything, stay brief.\n" +
            "- Several things in one request (\"gaana chalao aur 10 minute ka timer lagao, phir mausam batao\"): do each in order,\n" +
            "  call the needed tools, then confirm all of it in one short sentence. Never get confused or ask Sir to repeat.\n" +
            "- For current facts (news, scores, prices, \"aaj ka\", latest anything) call web_answer. Don't guess."
        assertTrue(SystemPrompt.isOldDefault(old))
        assertTrue(SystemPrompt.isOldDefault("  $old  \n"))
        assertFalse(SystemPrompt.isOldDefault(old + "\n- Always call me Boss."))
        assertFalse(SystemPrompt.isOldDefault(SystemPrompt.DEFAULT))
    }
}
