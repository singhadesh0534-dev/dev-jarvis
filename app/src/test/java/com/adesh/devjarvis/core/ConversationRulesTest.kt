package com.adesh.devjarvis.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationRulesTest {
    @Test
    fun recognisesEnders() {
        assertTrue(ConversationRules.isEnder("Bas"))
        assertTrue(ConversationRules.isEnder("Thank you, Jarvis!"))
        assertTrue(ConversationRules.isEnder("that's all"))
        assertTrue(ConversationRules.isEnder("ok bye"))
        assertTrue(ConversationRules.isEnder("Shukriya sir"))
    }

    @Test
    fun notTalkingToYouEndsConversation() {
        assertTrue(ConversationRules.isEnder("Arre maine tumko nahi bola"))
        assertTrue(ConversationRules.isEnder("I wasn't talking to you, Jarvis"))
        assertTrue(ConversationRules.isNotForJarvis("तुमसे नहीं बोल रहा"))
        assertFalse(ConversationRules.isNotForJarvis("tumko bola tha torch on karo"))
    }

    @Test
    fun spotsComplaints() {
        assertTrue(ConversationRules.isComplaint("ye galat hai"))
        assertTrue(ConversationRules.isComplaint("Maine ye nahi bola tha"))
        assertTrue(ConversationRules.isComplaint("that's wrong Jarvis"))
        assertFalse(ConversationRules.isComplaint("Mummy ko call karo"))
        assertFalse(ConversationRules.isComplaint("kal ka mausam batao"))
    }

    @Test
    fun normalQuestionsKeepConversationOpen() {
        assertFalse(ConversationRules.isEnder("bas ek kaam aur karo"))
        assertFalse(ConversationRules.isEnder("thank you ke baad kya bolte hain"))
        assertFalse(ConversationRules.isEnder("What's the weather?"))
    }
}

class GoQuietTest {
    @Test fun sirSendingJarvisToSleepEndsTheConversation() {
        org.junit.Assert.assertTrue(ConversationRules.isEnder("Switch off kar lo, galat awaaz mat suno."))
        org.junit.Assert.assertTrue(ConversationRules.isEnder("chup ho jao"))
        org.junit.Assert.assertTrue(ConversationRules.isEnder("ab so jao Jarvis"))
        org.junit.Assert.assertTrue(ConversationRules.isEnder("सो जाओ"))
        org.junit.Assert.assertTrue(ConversationRules.isEnder("switch off kar lo"))
        org.junit.Assert.assertTrue(ConversationRules.isEnder("band karo"))
    }

    @Test fun switchingThingsOffIsNotSleep() {
        org.junit.Assert.assertFalse(ConversationRules.isEnder("wifi band karo"))
        org.junit.Assert.assertFalse(ConversationRules.isEnder("TV switch off karo"))
        org.junit.Assert.assertFalse(ConversationRules.isEnder("torch off kar do"))
        org.junit.Assert.assertFalse(ConversationRules.isEnder("Rahul ko bolo wo so jaaye"))
        org.junit.Assert.assertFalse(ConversationRules.isEnder("kya socha aapne"))
    }
}

class WrongVoicesTest {
    @Test fun sirSayingJarvisHearsOtherPeople() {
        org.junit.Assert.assertTrue(ConversationRules.wrongVoices("भैया, वह एक लोगों से बात करा रहे हैं। गलत लोग हैं।"))
        org.junit.Assert.assertTrue(ConversationRules.wrongVoices("galat awaaz mat suno"))
        org.junit.Assert.assertTrue(ConversationRules.wrongVoices("सुन नहीं रहे हो तुम गलत बात सुनते हो 10 जगह भीड़ में"))
        org.junit.Assert.assertTrue(ConversationRules.wrongVoices("sirf meri awaaz suno"))
        org.junit.Assert.assertFalse(ConversationRules.wrongVoices("galat alarm laga diya"))
        org.junit.Assert.assertFalse(ConversationRules.wrongVoices("mausam kaisa hai"))
    }
}

class DayTalkTest {
    @Test fun hisBadDayIsNotAComplaintAboutJarvis() {
        org.junit.Assert.assertFalse(ConversationRules.isComplaint("आज का दिन एकदम बेकार रहा यार।"))
        org.junit.Assert.assertTrue(ConversationRules.isComplaint("tum bekaar ho"))
        org.junit.Assert.assertTrue(ConversationRules.isComplaint("aaj ka din bekar tumne kar diya"))
    }
}

class ReasoningLeakTest {
    @Test fun plannedStepsAreCutFromWhatSirSees() {
        val leaked = "Maafi, Boss, woh option nahi mil raha. Despina try karein?\n    *   *Step 1: Check available voice names.* I checked."
        org.junit.Assert.assertEquals("Maafi, Boss, woh option nahi mil raha. Despina try karein?", ConversationRules.stripReasoning(leaked))
        org.junit.Assert.assertEquals("Ho gaya.", ConversationRules.stripReasoning("Ho gaya."))
    }

    @Test fun thinkingHeadingsAndThirdPersonAreCut() {
        val leaked = "Theek hai.\n    *   *Analysis of Tool Output:* The tool returned that the JARVIS role already"
        org.junit.Assert.assertEquals("Theek hai.", ConversationRules.stripReasoning(leaked))
        org.junit.Assert.assertTrue(ConversationRules.leaksReasoning("The user wants a new voice"))
        org.junit.Assert.assertEquals("", ConversationRules.stripReasoning("I need to call the tool again."))
        org.junit.Assert.assertFalse(ConversationRules.leaksReasoning("Haan Sir, plan ready hai, analysis kar liya."))
        org.junit.Assert.assertFalse(ConversationRules.leaksReasoning("Kal ka plan: subah 6 baje gym."))
    }
}
