package com.adesh.devjarvis.brain

import org.junit.Assert.assertEquals
import org.junit.Test

class EffortTest {
    @Test fun simpleCommandsAreFast() {
        listOf("torch on karo", "Papa ko call karo", "YouTube kholo", "volume badhao", "kya haal hai")
            .forEach { assertEquals(it, Effort.FAST, Effort.forQuery(it)) }
    }

    @Test fun hardRequestsThinkMore() {
        listOf("iPhone aur Samsung compare karo", "mujhe samjhao blockchain kaise kaam karta hai", "is topic pe deep research karo")
            .forEach { assertEquals(it, Effort.DEEP, Effort.forQuery(it)) }
    }

    @Test fun longPlainRequestsAreNormal() {
        assertEquals(Effort.NORMAL, Effort.forQuery("kal subah saat baje mujhe utha dena aur uske baad mausam bata dena aur phir gaana chala dena"))
    }
}
