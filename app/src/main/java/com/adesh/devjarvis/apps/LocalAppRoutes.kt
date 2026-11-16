package com.adesh.devjarvis.apps

/** Which built app a local URL asks for: "/todo-list", "/todo-list/", "/todo-list.html" -> "todo-list". Pure, unit-tested. */
object LocalAppRoutes {
    private val SLUG = Regex("^[a-z0-9][a-z0-9-]{0,60}$")

    /** The request line "GET /x HTTP/1.1" -> the path "/x" (null if it is not a GET). */
    fun path(requestLine: String): String? {
        val parts = requestLine.trim().split(' ')
        if (parts.size < 2 || (parts[0] != "GET" && parts[0] != "HEAD")) return null
        return parts[1].substringBefore('?').substringBefore('#')
    }

    /** The app slug for a path, or null (root, favicon, anything outside the apps folder). */
    fun slugFor(path: String): String? {
        val p = path.trim('/').removeSuffix("/index.html").removeSuffix(".html").trim('/')
        return p.takeIf { SLUG.matches(it) }
    }
}
