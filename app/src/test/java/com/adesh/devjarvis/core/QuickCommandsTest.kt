package com.adesh.devjarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickCommandsTest {
    private fun q(s: String) = QuickCommands.match(s)

    @Test fun torch() {
        assertEquals(mapOf("on" to "true"), q("Jarvis torch on karo")!!.args)
        assertEquals(mapOf("on" to "false"), q("flashlight band kar do")!!.args)
        assertEquals("toggle_flashlight", q("टॉर्च जलाओ")!!.tool)
        assertEquals(emptyMap<String, String>(), q("torch")!!.args)
    }

    @Test fun togglesNeedAClearOnOrOff() {
        assertEquals(mapOf("target" to "bluetooth", "on" to "true"), q("bluetooth on karo")!!.args)
        assertEquals(mapOf("target" to "wifi", "on" to "false"), q("Wi-Fi band karo")!!.args)
        assertEquals(mapOf("target" to "mobile_data", "on" to "true"), q("mobile data chalu karo")!!.args)
        assertEquals("open_app", q("wifi kholo")!!.tool) // no on/off: not a toggle; opening fails and the brain takes over
    }

    @Test fun volumeBrightnessTimer() {
        assertEquals(mapOf("action" to "up"), q("volume badhao")!!.args)
        assertEquals(mapOf("action" to "down"), q("awaaz kam karo")!!.args)
        assertEquals(mapOf("action" to "set", "level" to "40"), q("volume 40 kar do")!!.args)
        assertEquals(mapOf("action" to "mute"), q("sound mute karo")!!.args)
        assertEquals(mapOf("level" to "30"), q("brightness 30 karo")!!.args)
        assertEquals(mapOf("level" to "85"), q("brightness badhao")!!.args)
        assertEquals(mapOf("level" to "25"), q("brightness kam karo")!!.args)
        assertEquals(mapOf("seconds" to "300"), q("5 minute ka timer lagao")!!.args)
        assertEquals(mapOf("seconds" to "45"), q("45 second timer")!!.args)
        assertEquals(mapOf("seconds" to "7200"), q("2 ghante ka timer")!!.args)
    }

    @Test fun mediaAndGettingAround() {
        assertEquals(mapOf("action" to "pause"), q("gaana roko")!!.args)
        assertEquals(mapOf("action" to "pause"), q("music band karo")!!.args)
        assertEquals(mapOf("action" to "next"), q("agla gaana")!!.args)
        assertEquals(mapOf("action" to "previous"), q("previous")!!.args)
        assertEquals("go_home", q("home jao")!!.tool)
        assertEquals("go_back", q("back karo")!!.tool)
        assertEquals("lock_screen", q("phone lock karo")!!.tool)
        assertEquals("take_screenshot", q("screenshot lo")!!.tool)
        assertEquals("get_time", q("time kya hua hai")!!.tool)
        assertEquals("get_time", q("kitne baje hain")!!.tool)
    }

    @Test fun openApp() {
        assertEquals(mapOf("name" to "youtube"), q("YouTube kholo")!!.args)
        assertEquals(mapOf("name" to "play store"), q("open play store")!!.args)
        assertEquals("Youtube khol diya, Sir.", q("Jarvis, YouTube kholo")!!.say)
    }

    @Test fun anythingThatNeedsThinkingGoesToTheBrain() {
        listOf(
            "YouTube kholo aur Guru Randhawa search karo",
            "Papa ko call karo",
            "kal subah 6 baje ka alarm lagao aur mausam batao",
            "mujhe 5 minute baad yaad dilana paani peena hai",
            "torch kyun nahi jal rahi",
            "settings mein bluetooth kholo",
            "kya haal hai",
            "WhatsApp pe Rahul ko message bhejo",
            "",
        ).forEach { assertNull(it, q(it)) }
    }

    @Test fun moreSwitches() {
        assertEquals(mapOf("target" to "battery_saver", "on" to "true"), q("battery saver on karo")!!.args)
        assertEquals(mapOf("target" to "do_not_disturb", "on" to "false"), q("dnd band karo")!!.args)
        assertEquals(mapOf("target" to "dark_mode", "on" to "true"), q("dark mode on karo")!!.args)
        assertEquals("mobile_data", q("data on karo")!!.args["target"])
    }
}
