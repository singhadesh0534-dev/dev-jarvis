package com.adesh.devjarvis.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentMoodTest {
    @Test fun doneIsHappy() {
        assertEquals(AgentMood.HAPPY, AgentMoods.of("Ho gaya Boss, Wi-Fi band ✓"))
        assertEquals(AgentMood.HAPPY, AgentMoods.of("Badhiya! Kal 9 baje yaad dila dunga."))
    }

    @Test fun failureIsSadEvenWithDone() {
        assertEquals(AgentMood.SAD, AgentMoods.of("Sorry Boss, call nahi ho paya."))
        assertEquals(AgentMood.SAD, AgentMoods.of("Ye kaam nahi hua, network error aaya — ho gaya to bataunga"))
    }

    @Test fun surpriseAndAnger() {
        assertEquals(AgentMood.SURPRISED, AgentMoods.of("Arre, sach mein? 100 runs!"))
        assertEquals(AgentMood.ANGRY, AgentMoods.of("Ab gussa aa raha hai 😠"))
    }

    @Test fun loveOnlyWhenMeantOrInGfRole() {
        assertEquals(AgentMood.LOVE, AgentMoods.of("I love you too ❤"))
        assertEquals(AgentMood.LOVE, AgentMoods.of("Haan jaan, bolo", role = "gf"))
        assertEquals(AgentMood.NEUTRAL, AgentMoods.of("Haan jaan, bolo", role = "jarvis"))
    }

    @Test fun plainIsNeutral() {
        assertEquals(AgentMood.NEUTRAL, AgentMoods.of("Kal ka mausam 31 degree rahega."))
        assertEquals(AgentMood.NEUTRAL, AgentMoods.of(""))
    }
}
