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
    fun normalQuestionsKeepConversationOpen() {
        assertFalse(ConversationRules.isEnder("bas ek kaam aur karo"))
        assertFalse(ConversationRules.isEnder("thank you ke baad kya bolte hain"))
        assertFalse(ConversationRules.isEnder("What's the weather?"))
    }
}
