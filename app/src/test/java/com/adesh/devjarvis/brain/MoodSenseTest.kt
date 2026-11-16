package com.adesh.devjarvis.brain

import org.junit.Assert.assertEquals
import org.junit.Test

class MoodSenseTest {
    @Test fun readsHinglishMoods() {
        assertEquals(MoodSense.Mood.LOW, MoodSense.of(listOf("aaj ka din bekar raha yaar")))
        assertEquals(MoodSense.Mood.STRESSED, MoodSense.of(listOf("bahut thak gaya hoon")))
        assertEquals(MoodSense.Mood.ANGRY, MoodSense.of(listOf("dimag kharab mat karo")))
        assertEquals(MoodSense.Mood.HAPPY, MoodSense.of(listOf("aaj promotion ho gaya!")))
        assertEquals(MoodSense.Mood.NEUTRAL, MoodSense.of(listOf("time kya hua")))
    }
}
