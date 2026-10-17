package com.simiacryptus.cognotik.webui.servlet

import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * Minimal classpath template loader. Templates live under /gifts/ and use `{{name}}` placeholders.
 * Substituted values are inserted verbatim (not re-scanned); callers must escape untrusted input.
 */
object GiftTemplates {
    private const val BASE = "/gifts/"
    private val cache = ConcurrentHashMap<String, String>()
    private val placeholder = Regex("""\{\{\s*([A-Za-z0-9_]+)\s*}}""")

    fun resource(name: String): String = cache.computeIfAbsent(name) { n ->
        GiftTemplates::class.java.getResourceAsStream(BASE + n)?.use {
            String(it.readAllBytes(), StandardCharsets.UTF_8)
        } ?: throw IllegalStateException("Missing resource $BASE$n")
    }

    fun render(name: String, vars: Map<String, Any?>): String =
        placeholder.replace(resource(name)) { m -> vars[m.groupValues[1]]?.toString() ?: "" }

    fun escape(value: Any?): String {
        val s = value?.toString() ?: return ""
        val sb = StringBuilder(s.length)
        for (c in s) {
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&#39;")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}