package com.adesh.devjarvis.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateInfoTest {
    @Test fun parsesRelease() {
        val json = """{"tag_name":"jarvis-latest","body":"Auto-built from main @ abc1234.\nversionCode=142\nversionName=1.0.42",
            "assets":[{"name":"JARVIS.apk","size":57049349,"browser_download_url":"https://x/JARVIS.apk"},
                      {"name":"JARVIS-lite.apk","size":56000000,"browser_download_url":"https://x/JARVIS-lite.apk"}]}"""
        val info = UpdateInfo.parse(json)!!
        assertEquals(142, info.versionCode)
        assertEquals("1.0.42", info.versionName)
        assertEquals("https://x/JARVIS-lite.apk", info.assets["JARVIS-lite.apk"]!!.url)
        assertEquals(57049349L, info.assets["JARVIS.apk"]!!.sizeBytes)
    }

    @Test fun oldReleaseWithoutVersionIsNull() {
        assertNull(UpdateInfo.parse("""{"body":"Auto-built from x","assets":[]}"""))
        assertNull(UpdateInfo.parse("not json"))
    }
}
