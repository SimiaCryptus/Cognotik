package com.simiacryptus.cognotik.webui.servlet

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.simiacryptus.cognotik.platform.ServiceRouter
import com.simiacryptus.cognotik.platform.model.ModelSchema
import com.simiacryptus.cognotik.platform.model.ModelSchema.TokenTypes
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.service.UsageInterface
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 *
 * GET /usage                         -> user usage (HTML, or JSON with ?format=json / Accept: application/json)
 *     ?from=yyyy-MM-dd&to=yyyy-MM-dd -> date range (from inclusive, to exclusive, UTC)
 * GET /usage?sessionId=...           -> session usage incl. per-request rows
 *     &details=false                 -> omit per-request rows
 *     &includeText=false             -> omit input/output text from rows
 * GET|POST .../graphql               -> GraphQL endpoint (see [UsageGraphQL])
 * Usage API.
 */
class UsageServlet : HttpServlet() {

    /** All data needed to render a usage report in any format. */
    private data class UsageReport(
        val scopeType: String,
        val scopeLabel: String,
        val usage: Map<String, ModelSchema.Usage>,
        val daily: List<UsageInterface.DailyUsage> = emptyList(),
        val credits: List<UsageInterface.CreditEntry> = emptyList(),
        val rows: List<UsageInterface.UsageRow> = emptyList(),
        val includeRows: Boolean = false,
        val includeText: Boolean = true,
        val availableBudget: Double? = null,
        val balance: Double? = null,
        val from: LocalDate? = null,
        val to: LocalDate? = null,
        val userEmail: String? = null,
        val sessionId: String? = null,
        val parentSessionId: String? = null,
    )

    public override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
        if (UsageGraphQL.isGraphQLRequest(request)) {
            UsageGraphQL.handle(request, response)
            return
        }
        val useJson = isJsonRequested(request)
        val usageManager = ServiceRouter as UsageInterface
        val user = ServiceRouter.authenticate(request)
        if (user == null) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            if (useJson) {
                response.contentType = "application/json"
                response.writer.write(Gson().toJson(mapOf("error" to "Authentication failed")))
            } else {
                response.contentType = "text/plain"
                response.writer.write("Authentication failed")
            }
            return
        }

        val report = if (request.parameterMap.containsKey("sessionId")) {
            buildSessionReport(request, user, usageManager)
        } else {
            buildUserReport(request, user, usageManager)
        }

        response.status = HttpServletResponse.SC_OK
        if (useJson) serveJson(response, report) else serveHtml(response, report)
    }

    public override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
        if (UsageGraphQL.isGraphQLRequest(request)) {
            UsageGraphQL.handle(request, response)
        } else {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST is only supported on the /graphql endpoint")
        }
    }

    private fun isJsonRequested(request: HttpServletRequest): Boolean {
        val formatParam = request.getParameter("format")?.equals("json", ignoreCase = true) == true
        val acceptHeader = request.getHeader("Accept")?.contains("application/json", ignoreCase = true) == true
        return formatParam || acceptHeader
    }

    private fun isFalse(value: String?): Boolean =
        value != null && value.trim().lowercase() in setOf("false", "0", "no", "off", "summary")

    private fun buildSessionReport(
        request: HttpServletRequest,
        user: User,
        usageManager: UsageInterface
    ): UsageReport {
        val session = Session(request.getParameter("sessionId"))
        val includeRows = !isFalse(request.getParameter("details"))
        val includeText = !isFalse(request.getParameter("includeText"))
        val usage = usageManager.getSessionUsageSummary(user = user, session = session)
        val rows = if (includeRows) {
            runCatching { usageManager.getSessionUsageRows(session, user) }
                .onFailure { log.warn("Failed to load usage rows for session ${session.sessionId}", it) }
                .getOrElse { emptyList() }
        } else emptyList()
        val parent = runCatching { usageManager.getParentSession(user, session) }
            .onFailure { log.debug("Failed to load parent session for ${session.sessionId}", it) }
            .getOrNull()
        return UsageReport(
            scopeType = "session",
            scopeLabel = "Session: ${session.sessionId}",
            usage = usage,
            rows = rows,
            includeRows = includeRows,
            includeText = includeText,
            userEmail = user.email,
            sessionId = session.sessionId,
            parentSessionId = parent?.sessionId,
        )
    }

    private fun buildUserReport(
        request: HttpServletRequest,
        user: User,
        usageManager: UsageInterface
    ): UsageReport {
        val (from, to) = UsageTokens.parseRange(request.getParameter("from"), request.getParameter("to"))
        val usage = usageManager.getUserUsageSummary(user, from, to)
        val daily = runCatching { usageManager.getUserDailyUsage(user, from, to) }
            .onFailure { log.warn("Failed to load daily usage", it) }.getOrElse { emptyList() }
        val budget = runCatching { usageManager.getAvailableBudget(user) }
            .onFailure { log.warn("Failed to load available budget", it) }.getOrNull()
        val balance = runCatching { usageManager.getUserBalance(user) }
            .onFailure { log.debug("Failed to load balance", it) }.getOrNull()
        val credits = runCatching { usageManager.getUserCredits(user, from, to) }
            .onFailure { log.warn("Failed to load credits", it) }.getOrElse { emptyList() }
        return UsageReport(
            scopeType = "user",
            scopeLabel = "User: ${user.email}",
            usage = usage,
            daily = daily,
            credits = credits,
            availableBudget = budget,
            balance = balance,
            from = from,
            to = to,
            userEmail = user.email,
        )
    }

    /**
     * Collects the union of all TokenTypes appearing in the summary, daily and row data,
     * in a stable column order.
     */
    private fun collectTokenTypes(report: UsageReport): List<TokenTypes> {
        val present = LinkedHashSet<TokenTypes>()
        report.usage.values.forEach { present.addAll(it.counts.keys) }
        report.daily.forEach { present.addAll(it.usage.counts.keys) }
        report.rows.forEach { present.addAll(it.tokenCounts.keys) }
        return UsageTokens.orderTokenTypes(present)
    }

    // ------------------------------------------------------------------ JSON

    private fun tokenFields(
        counts: Map<TokenTypes, Long>,
        allTokenTypes: List<TokenTypes>,
        cost: Double,
        totalTokens: Long
    ): MutableMap<String, Any?> = mutableMapOf(
        "cost" to cost,
        "total_tokens" to totalTokens,
        "tokens" to allTokenTypes.associate { it.name to (counts[it] ?: 0L) },
        // Backwards-compatible flat fields
        "prompt_tokens" to (counts[TokenTypes.Prompt] ?: 0L),
        "completion_tokens" to (counts[TokenTypes.Completion] ?: 0L)
    )

    private fun serveJson(resp: HttpServletResponse, report: UsageReport) {
        resp.contentType = "application/json"

        val allTokenTypes = collectTokenTypes(report)
        val totalCounts = UsageTokens.mergeCounts(report.usage.values.map { it.counts })
        val totalCost = report.usage.values.sumOf { it.cost }
        val totalTokens = report.usage.values.sumOf { UsageTokens.totalTokens(it) }

        val result = mutableMapOf<String, Any?>(
            "scope" to mutableMapOf<String, Any?>("type" to report.scopeType).also { s ->
                report.userEmail?.let { s["user"] = it }
                report.sessionId?.let { s["session_id"] = it }
                report.parentSessionId?.let { s["parent_session_id"] = it }
            },
            "token_types" to allTokenTypes.map { it.name },
            "models" to report.usage.entries.map { (model, u) ->
                tokenFields(u.counts, allTokenTypes, u.cost, UsageTokens.totalTokens(u)).apply {
                    put("model", model)
                }
            },
            "totals" to tokenFields(totalCounts, allTokenTypes, totalCost, totalTokens)
        )

        if (report.from != null && report.to != null) {
            result["range"] = mapOf("from" to report.from.toString(), "to" to report.to.toString())
        }
        report.availableBudget?.let { result["available_budget"] = it }
        report.balance?.let { result["balance"] = it }

        if (report.daily.isNotEmpty()) {
            result["daily"] = report.daily.map { d ->
                tokenFields(d.usage.counts, allTokenTypes, d.usage.cost, UsageTokens.totalTokens(d.usage)).apply {
                    put("day", d.day.toString())
                    put("model", d.model)
                }
            }
        }
        if (report.credits.isNotEmpty()) {
            result["credits"] = report.credits.map { c ->
                mutableMapOf<String, Any?>(
                    "datetime" to c.datetime.toString(),
                    "amount" to c.amount,
                    "comment" to c.comment
                ).also { m ->
                    if (!c.metadata.isNullOrEmpty()) m["metadata"] = c.metadata
                }
            }
        }
        if (report.includeRows) {
            result["rows"] = report.rows.map { r ->
                tokenFields(r.tokenCounts, allTokenTypes, r.cost, UsageTokens.totalTokens(r.tokenCounts)).apply {
                    put("id", r.id)
                    put("session_id", r.sessionId)
                    put("user_id", r.userId)
                    put("model", r.model)
                    put("datetime", r.datetime?.toString())
                    if (report.includeText) {
                        put("input_text", r.inputText)
                        put("output_text", r.outputText)
                    }
                }
            }
        }

        val gson: Gson = GsonBuilder().setPrettyPrinting().create()
        resp.writer.write(gson.toJson(result))
    }

    // ------------------------------------------------------------------ HTML

    private fun serveHtml(resp: HttpServletResponse, report: UsageReport) {
        resp.contentType = "text/html"

        val allTokenTypes = collectTokenTypes(report)
        val totalsByType: Map<TokenTypes, Long> = allTokenTypes.associateWith { type ->
            report.usage.values.sumOf { it.counts.getOrDefault(type, 0L) }
        }
        val totalCost = report.usage.values.sumOf { it.cost }
        val totalTokens = report.usage.values.sumOf { UsageTokens.totalTokens(it) }

        val scopeHtml = renderScope(report)
        val budgetHtml = renderBudget(report.availableBudget, report.balance)
        val rangeFormHtml = renderRangeForm(report.from, report.to)
        val modelTableHtml = renderModelTable(report.usage, allTokenTypes, totalsByType, totalCost, totalTokens)
        val dailyHtml = renderDailyTable(report.daily, allTokenTypes)
        val rowsHtml = if (report.includeRows) renderRowsTable(report.rows, allTokenTypes, report.includeText) else ""
        val creditsHtml = renderCreditsTable(report.credits)

        resp.writer.write(
            """
                <html>
                <head>
                    <title>Usage</title>
                    <meta name="color-scheme" content="light dark">
                    <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
                    <link href="/themes.css" id="theme-stylesheet" rel="stylesheet">
                    <script src="/themes.js"></script>
                    <script src="/modules/theme.js"></script>
                    <style>
                        /* Page-local variables derived from the central theme tokens (see /themes.css). */
                        :root {
                            --bg: var(--color-canvas);
                            --fg: var(--color-text);
                            --muted-fg: var(--color-text-muted);
                            --border: var(--color-border);
                            --row-alt-bg: var(--color-surface-alt);
                            --header-bg: var(--color-brand);
                            --header-fg: #ffffff;
                            --total-row-bg: color-mix(in srgb, var(--color-brand) 15%, var(--color-surface));
                            --budget-bg: color-mix(in srgb, var(--color-success) 15%, var(--color-surface));
                            --budget-border: var(--color-success);
                            --nav-bg: var(--color-surface-alt);
                            --nav-link: var(--color-link);
                            --nav-link-hover-bg: color-mix(in srgb, var(--color-brand) 15%, transparent);
                            --nav-active-bg: var(--color-brand);
                            --nav-active-fg: #ffffff;
                            --btn-primary-bg: var(--color-brand);
                            --btn-primary-fg: #ffffff;
                            --btn-primary-hover-bg: var(--color-brand-hover);
                            --btn-secondary-bg: var(--color-surface);
                            --btn-secondary-fg: var(--color-brand);
                            --btn-secondary-border: var(--color-brand);
                            --btn-secondary-hover-bg: var(--color-surface-alt);
                            --credit-positive: var(--color-success);
                            --credit-negative: var(--color-danger);
                            --credit-meta-fg: var(--color-text-muted);
                            --input-bg: var(--color-surface);
                            --input-fg: var(--color-text);
                            --input-border: var(--color-border-strong);
                            --pre-bg: var(--color-surface-alt);
                        }
                        html, body { background-color: var(--bg); color: var(--fg); }
                        body { font-family: Arial, sans-serif; margin: 20px; }
                        h1, h2 { color: var(--fg); }
                        a { color: var(--nav-link); }
                        table { width: 100%; border-collapse: collapse; margin-bottom: 20px; }
                        th, td { border: 1px solid var(--border); padding: 8px; text-align: left; vertical-align: top; }
                        tr:nth-child(even) { background-color: var(--row-alt-bg); }
                        .table-header { background-color: var(--header-bg); color: var(--header-fg); }
                        .table-header th { color: var(--header-fg); }
                        .total-row { font-weight: bold; background-color: var(--total-row-bg) !important; }
                        .scope { font-size: 1.1em; margin-bottom: 10px; color: var(--muted-fg); }
                        .budget { font-size: 1.1em; margin-bottom: 15px; padding: 8px; background: var(--budget-bg); border-left: 4px solid var(--budget-border); }
                        .range-form { margin-bottom: 15px; }
                        .range-form label { margin-right: 10px; }
                        .range-form input[type="date"] {
                            background: var(--input-bg); color: var(--input-fg);
                            border: 1px solid var(--input-border); padding: 4px 6px; border-radius: 3px;
                        }
                        .range-form button {
                            background: var(--btn-primary-bg); color: var(--btn-primary-fg);
                            border: none; padding: 6px 12px; border-radius: 4px; cursor: pointer;
                        }
                        .range-form button:hover { background: var(--btn-primary-hover-bg); }
                        #theme-selector, #palette-selector {
                            background: var(--input-bg); color: var(--input-fg);
                            border: 1px solid var(--input-border); padding: 4px 6px; border-radius: 3px;
                        }
                         .credit-positive { color: var(--credit-positive); font-weight: bold; }
                         .credit-negative { color: var(--credit-negative); font-weight: bold; }
                         .credit-meta { font-size: 0.85em; color: var(--credit-meta-fg); font-style: italic; }
                         .nav-bar { display: flex; gap: 8px; padding: 10px 12px; background: var(--nav-bg);
                                    border-radius: 6px; margin-bottom: 16px; flex-wrap: wrap; }
                         .nav-bar a { color: var(--nav-link); text-decoration: none; padding: 6px 12px;
                                      border-radius: 4px; font-size: 0.95em; }
                         .nav-bar a:hover { background: var(--nav-link-hover-bg); text-decoration: none; }
                         .nav-bar a.active { background: var(--nav-active-bg); color: var(--nav-active-fg); font-weight: 600; }
                         .actions-bar { margin: 15px 0; display: flex; gap: 10px; flex-wrap: wrap; }
                         .btn-primary { background: var(--btn-primary-bg); color: var(--btn-primary-fg); border: none; padding: 8px 16px;
                                        border-radius: 4px; cursor: pointer; text-decoration: none; font-size: 0.95em; }
                         .btn-primary:hover { background: var(--btn-primary-hover-bg); }
                         .btn-secondary { background: var(--btn-secondary-bg); color: var(--btn-secondary-fg); border: 1px solid var(--btn-secondary-border);
                                          padding: 8px 16px; border-radius: 4px; text-decoration: none; font-size: 0.95em; }
                         .btn-secondary:hover { background: var(--btn-secondary-hover-bg); }
                         .usage-text { white-space: pre-wrap; word-break: break-word; max-height: 300px; overflow: auto;
                                       font-size: 0.85em; background: var(--pre-bg); padding: 6px; border-radius: 3px; margin: 4px 0; }
                         .muted { color: var(--muted-fg); }
                    </style>
                </head>
                <body>
                <div class="theme-switcher" style="display:flex; justify-content:flex-end; align-items:center; margin-bottom:10px;">
                    <label for="palette-selector" style="margin-right:8px; font-size:0.95em;">Palette:</label>
                    <select id="palette-selector" aria-label="Palette selector" style="margin-right:16px;"></select>
                    <label for="theme-selector" style="margin-right:8px; font-size:0.95em;">Theme:</label>
                    <select id="theme-selector" aria-label="Theme selector"></select>
                </div>
                <h1>Usage Summary</h1>
                $scopeHtml
                ${navBar("usage")}
                $budgetHtml
                $rangeFormHtml
                <h2>By Model</h2>
                $modelTableHtml
                $dailyHtml
                $rowsHtml
             $creditsHtml
                <script>
                    (function() {
                        function initTheme() {
                            if (typeof ThemeManager !== 'undefined') {
                                // ThemeManager.init() runs automatically when theme.js loads.
                                var pal = document.getElementById('palette-selector');
                                if (pal) ThemeManager.bindPaletteSelector(pal);
                                var sel = document.getElementById('theme-selector');
                                if (sel) ThemeManager.bindSelector(sel);
                                // Follow theme changes made in other windows/frames live.
                                window.addEventListener('storage', function (e) {
                                    if (!e.newValue) return;
                                    if (e.key === ThemeManager.PALETTE_STORAGE_KEY) ThemeManager.setPalette(e.newValue);
                                    if (e.key === ThemeManager.STORAGE_KEY) ThemeManager.setTheme(e.newValue);
                                });
                            } else {
                                console.warn('ThemeManager not loaded from /modules/theme.js');
                            }
                        }
                        if (document.readyState === 'loading') {
                            document.addEventListener('DOMContentLoaded', initTheme);
                        } else {
                            initTheme();
                        }
                    })();
                </script>
                </body>
                </html>
                """.trimIndent()
        )
    }

    private fun sessionLink(sessionId: String): String {
        val encoded = URLEncoder.encode(sessionId, StandardCharsets.UTF_8)
        return """<a href="?sessionId=$encoded">${escapeHtml(sessionId)}</a>"""
    }

    private fun renderScope(report: UsageReport): String {
        if (report.scopeLabel.isEmpty()) return ""
        val parent = report.parentSessionId?.let { " <span class=\"muted\">(parent: ${sessionLink(it)})</span>" } ?: ""
        return """<div class="scope">${escapeHtml(report.scopeLabel)}$parent</div>"""
    }

    private fun renderBudget(availableBudget: Double?, balance: Double?): String {
        val parts = mutableListOf<String>()
        if (availableBudget != null) parts.add("Available budget: <strong>${"%.4f".format(availableBudget)}</strong>")
        if (balance != null) parts.add("Balance: <strong>${"%.4f".format(balance)}</strong>")
        return if (parts.isEmpty()) "" else """<div class="budget">${parts.joinToString(" &nbsp;|&nbsp; ")}</div>"""
    }

    private fun renderRangeForm(from: LocalDate?, to: LocalDate?): String =
        if (from != null && to != null) {
            """
                <form method="get" class="range-form">
                    <label>From: <input type="date" name="from" value="$from"/></label>
                    <label>To: <input type="date" name="to" value="$to"/></label>
                    <button type="submit">Apply</button>
                </form>
                """.trimIndent()
        } else ""

    private fun tokenHeaderCols(allTokenTypes: List<TokenTypes>): String =
        allTokenTypes.joinToString("\n") { t ->
            "                        <th>${escapeHtml(formatTokenTypeName(t))}</th>"
        }

    private fun tokenCells(counts: Map<TokenTypes, Long>, allTokenTypes: List<TokenTypes>): String =
        allTokenTypes.joinToString("\n") { t ->
            """                    <td class="token-cell token-${t.name.lowercase()}">${counts.getOrDefault(t, 0L)}</td>"""
        }

    private fun renderModelTable(
        usage: Map<String, ModelSchema.Usage>,
        allTokenTypes: List<TokenTypes>,
        totalsByType: Map<TokenTypes, Long>,
        totalCost: Double,
        totalTokens: Long
    ): String {
        val headerCols = tokenHeaderCols(allTokenTypes)
        val rows = usage.entries.joinToString("\n") { (model, count) ->
            """
                <tr class="table-row">
                    <td class="model-cell">${escapeHtml(model)}</td>
${tokenCells(count.counts, allTokenTypes)}
                    <td class="token-cell token-total">${UsageTokens.totalTokens(count)}</td>
                    <td class="cost-cell">${"%.4f".format(count.cost)}</td>
                </tr>
                """.trimIndent()
        }
        return """
                <table class="usage-table">
                    <tr class="table-header">
                        <th>Model</th>
$headerCols
                        <th>Total Tokens</th>
                        <th>Cost</th>
                    </tr>
                    $rows
                    <tr class="table-row total-row">
                        <td class="model-cell">Total</td>
${tokenCells(totalsByType, allTokenTypes)}
                        <td class="token-cell token-total">$totalTokens</td>
                        <td class="cost-cell">${"%.4f".format(totalCost)}</td>
                    </tr>
                </table>
                """.trimIndent()
    }

    private fun renderDailyTable(
        daily: List<UsageInterface.DailyUsage>,
        allTokenTypes: List<TokenTypes>
    ): String {
        if (daily.isEmpty()) return ""
        val headerCols = tokenHeaderCols(allTokenTypes)
        val rows = daily.joinToString("\n") { d ->
            """
                <tr class="table-row">
                    <td>${d.day}</td>
                    <td>${escapeHtml(d.model)}</td>
${tokenCells(d.usage.counts, allTokenTypes)}
                    <td>${UsageTokens.totalTokens(d.usage)}</td>
                    <td>${"%.4f".format(d.usage.cost)}</td>
                </tr>
                """.trimIndent()
        }
        return """
                <h2>Daily Breakdown</h2>
                <table class="usage-table">
                    <tr class="table-header">
                        <th>Day</th>
                        <th>Model</th>
$headerCols
                        <th>Total Tokens</th>
                        <th>Cost</th>
                    </tr>
                    $rows
                </table>
                """.trimIndent()
    }

    private fun renderRowsTable(
        rows: List<UsageInterface.UsageRow>,
        allTokenTypes: List<TokenTypes>,
        includeText: Boolean
    ): String {
        if (rows.isEmpty()) {
            return """
                <h2>Requests</h2>
                <p class="muted">No individual usage records found.</p>
                """.trimIndent()
        }
        val dtFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)
        val headerCols = tokenHeaderCols(allTokenTypes)
        val body = rows.joinToString("\n") { r ->
            val textCell = if (includeText) {
                val input = r.inputText?.takeIf { it.isNotEmpty() }?.let {
                    """<details><summary>Input (${it.length} chars)</summary><pre class="usage-text">${escapeHtml(it)}</pre></details>"""
                } ?: ""
                val output = r.outputText?.takeIf { it.isNotEmpty() }?.let {
                    """<details><summary>Output (${it.length} chars)</summary><pre class="usage-text">${escapeHtml(it)}</pre></details>"""
                } ?: ""
                val content = (input + output).ifEmpty { """<span class="muted">-</span>""" }
                "\n                    <td>$content</td>"
            } else ""
            """
                <tr class="table-row">
                    <td>${r.datetime?.let { dtFormatter.format(it) } ?: ""}</td>
                    <td>${r.sessionId?.let { sessionLink(it) } ?: ""}</td>
                    <td>${escapeHtml(r.model ?: "")}</td>
${tokenCells(r.tokenCounts, allTokenTypes)}
                    <td>${UsageTokens.totalTokens(r.tokenCounts)}</td>
                    <td>${"%.4f".format(r.cost)}</td>$textCell
                </tr>
                """.trimIndent()
        }
        val textHeader = if (includeText) "\n                        <th>Input / Output</th>" else ""
        return """
                <h2>Requests (${rows.size})</h2>
                <table class="usage-table">
                    <tr class="table-header">
                        <th>Date/Time (UTC)</th>
                        <th>Session</th>
                        <th>Model</th>
$headerCols
                        <th>Total Tokens</th>
                        <th>Cost</th>$textHeader
                    </tr>
                    $body
                </table>
                """.trimIndent()
    }

    /**
     * Formats a TokenTypes enum value into a more readable column header.
     * Inserts spaces before camel-case boundaries, e.g. "CacheWrite5m" -> "Cache Write5m".
     */
    private fun formatTokenTypeName(t: TokenTypes): String =
        t.name.replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), " ")

    private fun renderCreditsTable(credits: List<UsageInterface.CreditEntry>): String {
        if (credits.isEmpty()) return ""
        val dtFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)
        val totalCredits = credits.sumOf { it.amount }
        val rows = credits.joinToString("\n") { c ->
            val amountClass = if (c.amount >= 0) "credit-positive" else "credit-negative"
            val amountStr = "%+.4f".format(c.amount)
            val commentStr = c.comment?.let { escapeHtml(it) } ?: ""
            val metaStr = if (!c.metadata.isNullOrEmpty()) {
                """<br/><span class="credit-meta">${escapeHtml(c.metadata?.entries?.joinToString(", ") { "${it.key}=${it.value}" } ?: "")}</span>"""
            } else ""
            """
             <tr class="table-row">
                 <td>${dtFormatter.format(c.datetime)}</td>
                 <td class="$amountClass">$amountStr</td>
                 <td>$commentStr$metaStr</td>
             </tr>
             """.trimIndent()
        }
        val totalClass = if (totalCredits >= 0) "credit-positive" else "credit-negative"
        return """
             <h2>Credits History</h2>
             <table class="usage-table">
                 <tr class="table-header">
                     <th>Date/Time (UTC)</th>
                     <th>Amount</th>
                     <th>Comment / Metadata</th>
                 </tr>
                 $rows
                 <tr class="table-row total-row">
                     <td>Total</td>
                     <td class="$totalClass">${"%+.4f".format(totalCredits)}</td>
                     <td></td>
                 </tr>
             </table>
             """.trimIndent()
    }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    private fun navBar(active: String): String {
        fun cls(name: String) = if (name == active) "active" else ""
        return """
            <nav class="nav-bar">
                <a href="/usage" class="${cls("usage")}">📊 Usage</a>
                <a href="/credits" class="${cls("credits")}">💳 Buy Credits</a>
                <a href="/gifts/" class="${cls("gifts")}">🎁 Send Gifts</a>
            </nav>
        """.trimIndent()
    }

    companion object {
        val log = LoggerFactory.getLogger(UsageServlet::class.java)
    }
}