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
