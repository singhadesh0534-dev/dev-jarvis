package com.adesh.devjarvis.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppMakerTest {
    private val page = "<!DOCTYPE html><html><head><title>x</title></head><body><button onclick=\"a()\">Add</button>" +
        "<script>function a(){localStorage.setItem('k','v')}</script>" + " ".repeat(300) + "</body></html>"

    @Test fun findsTheDocumentInsideChatter() {
        val raw = "Here is your app:\n```html\n$page\n```\nEnjoy!"
        assertEquals(page, AppMaker.extractHtml(raw))
        assertNull(AppMaker.extractHtml("no html here"))
        assertTrue(AppMaker.looksComplete(AppMaker.extractHtml(raw)))
        assertFalse(AppMaker.looksComplete("<html><body></body></html>"))
    }

    @Test fun slugs() {
        assertEquals("kharcha-app", AppMaker.slug("Kharcha App!"))
        assertTrue(AppMaker.slug("खर्चा").startsWith("app-"))
    }
}
