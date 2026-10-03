package com.simiacryptus.cognotik.webui.servlet

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import com.simiacryptus.cognotik.platform.ServiceRouter
import com.simiacryptus.cognotik.platform.model.ModelSchema
import com.simiacryptus.cognotik.platform.model.ModelSchema.TokenTypes
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.service.UsageInterface
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.floor

/**
 * Standalone servlet exposing the usage GraphQL endpoint.
 * Register at e.g. `/usage/graphql`.
 */
class UsageGraphQLServlet : HttpServlet() {
  public override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) = UsageGraphQL.handle(req, resp)
  public override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) = UsageGraphQL.handle(req, resp)
}

/**
 * GraphQL API for usage data.
 *
 * - POST with JSON body `{"query": "...", "variables": {...}, "operationName": "..."}`
 * - POST with `Content-Type: application/graphql` and the raw query as body
 * - GET with `?query=...&variables=...&operationName=...`
 * - GET with no query returns the schema SDL as text/plain
 *
 * Token counts are exposed as `Float` since GraphQL `Int` is 32-bit.
 */
object UsageGraphQL {
  private val log = LoggerFactory.getLogger(UsageGraphQL::class.java)
  private const val USER_KEY = "user"

  private val gsonIn: Gson = Gson()

  /** Emits whole-valued doubles (e.g. token counts) as integers in the JSON response. */
  private val gsonOut: Gson = GsonBuilder()
    .serializeNulls()
    .setPrettyPrinting()
    .registerTypeAdapter(Double::class.javaObjectType, JsonSerializer<Double> { src, _, _ ->
      if (!src.isNaN() && !src.isInfinite() && src == floor(src) && abs(src) < 1e15) JsonPrimitive(src.toLong())
      else JsonPrimitive(src)
    })
    .create()

  val sdl: String = """
    schema { query: Query }

    enum TokenType { ${TokenTypes.values().joinToString(" ") { it.name }} }

    type TokenTypeInfo {
      name: TokenType!
      parent: TokenType
    }

    type TokenCount {
      type: TokenType!
      count: Float!
    }

    type UsageTotals {
      cost: Float!
      totalTokens: Float!
      promptTokens: Float!
      completionTokens: Float!
      tokens: [TokenCount!]!
    }

    type ModelUsage {
      model: String!
      cost: Float!
      totalTokens: Float!
      promptTokens: Float!
      completionTokens: Float!
      tokens: [TokenCount!]!
    }

    type DailyUsage {
      day: String!
      model: String!
      cost: Float!
      totalTokens: Float!
      promptTokens: Float!
      completionTokens: Float!
      tokens: [TokenCount!]!
    }

    type KeyValue {
      key: String!
      value: String!
    }

    type CreditEntry {
      datetime: String!
      amount: Float!
      comment: String
      metadata: [KeyValue!]!
    }

    type UsageRow {
      id: ID!
      sessionId: String
      userId: String
      model: String
      datetime: String
      cost: Float!
      totalTokens: Float!
      promptTokens: Float!
      completionTokens: Float!
      tokens: [TokenCount!]!
      inputText: String
      outputText: String
    }

    type UserUsage {
      from: String!
      to: String!
      models: [ModelUsage!]!
      totals: UsageTotals!
      daily(model: String): [DailyUsage!]!
      credits: [CreditEntry!]!
      availableBudget: Float
      balance: Float
    }

    type SessionUsage {
      sessionId: String!
      parentSessionId: String
      models: [ModelUsage!]!
      totals: UsageTotals!
      rowCount: Int!
      rows(model: String, limit: Int, offset: Int): [UsageRow!]!
    }

    type SessionSummary {
      sessionId: String!
      models: [ModelUsage!]!
      totals: UsageTotals!
    }

    type Query {
      # Available budget (credits minus cost-to-date) for the authenticated user
      availableBudget: Float
      # Account balance for the authenticated user
      balance: Float
      # User-scoped usage; dates are ISO yyyy-MM-dd, from inclusive / to exclusive (UTC).
      # Defaults to the last 30 days.
      userUsage(from: String, to: String): UserUsage!
      # Session-scoped usage (includes descendant sessions)
      sessionUsage(sessionId: String!): SessionUsage!
      # Bulk, non-recursive per-session summaries
      sessionsUsage(sessionIds: [String!]!): [SessionSummary!]!
      # Known token types
      tokenTypes: [TokenTypeInfo!]!
    }
  """.trimIndent()

  // ---- Source objects (resolved lazily so only requested data is loaded) ----

  class UserUsageSource(val user: User, val fromDate: LocalDate, val toDate: LocalDate) {
    val from: String get() = fromDate.toString()
    val to: String get() = toDate.toString()
    val summary: Map<String, ModelSchema.Usage> by lazy {
      usageManager().getUserUsageSummary(user, fromDate, toDate)
    }
  }

  class SessionUsageSource(val user: User, val session: Session) {
    val sessionId: String get() = session.sessionId
    val summary: Map<String, ModelSchema.Usage> by lazy {
      usageManager().getSessionUsageSummary(user = user, session = session)
    }
    val rows: List<UsageInterface.UsageRow> by lazy {
      usageManager().getSessionUsageRows(session, user)
    }
  }

  private fun usageManager(): UsageInterface = ServiceRouter as UsageInterface

  private fun DataFetchingEnvironment.user(): User =
    graphQlContext.get<User?>(USER_KEY) ?: throw IllegalStateException("Authentication required")

  // ---- Mapping helpers ----

  private fun tokenList(counts: Map<TokenTypes, Long>): List<Map<String, Any?>> =
    UsageTokens.orderTokenTypes(counts.keys).map { t ->
      mapOf("type" to t.name, "count" to (counts[t] ?: 0L))
    }

  private fun usageFields(counts: Map<TokenTypes, Long>, cost: Double, total: Long): MutableMap<String, Any?> =
    mutableMapOf(
      "cost" to cost,
      "totalTokens" to total,
      "promptTokens" to (counts[TokenTypes.Prompt] ?: 0L),
      "completionTokens" to (counts[TokenTypes.Completion] ?: 0L),
      "tokens" to tokenList(counts)
    )

  private fun modelList(summary: Map<String, ModelSchema.Usage>): List<Map<String, Any?>> =
    summary.entries.map { (model, u) ->
      usageFields(u.counts, u.cost, UsageTokens.totalTokens(u)).apply { put("model", model) }
    }

  private fun totals(summary: Map<String, ModelSchema.Usage>): Map<String, Any?> {
    val merged = UsageTokens.mergeCounts(summary.values.map { it.counts })
    return usageFields(
      merged,
      summary.values.sumOf { it.cost },
      summary.values.sumOf { UsageTokens.totalTokens(it) }
    )
  }

  private fun rowMap(r: UsageInterface.UsageRow): Map<String, Any?> =
    usageFields(r.tokenCounts, r.cost, UsageTokens.totalTokens(r.tokenCounts)).apply {
      put("id", r.id.toString())
      put("sessionId", r.sessionId)
      put("userId", r.userId)
      put("model", r.model)
      put("datetime", r.datetime?.toString())
      put("inputText", r.inputText)
      put("outputText", r.outputText)
    }

  // ---- Schema ----

  val graphQL: GraphQL by lazy { buildGraphQL() }

  private fun buildGraphQL(): GraphQL {
    val registry = SchemaParser().parse(sdl)
    val wiring = RuntimeWiring.newRuntimeWiring()
      .type("Query") { b ->
        b.dataFetcher("availableBudget", DataFetcher { env -> usageManager().getAvailableBudget(env.user()) })
          .dataFetcher("balance", DataFetcher { env -> usageManager().getUserBalance(env.user()) })
          .dataFetcher("userUsage", DataFetcher { env ->
            val (from, to) = UsageTokens.parseRange(
              env.getArgument<String?>("from"),
              env.getArgument<String?>("to")
            )
            UserUsageSource(env.user(), from, to)
          })
          .dataFetcher("sessionUsage", DataFetcher { env ->
            val id = env.getArgument<String>("sessionId")
              ?: throw IllegalArgumentException("sessionId is required")
            SessionUsageSource(env.user(), Session(id))
          })
          .dataFetcher("sessionsUsage", DataFetcher { env ->
            val ids = env.getArgument<List<String>>("sessionIds") ?: emptyList()
            val sessions = ids.distinct().map { Session(it) }
            usageManager().getSessionUsageSummaryBulk(env.user(), sessions).map { (s, summary) ->
              mapOf(
                "sessionId" to s.sessionId,
                "models" to modelList(summary),
                "totals" to totals(summary)
              )
            }
          })
          .dataFetcher("tokenTypes", DataFetcher { _ ->
            TokenTypes.values().map { mapOf("name" to it.name, "parent" to it.parent?.name) }
          })
      }
      .type("UserUsage") { b ->
        b.dataFetcher("models", DataFetcher { env -> modelList(env.getSource<UserUsageSource>()!!.summary) })
          .dataFetcher("totals", DataFetcher { env -> totals(env.getSource<UserUsageSource>()!!.summary) })
          .dataFetcher("daily", DataFetcher { env ->
            val src = env.getSource<UserUsageSource>()!!
            val modelFilter = env.getArgument<String?>("model")
            usageManager().getUserDailyUsage(src.user, src.fromDate, src.toDate)
              .filter { modelFilter == null || it.model == modelFilter }
              .map { d ->
                usageFields(d.usage.counts, d.usage.cost, UsageTokens.totalTokens(d.usage)).apply {
                  put("day", d.day.toString())
                  put("model", d.model)
                }
              }
          })
          .dataFetcher("credits", DataFetcher { env ->
            val src = env.getSource<UserUsageSource>()!!
            usageManager().getUserCredits(src.user, src.fromDate, src.toDate).map { c ->
              mapOf(
                "datetime" to c.datetime.toString(),
                "amount" to c.amount,
                "comment" to c.comment,
                "metadata" to (c.metadata ?: emptyMap()).map { (k, v) -> mapOf("key" to k, "value" to v) }
              )
            }
          })
          .dataFetcher("availableBudget", DataFetcher { env ->
            usageManager().getAvailableBudget(env.getSource<UserUsageSource>()!!.user)
          })
          .dataFetcher("balance", DataFetcher { env ->
            usageManager().getUserBalance(env.getSource<UserUsageSource>()!!.user)
          })
      }
      .type("SessionUsage") { b ->
        b.dataFetcher("parentSessionId", DataFetcher { env ->
          val src = env.getSource<SessionUsageSource>()!!
          usageManager().getParentSession(src.user, src.session)?.sessionId
        })
          .dataFetcher("models", DataFetcher { env -> modelList(env.getSource<SessionUsageSource>()!!.summary) })
          .dataFetcher("totals", DataFetcher { env -> totals(env.getSource<SessionUsageSource>()!!.summary) })
          .dataFetcher("rowCount", DataFetcher { env -> env.getSource<SessionUsageSource>()!!.rows.size })
          .dataFetcher("rows", DataFetcher { env ->
            val src = env.getSource<SessionUsageSource>()!!
            val modelFilter = env.getArgument<String?>("model")
            val offset = (env.getArgument<Int?>("offset") ?: 0).coerceAtLeast(0)
            val limit = env.getArgument<Int?>("limit")?.coerceAtLeast(0)
            src.rows.asSequence()
              .filter { modelFilter == null || it.model == modelFilter }
              .drop(offset)
              .let { if (limit != null) it.take(limit) else it }
              .map { rowMap(it) }
              .toList()
          })
      }
      .build()
    val schema = SchemaGenerator().makeExecutableSchema(registry, wiring)
    return GraphQL.newGraphQL(schema).build()
  }

  // ---- HTTP handling ----

  fun isGraphQLRequest(request: HttpServletRequest): Boolean {
    val path = (request.pathInfo ?: request.servletPath ?: "").trimEnd('/')
    return path.endsWith("/graphql")
  }

  private data class GqlRequest(
    val query: String?,
    val operationName: String?,
    val variables: Map<String, Any?>
  )

  fun handle(request: HttpServletRequest, response: HttpServletResponse) {
    val isGet = request.method.equals("GET", ignoreCase = true)
    if (isGet && request.getParameter("query") == null) {
      response.status = HttpServletResponse.SC_OK
      response.contentType = "text/plain; charset=utf-8"
      response.writer.write(sdl)
      return
    }
    if (!isGet && !request.method.equals("POST", ignoreCase = true)) {
      writeErrors(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Only GET and POST are supported")
      return
    }

    val user = ServiceRouter.authenticate(request)
    if (user == null) {
      writeErrors(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required")
      return
    }

    val gql = try {
      parseRequest(request)
    } catch (e: Exception) {
      log.debug("Invalid GraphQL request body", e)
      writeErrors(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid request: ${e.message}")
      return
    }
    if (gql.query.isNullOrBlank()) {
      writeErrors(response, HttpServletResponse.SC_BAD_REQUEST, "Missing 'query'")
      return
    }

    val input = ExecutionInput.newExecutionInput()
      .query(gql.query)
      .operationName(gql.operationName)
      .variables(gql.variables)
      .graphQLContext(mapOf(USER_KEY to user))
      .build()

    val result = try {
      graphQL.execute(input)
    } catch (e: Exception) {
      log.warn("GraphQL execution failed", e)
      writeErrors(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Execution failed: ${e.message}")
      return
    }
    result.errors.takeIf { it.isNotEmpty() }?.let { errs ->
      log.debug("GraphQL errors: {}", errs.joinToString("; ") { it.message })
    }
    response.status = HttpServletResponse.SC_OK
    response.contentType = "application/json; charset=utf-8"
    response.writer.write(gsonOut.toJson(result.toSpecification()))
  }

  private fun writeErrors(response: HttpServletResponse, status: Int, message: String) {
    response.status = status
    response.contentType = "application/json; charset=utf-8"
    response.writer.write(gsonOut.toJson(mapOf("errors" to listOf(mapOf("message" to message)))))
  }

  private fun parseRequest(request: HttpServletRequest): GqlRequest {
    if (request.method.equals("GET", ignoreCase = true)) {
      return GqlRequest(
        request.getParameter("query"),
        request.getParameter("operationName"),
        parseVariables(request.getParameter("variables"))
      )
    }
    val body = request.reader.readText()
    val contentType = request.contentType?.lowercase() ?: ""
    if (contentType.startsWith("application/graphql")) {
      return GqlRequest(
        body,
        request.getParameter("operationName"),
        parseVariables(request.getParameter("variables"))
      )
    }
    if (body.isBlank()) {
      return GqlRequest(
        request.getParameter("query"),
        request.getParameter("operationName"),
        parseVariables(request.getParameter("variables"))
      )
    }
    val json = gsonIn.fromJson(body, Map::class.java) as? Map<*, *>
      ?: throw IllegalArgumentException("Body must be a JSON object")
    return GqlRequest(
      json["query"] as? String,
      json["operationName"] as? String,
      toVariables(json["variables"])
    )
  }

  private fun parseVariables(s: String?): Map<String, Any?> =
    if (s.isNullOrBlank()) emptyMap() else toVariables(gsonIn.fromJson(s, Map::class.java))

  private fun toVariables(v: Any?): Map<String, Any?> = when (v) {
    null -> emptyMap()
    is String -> parseVariables(v)
    is Map<*, *> -> v.entries.associate { it.key.toString() to it.value }
    else -> throw IllegalArgumentException("'variables' must be a JSON object")
  }
}