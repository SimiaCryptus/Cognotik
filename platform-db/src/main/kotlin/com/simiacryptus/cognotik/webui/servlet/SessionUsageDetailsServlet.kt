package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.models.ModelSchema.TokenTypes
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.SessionMetadata
import com.simiacryptus.cognotik.platform.UsageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.application.UserProviderImpl
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*

/**
 * Servlet that displays the per-usage-row details for a session, including the
 * recorded input and output text (when retained), token counts, cost, model,
 * timestamp, and the originating user.
 *
 * Query parameters:
 *   - session   (required): The session ID to view usage rows for
 *   - format    (optional): "json" or "html" (default: based on Accept header / JSON)
 *   - page      (optional): Page number (1-based, default 1)
 *   - pageSize  (optional): Page size (default 25, 1..1000)
 *   - sortBy    (optional): One of "time", "model", "user", "tokens", "cost" (default "time")
 *   - sortDir   (optional): "asc" or "desc" (default "desc")
 */
class SessionUsageDetailsServlet : HttpServlet() {
    private val metadataDB by lazy { ApplicationServices.fileApplicationServices().metadataDB }
    private val usageDB by lazy { ApplicationServices.fileApplicationServices().usageDB }

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val user = UserProviderImpl().authenticate(req, resp)
          ?: throw RuntimeException("User must be authenticated to view session usage details")

        val sessionId = req.getParameter("session")?.trim().orEmpty()
        if (sessionId.isEmpty()) {
            resp.status = HttpServletResponse.SC_BAD_REQUEST
            resp.contentType = "text/plain"
            resp.writer.write("Missing required 'session' query parameter")
            return
        }
        val session = Session(sessionId)

        // Authorization: ensure the user owns the session (or it is anonymous/shared).
        val metadata = try {
            metadataDB.getSessionMetadata(user, session)
        } catch (e: Exception) {
            log.warn("Failed to load metadata for session {}: {}", sessionId, e.message, e)
            null
        }

        val rows = try {
            usageDB.getSessionUsageRows(session)
        } catch (e: Exception) {
            log.error("Failed to load usage rows for session {}", sessionId, e)
            emptyList()
        }

        // Sort
        val sortBy = req.getParameter("sortBy")?.lowercase() ?: "time"
        val sortDir = req.getParameter("sortDir")?.lowercase() ?: "desc"
        val sortedRows = sortRows(rows, sortBy, sortDir)

        // Paginate
        val page = (req.getParameter("page")?.toIntOrNull() ?: 1).coerceAtLeast(1)
        val pageSize = (req.getParameter("pageSize")?.toIntOrNull() ?: 25).coerceIn(1, 1000)
        val totalCount = sortedRows.size
        val totalPages = if (totalCount == 0) 1 else ((totalCount + pageSize - 1) / pageSize)
        val effectivePage = page.coerceAtMost(totalPages)
        val fromIdx = ((effectivePage - 1) * pageSize).coerceAtLeast(0)
        val toIdx = (fromIdx + pageSize).coerceAtMost(totalCount)
        val pagedRows = if (fromIdx < toIdx) sortedRows.subList(fromIdx, toIdx) else emptyList()

        when (resolveFormat(req)) {
            Format.JSON -> writeJson(
                resp, user, sessionId, metadataName(metadata),
                pagedRows, totalCount, effectivePage, pageSize, totalPages, sortBy, sortDir
            )

            Format.HTML -> writeHtml(
                resp, user, sessionId, metadataName(metadata),
                pagedRows, totalCount, effectivePage, pageSize, totalPages, sortBy, sortDir
            )
        }
    }

    private fun metadataName(metadata: SessionMetadata?): String? =
        metadata?.name

    private fun sortRows(
        rows: List<UsageInterface.UsageRow>,
        sortBy: String,
        sortDir: String
    ): List<UsageInterface.UsageRow> {
        val comparator: Comparator<UsageInterface.UsageRow> = when (sortBy) {
            "model" -> compareBy(nullsLast()) { it.model?.lowercase() }
            "user" -> compareBy(nullsLast()) { it.userId?.lowercase() }
            "tokens" -> compareBy { it.tokenCounts.values.sum() }
            "cost" -> compareBy { it.cost }
            "time" -> compareBy(nullsLast()) { it.datetime?.toEpochMilli() }
            else -> compareBy(nullsLast()) { it.datetime?.toEpochMilli() }
        }
        val sorted = rows.sortedWith(comparator)
        return if (sortDir == "asc") sorted else sorted.reversed()
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
        sessionId: String,
        sessionName: String?,
        rows: List<UsageInterface.UsageRow>,
        totalCount: Int,
        page: Int,
        pageSize: Int,
        totalPages: Int,
        sortBy: String,
        sortDir: String
    ) {
        resp.contentType = "application/json"
        resp.characterEncoding = "UTF-8"
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"user\":").append(jsonString(user.email)).append(",")
        sb.append("\"sessionId\":").append(jsonString(sessionId)).append(",")
        sb.append("\"sessionName\":").append(jsonString(sessionName)).append(",")
        sb.append("\"totalCount\":").append(totalCount).append(",")
        sb.append("\"page\":").append(page).append(",")
        sb.append("\"pageSize\":").append(pageSize).append(",")
        sb.append("\"totalPages\":").append(totalPages).append(",")
        sb.append("\"sortBy\":").append(jsonString(sortBy)).append(",")
        sb.append("\"sortDir\":").append(jsonString(sortDir)).append(",")
        sb.append("\"count\":").append(rows.size).append(",")
        sb.append("\"rows\":[")
        rows.forEachIndexed { idx, row ->
            if (idx > 0) sb.append(",")
            sb.append("{")
            sb.append("\"id\":").append(row.id).append(",")
            sb.append("\"sessionId\":").append(jsonString(row.sessionId)).append(",")
            sb.append("\"userId\":").append(jsonString(row.userId)).append(",")
            sb.append("\"model\":").append(jsonString(row.model)).append(",")
            sb.append("\"datetime\":").append(row.datetime?.let { jsonString(isoInstant(it)) } ?: "null")
                .append(",")
            sb.append("\"cost\":").append(row.cost).append(",")
            sb.append("\"totalTokens\":").append(row.tokenCounts.values.sum()).append(",")
            sb.append("\"tokenCounts\":{")
            var first = true
            for ((type, count) in row.tokenCounts) {
                if (!first) sb.append(",")
                first = false
                sb.append(jsonString(type.name)).append(":").append(count)
            }
            sb.append("},")
            sb.append("\"inputText\":").append(jsonString(row.inputText)).append(",")
            sb.append("\"outputText\":").append(jsonString(row.outputText))
            sb.append("}")
        }
        sb.append("]")
        sb.append("}")
        resp.writer.write(sb.toString())
    }

    private fun writeHtml(
        resp: HttpServletResponse,
        user: User,
        sessionId: String,
        sessionName: String?,
        rows: List<UsageInterface.UsageRow>,
        totalCount: Int,
        page: Int,
        pageSize: Int,
        totalPages: Int,
        sortBy: String,
        sortDir: String
    ) {
        resp.contentType = "text/html"
        resp.characterEncoding = "UTF-8"
        val html = buildString {
            append("<!DOCTYPE html>\n")
            append("<html lang=\"en\"><head>\n")
            append("<meta charset=\"UTF-8\">\n")
            append("<title>Session Usage Details</title>\n")
            append("<style>\n")
            append(CSS)
            append("</style>\n")
            append("</head><body>\n")
            append("<div class=\"container\">\n")
            append("<header>\n")
            append("<h1>Usage Details</h1>\n")
            append("<div class=\"meta\">\n")
            append("<span class=\"badge\">User: ").append(htmlEscape(user.email)).append("</span>\n")
            append("<span class=\"badge\">Session: ")
                .append(htmlEscape(sessionName ?: sessionId))
                .append("</span>\n")
            append("<span class=\"badge\">Rows: ").append(totalCount).append("</span>\n")
            append("<select id=\"theme-selector\" class=\"theme-selector\" aria-label=\"Theme\">\n")
            append("<option value=\"auto\">Auto</option>\n")
            append("<option value=\"light\">Light</option>\n")
            append("<option value=\"dark\">Dark</option>\n")
            append("</select>\n")
            append("<a class=\"link\" href=\"?session=").append(htmlEscape(sessionId))
                .append("&format=json&page=").append(page)
                .append("&pageSize=").append(pageSize)
                .append("&sortBy=").append(htmlEscape(sortBy))
                .append("&sortDir=").append(htmlEscape(sortDir))
                .append("\">View as JSON</a>\n")
            append("<a class=\"link\" href=\"/sessions\">⬅ All Sessions</a>\n")
            append("</div>\n")
            append("</header>\n")

            if (totalCount == 0) {
                append("<div class=\"empty\">No usage rows recorded for this session.</div>\n")
            } else {
                // Summary KPIs
                val totalCost = rows.sumOf { it.cost }
                val totalTokens = rows.sumOf { it.tokenCounts.values.sum() }
                val typeSums = mutableMapOf<TokenTypes, Long>()
                for (r in rows) for ((t, c) in r.tokenCounts) {
                    typeSums[t] = (typeSums[t] ?: 0L) + c
                }
                append("<div class=\"kpis\">\n")
                append("<div class=\"kpi\"><div class=\"kpi-label\">Rows on Page</div><div class=\"kpi-value\">")
                    .append(rows.size).append("</div></div>\n")
                append("<div class=\"kpi\"><div class=\"kpi-label\">Total Tokens (page)</div><div class=\"kpi-value\">")
                    .append(formatNumber(totalTokens)).append("</div></div>\n")
                for ((t, v) in typeSums.entries.sortedBy { it.key.name }) {
                    if (v == 0L) continue
                    append("<div class=\"kpi\"><div class=\"kpi-label\">")
                        .append(htmlEscape(friendlyTokenTypeName(t)))
                        .append("</div><div class=\"kpi-value\">")
                        .append(formatNumber(v))
                        .append("</div></div>\n")
                }
                append("<div class=\"kpi\"><div class=\"kpi-label\">Cost (page)</div><div class=\"kpi-value\">")
                    .append(formatCost(totalCost)).append("</div></div>\n")
                append("</div>\n")

                append("<div class=\"toolbar\">\n")
                append("<input id=\"filter\" type=\"text\" placeholder=\"Filter rows...\" onkeyup=\"filterRows()\">\n")
                append("<button type=\"button\" class=\"btn\" onclick=\"expandAll()\" title=\"Expand all rows\">Expand All</button>\n")
                append("<button type=\"button\" class=\"btn\" onclick=\"collapseAll()\" title=\"Collapse all rows\">Collapse All</button>\n")
                append("<div class=\"pagesize\">\n")
                append("<label for=\"pageSizeSelect\">Page size:</label>\n")
                append("<select id=\"pageSizeSelect\" onchange=\"changePageSize(this.value)\">\n")
                for (sz in listOf(10, 25, 50, 100, 250)) {
                    append("<option value=\"").append(sz).append("\"")
                    if (sz == pageSize) append(" selected")
                    append(">").append(sz).append("</option>\n")
                }
                append("</select>\n")
                append("</div>\n")
                append("</div>\n")

                append("<table id=\"usage-rows\">\n")
                append("<thead><tr>")
                appendSortableHeader(this, "Time", "time", sortBy, sortDir, page, pageSize, sessionId)
                appendSortableHeader(this, "Model", "model", sortBy, sortDir, page, pageSize, sessionId)
                appendSortableHeader(this, "User", "user", sortBy, sortDir, page, pageSize, sessionId)
                appendSortableHeader(this, "Tokens", "tokens", sortBy, sortDir, page, pageSize, sessionId, true)
                appendSortableHeader(this, "Cost", "cost", sortBy, sortDir, page, pageSize, sessionId, true)
                append("<th>Details</th>")
                append("</tr></thead>\n")
                append("<tbody>\n")
                for (row in rows) {
                    val rowId = "row-${row.id}"
                    val rowTokens = row.tokenCounts.values.sum()
                    append("<tr class=\"clickable\" onclick=\"toggleDetails('").append(rowId).append("')\">")
                    append("<td>").append(htmlEscape(row.datetime?.let { isoInstant(it) } ?: "")).append("</td>")
                    append("<td class=\"mono\">").append(htmlEscape(row.model ?: "")).append("</td>")
                    append("<td>").append(htmlEscape(row.userId ?: "")).append("</td>")
                    append("<td class=\"num\">").append(formatNumber(rowTokens)).append("</td>")
                    append("<td class=\"num\">").append(formatCost(row.cost)).append("</td>")
                    append("<td><a class=\"link\" href=\"javascript:void(0)\" onclick=\"event.stopPropagation();toggleDetails('")
                        .append(rowId).append("')\">Show</a></td>")
                    append("</tr>\n")
                    // Detail row
                    append("<tr id=\"").append(rowId)
                        .append("\" class=\"details\" style=\"display:none\"><td colspan=\"6\">\n")
                    append("<div class=\"details-content\">\n")
                    // Metadata table
                    append("<table class=\"sub\">\n")
                    append("<tr><th>ID</th><td class=\"mono\">").append(row.id).append("</td></tr>\n")
                    append("<tr><th>Session ID</th><td class=\"mono\">").append(htmlEscape(row.sessionId ?: ""))
                        .append("</td></tr>\n")
                    append("<tr><th>Model</th><td class=\"mono\">").append(htmlEscape(row.model ?: ""))
                        .append("</td></tr>\n")
                    append("<tr><th>User</th><td>").append(htmlEscape(row.userId ?: "")).append("</td></tr>\n")
                    append("<tr><th>Time</th><td>").append(htmlEscape(row.datetime?.let { isoInstant(it) } ?: ""))
                        .append("</td></tr>\n")
                    append("<tr><th>Cost</th><td class=\"num\">").append(formatCost(row.cost))
                        .append("</td></tr>\n")
                    append("<tr><th>Total Tokens</th><td class=\"num\">").append(formatNumber(rowTokens))
                        .append("</td></tr>\n")
                    append("</table>\n")
                    // Token counts table
                    if (row.tokenCounts.isNotEmpty()) {
                        append("<h4>Token Counts</h4>\n")
                        append("<table class=\"sub\">\n")
                        append("<thead><tr><th>Type</th><th class=\"num\">Count</th></tr></thead>\n")
                        append("<tbody>\n")
                        for ((t, c) in row.tokenCounts.entries.sortedBy { it.key.name }) {
                            append("<tr><td>").append(htmlEscape(friendlyTokenTypeName(t)))
                                .append("</td><td class=\"num\">").append(formatNumber(c)).append("</td></tr>\n")
                        }
                        append("</tbody>\n")
                        append("</table>\n")
                    }
                    // Input text
                    append("<h4>Input <button type=\"button\" class=\"btn small\" onclick=\"copyText('")
                        .append(rowId).append("-in')\">Copy</button></h4>\n")
                    if (row.inputText.isNullOrEmpty()) {
                        append("<div class=\"empty-inline\">No input text retained.</div>\n")
                    } else {
                        append("<pre id=\"").append(rowId).append("-in\" class=\"text-block\">")
                            .append(htmlEscape(row.inputText)).append("</pre>\n")
                    }
                    // Output text
                    append("<h4>Output <button type=\"button\" class=\"btn small\" onclick=\"copyText('")
                        .append(rowId).append("-out')\">Copy</button></h4>\n")
                    if (row.outputText.isNullOrEmpty()) {
                        append("<div class=\"empty-inline\">No output text retained.</div>\n")
                    } else {
                        append("<pre id=\"").append(rowId).append("-out\" class=\"text-block\">")
                            .append(htmlEscape(row.outputText)).append("</pre>\n")
                    }
                    append("</div>\n")
                    append("</td></tr>\n")
                }
                append("</tbody>\n")
                append("</table>\n")
                // Pagination
                appendPagination(this, page, pageSize, totalPages, totalCount, sortBy, sortDir, sessionId)
            }
            append("</div>\n")
            append("<script src=\"/modules/theme.js\"></script>\n")
            append("<script>\n")
            append(JS)
            append("</script>\n")
            append("</body></html>")
        }
        resp.writer.write(html)
    }

    private fun appendSortableHeader(
        sb: StringBuilder,
        label: String,
        key: String,
        currentSortBy: String,
        currentSortDir: String,
        page: Int,
        pageSize: Int,
        sessionId: String,
        numeric: Boolean = false
    ) {
        val isCurrent = currentSortBy == key
        val nextDir = if (isCurrent && currentSortDir == "asc") "desc" else if (isCurrent) "asc" else "desc"
        val indicator = if (isCurrent) (if (currentSortDir == "asc") " ▲" else " ▼") else ""
        val cls = if (numeric) "num sortable" else "sortable"
        sb.append("<th class=\"").append(cls).append("\">")
        sb.append("<a class=\"sort-link\" href=\"?session=").append(htmlEscape(sessionId))
            .append("&page=1&pageSize=").append(pageSize)
            .append("&sortBy=").append(key)
            .append("&sortDir=").append(nextDir)
            .append("\">").append(label).append(indicator).append("</a>")
        sb.append("</th>")
    }

    private fun appendPagination(
        sb: StringBuilder,
        page: Int,
        pageSize: Int,
        totalPages: Int,
        totalCount: Int,
        sortBy: String,
        sortDir: String,
        sessionId: String
    ) {
        sb.append("<nav class=\"pagination\">\n")
        sb.append("<span class=\"page-info\">Page ").append(page).append(" of ").append(totalPages)
            .append(" (").append(totalCount).append(" rows)</span>\n")
        sb.append("<div class=\"page-links\">\n")
        val baseQuery =
            "session=${htmlEscape(sessionId)}&pageSize=$pageSize&sortBy=${htmlEscape(sortBy)}&sortDir=${
                htmlEscape(
                    sortDir
                )
            }"
        if (page > 1) {
            sb.append("<a class=\"page-link\" href=\"?page=1&").append(baseQuery).append("\">« First</a>\n")
            sb.append("<a class=\"page-link\" href=\"?page=").append(page - 1).append("&").append(baseQuery)
                .append("\">‹ Prev</a>\n")
        } else {
            sb.append("<span class=\"page-link disabled\">« First</span>\n")
            sb.append("<span class=\"page-link disabled\">‹ Prev</span>\n")
        }
        val windowSize = 5
        val start = (page - windowSize / 2).coerceAtLeast(1)
        val end = (start + windowSize - 1).coerceAtMost(totalPages)
        val realStart = (end - windowSize + 1).coerceAtLeast(1)
        for (p in realStart..end) {
            if (p == page) {
                sb.append("<span class=\"page-link current\">").append(p).append("</span>\n")
            } else {
                sb.append("<a class=\"page-link\" href=\"?page=").append(p).append("&").append(baseQuery)
                    .append("\">").append(p).append("</a>\n")
            }
        }
        if (page < totalPages) {
            sb.append("<a class=\"page-link\" href=\"?page=").append(page + 1).append("&").append(baseQuery)
                .append("\">Next ›</a>\n")
            sb.append("<a class=\"page-link\" href=\"?page=").append(totalPages).append("&").append(baseQuery)
                .append("\">Last »</a>\n")
        } else {
            sb.append("<span class=\"page-link disabled\">Next ›</span>\n")
            sb.append("<span class=\"page-link disabled\">Last »</span>\n")
        }
        sb.append("</div>\n")
        sb.append("</nav>\n")
    }

    private fun friendlyTokenTypeName(type: TokenTypes): String {
        val name = type.name
        val sb = StringBuilder(name.length + 4)
        for ((i, c) in name.withIndex()) {
            if (i > 0 && c.isUpperCase() && name[i - 1].isLowerCase()) sb.append(' ')
            sb.append(c)
        }
        return sb.toString()
    }

    private fun formatNumber(n: Long): String = String.format(Locale.US, "%,d", n)

    private fun formatCost(c: Double): String =
        if (c == 0.0) "$0.0000" else String.format(Locale.US, "$%.4f", c)

    private fun isoInstant(i: Instant): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z")
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(i.toEpochMilli()))
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

    private enum class Format { JSON, HTML }

    companion object {
        private val log = LoggerFactory.getLogger(SessionUsageDetailsServlet::class.java)

        private const val CSS = """
            * { box-sizing: border-box; }
            :root {
                --bg: #f5f7fa;
                --fg: #1f2937;
                --header-fg: #111827;
                --card-bg: #ffffff;
                --muted-fg: #6b7280;
                --subtle-fg: #374151;
                --border: #e5e7eb;
                --border-strong: #d1d5db;
                --hover-bg: #f9fafb;
                --badge-bg: #e5e7eb;
                --badge-fg: #374151;
                --link: #2563eb;
                --details-bg: #f3f4f6;
                --input-bg: #ffffff;
                --shadow: 0 1px 3px rgba(0,0,0,0.06);
                --disabled-fg: #d1d5db;
                --disabled-bg: #f9fafb;
                --primary: #2563eb;
                --primary-fg: #ffffff;
                --code-bg: #f3f4f6;
            }
            [data-theme="dark"] {
                --bg: #0f172a;
                --fg: #e5e7eb;
                --header-fg: #f9fafb;
                --card-bg: #1e293b;
                --muted-fg: #94a3b8;
                --subtle-fg: #cbd5e1;
                --border: #334155;
                --border-strong: #475569;
                --hover-bg: #273449;
                --badge-bg: #334155;
                --badge-fg: #e5e7eb;
                --link: #60a5fa;
                --details-bg: #172033;
                --input-bg: #0f172a;
                --shadow: 0 1px 3px rgba(0,0,0,0.4);
                --disabled-fg: #475569;
                --disabled-bg: #1e293b;
                --primary: #3b82f6;
                --primary-fg: #ffffff;
                --code-bg: #0b1220;
            }
            body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                margin: 0; padding: 0; background: var(--bg); color: var(--fg); }
            .container { max-width: 1400px; margin: 0 auto; padding: 24px; }
            header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
            header h1 { margin: 0; font-size: 24px; color: var(--header-fg); }
            .meta { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
            .badge { background: var(--badge-bg); color: var(--badge-fg); padding: 4px 10px; border-radius: 12px; font-size: 13px; }
            .link { color: var(--link); text-decoration: none; font-size: 13px; }
            .link:hover { text-decoration: underline; }
            .theme-selector { padding: 4px 8px; border: 1px solid var(--border-strong); border-radius: 6px; font-size: 13px; background: var(--input-bg); color: var(--fg); }
            .toolbar { margin-bottom: 12px; display: flex; gap: 12px; align-items: center; flex-wrap: wrap; justify-content: space-between; }
            .pagesize { display: flex; gap: 6px; align-items: center; font-size: 13px; color: var(--subtle-fg); }
            .pagesize select { padding: 6px 8px; border: 1px solid var(--border-strong); border-radius: 6px; font-size: 13px; background: var(--input-bg); color: var(--fg); }
            #filter { flex: 1; max-width: 320px; padding: 8px 12px; border: 1px solid var(--border-strong); border-radius: 6px; font-size: 14px; background: var(--input-bg); color: var(--fg); }
            table { width: 100%; border-collapse: collapse; background: var(--card-bg); border-radius: 8px; overflow: hidden; box-shadow: var(--shadow); }
            th, td { padding: 10px 12px; text-align: left; border-bottom: 1px solid var(--border); font-size: 14px; vertical-align: top; }
            th { background: var(--hover-bg); font-weight: 600; color: var(--subtle-fg); font-size: 12px; text-transform: uppercase; letter-spacing: 0.05em; }
            th.sortable .sort-link { color: var(--subtle-fg); text-decoration: none; display: block; }
            th.sortable .sort-link:hover { color: var(--link); }
            tbody tr:hover { background: var(--hover-bg); }
            .mono { font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; font-size: 12px; }
            .num { text-align: right; font-variant-numeric: tabular-nums; }
            .empty { padding: 40px; text-align: center; color: var(--muted-fg); background: var(--card-bg); border-radius: 8px; }
            .empty-inline { padding: 8px; color: var(--muted-fg); font-size: 13px; font-style: italic; }
            .details td { background: var(--details-bg); }
            .details-content { padding: 8px 4px; }
            .details-content h4 { margin: 12px 0 6px; font-size: 13px; color: var(--subtle-fg); display: flex; gap: 8px; align-items: center; }
            table.sub { width: auto; box-shadow: none; background: transparent; }
            table.sub th { background: transparent; text-transform: none; letter-spacing: normal; font-size: 13px; padding: 4px 8px 4px 0; }
            table.sub td { padding: 4px 8px; border: none; }
            tr.clickable { cursor: pointer; }
            .pagination { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; margin-top: 16px; padding: 12px 0; }
            .page-info { font-size: 13px; color: var(--muted-fg); }
            .page-links { display: flex; gap: 4px; flex-wrap: wrap; }
            .page-link { padding: 6px 10px; border: 1px solid var(--border-strong); border-radius: 6px; font-size: 13px; color: var(--subtle-fg); text-decoration: none; background: var(--card-bg); }
            .page-link:hover:not(.disabled):not(.current) { background: var(--hover-bg); border-color: var(--muted-fg); }
            .page-link.current { background: var(--primary); color: var(--primary-fg); border-color: var(--primary); font-weight: 600; }
            .page-link.disabled { color: var(--disabled-fg); background: var(--disabled-bg); cursor: not-allowed; }
            .btn { padding: 6px 10px; border: 1px solid var(--border-strong); border-radius: 6px; font-size: 13px; background: var(--card-bg); color: var(--fg); cursor: pointer; font-family: inherit; }
            .btn:hover { background: var(--hover-bg); border-color: var(--muted-fg); }
            .btn:active { transform: translateY(1px); }
            .btn.small { padding: 2px 8px; font-size: 12px; }
            .btn.copied { background: var(--primary); color: var(--primary-fg); border-color: var(--primary); }
            .kpis { display: flex; flex-wrap: wrap; gap: 8px; margin: 4px 0 16px; }
            .kpi { background: var(--card-bg); border: 1px solid var(--border); border-radius: 6px; padding: 8px 12px; min-width: 110px; box-shadow: var(--shadow); }
            .kpi-label { font-size: 11px; color: var(--muted-fg); text-transform: uppercase; letter-spacing: 0.05em; }
            .kpi-value { font-size: 16px; font-weight: 600; color: var(--header-fg); font-variant-numeric: tabular-nums; margin-top: 2px; }
            pre.text-block {
                background: var(--code-bg); border: 1px solid var(--border); border-radius: 6px;
                padding: 12px; max-height: 480px; overflow: auto; white-space: pre-wrap; word-wrap: break-word;
                font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
                font-size: 12px; line-height: 1.5;
            }
        """

        private const val JS = """
            (function() {
                if (typeof ThemeManager !== 'undefined') {
                    ThemeManager.init();
                    var sel = document.getElementById('theme-selector');
                    if (sel && typeof ThemeManager.bindSelector === 'function') {
                        ThemeManager.bindSelector(sel);
                    }
                }
            })();
            function toggleDetails(id) {
                var row = document.getElementById(id);
                if (!row) return;
                row.style.display = (row.style.display === 'none' || !row.style.display) ? 'table-row' : 'none';
            }
            function expandAll() {
                var rows = document.querySelectorAll('tr.details');
                for (var i = 0; i < rows.length; i++) rows[i].style.display = 'table-row';
            }
            function collapseAll() {
                var rows = document.querySelectorAll('tr.details');
                for (var i = 0; i < rows.length; i++) rows[i].style.display = 'none';
            }
            function changePageSize(size) {
                var url = new URL(window.location.href);
                url.searchParams.set('pageSize', size);
                url.searchParams.set('page', '1');
                window.location.href = url.toString();
            }
            function filterRows() {
                var q = document.getElementById('filter').value.toLowerCase();
                var rows = document.querySelectorAll('#usage-rows tbody tr');
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
            function copyText(id) {
                var el = document.getElementById(id);
                if (!el) return;
                var text = el.innerText || el.textContent || '';
                var doneClass = function(btn) {
                    if (!btn) return;
                    var orig = btn.innerHTML;
                    btn.innerHTML = '✓ Copied';
                    btn.classList.add('copied');
                    setTimeout(function() { btn.innerHTML = orig; btn.classList.remove('copied'); }, 1200);
                };
                var btn = (event && event.target) ? event.target : null;
                if (navigator.clipboard && navigator.clipboard.writeText) {
                    navigator.clipboard.writeText(text).then(function() { doneClass(btn); });
                } else {
                    try {
                        var ta = document.createElement('textarea');
                        ta.value = text;
                        ta.style.position = 'fixed'; ta.style.left = '-9999px';
                        document.body.appendChild(ta); ta.select();
                        document.execCommand('copy');
                        document.body.removeChild(ta);
                        doneClass(btn);
                    } catch(e) {}
                }
            }
        """
    }
}