package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.models.ModelSchema.TokenTypes
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.SessionMetadata
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.application.authenticate
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat
import java.util.*

class SessionsServlet : HttpServlet() {
    val metadataDB by lazy { ApplicationServices.fileApplicationServices().metadataDB }
    val usageDB by lazy { ApplicationServices.fileApplicationServices().usageDB }

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val user = authenticate(req, resp) ?: throw RuntimeException("User must be authenticated to list sessions")
        val sessions = try {
            metadataDB.listSessions(user).map { Session(it) }
        } catch (e: Exception) {
            log.error("Failed to list sessions for user ${user.email}", e)
            emptyList()
        }
        val sessionParents = sessions.mapNotNull { session ->
            usageDB.getParentSession(session)?.sessionId?.let { parent -> session to Session(parent) }
        }.toMap()
        val allMetadata = sessions.mapNotNull { sessionId ->
            try {
                metadataDB.getSessionMetadata(user, sessionId)
            } catch (e: Exception) {
                log.warn("Failed to load metadata for session $sessionId", e)
                null
            }
        }
        val parentSessionIds = sessionParents.values.toSet()
        // Build reverse map: parent -> list of children metadata
        val childrenByParent: Map<Session, List<SessionMetadata>> = sessionParents.entries
            .groupBy({ it.value }, { it.key })
            .mapValues { (_, childSessions) ->
                childSessions.mapNotNull { childSession ->
                    allMetadata.firstOrNull { it.id == childSession }
                        ?: try {
                            metadataDB.getSessionMetadata(user, childSession)
                        } catch (e: Exception) {
                            log.warn("Failed to load metadata for child session $childSession", e)
                            null
                        }
                }
            }


        // Filter sessions that are visible (not children, have a path, and have messages or are parents)
        val visibleMetadata = allMetadata
            .filter { !sessionParents.containsKey(it.id) }
            .filter { !it.path.isNullOrBlank() }
            .filter { it.messageIds.isNotEmpty() || parentSessionIds.contains(it.id) }


        // Compute usage summaries for visible sessions (includes children via getSessionUsageSummary)
        val sessionUsages: Map<SessionMetadata, Map<String, ModelSchema.Usage>> = visibleMetadata.associateWith {
            try {
                usageDB.getSessionUsageSummary(it.id)
            } catch (e: Exception) {
                log.warn("Failed to load usage for session ${it.id}", e)
                emptyMap()
            }
        }
        // Compute per-child usage summaries for visible parent sessions
        val childUsages: Map<Session, Map<String, ModelSchema.Usage>> = visibleMetadata
            .flatMap { parent -> childrenByParent[parent.id] ?: emptyList() }
            .associate { childMeta ->
                childMeta.id to try {
                    usageDB.getSessionUsageSummary(childMeta.id)
                } catch (e: Exception) {
                    log.warn("Failed to load usage for child session ${childMeta.id}", e)
                    emptyMap()
                }
            }

        // Sort
        val sortBy = req.getParameter("sortBy")?.lowercase() ?: "time"
        val sortDir = req.getParameter("sortDir")?.lowercase() ?: "desc"
        val sortedMetadata = sortSessions(visibleMetadata, sessionUsages, sortBy, sortDir)

        // Paginate
        val page = (req.getParameter("page")?.toIntOrNull() ?: 1).coerceAtLeast(1)
        val pageSize = (req.getParameter("pageSize")?.toIntOrNull() ?: 10).coerceIn(1, 1000)
        val totalCount = sortedMetadata.size
        val totalPages = if (totalCount == 0) 1 else ((totalCount + pageSize - 1) / pageSize)
        val effectivePage = page.coerceAtMost(totalPages)
        val fromIdx = ((effectivePage - 1) * pageSize).coerceAtLeast(0)
        val toIdx = (fromIdx + pageSize).coerceAtMost(totalCount)
        val pagedMetadata = if (fromIdx < toIdx) sortedMetadata.subList(fromIdx, toIdx) else emptyList()

        when (resolveFormat(req)) {
            Format.JSON -> writeJson(
                resp, user, pagedMetadata, sessionUsages,
                childrenByParent, childUsages,
                totalCount, effectivePage, pageSize, totalPages, sortBy, sortDir
            )

            Format.HTML -> writeHtml(
                resp, user, pagedMetadata, sessionUsages,
                childrenByParent, childUsages,
                totalCount, effectivePage, pageSize, totalPages, sortBy, sortDir
            )
        }
    }

    private fun sortSessions(
        sessions: List<SessionMetadata>,
        usages: Map<SessionMetadata, Map<String, ModelSchema.Usage>>,
        sortBy: String,
        sortDir: String
    ): List<SessionMetadata> {
        val comparator: Comparator<SessionMetadata> = when (sortBy) {
            "name" -> compareBy(nullsLast()) { it.name?.lowercase() }
            "owner" -> compareBy(nullsLast()) { it.ownerId?.lowercase() }
            "messages" -> compareBy { it.messageIds.size }
            "tokens" -> compareBy { totalTokens(usages[it]) }
            "cost" -> compareBy { totalCost(usages[it]) }
            "time" -> compareBy(nullsLast()) { it.sessionTime?.time }
            else -> compareBy(nullsLast()) { it.sessionTime?.time }
        }
        val sorted = sessions.sortedWith(comparator)
        return if (sortDir == "asc") sorted else sorted.reversed()
    }

    private fun totalTokens(usage: Map<String, ModelSchema.Usage>?): Long {
        if (usage == null) return 0L
        return usage.values.sumOf { it.total_tokens }
    }

    private fun totalPromptTokens(usage: Map<String, ModelSchema.Usage>?): Long {
        if (usage == null) return 0L
        return usage.values.sumOf { it.counts.getOrDefault(TokenTypes.Prompt, 0) }
    }

    private fun totalCompletionTokens(usage: Map<String, ModelSchema.Usage>?): Long {
        if (usage == null) return 0L
        return usage.values.sumOf { it.counts.getOrDefault(TokenTypes.Completion, 0) }
    }

    private fun totalCost(usage: Map<String, ModelSchema.Usage>?): Double {
        if (usage == null) return 0.0
        return usage.values.sumOf { it.cost }
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
        sessionUsages: Map<SessionMetadata, Map<String, ModelSchema.Usage>>,
        childrenByParent: Map<Session, List<SessionMetadata>>,
        childUsages: Map<Session, Map<String, ModelSchema.Usage>>,
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
        sb.append("\"totalCount\":").append(totalCount).append(",")
        sb.append("\"page\":").append(page).append(",")
        sb.append("\"pageSize\":").append(pageSize).append(",")
        sb.append("\"totalPages\":").append(totalPages).append(",")
        sb.append("\"sortBy\":").append(jsonString(sortBy)).append(",")
        sb.append("\"sortDir\":").append(jsonString(sortDir)).append(",")
        sb.append("\"count\":").append(sessions.size).append(",")
        sb.append("\"sessions\":[")
        sessions.forEachIndexed { idx, meta ->
            if (idx > 0) sb.append(",")
            val usage = sessionUsages[meta] ?: emptyMap()
            val children = childrenByParent[meta.id] ?: emptyList()
            sb.append("{")
            sb.append("\"id\":").append(jsonString(meta.id.sessionId)).append(",")
            sb.append("\"name\":").append(jsonString(meta.name)).append(",")
            sb.append("\"ownerId\":").append(jsonString(meta.ownerId)).append(",")
            sb.append("\"path\":").append(jsonString(meta.path)).append(",")
            sb.append("\"shareUrl\":").append(jsonString(buildShareUrl(meta.path ?: "", meta.id.sessionId))).append(",")
            sb.append("\"sessionTime\":").append(
                meta.sessionTime?.let { jsonString(isoDate(it)) } ?: "null"
            ).append(",")
            sb.append("\"messageCount\":").append(meta.messageIds.size).append(",")
            sb.append("\"childCount\":").append(children.size).append(",")
            sb.append("\"usage\":{")
            sb.append("\"totalTokens\":").append(totalTokens(usage)).append(",")
            sb.append("\"promptTokens\":").append(totalPromptTokens(usage)).append(",")
            sb.append("\"completionTokens\":").append(totalCompletionTokens(usage)).append(",")
            sb.append("\"cost\":").append(totalCost(usage))
            sb.append("},")
            sb.append("\"usageByModel\":{")
            var first = true
            for ((model, u) in usage) {
                if (!first) sb.append(",")
                first = false
                sb.append(jsonString(model)).append(":{")
                sb.append("\"promptTokens\":").append(u.counts.getOrDefault(TokenTypes.Prompt, 0)).append(",")
                sb.append("\"completionTokens\":").append(u.counts.getOrDefault(TokenTypes.Completion, 0)).append(",")
                sb.append("\"totalTokens\":").append(u.total_tokens).append(",")
                sb.append("\"cost\":").append(u.cost ?: 0.0)
                sb.append("}")
            }
            sb.append("},")
            sb.append("\"children\":[")
            children.forEachIndexed { cIdx, child ->
                if (cIdx > 0) sb.append(",")
                val cUsage = childUsages[child.id] ?: emptyMap()
                sb.append("{")
                sb.append("\"id\":").append(jsonString(child.id.sessionId)).append(",")
                sb.append("\"name\":").append(jsonString(child.name)).append(",")
                sb.append("\"sessionTime\":").append(
                    child.sessionTime?.let { jsonString(isoDate(it)) } ?: "null"
                ).append(",")
                sb.append("\"messageCount\":").append(child.messageIds.size).append(",")
                sb.append("\"usage\":{")
                sb.append("\"totalTokens\":").append(totalTokens(cUsage)).append(",")
                sb.append("\"promptTokens\":").append(totalPromptTokens(cUsage)).append(",")
                sb.append("\"completionTokens\":").append(totalCompletionTokens(cUsage)).append(",")
                sb.append("\"cost\":").append(totalCost(cUsage))
                sb.append("},")
                sb.append("\"usageByModel\":{")
                var cFirst = true
                for ((model, u) in cUsage) {
                    if (!cFirst) sb.append(",")
                    cFirst = false
                    sb.append(jsonString(model)).append(":{")
                    sb.append("\"promptTokens\":").append(u.counts.getOrDefault(TokenTypes.Prompt, 0)).append(",")
                    sb.append("\"completionTokens\":").append(u.counts.getOrDefault(TokenTypes.Completion, 0)).append(",")
                    sb.append("\"totalTokens\":").append(u.total_tokens).append(",")
                    sb.append("\"cost\":").append(u.cost ?: 0.0)
                    sb.append("}")
                }
                sb.append("}")
                sb.append("}")
            }
            sb.append("],")
            sb.append("\"additional\":{")
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
        sessionUsages: Map<SessionMetadata, Map<String, ModelSchema.Usage>>,
        childrenByParent: Map<Session, List<SessionMetadata>>,
        childUsages: Map<Session, Map<String, ModelSchema.Usage>>,
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
            append("<span class=\"badge\">Total: ").append(totalCount).append("</span>\n")
            append("<select id=\"theme-selector\" class=\"theme-selector\" aria-label=\"Theme\">\n")
            append("<option value=\"auto\">Auto</option>\n")
            append("<option value=\"light\">Light</option>\n")
            append("<option value=\"dark\">Dark</option>\n")
            append("</select>\n")
            append("<a class=\"link\" href=\"?format=json&page=").append(page)
                .append("&pageSize=").append(pageSize)
                .append("&sortBy=").append(htmlEscape(sortBy))
                .append("&sortDir=").append(htmlEscape(sortDir))
                .append("\">View as JSON</a>\n")
            append("</div>\n")
            append("</header>\n")

            if (totalCount == 0) {
                append("<div class=\"empty\">No sessions found.</div>\n")
            } else {
                append("<div class=\"toolbar\">\n")
                append("<input id=\"filter\" type=\"text\" placeholder=\"Filter sessions...\" onkeyup=\"filterRows()\">\n")
                append("<button type=\"button\" class=\"btn\" onclick=\"copyTableToClipboard()\" title=\"Copy table to clipboard\">📋 Copy Table</button>\n")
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
                append("<table id=\"sessions\">\n")
                append("<thead><tr>")
                appendSortableHeader(this, "Name", "name", sortBy, sortDir, page, pageSize)
                appendSortableHeader(this, "Owner", "owner", sortBy, sortDir, page, pageSize)
                appendSortableHeader(this, "Time", "time", sortBy, sortDir, page, pageSize)
                appendSortableHeader(this, "Tokens", "tokens", sortBy, sortDir, page, pageSize, numeric = true)
                appendSortableHeader(this, "Cost", "cost", sortBy, sortDir, page, pageSize, numeric = true)
                append("<th class=\"num\">Subsessions</th>")
                append("<th>Details</th>")
                append("<th>Share</th>")
                append("</tr></thead>\n")
                append("<tbody>\n")
                for (meta in sessions) {
                    val id = meta.id.sessionId
                    val path = meta.path ?: ""
                    val usage = sessionUsages[meta] ?: emptyMap()
                    val tokens = totalTokens(usage)
                    val cost = totalCost(usage)
                    val rowId = htmlEscape(id)
                    val shareUrl = buildShareUrl(path, id)
                    val children = childrenByParent[meta.id] ?: emptyList()
                    append("<tr class=\"clickable\" onclick=\"navigateTo('")
                        .append(jsEscape(path)).append("')\">")
                    append("<td>").append(htmlEscape(meta.name ?: id)).append("</td>")
                    append("<td>").append(htmlEscape(meta.ownerId ?: "")).append("</td>")
                    append("<td>").append(htmlEscape(meta.sessionTime?.let { isoDate(it) } ?: "")).append("</td>")
                    append("<td class=\"num\">").append(formatNumber(tokens)).append("</td>")
                    append("<td class=\"num\">").append(formatCost(cost)).append("</td>")
                    append("<td class=\"num\">").append(children.size).append("</td>")
                    append("<td><a class=\"link\" href=\"javascript:void(0)\" onclick=\"event.stopPropagation();toggleDetails('")
                        .append(jsEscape(id)).append("')\">Show</a></td>")
                    append("<td><a class=\"link\" href=\"")
                        .append(htmlEscape(shareUrl))
                        .append("\" target=\"_blank\" onclick=\"event.stopPropagation()\">Share</a></td>")
                    append("</tr>\n")
                    // Details row
                    append("<tr id=\"details-").append(rowId)
                        .append("\" class=\"details\" style=\"display:none\"><td colspan=\"8\">\n")
                    append("<div class=\"details-content\">\n")
                    append("<h4>Usage Summary</h4>\n")
                    if (usage.isEmpty()) {
                        append("<div class=\"empty-inline\">No usage recorded.</div>\n")
                    } else {
                        append("<table class=\"sub\">\n")
                        append("<thead><tr><th>Model</th><th class=\"num\">Prompt</th><th class=\"num\">Completion</th><th class=\"num\">Total</th><th class=\"num\">Cost</th></tr></thead>\n")
                        append("<tbody>\n")
                        for ((model, u) in usage) {
                            append("<tr>")
                            append("<td class=\"mono\">").append(htmlEscape(model)).append("</td>")
                            append("<td class=\"num\">").append(formatNumber(u.counts.getOrDefault(TokenTypes.Prompt, 0))).append("</td>")
                            append("<td class=\"num\">").append(formatNumber(
                                u.counts.getOrDefault(
                                    TokenTypes.Completion,
                                    0
                                )
                            )).append("</td>")
                            append("<td class=\"num\">").append(formatNumber(u.total_tokens)).append("</td>")
                            append("<td class=\"num\">").append(formatCost(u.cost ?: 0.0)).append("</td>")
                            append("</tr>\n")
                        }
                        append("<tr class=\"total-row\">")
                        append("<td><strong>Total</strong></td>")
                        append("<td class=\"num\"><strong>").append(formatNumber(totalPromptTokens(usage)))
                            .append("</strong></td>")
                        append("<td class=\"num\"><strong>").append(formatNumber(totalCompletionTokens(usage)))
                            .append("</strong></td>")
                        append("<td class=\"num\"><strong>").append(formatNumber(totalTokens(usage)))
                            .append("</strong></td>")
                        append("<td class=\"num\"><strong>").append(formatCost(totalCost(usage)))
                            .append("</strong></td>")
                        append("</tr>\n")
                        append("</tbody>\n")
                        append("</table>\n")
                    }
                    // Subsessions section
                    if (children.isNotEmpty()) {
                        append("<h4>Subsessions (").append(children.size).append(")</h4>\n")
                        append("<table class=\"sub subsessions\">\n")
                        append("<thead><tr>")
                        append("<th>ID</th>")
                        append("<th>Name</th>")
                        append("<th>Time</th>")
                        append("<th class=\"num\">Messages</th>")
                        append("<th class=\"num\">Prompt</th>")
                        append("<th class=\"num\">Completion</th>")
                        append("<th class=\"num\">Total Tokens</th>")
                        append("<th class=\"num\">Cost</th>")
                        append("</tr></thead>\n")
                        append("<tbody>\n")
                        // Sort children by session time desc
                        val sortedChildren = children.sortedByDescending { it.sessionTime?.time ?: 0L }
                        var childPromptSum = 0L
                        var childCompletionSum = 0L
                        var childTokenSum = 0L
                        var childCostSum = 0.0
                        for (child in sortedChildren) {
                            val cUsage = childUsages[child.id] ?: emptyMap()
                            val cPrompt = totalPromptTokens(cUsage)
                            val cCompletion = totalCompletionTokens(cUsage)
                            val cTotal = totalTokens(cUsage)
                            val cCost = totalCost(cUsage)
                            childPromptSum += cPrompt
                            childCompletionSum += cCompletion
                            childTokenSum += cTotal
                            childCostSum += cCost
                            append("<tr>")
                            append("<td class=\"mono\">").append(htmlEscape(child.id.sessionId)).append("</td>")
                            append("<td>").append(htmlEscape(child.name ?: child.id.sessionId)).append("</td>")
                            append("<td>").append(htmlEscape(child.sessionTime?.let { isoDate(it) } ?: ""))
                                .append("</td>")
                            append("<td class=\"num\">").append(child.messageIds.size).append("</td>")
                            append("<td class=\"num\">").append(formatNumber(cPrompt)).append("</td>")
                            append("<td class=\"num\">").append(formatNumber(cCompletion)).append("</td>")
                            append("<td class=\"num\">").append(formatNumber(cTotal)).append("</td>")
                            append("<td class=\"num\">").append(formatCost(cCost)).append("</td>")
                            append("</tr>\n")
                            // Per-model breakdown row (collapsible by default hidden via nested table)
                            if (cUsage.isNotEmpty()) {
                                append("<tr class=\"submodel-row\"><td></td><td colspan=\"7\">\n")
                                append("<table class=\"sub nested\">\n")
                                append("<thead><tr><th>Model</th><th class=\"num\">Prompt</th><th class=\"num\">Completion</th><th class=\"num\">Total</th><th class=\"num\">Cost</th></tr></thead>\n")
                                append("<tbody>\n")
                                for ((model, u) in cUsage) {
                                    append("<tr>")
                                    append("<td class=\"mono\">").append(htmlEscape(model)).append("</td>")
                                    append("<td class=\"num\">").append(formatNumber(
                                        u.counts.getOrDefault(
                                            TokenTypes.Prompt,
                                            0
                                        )
                                    )).append("</td>")
                                    append("<td class=\"num\">").append(formatNumber(
                                        u.counts.getOrDefault(
                                            TokenTypes.Completion,
                                            0
                                        )
                                    ))
                                        .append("</td>")
                                    append("<td class=\"num\">").append(formatNumber(u.total_tokens)).append("</td>")
                                    append("<td class=\"num\">").append(formatCost(u.cost ?: 0.0)).append("</td>")
                                    append("</tr>\n")
                                }
                                append("</tbody></table>\n")
                                append("</td></tr>\n")
                            }
                        }
                        append("<tr class=\"total-row\">")
                        append("<td colspan=\"4\"><strong>Subsessions Total</strong></td>")
                        append("<td class=\"num\"><strong>").append(formatNumber(childPromptSum))
                            .append("</strong></td>")
                        append("<td class=\"num\"><strong>").append(formatNumber(childCompletionSum))
                            .append("</strong></td>")
                        append("<td class=\"num\"><strong>").append(formatNumber(childTokenSum))
                            .append("</strong></td>")
                        append("<td class=\"num\"><strong>").append(formatCost(childCostSum)).append("</strong></td>")
                        append("</tr>\n")
                        append("</tbody>\n")
                        append("</table>\n")
                    }
                    append("<h4>Session</h4>\n")
                    append("<table class=\"sub\">\n")
                    append("<tr><th>ID</th><td class=\"mono\">").append(htmlEscape(id)).append("</td></tr>\n")
                    append("<tr><th>Path</th><td class=\"mono\">").append(htmlEscape(path)).append("</td></tr>\n")
                    if (children.isNotEmpty()) {
                        append("<tr><th>Subsession Count</th><td>").append(children.size).append("</td></tr>\n")
                    }
                    append("</table>\n")
                    append("</div>\n")
                    append("</td></tr>\n")
                }
                append("</tbody>\n")
                append("</table>\n")
                // Pagination
                appendPagination(this, page, pageSize, totalPages, totalCount, sortBy, sortDir)
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
        numeric: Boolean = false
    ) {
        val isCurrent = currentSortBy == key
        val nextDir = if (isCurrent && currentSortDir == "asc") "desc" else if (isCurrent) "asc" else "desc"
        val indicator = if (isCurrent) (if (currentSortDir == "asc") " ▲" else " ▼") else ""
        val cls = if (numeric) "num sortable" else "sortable"
        sb.append("<th class=\"").append(cls).append("\">")
        sb.append("<a class=\"sort-link\" href=\"?page=1&pageSize=").append(pageSize)
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
        sortDir: String
    ) {
        sb.append("<nav class=\"pagination\">\n")
        sb.append("<span class=\"page-info\">Page ").append(page).append(" of ").append(totalPages)
            .append(" (").append(totalCount).append(" sessions)</span>\n")
        sb.append("<div class=\"page-links\">\n")
        val baseQuery = "pageSize=$pageSize&sortBy=${htmlEscape(sortBy)}&sortDir=${htmlEscape(sortDir)}"
        if (page > 1) {
            sb.append("<a class=\"page-link\" href=\"?page=1&").append(baseQuery).append("\">« First</a>\n")
            sb.append("<a class=\"page-link\" href=\"?page=").append(page - 1).append("&").append(baseQuery)
                .append("\">‹ Prev</a>\n")
        } else {
            sb.append("<span class=\"page-link disabled\">« First</span>\n")
            sb.append("<span class=\"page-link disabled\">‹ Prev</span>\n")
        }
        // Page number window
        val windowSize = 5
        val start = (page - windowSize / 2).coerceAtLeast(1)
        val end = (start + windowSize - 1).coerceAtMost(totalPages)
        val realStart = (end - windowSize + 1).coerceAtLeast(1)
        for (p in realStart..end) {
            if (p == page) {
                sb.append("<span class=\"page-link current\">").append(p).append("</span>\n")
            } else {
                sb.append("<a class=\"page-link\" href=\"?page=").append(p).append("&").append(baseQuery).append("\">")
                    .append(p).append("</a>\n")
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

    private fun formatNumber(n: Long): String {
        return String.format(Locale.US, "%,d", n)
    }

    private fun formatCost(c: Double): String {
        return if (c == 0.0) "$0.0000" else String.format(Locale.US, "$%.4f", c)
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

    private fun buildShareUrl(path: String, sessionId: String): String {
        if (path.isBlank()) return "share/$sessionId/"
        // The path may include a session-specific suffix like "fileIndex/<sessionId>/app.html".
        // Strip it to get the app base path before appending share/<sessionId>/.
        val basePath = stripSessionSuffix(path, sessionId)
        val normalized = if (basePath.endsWith("/")) basePath else "$basePath/"
        return "${normalized}share/$sessionId/"
    }

    private fun stripSessionSuffix(path: String, sessionId: String): String {
        // Look for "/fileIndex/<sessionId>" and strip everything from there onward.
        val marker = "/fileIndex/$sessionId"
        val idx = path.indexOf(marker)
        if (idx >= 0) {
            return path.substring(0, idx)
        }
        // Generic fallback: if the path contains the sessionId, strip from the segment that contains it.
        val sessionIdx = path.indexOf(sessionId)
        if (sessionIdx > 0) {
            // Find the last '/' before the sessionId
            val precedingSlash = path.lastIndexOf('/', sessionIdx - 1)
            // Also look further back to skip an intermediate segment like "fileIndex"
            if (precedingSlash > 0) {
                val priorSlash = path.lastIndexOf('/', precedingSlash - 1)
                if (priorSlash >= 0) return path.substring(0, priorSlash)
                return path.substring(0, precedingSlash)
            }
        }
        return path
    }


    private fun isoDate(d: Date): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z")
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(d)
    }

    private enum class Format { JSON, HTML }

    companion object {
        private val log = LoggerFactory.getLogger(SessionsServlet::class.java)

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
                     }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                         margin: 0; padding: 0; background: var(--bg); color: var(--fg);
                    }
                    .container { max-width: 1200px; margin: 0 auto; padding: 24px; }
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
                     .details-content h4 { margin: 8px 0 6px; font-size: 13px; color: var(--subtle-fg); }
                    table.sub { width: auto; box-shadow: none; background: transparent; }
                    table.sub th { background: transparent; text-transform: none; letter-spacing: normal; font-size: 13px; padding: 4px 8px 4px 0; }
                    table.sub td { padding: 4px 8px; border: none; }
                     table.sub tr.total-row td { border-top: 1px solid var(--border-strong); }
                      table.sub.subsessions { width: 100%; margin-bottom: 8px; border: 1px solid var(--border); border-radius: 6px; }
                      table.sub.subsessions thead th { background: var(--hover-bg); padding: 6px 8px; border-bottom: 1px solid var(--border); }
                      table.sub.subsessions tbody tr { border-bottom: 1px solid var(--border); }
                      table.sub.subsessions tbody tr:last-child { border-bottom: none; }
                      table.sub.subsessions td { padding: 6px 8px; }
                      table.sub.nested { width: 100%; margin: 4px 0 4px 12px; background: var(--bg); border-left: 2px solid var(--border-strong); padding-left: 8px; }
                      table.sub.nested th { font-size: 11px; color: var(--muted-fg); }
                      table.sub.nested td { font-size: 12px; padding: 2px 6px; }
                      tr.submodel-row td { padding: 0 8px 6px !important; background: transparent !important; }
                    tr.clickable { cursor: pointer; }
                    .pagination { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; margin-top: 16px; padding: 12px 0; }
                     .page-info { font-size: 13px; color: var(--muted-fg); }
                    .page-links { display: flex; gap: 4px; flex-wrap: wrap; }
                     .page-link { padding: 6px 10px; border: 1px solid var(--border-strong); border-radius: 6px; font-size: 13px; color: var(--subtle-fg); text-decoration: none; background: var(--card-bg); }
                     .page-link:hover:not(.disabled):not(.current) { background: var(--hover-bg); border-color: var(--muted-fg); }
                     .page-link.current { background: var(--primary); color: var(--primary-fg); border-color: var(--primary); font-weight: 600; }
                     .page-link.disabled { color: var(--disabled-fg); background: var(--disabled-bg); cursor: not-allowed; }
                      .btn { padding: 8px 12px; border: 1px solid var(--border-strong); border-radius: 6px; font-size: 13px; background: var(--card-bg); color: var(--fg); cursor: pointer; font-family: inherit; }
                      .btn:hover { background: var(--hover-bg); border-color: var(--muted-fg); }
                      .btn:active { transform: translateY(1px); }
                      .btn.copied { background: var(--primary); color: var(--primary-fg); border-color: var(--primary); }
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
                    function navigateTo(path) {
                       if (path) window.open(path, '_blank');
                    }
                    function toggleDetails(id) {
                        var row = document.getElementById('details-' + id);
                        if (!row) return;
                        row.style.display = (row.style.display === 'none' || !row.style.display) ? 'table-row' : 'none';
                    }
                    function changePageSize(size) {
                        var url = new URL(window.location.href);
                        url.searchParams.set('pageSize', size);
                        url.searchParams.set('page', '1');
                        window.location.href = url.toString();
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
                     function copyTableToClipboard() {
                         var table = document.getElementById('sessions');
                         if (!table) return;
                         var lines = [];
                         // Header row
                         var headerCells = table.querySelectorAll('thead th');
                         var headers = [];
                         for (var h = 0; h < headerCells.length; h++) {
                             headers.push((headerCells[h].innerText || '').replace(/\s+/g, ' ').replace(/[▲▼]/g, '').trim());
                         }
                         lines.push(headers.join('\t'));
                         // Body rows (skip details rows and hidden rows)
                         var rows = table.querySelectorAll('tbody tr');
                         for (var i = 0; i < rows.length; i++) {
                             var row = rows[i];
                             if (row.classList.contains('details')) continue;
                             if (row.style.display === 'none') continue;
                             var cells = row.querySelectorAll('td');
                             var values = [];
                             for (var c = 0; c < cells.length; c++) {
                                 var cell = cells[c];
                                 var text;
                                 // For action cells (links), prefer href if it's a real URL
                                 var link = cell.querySelector('a[href]');
                                 if (link && link.getAttribute('href') && link.getAttribute('href').indexOf('javascript:') !== 0) {
                                     text = link.getAttribute('href');
                                 } else {
                                     text = cell.innerText || '';
                                 }
                                 text = text.replace(/\t/g, ' ').replace(/\r?\n/g, ' ').trim();
                                 values.push(text);
                             }
                             lines.push(values.join('\t'));
                         }
                         var content = lines.join('\n');
                         var btn = document.querySelector('.toolbar .btn');
                         var showCopied = function() {
                             if (!btn) return;
                             var orig = btn.innerHTML;
                             btn.innerHTML = '✓ Copied!';
                             btn.classList.add('copied');
                             setTimeout(function() {
                                 btn.innerHTML = orig;
                                 btn.classList.remove('copied');
                             }, 1500);
                         };
                         var showError = function() {
                             if (!btn) return;
                             var orig = btn.innerHTML;
                             btn.innerHTML = '✗ Failed';
                             setTimeout(function() { btn.innerHTML = orig; }, 1500);
                         };
                         if (navigator.clipboard && navigator.clipboard.writeText) {
                             navigator.clipboard.writeText(content).then(showCopied, function() {
                                 fallbackCopy(content) ? showCopied() : showError();
                             });
                         } else {
                             fallbackCopy(content) ? showCopied() : showError();
                         }
                     }
                     function fallbackCopy(text) {
                         try {
                             var ta = document.createElement('textarea');
                             ta.value = text;
                             ta.style.position = 'fixed';
                             ta.style.left = '-9999px';
                             document.body.appendChild(ta);
                             ta.select();
                             var ok = document.execCommand('copy');
                             document.body.removeChild(ta);
                             return ok;
                         } catch (e) {
                             return false;
                         }
                     }
                """
    }
}