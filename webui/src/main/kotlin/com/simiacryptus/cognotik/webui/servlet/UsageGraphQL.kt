package com.simiacryptus.cognotik.webui.servlet

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import com.simiacryptus.cognotik.platform.ServiceRouter
import com.simiacryptus.cognotik.platform.model.ModelSchema
import com.simiacryptus.cognotik.platform.model.ModelSchema.TokenTypes
import com.simiacryptus.cognotik.platform.model.Page
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.SessionListEntry
import com.simiacryptus.cognotik.platform.model.SessionQuery
import com.simiacryptus.cognotik.platform.model.SessionSort
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.model.paginate
import com.simiacryptus.cognotik.platform.service.SessionMetadataInterface
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
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
 * GraphQL API for usage data, sessions and session metadata (see [SessionGraphQL]).
 *
 * - POST with JSON body `{"query": "...", "variables": {...}, "operationName": "..."}`
 * - POST with `Content-Type: application/graphql` and the raw query as body
 * - GET with `?query=...&variables=...&operationName=...`
 * - GET with no query returns the schema SDL as text/plain
 *
 * Mutations are only executed for POST requests.
 *
 * Session browsing:
 * - `usageSessions` — filterable, sortable, paged session listing joined with direct usage
 * - `usageSessionTree` — transitive child-session tree with direct and subtree usage per node
 *
 * Token counts are exposed as `Float` since GraphQL `Int` is 32-bit.
 */
object UsageGraphQL {
  private val log = LoggerFactory.getLogger(UsageGraphQL::class.java)
  internal const val USER_KEY = "user"
  internal const val MUTATIONS_ALLOWED_KEY = "mutationsAllowed"

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
    schema { query: Query mutation: Mutation }

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

    input UsageSessionFilter {
      # Case-insensitive substring of the session name (or id when unnamed)
      nameContains: String
      # Exact application path
      path: String
      ownerId: String
      # Bounds on the session timestamp, ISO yyyy-MM-dd (UTC); from inclusive / to exclusive
      from: String
      to: String
      # Restrict to these session ids
      sessionIds: [String!]
      # Only sessions without a registered parent
      rootsOnly: Boolean
      # Minimum direct (non-recursive) cost
      minCost: Float
    }

    enum UsageSessionSort { TIME_DESC TIME_ASC NAME_ASC NAME_DESC COST_DESC COST_ASC TOKENS_DESC TOKENS_ASC }

    type UsageSessionEntry {
      sessionId: String!
      name: String
      path: String
      ownerId: String
      workerId: String
      time: String
      parentSessionId: String
      # Direct (non-recursive) usage
      models: [ModelUsage!]!
      totals: UsageTotals!
    }

    type UsageSessionPage {
      items: [UsageSessionEntry!]!
      total: Int!
      nextCursor: String
      # All distinct application paths the user has sessions under
      paths: [String!]!
    }

    type UsageSessionNode {
      sessionId: String!
      parentSessionId: String
      depth: Int!
      childCount: Int!
      descendantCount: Int!
      name: String
      path: String
      time: String
      # Calls made directly by this session
      calls: Int!
      firstCall: String
      lastCall: String
      # Usage made directly by this session
      direct: UsageTotals!
      # Usage of this session plus all its descendants
      subtree: UsageTotals!
      # Direct usage by model
      models: [ModelUsage!]!
    }

    type UsageSessionTree {
      rootSessionId: String!
      # False when the backend cannot enumerate child sessions (tree rebuilt from usage rows)
      discoverySupported: Boolean!
      truncated: Boolean!
      totals: UsageTotals!
      # Depth-first, pre-order (root first)
      nodes: [UsageSessionNode!]!
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
      # Filterable, sortable, paged listing of the user's sessions with direct usage
      usageSessions(filter: UsageSessionFilter, sort: UsageSessionSort, limit: Int, cursor: String): UsageSessionPage!
      # Transitive child-session tree with per-session and subtree usage
      usageSessionTree(sessionId: String!, maxDepth: Int): UsageSessionTree!
    }
  """.trimIndent() + "\n\n" + SessionGraphQL.sdl

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

  private fun metadataManager(): SessionMetadataInterface =
    (ServiceRouter as? SessionMetadataInterface)
      ?: throw IllegalStateException("Session metadata service is not available")

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

  private fun costOf(summary: Map<String, ModelSchema.Usage>?): Double =
    summary?.values?.sumOf { it.cost } ?: 0.0

  private fun tokensOf(summary: Map<String, ModelSchema.Usage>?): Long =
    summary?.values?.sumOf { UsageTokens.totalTokens(it) } ?: 0L

  private fun parseDayInstant(s: String?): Instant? =
    s?.trim()?.takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant() }

  /** Mutable accumulator used to aggregate usage rows per session / subtree. */
  private class UsageAcc {
    val counts = HashMap<TokenTypes, Long>()
    var cost = 0.0
    var total = 0L
    var calls = 0
    var first: Instant? = null
    var last: Instant? = null
    val models = HashMap<String, UsageAcc>()

    fun addRow(r: UsageInterface.UsageRow) {
      val t = UsageTokens.totalTokens(r.tokenCounts)
      add(r.tokenCounts, r.cost, t, 1, r.datetime, r.datetime)
      models.getOrPut(r.model ?: "(unknown)") { UsageAcc() }
        .add(r.tokenCounts, r.cost, t, 1, r.datetime, r.datetime)
    }

    fun merge(o: UsageAcc) {
      add(o.counts, o.cost, o.total, o.calls, o.first, o.last)
      o.models.forEach { (m, a) ->
        models.getOrPut(m) { UsageAcc() }.add(a.counts, a.cost, a.total, a.calls, a.first, a.last)
      }
    }

    private fun add(c: Map<TokenTypes, Long>, cost: Double, total: Long, calls: Int, first: Instant?, last: Instant?) {
      c.forEach { (k, v) -> counts[k] = (counts[k] ?: 0L) + v }
      this.cost += cost
      this.total += total
      this.calls += calls
      val f = this.first
      if (first != null && (f == null || first.isBefore(f))) this.first = first
      val l = this.last
      if (last != null && (l == null || last.isAfter(l))) this.last = last
    }
  }

  private fun accFields(a: UsageAcc): Map<String, Any?> = usageFields(a.counts, a.cost, a.total)

  private fun accModels(a: UsageAcc): List<Map<String, Any?>> =
    a.models.entries.sortedByDescending { it.value.cost }.map { (m, x) ->
      usageFields(x.counts, x.cost, x.total).apply { put("model", m) }
    }

  // ---- Session listing ----

  private val USAGE_SORTS = setOf("COST_DESC", "COST_ASC", "TOKENS_DESC", "TOKENS_ASC")

  @Suppress("UNCHECKED_CAST")
  private fun listUsageSessions(env: DataFetchingEnvironment): Map<String, Any?> {
    val user = env.user()
    val meta = metadataManager()
    val usage = usageManager()
    val filter = (env.getArgument<Any?>("filter") as? Map<String, Any?>) ?: emptyMap()
    val sortName = env.getArgument<Any?>("sort")?.toString() ?: "TIME_DESC"
    val limit = (env.getArgument<Int?>("limit") ?: 50).coerceIn(1, 1000)
    val cursor = env.getArgument<String?>("cursor")?.takeIf { it.isNotBlank() }
    val rootsOnly = filter["rootsOnly"] == true
    val minCost = (filter["minCost"] as? Number)?.toDouble()

    val query = SessionQuery(
      path = (filter["path"] as? String)?.takeIf { it.isNotBlank() },
      nameContains = (filter["nameContains"] as? String)?.takeIf { it.isNotBlank() },
      ownerId = (filter["ownerId"] as? String)?.takeIf { it.isNotBlank() },
      from = parseDayInstant(filter["from"] as? String),
      to = parseDayInstant(filter["to"] as? String),
      sessionIds = (filter["sessionIds"] as? List<*>)?.mapNotNull { it?.toString() }?.toSet(),
      sort = when (sortName) {
        "TIME_ASC" -> SessionSort.TIME_ASC
        "NAME_ASC" -> SessionSort.NAME_ASC
        "NAME_DESC" -> SessionSort.NAME_DESC
        else -> SessionSort.TIME_DESC
      }
    )

    val sel = env.selectionSet
    val wantUsage = sel.contains("items/totals") || sel.contains("items/models")
    val wantParent = sel.contains("items/parentSessionId")
    val usageSort = sortName in USAGE_SORTS

    var summaries: Map<Session, Map<String, ModelSchema.Usage>>? = null
    var parents: Map<Session, Session?>? = null
    val items: List<SessionListEntry>
    val total: Int
    val nextCursor: String?

    if (!usageSort && !rootsOnly && minCost == null) {
      // Fully pushed down to the metadata backend.
      val page = meta.querySessions(user, query, Page(limit, cursor))
      items = page.items
      nextCursor = page.nextCursor
      total = meta.countSessions(user, query)
    } else {
      // Filters/sorts that depend on usage or parent links are evaluated over all matches.
      var all = meta.querySessions(user, query)
      if (rootsOnly) {
        val p = usage.getParentSessions(user, all.map { it.id })
        parents = p
        all = all.filter { p[it.id] == null }
      }
      if (usageSort || minCost != null) {
        val s = usage.getSessionUsageSummaryBulk(user, all.map { it.id })
        summaries = s
        if (minCost != null) all = all.filter { costOf(s[it.id]) >= minCost }
        all = when (sortName) {
          "COST_DESC" -> all.sortedByDescending { costOf(s[it.id]) }
          "COST_ASC" -> all.sortedBy { costOf(s[it.id]) }
          "TOKENS_DESC" -> all.sortedByDescending { tokensOf(s[it.id]) }
          "TOKENS_ASC" -> all.sortedBy { tokensOf(s[it.id]) }
          else -> all
        }
      }
      val page = all.paginate(Page(limit, cursor))
      items = page.items
      nextCursor = page.nextCursor
      total = all.size
    }

    val ids = items.map { it.id }
    val sums = summaries ?: if (wantUsage && ids.isNotEmpty()) usage.getSessionUsageSummaryBulk(user, ids) else emptyMap()
    val par = parents ?: if (wantParent && ids.isNotEmpty()) usage.getParentSessions(user, ids) else emptyMap()

    return mapOf(
      "total" to total,
      "nextCursor" to nextCursor,
      "paths" to (if (sel.contains("paths")) meta.listSessionPaths(user) else emptyList()),
      "items" to items.map { e ->
        val summary = sums[e.id].orEmpty()
        mapOf(
          "sessionId" to e.id.sessionId,
          "name" to e.name,
          "path" to e.path,
          "ownerId" to e.ownerId,
          "workerId" to e.workerId,
          "time" to e.sessionInstant?.toString(),
          "parentSessionId" to par[e.id]?.sessionId,
          "models" to modelList(summary),
          "totals" to totals(summary)
        )
      }
    )
  }

  // ---- Session tree ----

  private fun buildSessionTree(env: DataFetchingEnvironment): Map<String, Any?> {
    val user = env.user()
    val id = env.getArgument<String>("sessionId") ?: throw IllegalArgumentException("sessionId is required")
    val root = Session(id)
    val maxDepth = (env.getArgument<Int?>("maxDepth") ?: UsageInterface.DEFAULT_MAX_TREE_DEPTH)
      .coerceIn(1, UsageInterface.DEFAULT_MAX_TREE_DEPTH)
    val usage = usageManager()

    // 1. Discover descendants through the parent/child registry.
    val parentOf = LinkedHashMap<Session, Session?>()
    parentOf[root] = null
    var discoverySupported = true
    var truncated = false
    try {
      val tree = usage.listDescendantSessions(user, root, maxDepth)
      tree.descendants.forEach { parentOf.putIfAbsent(it.session, it.parent) }
      truncated = tree.truncated
    } catch (e: UnsupportedOperationException) {
      log.debug("Child-session discovery unsupported; rebuilding tree from usage rows", e)
      discoverySupported = false
    }

    // 2. Attribute usage rows (which include descendants) to the session that produced them.
    val direct = HashMap<Session, UsageAcc>()
    usage.getSessionUsageRows(root, user).forEach { r ->
      val s = r.sessionId?.let { Session.tryParse(it) } ?: root
      direct.getOrPut(s) { UsageAcc() }.addRow(r)
    }

    // 3. Fold in sessions that produced usage but were not discovered.
    val extra = direct.keys.filter { it !in parentOf }
    if (extra.isNotEmpty()) {
      val parents = try {
        usage.getParentSessions(user, extra)
      } catch (e: Exception) {
        log.debug("Parent lookup failed", e)
        emptyMap()
      }
      extra.forEach { parentOf[it] = parents[it] }
    }

    // 4. Repair dangling / cyclic links so every node hangs off the root.
    fun reachesRoot(s: Session): Boolean {
      var cur: Session? = s
      val seen = HashSet<Session>()
      while (cur != null) {
        if (cur == root) return true
        if (!seen.add(cur)) return false
        cur = parentOf[cur]
      }
      return false
    }
    parentOf.keys.toList().forEach { s -> if (s != root && !reachesRoot(s)) parentOf[s] = root }

    val children = HashMap<Session, MutableList<Session>>()
    parentOf.forEach { (s, p) -> if (p != null) children.getOrPut(p) { mutableListOf() }.add(s) }

    // 5. Subtree aggregation (post-order).
    val subtree = HashMap<Session, UsageAcc>()
    val descCount = HashMap<Session, Int>()
    fun accumulate(s: Session): UsageAcc {
      subtree[s]?.let { return it }
      val acc = UsageAcc()
      direct[s]?.let { acc.merge(it) }
      var count = 0
      for (c in children[s].orEmpty()) {
        acc.merge(accumulate(c))
        count += 1 + (descCount[c] ?: 0)
      }
      subtree[s] = acc
      descCount[s] = count
      return acc
    }
    accumulate(root)

    // 6. Optional metadata (names, paths, timestamps).
    val sel = env.selectionSet
    val needMeta = sel.contains("nodes/name") || sel.contains("nodes/path") || sel.contains("nodes/time")
    val meta: Map<String, SessionListEntry>? = if (!needMeta) null else try {
      (ServiceRouter as? SessionMetadataInterface)?.getSessionEntries(user, parentOf.keys.map { it.sessionId })
    } catch (e: Exception) {
      log.debug("Session metadata lookup failed", e)
      null
    }

    // 7. Emit depth-first, children ordered by subtree cost.
    val nodes = mutableListOf<Map<String, Any?>>()
    val visited = HashSet<Session>()
    fun emit(s: Session, depth: Int) {
      if (!visited.add(s)) return
      val d = direct[s] ?: UsageAcc()
      val entry = meta?.get(s.sessionId)
      nodes += mapOf(
        "sessionId" to s.sessionId,
        "parentSessionId" to parentOf[s]?.sessionId,
        "depth" to depth,
        "childCount" to (children[s]?.size ?: 0),
        "descendantCount" to (descCount[s] ?: 0),
        "name" to entry?.name,
        "path" to entry?.path,
        "time" to entry?.sessionInstant?.toString(),
        "calls" to d.calls,
        "firstCall" to d.first?.toString(),
        "lastCall" to d.last?.toString(),
        "direct" to accFields(d),
        "subtree" to accFields(subtree[s] ?: d),
        "models" to accModels(d)
      )
      children[s].orEmpty()
        .sortedWith(compareByDescending<Session> { subtree[it]?.cost ?: 0.0 }.thenBy { it.sessionId })
        .forEach { emit(it, depth + 1) }
    }
    emit(root, 0)

    return mapOf(
      "rootSessionId" to root.sessionId,
      "discoverySupported" to discoverySupported,
      "truncated" to truncated,
      "totals" to accFields(subtree.getValue(root)),
      "nodes" to nodes
    )
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
          .dataFetcher("usageSessions", DataFetcher { env -> listUsageSessions(env) })
          .dataFetcher("usageSessionTree", DataFetcher { env -> buildSessionTree(env) })
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
      .also { SessionGraphQL.wire(it) }
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
      .graphQLContext(mapOf(USER_KEY to user, MUTATIONS_ALLOWED_KEY to !isGet))
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