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
