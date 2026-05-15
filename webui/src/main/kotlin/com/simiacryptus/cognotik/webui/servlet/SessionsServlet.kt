package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.SessionMetadata
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.application.authenticate
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.text.SimpleDateFormat
import java.util.*

class SessionsServlet : HttpServlet() {
    val metadataDB by lazy { ApplicationServices.fileApplicationServices().metadataStorageFactory }
    val usageDB by lazy { ApplicationServices.fileApplicationServices().usageManager }

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val user = authenticate(req, resp) ?: throw RuntimeException("User must be authenticated to list sessions")
        val sessions = try {
            metadataDB.listSessions(user).map {Session(it)}
        } catch (e: Exception) {
            log.error("Failed to list sessions for user ${user.email}", e)
            emptyList()
        }
        val sessionParents = sessions.mapNotNull { session ->
            usageDB.getParentSession(session)?.sessionId?.let { parent -> session to Session(parent) }
        }.toMap()
        val sessionMetadata = sessions.mapNotNull { sessionId ->
            try {
                metadataDB.getSessionMetadata(user, sessionId)
            } catch (e: Exception) {
                log.warn("Failed to load metadata for session $sessionId", e)
                null
            }
        }.sortedByDescending { it.sessionTime?.time ?: 0L }

        when (resolveFormat(req)) {
            Format.JSON -> writeJson(resp, user, sessionMetadata, sessionParents)
            Format.HTML -> writeHtml(resp, user, sessionMetadata, sessionParents)
        }
    }

    private fun resolveFormat(req: HttpServletRequest): Format {
        val formatParam = req.getParameter("format")?.lowercase()
        if (formatParam != null) {
            return when (formatParam) {
                "json" -> Format.JSON
                "html" -> Format.HTML
                else -> Format.JSON
            }
        }
        val accept = req.getHeader("Accept") ?: ""
        return when {
            accept.contains("application/json", ignoreCase = true) -> Format.JSON
            accept.contains("text/html", ignoreCase = true) -> Format.HTML
            else -> Format.JSON
        }
    }

    private fun writeJson(
        resp: HttpServletResponse,
        user: User,
        sessions: List<SessionMetadata>,
        sessionParents: Map<Session, Session>
    ) {
        val sessions = sessions
            .filter { !sessionParents.containsKey(it.id) }
            .filter { !it.path.isNullOrBlank() }

        resp.contentType = "application/json"
        resp.characterEncoding = "UTF-8"
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"user\":").append(jsonString(user.email)).append(",")
        sb.append("\"count\":").append(sessions.size).append(",")
        sb.append("\"sessions\":[")
        sessions.forEachIndexed { idx, meta ->
            if (idx > 0) sb.append(",")
            sb.append("{")
            sb.append("\"id\":").append(jsonString(meta.id.sessionId)).append(",")
            sb.append("\"name\":").append(jsonString(meta.name)).append(",")
            sb.append("\"ownerId\":").append(jsonString(meta.ownerId)).append(",")
            sb.append("\"path\":").append(jsonString(meta.path)).append(",")
            sb.append("\"sessionTime\":").append(
                meta.sessionTime?.let { jsonString(isoDate(it)) } ?: "null"
            ).append(",")
            sb.append("\"messageCount\":").append(meta.messageIds.size).append(",")
            sb.append("\"messageIds\":[")
            meta.messageIds.forEachIndexed { i, mid ->
                if (i > 0) sb.append(",")
                sb.append(jsonString(mid))
            }
            sb.append("],")
            sb.append("\"additional\":{")
            meta.additional.entries.forEachIndexed { i, e ->
                if (i > 0) sb.append(",")
                sb.append(jsonString(e.key)).append(":").append(jsonString(e.value))
            }
            sb.append("}")
            sb.append("}")
        }
        sb.append("]")
        sb.append("}")
        resp.writer.write(sb.toString())
    }

    private fun writeHtml(
        resp: HttpServletResponse,
        user: User,
        sessions: List<SessionMetadata>,
        sessionParents: Map<Session, Session>
    ) {
        val sessions = sessions
            .filter { !sessionParents.containsKey(it.id) }
            .filter { !it.path.isNullOrBlank() }

        resp.contentType = "text/html"
        resp.characterEncoding = "UTF-8"
        val html = buildString {
            append("<!DOCTYPE html>\n")
            append("<html lang=\"en\"><head>\n")
            append("<meta charset=\"UTF-8\">\n")
            append("<title>Sessions</title>\n")
            append("<style>\n")
            append(CSS)
            append("</style>\n")
            append("</head><body>\n")
            append("<div class=\"container\">\n")
            append("<header>\n")
            append("<h1>Sessions</h1>\n")
            append("<div class=\"meta\">\n")
            append("<span class=\"badge\">User: ").append(htmlEscape(user.email)).append("</span>\n")
            append("<span class=\"badge\">Total: ").append(sessions.size).append("</span>\n")
            append("<a class=\"link\" href=\"?format=json\">View as JSON</a>\n")
            append("</div>\n")
            append("</header>\n")

            if (sessions.isEmpty()) {
                append("<div class=\"empty\">No sessions found.</div>\n")
            } else {
                append("<div class=\"toolbar\">\n")
                append("<input id=\"filter\" type=\"text\" placeholder=\"Filter sessions...\" onkeyup=\"filterRows()\">\n")
                append("</div>\n")
                append("<table id=\"sessions\">\n")
                append("<thead><tr>")
                append("<th>Name</th>")
                append("<th>Owner</th>")
                append("<th>Time</th>")
                append("<th>Actions</th>")
                append("</tr></thead>\n")
                append("<tbody>\n")
                for (meta in sessions) {
                    val id = meta.id.sessionId
                    val path = meta.path ?: ""
                    append("<tr class=\"clickable\" onclick=\"navigateTo('")
                        .append(jsEscape(path)).append("')\">")
                    append("<td>").append(htmlEscape(meta.name ?: id)).append("</td>")
                    append("<td>").append(htmlEscape(meta.ownerId ?: "")).append("</td>")
                    append("<td>").append(htmlEscape(meta.sessionTime?.let { isoDate(it) } ?: "")).append("</td>")
                    append("<td><a class=\"link\" href=\"#")
                        .append(htmlEscape(id))
                        .append("\" onclick=\"event.stopPropagation();toggleDetails('")
                        .append(jsEscape(id)).append("');return false;\">Details</a></td>")
                    append("</tr>\n")
                    append("<tr id=\"details-").append(htmlEscape(id))
                        .append("\" class=\"details\" style=\"display:none\">")
                    append("<td colspan=\"4\">")
                    append("<div class=\"details-content\">")
                    if (meta.additional.isNotEmpty()) {
                        append("<h4>Additional Metadata</h4>")
                        append("<table class=\"sub\"><tbody>")
                        for ((k, v) in meta.additional) {
                            append("<tr><th>").append(htmlEscape(k)).append("</th><td>")
                                .append(htmlEscape(v)).append("</td></tr>")
                        }
                        append("</tbody></table>")
                    }
                    if (meta.additional.isEmpty()) {
                        append("<p><em>No additional metadata.</em></p>")
                    }
                    append("</div>")
                    append("</td></tr>\n")
                }
                append("</tbody>\n")
                append("</table>\n")
            }
            append("</div>\n")
            append("<script>\n")
            append(JS)
            append("</script>\n")
            append("</body></html>")
        }
        resp.writer.write(html)
    }

    private fun jsonString(s: String?): String {
        if (s == null) return "null"
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c.code < 0x20) {
                    sb.append(String.format("\\u%04x", c.code))
                } else {
                    sb.append(c)
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }

    private fun htmlEscape(s: String?): String {
        if (s == null) return ""
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

    private fun jsEscape(s: String): String =
        s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r")

    private fun isoDate(d: Date): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z")
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(d)
    }

    private enum class Format { JSON, HTML }

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(SessionsServlet::class.java)

        private const val CSS = """
                * { box-sizing: border-box; }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                    margin: 0; padding: 0; background: #f5f7fa; color: #1f2937;
                }
                .container { max-width: 1200px; margin: 0 auto; padding: 24px; }
                header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
                header h1 { margin: 0; font-size: 24px; color: #111827; }
                .meta { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
                .badge { background: #e5e7eb; color: #374151; padding: 4px 10px; border-radius: 12px; font-size: 13px; }
                .link { color: #2563eb; text-decoration: none; font-size: 13px; }
                .link:hover { text-decoration: underline; }
                .toolbar { margin-bottom: 12px; }
                #filter { width: 100%; max-width: 320px; padding: 8px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; }
                table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 8px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,0.06); }
                th, td { padding: 10px 12px; text-align: left; border-bottom: 1px solid #e5e7eb; font-size: 14px; vertical-align: top; }
                th { background: #f9fafb; font-weight: 600; color: #374151; font-size: 12px; text-transform: uppercase; letter-spacing: 0.05em; }
                tbody tr:hover { background: #f9fafb; }
                .mono { font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; font-size: 12px; }
                .num { text-align: right; font-variant-numeric: tabular-nums; }
                .empty { padding: 40px; text-align: center; color: #6b7280; background: #fff; border-radius: 8px; }
                .details td { background: #f3f4f6; }
                .details-content { padding: 8px 4px; }
                .details-content h4 { margin: 8px 0 6px; font-size: 13px; color: #374151; }
                table.sub { width: auto; box-shadow: none; background: transparent; }
                table.sub th { background: transparent; text-transform: none; letter-spacing: normal; font-size: 13px; padding: 4px 8px 4px 0; }
                table.sub td { padding: 4px 8px; border: none; }
                tr.clickable { cursor: pointer; }
            """

        private const val JS = """
                function navigateTo(path) {
                   if (path) window.open(path, '_blank');
                }
                function toggleDetails(id) {
                    var row = document.getElementById('details-' + id);
                    if (!row) return;
                    row.style.display = (row.style.display === 'none' || !row.style.display) ? 'table-row' : 'none';
                }
                function filterRows() {
                    var q = document.getElementById('filter').value.toLowerCase();
                    var rows = document.querySelectorAll('#sessions tbody tr');
                    var i = 0;
                    while (i < rows.length) {
                        var row = rows[i];
                        if (row.classList.contains('details')) { i++; continue; }
                        var text = row.innerText.toLowerCase();
                        var match = text.indexOf(q) !== -1;
                        row.style.display = match ? '' : 'none';
                        if (i + 1 < rows.length && rows[i + 1].classList.contains('details')) {
                            if (!match) rows[i + 1].style.display = 'none';
                        }
                        i++;
                    }
                }
            """
    }
}