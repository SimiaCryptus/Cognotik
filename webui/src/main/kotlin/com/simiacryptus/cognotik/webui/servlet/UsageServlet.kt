package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.webui.application.authenticate
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class UsageServlet : HttpServlet() {
  public override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
    response.status = HttpServletResponse.SC_OK
     val useJson = request.getParameter("format")?.equals("json", ignoreCase = true) == true ||
         request.getHeader("Accept")?.contains("application/json", ignoreCase = true) == true


    val usageManager = ApplicationServices.fileApplicationServices().usageManager
    if (request.parameterMap.containsKey("sessionId")) {
      val session = Session(request.getParameter("sessionId"))
       serve(response, usageManager.getSessionUsageSummary(session), useJson)
    } else {
      val userinfo = authenticate(request, response) ?: return
      val usage = usageManager.getUserUsageSummary(userinfo)
       serve(response, usage, useJson)
    }
  }

  private fun serve(
    resp: HttpServletResponse,
     usage: Map<String, ModelSchema.Usage>,
     useJson: Boolean = false
  ) {
     if (useJson) {
       serveJson(resp, usage)
     } else {
       serveHtml(resp, usage)
     }
   }
   private fun serveJson(
     resp: HttpServletResponse,
     usage: Map<String, ModelSchema.Usage>
   ) {
     resp.contentType = "application/json"
     val totalPromptTokens = usage.values.sumOf { it.prompt_tokens }
     val totalCompletionTokens = usage.values.sumOf { it.completion_tokens }
     val totalCost = usage.entries.sumOf { (_, count) -> count.cost ?: 0.0 }
     val result = mapOf(
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
     val gson: Gson = GsonBuilder().setPrettyPrinting().create()
     resp.writer.write(gson.toJson(result))
   }
   private fun serveHtml(
     resp: HttpServletResponse,
     usage: Map<String, ModelSchema.Usage>
   ) {
     resp.contentType = "text/html"
    val totalPromptTokens = usage.values.sumOf { it.prompt_tokens }
    val totalCompletionTokens = usage.values.sumOf { it.completion_tokens }
    val totalCost = usage.entries.sumOf { (_, count) -> count.cost ?: 0.0 }

    resp.writer.write(
      """
            <html>
            <head>
                <title>Usage</title>
                <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
                <style>
                    body { font-family: Arial, sans-serif; }
                    table { width: 100%; border-collapse: collapse; }
                    th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
                    tr:nth-child(even) { background-color: #f2f2f2; }
                </style>
            </head>
            <body>
            <table class="usage-table">
                <tr class="table-header">
                    <th>Model</th>
                    <th>Prompt</th>
                    <th>Completion</th>
                    <th>Cost</th>
                </tr>
                ${
        usage.entries.joinToString("\n") { (model, count) ->
          """
                        <tr class="table-row">
                            <td class="model-cell">$model</td>
                            <td class="prompt-cell">${count.prompt_tokens}</td>
                            <td class="completion-cell">${count.completion_tokens}</td>
                            <td class="cost-cell">${"%.4f".format(count.cost ?: 0.0)}</td>
                        </tr>
                        """.trimIndent()
        }
      }
            <tr class="table-row">
                <td class="model-cell">Total</td>
                <td class="prompt-cell">$totalPromptTokens</td>
                <td class="completion-cell">$totalCompletionTokens</td>
                <td class="cost-cell">${"%.4f".format(totalCost)}</td>
            </tr>
            </table>
            </body>
            </html>
            """.trimIndent())
  }

  companion object
}