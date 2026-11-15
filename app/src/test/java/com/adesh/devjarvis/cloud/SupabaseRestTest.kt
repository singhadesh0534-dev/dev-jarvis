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
        assertEquals("https://abc.supabase.co/rest/v1/jarvis_backup?id=eq.dev-jarvis&select=data,updated_at", SupabaseRest.selectUrl("https://abc.supabase.co/"))
        assertTrue(SupabaseRest.upsertBody("{\"a\":1}").contains("\"id\":\"dev-jarvis\""))
        assertEquals("{\"app\":\"dev-jarvis\"}", SupabaseRest.snapshotFrom("""[{"data":"{\"app\":\"dev-jarvis\"}","updated_at":"x"}]"""))
        assertNull(SupabaseRest.snapshotFrom("[]"))
    }
}
