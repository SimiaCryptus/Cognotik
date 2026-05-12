package com.simiacryptus.cognotik.webui.servlet
    
    import com.google.gson.Gson
    import com.google.gson.GsonBuilder
    import com.simiacryptus.cognotik.models.ModelSchema
    import com.simiacryptus.cognotik.platform.ApplicationServices
    import com.simiacryptus.cognotik.platform.Session
    import com.simiacryptus.cognotik.platform.model.UsageInterface
    import com.simiacryptus.cognotik.webui.application.authenticate
    import jakarta.servlet.http.HttpServlet
    import jakarta.servlet.http.HttpServletRequest
    import jakarta.servlet.http.HttpServletResponse
    import org.slf4j.LoggerFactory
    import java.time.LocalDate
    import java.time.ZoneOffset
    import java.time.format.DateTimeParseException
    
    class UsageServlet : HttpServlet() {
    
        public override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
            response.status = HttpServletResponse.SC_OK
            val useJson = isJsonRequested(request)
            val usageManager = ApplicationServices.fileApplicationServices().usageManager
    
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
    
            if (useJson) {
                serveJson(response, usage, daily, budget, from, to)
            } else {
                serveHtml(
                    resp = response,
                    usage = usage,
                    daily = daily,
                    availableBudget = budget,
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
                serveJson(resp, usage, emptyList(), null, null, null)
            } else {
                serveHtml(resp, usage, emptyList(), null, null, null, scopeLabel)
            }
        }
    
        private fun serveJson(
            resp: HttpServletResponse,
            usage: Map<String, ModelSchema.Usage>,
            daily: List<UsageInterface.DailyUsage>,
            availableBudget: Double?,
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
    
            val gson: Gson = GsonBuilder().setPrettyPrinting().create()
            resp.writer.write(gson.toJson(result))
        }
    
        private fun serveHtml(
            resp: HttpServletResponse,
            usage: Map<String, ModelSchema.Usage>,
            daily: List<UsageInterface.DailyUsage>,
            availableBudget: Double?,
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
    
            resp.writer.write(
                """
                <html>
                <head>
                    <title>Usage</title>
                    <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
                    <style>
                        body { font-family: Arial, sans-serif; margin: 20px; }
                        h1, h2 { color: #333; }
                        table { width: 100%; border-collapse: collapse; margin-bottom: 20px; }
                        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
                        tr:nth-child(even) { background-color: #f2f2f2; }
                        .table-header { background-color: #4a6fa5; color: white; }
                        .total-row { font-weight: bold; background-color: #e8eef7 !important; }
                        .scope { font-size: 1.1em; margin-bottom: 10px; color: #555; }
                        .budget { font-size: 1.1em; margin-bottom: 15px; padding: 8px; background: #eef7ee; border-left: 4px solid #4a8; }
                        .range-form { margin-bottom: 15px; }
                        .range-form label { margin-right: 10px; }
                    </style>
                </head>
                <body>
                <h1>Usage Summary</h1>
                $scopeHtml
                $budgetHtml
                $rangeFormHtml
                <h2>By Model</h2>
                $modelTableHtml
                $dailyHtml
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
    
        companion object {
            val log = LoggerFactory.getLogger(UsageServlet::class.java)
        }
    }