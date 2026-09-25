package com.simiacryptus.cognotik.platform.client

import com.simiacryptus.cognotik.platform.UsageInterface
import com.simiacryptus.cognotik.platform.model.AIModel
import com.simiacryptus.cognotik.platform.model.ModelSchema

/**
 * Wire types shared between `com.simiacryptus.cognotik.webui.servlet.UsageStorageApiServlet`
 * (server side, in the `webui` module) and [UsageClient] (client side, here).
 *
 * These exist mainly so that JSON deserialization has concrete, non-erased field
 * types to work with - a bare `Map<String, Any>` loses the [ModelSchema.Usage]
 * typing of nested values on the way back in.
 */

data class UsageSummaryResponse(val summary: Map<String, ModelSchema.Usage>)

data class SessionSummaryBulkRequest(val sessionIds: List<String>)
data class SessionSummaryBulkResponse(val summary: Map<String, Map<String, ModelSchema.Usage>>)

data class IncrementUsageRequest(
  val sessionId: String,
  val model: AIModel,
  val usage: ModelSchema.Usage,
  val data: ModelSchema.UsageData? = null
)

data class CreditRequest(
  val amount: Double,
  val comment: String? = null,
  val metadata: Map<String, String>? = null
)

data class CreditResponse(val balance: Double)

data class ParentSessionRequest(val child: String, val parent: String)
data class ParentSessionResponse(val parent: String?)

data class BudgetResponse(val budget: Double)
data class BalanceResponse(val balance: Double)

data class DailyUsageResponse(val entries: List<UsageInterface.DailyUsage>)
data class CreditsResponse(val entries: List<UsageInterface.CreditEntry>)
data class SessionRowsResponse(val rows: List<UsageInterface.UsageRow>)

data class StatusResponse(val status: String = "ok")
data class ErrorResponse(val error: String)