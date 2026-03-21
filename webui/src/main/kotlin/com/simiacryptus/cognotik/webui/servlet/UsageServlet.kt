package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.webui.application.authenticate
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class UsageServlet : HttpServlet() {
  public override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
    response.contentType = "text/html"
    response.status = HttpServletResponse.SC_OK

    val usageManager = ApplicationServices.fileApplicationServices().usageManager
    if (request.parameterMap.containsKey("sessionId")) {
      val session = Session(request.getParameter("sessionId"))
      serve(response, usageManager.getSessionUsageSummary(session))
    } else {
      val userinfo = authenticate(request, response) ?: return
      val usage = usageManager.getUserUsageSummary(userinfo)
      serve(response, usage)
    }
  }

  private fun serve(
    resp: HttpServletResponse,
    usage: Map<String, ModelSchema.Usage>
  ) {
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

