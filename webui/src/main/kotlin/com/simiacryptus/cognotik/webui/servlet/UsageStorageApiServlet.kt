package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServicesImpl
import com.simiacryptus.cognotik.platform.UsageInterface
import com.simiacryptus.cognotik.platform.client.BalanceResponse
import com.simiacryptus.cognotik.platform.client.BudgetResponse
import com.simiacryptus.cognotik.platform.client.CreditRequest
import com.simiacryptus.cognotik.platform.client.CreditResponse
import com.simiacryptus.cognotik.platform.client.CreditsResponse
import com.simiacryptus.cognotik.platform.client.DailyUsageResponse
import com.simiacryptus.cognotik.platform.client.ErrorResponse
import com.simiacryptus.cognotik.platform.client.IncrementUsageRequest
import com.simiacryptus.cognotik.platform.client.ParentSessionRequest
import com.simiacryptus.cognotik.platform.client.ParentSessionResponse
import com.simiacryptus.cognotik.platform.client.SessionRowsResponse
import com.simiacryptus.cognotik.platform.client.SessionSummaryBulkRequest
import com.simiacryptus.cognotik.platform.client.SessionSummaryBulkResponse
import com.simiacryptus.cognotik.platform.client.StatusResponse
import com.simiacryptus.cognotik.platform.client.UsageSummaryResponse
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.webui.application.UserProviderImpl
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.time.LocalDate

/**
 * HTTP surface for [UsageInterface], consumed by
 * [com.simiacryptus.cognotik.platform.client.UsageClient].
 *
 * All user-scoped operations act on the *authenticated caller*; there is no support
 * for querying or crediting another user's usage through this API. `clear()` is
 * exposed for completeness/testing but is destructive - deployments that shouldn't
 * allow it should override [doPost] or replace this servlet entirely.
 */
class UsageStorageApiServlet(
  private val usage: UsageInterface = ApplicationServicesImpl.fileApplicationServices().usageDB
) : HttpServlet() {

  private fun currentUser(request: HttpServletRequest): User =
    UserProviderImpl().authenticate(request) ?: throw IllegalStateException("Authentication failed")

  private fun action(request: HttpServletRequest): String =
    (request.pathInfo ?: request.servletPath)
      ?.trim('/')?.substringBefore('/')?.takeIf { it.isNotBlank() }
      ?: throw IllegalArgumentException("No action specified")

  private fun requireParam(request: HttpServletRequest, name: String): String =
    request.getParameter(name) ?: throw IllegalArgumentException("Missing required parameter: $name")

  private fun writeJson(response: HttpServletResponse, value: Any) {
    response.contentType = "application/json"
    response.status = HttpServletResponse.SC_OK
    response.writer.write(JsonUtil.toJson(value))
  }

  private fun writeError(response: HttpServletResponse, status: Int, message: String?) {
    response.status = status
    response.contentType = "application/json"
    response.writer.write(JsonUtil.toJson(ErrorResponse(message ?: "error")))
  }

  private fun <T> readBody(request: HttpServletRequest, clazz: Class<T>): T {
    val body = request.reader.use { it.readText() }
    return JsonUtil.fromJson(body, clazz)
  }

  override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
    try {
      when (action(request)) {
        "userSummary" -> {
          val user = currentUser(request)
          val from = LocalDate.parse(requireParam(request, "from"))
          val to = LocalDate.parse(requireParam(request, "to"))
          writeJson(response, UsageSummaryResponse(usage.getUserUsageSummary(user, from, to)))
        }

        "sessionSummary" -> {
          val session = Session(requireParam(request, "sessionId"))
          writeJson(response, UsageSummaryResponse(usage.getSessionUsageSummary(session)))
        }

        "sessionRows" -> {
          val session = Session(requireParam(request, "sessionId"))
          writeJson(response, SessionRowsResponse(usage.getSessionUsageRows(session)))
        }

        "budget" -> writeJson(response, BudgetResponse(usage.getAvailableBudget(currentUser(request))))

        "balance" -> writeJson(response, BalanceResponse(usage.getUserBalance(currentUser(request))))

        "dailyUsage" -> {
          val user = currentUser(request)
          val from = LocalDate.parse(requireParam(request, "from"))
          val to = LocalDate.parse(requireParam(request, "to"))
          writeJson(response, DailyUsageResponse(usage.getUserDailyUsage(user, from, to)))
        }

        "credits" -> {
          val user = currentUser(request)
          val from = LocalDate.parse(requireParam(request, "from"))
          val to = LocalDate.parse(requireParam(request, "to"))
          writeJson(response, CreditsResponse(usage.getUserCredits(user, from, to)))
        }

        "parentSession" -> {
          val child = Session(requireParam(request, "child"))
          writeJson(response, ParentSessionResponse(usage.getParentSession(child)?.sessionId))
        }

        else -> writeError(response, HttpServletResponse.SC_NOT_FOUND, "Unknown action")
      }
    } catch (e: IllegalArgumentException) {
      writeError(response, HttpServletResponse.SC_BAD_REQUEST, e.message)
    } catch (e: IllegalStateException) {
      writeError(response, HttpServletResponse.SC_UNAUTHORIZED, e.message)
    } catch (e: Exception) {
      log.error("Error handling usage storage API GET request (path=${request.pathInfo})", e)
      writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.message)
    }
  }

  override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
    try {
      when (action(request)) {
        "sessionSummaryBulk" -> {
          val req = readBody(request, SessionSummaryBulkRequest::class.java)
          val result = usage.getSessionUsageSummaryBulk(req.sessionIds.map { Session(it) })
            .mapKeys { it.key.sessionId }
          writeJson(response, SessionSummaryBulkResponse(result))
        }

        "increment" -> {
          val req = readBody(request, IncrementUsageRequest::class.java)
          usage.incrementUsage(
            session = Session(req.sessionId),
            user = currentUser(request),
            model = req.model,
            usage = req.usage,
            data = req.data
          )
          writeJson(response, StatusResponse())
        }

        "credit" -> {
          val req = readBody(request, CreditRequest::class.java)
          val balance = usage.creditUser(currentUser(request), req.amount, req.comment, req.metadata)
          writeJson(response, CreditResponse(balance))
        }

        "parentSession" -> {
          val req = readBody(request, ParentSessionRequest::class.java)
          usage.setParentSession(Session(req.child), Session(req.parent))
          writeJson(response, StatusResponse())
        }

        "clear" -> {
          usage.clear()
          writeJson(response, StatusResponse())
        }

        else -> writeError(response, HttpServletResponse.SC_NOT_FOUND, "Unknown action")
      }
    } catch (e: IllegalArgumentException) {
      writeError(response, HttpServletResponse.SC_BAD_REQUEST, e.message)
    } catch (e: IllegalStateException) {
      writeError(response, HttpServletResponse.SC_UNAUTHORIZED, e.message)
    } catch (e: Exception) {
      log.error("Error handling usage storage API POST request (path=${request.pathInfo})", e)
      writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.message)
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(UsageStorageApiServlet::class.java)
  }
}