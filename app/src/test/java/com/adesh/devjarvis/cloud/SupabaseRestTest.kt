package com.adesh.devjarvis.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseRestTest {
    @Test fun urlsAndBodies() {
        assertTrue(SupabaseRest.valid("https://abc.supabase.co", "x".repeat(40)))
        assertFalse(SupabaseRest.valid("http://abc.supabase.co", "x".repeat(40)))
        assertEquals("https://abc.supabase.co/rest/v1/jarvis_backup?id=eq.jv-abc&select=data,updated_at", SupabaseRest.selectUrl("https://abc.supabase.co/", "jv-abc"))
        assertTrue(SupabaseRest.upsertBody("jv-abc", "{\"a\":1}").contains("\"id\":\"jv-abc\""))
        assertTrue(SupabaseRest.validCode(SupabaseRest.newSyncCode()))
        assertFalse(SupabaseRest.validCode("short"))
        assertEquals("{\"app\":\"dev-jarvis\"}", SupabaseRest.snapshotFrom("""[{"data":"{\"app\":\"dev-jarvis\"}","updated_at":"x"}]"""))
        assertNull(SupabaseRest.snapshotFrom("[]"))
    }
}
