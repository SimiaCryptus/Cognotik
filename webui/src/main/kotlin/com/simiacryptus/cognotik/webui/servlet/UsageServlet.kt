package com.simiacryptus.cognotik.webui.servlet

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.UsageInterface
import com.simiacryptus.cognotik.webui.application.authenticate
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class UsageServlet : HttpServlet() {

    public override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
        response.status = HttpServletResponse.SC_OK
        val useJson = isJsonRequested(request)
        val usageManager = ApplicationServices.fileApplicationServices().usageDB

        if (request.parameterMap.containsKey("sessionId")) {
            handleSessionUsage(request, response, useJson, usageManager)
        } else {
            handleUserUsage(request, response, useJson, usageManager)
        }
    }

    private fun isJsonRequested(request: HttpServletRequest): Boolean {
        val formatParam = request.getParameter("format")?.equals("json", ignoreCase = true) == true
        val acceptHeader = request.getHeader("Accept")?.contains("application/json", ignoreCase = true) == true
        return formatParam || acceptHeader
    }

    private fun handleSessionUsage(
        request: HttpServletRequest,
        response: HttpServletResponse,
        useJson: Boolean,
        usageManager: UsageInterface
    ) {
        val session = Session(request.getParameter("sessionId"))
        val usage = usageManager.getSessionUsageSummary(session)
        serve(
            resp = response,
            usage = usage,
            useJson = useJson,
            scopeLabel = "Session: ${session.sessionId}"
        )
    }

    private fun handleUserUsage(
        request: HttpServletRequest,
        response: HttpServletResponse,
        useJson: Boolean,
        usageManager: UsageInterface
    ) {
        val userinfo = authenticate(request, response) ?: throw RuntimeException("Authentication failed")
        val (from, to) = parseDateRange(request)

        val usage = usageManager.getUserUsageSummary(userinfo, from, to)
        val daily = runCatching { usageManager.getUserDailyUsage(userinfo, from, to) }.getOrElse { emptyList() }
        val budget = runCatching { usageManager.getAvailableBudget(userinfo) }.getOrNull()
        val credits = runCatching { usageManager.getUserCredits(userinfo, from, to) }.getOrElse { emptyList() }

        if (useJson) {
            serveJson(response, usage, daily, budget, credits, from, to)
        } else {
            serveHtml(
                resp = response,
                usage = usage,
                daily = daily,
                availableBudget = budget,
                credits = credits,
                from = from,
                to = to,
                scopeLabel = "User: ${userinfo.email}"
            )
        }
    }

    private fun parseDateRange(request: HttpServletRequest): Pair<LocalDate, LocalDate> {
        val today = LocalDate.now(ZoneOffset.UTC)
        val defaultFrom = today.minusDays(30)
        val defaultTo = today.plusDays(1)
        val from = request.getParameter("from")?.let { parseDateOrNull(it) } ?: defaultFrom
        val to = request.getParameter("to")?.let { parseDateOrNull(it) } ?: defaultTo
        return if (to.isBefore(from)) defaultFrom to defaultTo else from to to
    }

    private fun parseDateOrNull(s: String): LocalDate? = try {
        LocalDate.parse(s)
    } catch (e: DateTimeParseException) {
        null
    }

    private fun serve(
        resp: HttpServletResponse,
        usage: Map<String, ModelSchema.Usage>,
        useJson: Boolean = false,
        scopeLabel: String = ""
    ) {
        if (useJson) {
            serveJson(resp, usage, emptyList(), null, emptyList(), null, null)
        } else {
            serveHtml(resp, usage, emptyList(), null, emptyList(), null, null, scopeLabel)
        }
    }

    private fun serveJson(
        resp: HttpServletResponse,
        usage: Map<String, ModelSchema.Usage>,
        daily: List<UsageInterface.DailyUsage>,
        availableBudget: Double?,
        credits: List<UsageInterface.CreditEntry>,
        from: LocalDate?,
        to: LocalDate?
    ) {
        resp.contentType = "application/json"

        val totalPromptTokens = usage.values.sumOf { it.prompt_tokens }
        val totalCompletionTokens = usage.values.sumOf { it.completion_tokens }
        val totalCost = usage.values.sumOf { it.cost ?: 0.0 }

        val result = mutableMapOf<String, Any?>(
            "models" to usage.entries.map { (model, count) ->
                mapOf(
                    "model" to model,
                    "prompt_tokens" to count.prompt_tokens,
                    "completion_tokens" to count.completion_tokens,
                    "cost" to (count.cost ?: 0.0)
                )
            },
            "totals" to mapOf(
                "prompt_tokens" to totalPromptTokens,
                "completion_tokens" to totalCompletionTokens,
                "cost" to totalCost
            )
        )

        if (from != null && to != null) {
            result["range"] = mapOf(
                "from" to from.toString(),
                "to" to to.toString()
            )
        }
        if (availableBudget != null) {
            result["available_budget"] = availableBudget
        }
        if (daily.isNotEmpty()) {
            result["daily"] = daily.map { d ->
                mapOf(
                    "day" to d.day.toString(),
                    "model" to d.model,
                    "prompt_tokens" to d.usage.prompt_tokens,
                    "completion_tokens" to d.usage.completion_tokens,
                    "cost" to (d.usage.cost ?: 0.0)
                )
            }
        }
        if (credits.isNotEmpty()) {
            result["credits"] = credits.map { c ->
                mutableMapOf<String, Any?>(
                    "datetime" to c.datetime.toString(),
                    "amount" to c.amount,
                    "comment" to c.comment
                ).also { m ->
                    if (!c.metadata.isNullOrEmpty()) m["metadata"] = c.metadata
                }
            }
        }

        val gson: Gson = GsonBuilder().setPrettyPrinting().create()
        resp.writer.write(gson.toJson(result))
    }

    private fun serveHtml(
        resp: HttpServletResponse,
        usage: Map<String, ModelSchema.Usage>,
        daily: List<UsageInterface.DailyUsage>,
        availableBudget: Double?,
        credits: List<UsageInterface.CreditEntry>,
        from: LocalDate?,
        to: LocalDate?,
        scopeLabel: String = ""
    ) {
        resp.contentType = "text/html"

        val totalPromptTokens = usage.values.sumOf { it.prompt_tokens }
        val totalCompletionTokens = usage.values.sumOf { it.completion_tokens }
        val totalCost = usage.values.sumOf { it.cost ?: 0.0 }

        val scopeHtml = renderScope(scopeLabel)
        val budgetHtml = renderBudget(availableBudget)
        val rangeFormHtml = renderRangeForm(from, to)
        val modelTableHtml = renderModelTable(usage, totalPromptTokens, totalCompletionTokens, totalCost)
        val dailyHtml = renderDailyTable(daily)
        val creditsHtml = renderCreditsTable(credits)

        resp.writer.write(
            """
                <html>
                <head>
                    <title>Usage</title>
                    <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
                    <script src="/modules/theme.js"></script>
                    <style>
                        :root,
                        html[data-theme="light"] {
                            --bg: #ffffff;
                            --fg: #333333;
                            --muted-fg: #555555;
                            --border: #dddddd;
                            --row-alt-bg: #f2f2f2;
                            --header-bg: #4a6fa5;
                            --header-fg: #ffffff;
                            --total-row-bg: #e8eef7;
                            --budget-bg: #eef7ee;
                            --budget-border: #4a8;
                            --nav-bg: #f0f3f8;
                            --nav-link: #4a6fa5;
                            --nav-link-hover-bg: #e1e7f1;
                            --nav-active-bg: #4a6fa5;
                            --nav-active-fg: #ffffff;
                            --btn-primary-bg: #4a6fa5;
                            --btn-primary-fg: #ffffff;
                            --btn-primary-hover-bg: #3a5a8c;
                            --btn-secondary-bg: #ffffff;
                            --btn-secondary-fg: #4a6fa5;
                            --btn-secondary-border: #4a6fa5;
                            --btn-secondary-hover-bg: #eef2f9;
                            --credit-positive: #2a7a2a;
                            --credit-negative: #a02020;
                            --credit-meta-fg: #666666;
                            --input-bg: #ffffff;
                            --input-fg: #333333;
                            --input-border: #cccccc;
                        }
                        html[data-theme="dark"] {
                            --bg: #1e1e1e;
                            --fg: #e6e6e6;
                            --muted-fg: #bbbbbb;
                            --border: #444444;
                            --row-alt-bg: #2a2a2a;
                            --header-bg: #2c4a78;
                            --header-fg: #ffffff;
                            --total-row-bg: #2f3b50;
                            --budget-bg: #1f3a1f;
                            --budget-border: #4a8;
                            --nav-bg: #2a2a2a;
                            --nav-link: #8ab0e0;
                            --nav-link-hover-bg: #3a3a3a;
                            --nav-active-bg: #2c4a78;
                            --nav-active-fg: #ffffff;
                            --btn-primary-bg: #2c4a78;
                            --btn-primary-fg: #ffffff;
                            --btn-primary-hover-bg: #3a5a8c;
                            --btn-secondary-bg: #2a2a2a;
                            --btn-secondary-fg: #8ab0e0;
                            --btn-secondary-border: #8ab0e0;
                            --btn-secondary-hover-bg: #3a3a3a;
                            --credit-positive: #5fcf5f;
                            --credit-negative: #ff7070;
                            --credit-meta-fg: #aaaaaa;
                            --input-bg: #2a2a2a;
                            --input-fg: #e6e6e6;
                            --input-border: #555555;
                        }
                        @media (prefers-color-scheme: dark) {
                            html[data-theme="auto"] {
                                --bg: #1e1e1e;
                                --fg: #e6e6e6;
                                --muted-fg: #bbbbbb;
                                --border: #444444;
                                --row-alt-bg: #2a2a2a;
                                --header-bg: #2c4a78;
                                --header-fg: #ffffff;
                                --total-row-bg: #2f3b50;
                                --budget-bg: #1f3a1f;
                                --budget-border: #4a8;
                                --nav-bg: #2a2a2a;
                                --nav-link: #8ab0e0;
                                --nav-link-hover-bg: #3a3a3a;
                                --nav-active-bg: #2c4a78;
                                --nav-active-fg: #ffffff;
                                --btn-primary-bg: #2c4a78;
                                --btn-primary-fg: #ffffff;
                                --btn-primary-hover-bg: #3a5a8c;
                                --btn-secondary-bg: #2a2a2a;
                                --btn-secondary-fg: #8ab0e0;
                                --btn-secondary-border: #8ab0e0;
                                --btn-secondary-hover-bg: #3a3a3a;
                                --credit-positive: #5fcf5f;
                                --credit-negative: #ff7070;
                                --credit-meta-fg: #aaaaaa;
                                --input-bg: #2a2a2a;
                                --input-fg: #e6e6e6;
                                --input-border: #555555;
                            }
                        }
                        html, body { background-color: var(--bg); color: var(--fg); }
                        body { font-family: Arial, sans-serif; margin: 20px; }
                        h1, h2 { color: var(--fg); }
                        table { width: 100%; border-collapse: collapse; margin-bottom: 20px; }
                        th, td { border: 1px solid var(--border); padding: 8px; text-align: left; }
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
                        #theme-selector {
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
                    </style>
                </head>
                <body>
                <div class="theme-switcher" style="display:flex; justify-content:flex-end; align-items:center; margin-bottom:10px;">
                    <label for="theme-selector" style="margin-right:8px; font-size:0.95em;">Theme:</label>
                    <select id="theme-selector" aria-label="Theme selector">
                        <option value="auto">Auto</option>
                        <option value="light">Light</option>
                        <option value="dark">Dark</option>
                    </select>
                </div>
                <h1>Usage Summary</h1>
                $scopeHtml
                ${navBar("usage")}
                $budgetHtml
                $rangeFormHtml
                <h2>By Model</h2>
                $modelTableHtml
                $dailyHtml
             $creditsHtml
                <script>
                    (function() {
                        function initTheme() {
                            if (typeof ThemeManager !== 'undefined') {
                                ThemeManager.init();
                                var sel = document.getElementById('theme-selector');
                                if (sel) ThemeManager.bindSelector(sel);
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

    private fun renderScope(scopeLabel: String): String =
        if (scopeLabel.isNotEmpty()) """<div class="scope">$scopeLabel</div>""" else ""

    private fun renderBudget(availableBudget: Double?): String =
        if (availableBudget != null) {
            """<div class="budget">Available budget: <strong>${"%.4f".format(availableBudget)}</strong></div>"""
        } else ""

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

    private fun renderModelTable(
        usage: Map<String, ModelSchema.Usage>,
        totalPromptTokens: Long,
        totalCompletionTokens: Long,
        totalCost: Double
    ): String {
        val rows = usage.entries.joinToString("\n") { (model, count) ->
            """
                <tr class="table-row">
                    <td class="model-cell">$model</td>
                    <td class="prompt-cell">${count.prompt_tokens}</td>
                    <td class="completion-cell">${count.completion_tokens}</td>
                    <td class="cost-cell">${"%.4f".format(count.cost ?: 0.0)}</td>
                </tr>
                """.trimIndent()
        }
        return """
                <table class="usage-table">
                    <tr class="table-header">
                        <th>Model</th>
                        <th>Prompt</th>
                        <th>Completion</th>
                        <th>Cost</th>
                    </tr>
                    $rows
                    <tr class="table-row total-row">
                        <td class="model-cell">Total</td>
                        <td class="prompt-cell">$totalPromptTokens</td>
                        <td class="completion-cell">$totalCompletionTokens</td>
                        <td class="cost-cell">${"%.4f".format(totalCost)}</td>
                    </tr>
                </table>
                """.trimIndent()
    }

    private fun renderDailyTable(daily: List<UsageInterface.DailyUsage>): String {
        if (daily.isEmpty()) return ""
        val rows = daily.joinToString("\n") { d ->
            """
                <tr class="table-row">
                    <td>${d.day}</td>
                    <td>${d.model}</td>
                    <td>${d.usage.prompt_tokens}</td>
                    <td>${d.usage.completion_tokens}</td>
                    <td>${"%.4f".format(d.usage.cost ?: 0.0)}</td>
                </tr>
                """.trimIndent()
        }
        return """
                <h2>Daily Breakdown</h2>
                <table class="usage-table">
                    <tr class="table-header">
                        <th>Day</th>
                        <th>Model</th>
                        <th>Prompt</th>
                        <th>Completion</th>
                        <th>Cost</th>
                    </tr>
                    $rows
                </table>
                """.trimIndent()
    }

    private fun renderCreditsTable(credits: List<UsageInterface.CreditEntry>): String {
        if (credits.isEmpty()) return ""
        val dtFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)
        val totalCredits = credits.sumOf { it.amount }
        val rows = credits.joinToString("\n") { c ->
            val amountClass = if (c.amount >= 0) "credit-positive" else "credit-negative"
            val amountStr = "%+.4f".format(c.amount)
            val commentStr = c.comment?.let { escapeHtml(it) } ?: ""
            val metaStr = if (!c.metadata.isNullOrEmpty()) {
                """<br/><span class="credit-meta">${escapeHtml(c.metadata.entries.joinToString(", ") { "${it.key}=${it.value}" })}</span>"""
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