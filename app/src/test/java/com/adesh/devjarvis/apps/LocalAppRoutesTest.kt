package com.adesh.devjarvis.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalAppRoutesTest {
    @Test fun pathsMapToApps() {
        assertEquals("/todo-list", LocalAppRoutes.path("GET /todo-list HTTP/1.1"))
        assertEquals("/a", LocalAppRoutes.path("GET /a?x=1 HTTP/1.1"))
        assertNull(LocalAppRoutes.path("POST /a HTTP/1.1"))
        assertEquals("todo-list", LocalAppRoutes.slugFor("/todo-list"))
        assertEquals("todo-list", LocalAppRoutes.slugFor("/todo-list/"))
        assertEquals("todo-list", LocalAppRoutes.slugFor("/todo-list.html"))
        assertNull(LocalAppRoutes.slugFor("/"))
        assertNull(LocalAppRoutes.slugFor("/../secret"))
        assertNull(LocalAppRoutes.slugFor("/favicon.ico"))
    }
}

class AppMakerKitTest {
    @Test fun recipesFollowSirsWords() {
        org.junit.Assert.assertTrue(AppMaker.recipeFor("ek kharcha app banao").startsWith("Recipe (expenses)"))
        org.junit.Assert.assertTrue(AppMaker.recipeFor("Todo list chahiye").startsWith("Recipe (todo)"))
        org.junit.Assert.assertEquals("", AppMaker.recipeFor("ek calculator"))
    }

    @Test fun lookVerdict() {
        org.junit.Assert.assertNull(AppMaker.lookProblems("OK"))
        org.junit.Assert.assertNull(AppMaker.lookProblems("ok."))
        org.junit.Assert.assertEquals("- Add button cut off", AppMaker.lookProblems("- Add button cut off"))
    }

    @Test fun testHooksGoFirstInHead() {
        val h = AppMaker.withTestHooks("<!DOCTYPE html><html><head><title>x</title></head><body></body></html>")
        org.junit.Assert.assertTrue(h.indexOf("window.onerror") < h.indexOf("<title>"))
        org.junit.Assert.assertEquals("- JS error: x is not defined (line 3)",
            AppMaker.errorReport(listOf("JS error: x is not defined (line 3)", "JARVIS_TEST_DONE", "JS error: x is not defined (line 3)")))
    }

    @Test fun keepsNewestVersions() {
        val names = (1..12).map { "${it * 1000}.html" }
        org.junit.Assert.assertEquals(listOf("2000.html", "1000.html"), AppMaker.versionsToPrune(names))
    }
}
