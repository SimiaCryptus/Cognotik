package com.simiacryptus.cognotik.webui.servlet

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import com.simiacryptus.cognotik.platform.ServiceRouter
import com.simiacryptus.cognotik.platform.model.*
import com.simiacryptus.cognotik.platform.service.*
import com.simiacryptus.cognotik.webui.servlet.MetricServlet.Companion.MAX_EVENT_SCAN
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
import java.time.*
import java.time.format.DateTimeParseException
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.abs
import kotlin.math.floor

class MetricServlet(
  private val isAllowed: (User) -> Boolean = { defaultAuthorization(it) },
  private val metricsProvider: () -> MetricsInterface = { ServiceRouter },
) : HttpServlet() {

  public override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) = handle(req, resp)
  public override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) = handle(req, resp)

  private fun metrics(): MetricsInterface = metricsProvider()

  // ------------------------------------------------------------------ filters

  private data class SeriesFilter(
    val names: Set<String>?,
    val prefix: String?,
    val kind: MetricKind?,
    val attributes: Map<String, String>,
  ) {
    fun matchesMetric(m: MetricType): Boolean =
      (names == null || m.name in names) &&
          (prefix == null || m.name.startsWith(prefix)) &&
          (kind == null || kindOf(m) == null || kindOf(m) == kind)

    fun matches(s: SeriesSnapshot): Boolean =
      matchesMetric(s.metric) &&
          (kind == null || s.kind == kind) &&
          matchesAttributes(s.attributes, attributes)
  }

  private data class EventFilter(
    val types: Set<String>?,
    val since: Instant?,
    val until: Instant?,
    val attributes: Map<String, String>,
  ) {
    fun matches(e: RecordedEvent): Boolean =
      (types == null || e.type.name in types) &&
          (since == null || !e.timestamp.isBefore(since)) &&
          (until == null || e.timestamp.isBefore(until)) &&
          matchesAttributes(e.attributes, attributes)
  }

  @Suppress("UNCHECKED_CAST")
  private fun filterArg(env: DataFetchingEnvironment): Map<String, Any?> =
    (env.getArgument<Any?>("filter") as? Map<String, Any?>) ?: emptyMap()

  private fun names(single: Any?, many: Any?): Set<String>? = buildSet {
    (single as? String)?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
    (many as? List<*>)?.forEach { v -> v?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) } }
  }.takeIf { it.isNotEmpty() }

  private fun attributeFilter(v: Any?): Map<String, String> =
    (v as? List<*>)?.mapNotNull { item ->
      val m = item as? Map<*, *> ?: return@mapNotNull null
      val k = m["key"]?.toString() ?: return@mapNotNull null
      val value = m["value"]?.toString() ?: return@mapNotNull null
      k to value
    }?.toMap() ?: emptyMap()

  private fun seriesFilter(env: DataFetchingEnvironment): SeriesFilter {
    val f = filterArg(env)
    return SeriesFilter(
      names = names(f["metric"], f["metrics"]),
      prefix = (f["metricPrefix"] as? String)?.takeIf { it.isNotBlank() },
      kind = f["kind"]?.toString()?.let { MetricKind.valueOf(it) },
      attributes = attributeFilter(f["attributes"]),
    )
  }

  private fun eventFilter(env: DataFetchingEnvironment): EventFilter {
    val f = filterArg(env)
    return EventFilter(
      types = names(f["type"], f["types"]),
      since = parseTime(f["since"] as? String),
      until = parseTime(f["until"] as? String),
      attributes = attributeFilter(f["attributes"]),
    )
  }

  // ------------------------------------------------------------------ loading

  private fun loadSeries(filter: SeriesFilter): List<SeriesSnapshot> {
    val m = metrics()
    if (!m.supportsQueries) return emptyList()
    return m.querySeries(MetricQuery(kind = filter.kind))
      .filter(filter::matches)
      .sortedWith(compareBy({ it.metric.name }, { it.attributes.toString() }))
  }

  /**
   * Loads events matching [filter], newest first. The backend type filter is used when
   * possible; attribute filters and unknown types require scanning up to [MAX_EVENT_SCAN].
   */
  private fun loadEvents(filter: EventFilter, limit: Int): List<RecordedEvent> {
    val m = metrics()
    if (!m.supportsQueries || limit <= 0) return emptyList()
    val singleType = filter.types?.singleOrNull()?.let { knownEventsByName()[it] }
    val needsScan = filter.attributes.isNotEmpty() || (filter.types != null && singleType == null)
    val fetchLimit = if (needsScan) MAX_EVENT_SCAN else limit
    return m.queryEvents(EventQuery(type = singleType, since = filter.since, until = filter.until, limit = fetchLimit))
      .filter(filter::matches)
      .take(limit)
  }

  // ------------------------------------------------------------------ mapping

  private fun attrList(a: Attributes): List<Map<String, String>> =
    a.asStringMap().map { (k, v) -> mapOf("key" to k, "value" to v) }

  private fun distMap(d: DistributionStats): Map<String, Any?> = mapOf(
    "count" to d.count.toDouble(),
    "sum" to d.sum.finiteOrNull(),
    "min" to d.min.finiteOrNull(),
    "max" to d.max.finiteOrNull(),
    "mean" to d.mean.finiteOrNull(),
  )

  private fun seriesMap(s: SeriesSnapshot): Map<String, Any?> = mapOf(
    "metric" to s.metric.name,
    "kind" to s.kind.name,
    "unit" to s.metric.unit.name,
    "description" to s.metric.description,
    "attributes" to attrList(s.attributes),
    "value" to s.value.finiteOrNull(),
    "distribution" to s.distribution?.let { distMap(it) },
  )

  private fun eventMap(e: RecordedEvent): Map<String, Any?> = mapOf(
    "type" to e.type.name,
    "description" to e.type.description,
    "timestamp" to e.timestamp.toString(),
    "timestampMs" to e.timestamp.toEpochMilli().toDouble(),
    "attributes" to attrList(e.attributes),
  )

  // ------------------------------------------------------------------ resolvers

  private fun listMetricInfo(env: DataFetchingEnvironment): List<Map<String, Any?>> {
    val filter = seriesFilter(env)
    val activeOnly = env.getArgument<Boolean?>("activeOnly") ?: false
    val series = loadSeries(filter)
    val counts = series.groupingBy { it.metric.name }.eachCount()
    val all = LinkedHashMap<String, MetricType>()
    knownMetrics().forEach { all.putIfAbsent(it.name, it) }
    series.forEach { all.putIfAbsent(it.metric.name, it.metric) }
    // Kind of metrics not statically typed is inferred from their series.
    val seriesKinds = series.associate { it.metric.name to it.kind }
    return all.values
      .filter(filter::matchesMetric)
      .filter { !activeOnly || (counts[it.name] ?: 0) > 0 }
      .sortedBy { it.name }
      .map { m ->
        mapOf(
          "name" to m.name,
          "kind" to (kindOf(m) ?: seriesKinds[m.name])?.name,
          "unit" to m.unit.name,
          "description" to m.description,
          "attributes" to m.attributes.sortedBy { it.name }.map {
            mapOf("name" to it.name, "highCardinality" to it.highCardinality)
          },
          "seriesCount" to (counts[m.name] ?: 0),
        )
      }
  }

  private class Agg(val metric: MetricType, val kind: MetricKind) {
    var value = 0.0
    var seriesCount = 0
    var dCount = 0L
    var dSum = 0.0
    var dMin = Double.NaN
    var dMax = Double.NaN
    var hasDist = false

    fun add(s: SeriesSnapshot) {
      seriesCount++
      if (!s.value.isNaN()) value += s.value
      s.distribution?.let { d ->
        hasDist = true
        dCount += d.count
        dSum += d.sum
        if (!d.min.isNaN()) dMin = if (dMin.isNaN()) d.min else minOf(dMin, d.min)
        if (!d.max.isNaN()) dMax = if (dMax.isNaN()) d.max else maxOf(dMax, d.max)
      }
    }

    fun stats(): DistributionStats? = if (hasDist) DistributionStats(dCount, dSum, dMin, dMax) else null
  }

  /**
   * Groups matching series per metric by the [groupBy] attribute names, summing values.
   * Gauges are summed too (e.g. nodes across services); distributions are merged.
   */
  private fun aggregate(env: DataFetchingEnvironment): List<Map<String, Any?>> {
    val filter = seriesFilter(env)
    val groupBy = env.getArgument<List<String>?>("groupBy").orEmpty().distinct()
    val groups = LinkedHashMap<Pair<String, List<Pair<String, String>>>, Agg>()
    loadSeries(filter).forEach { s ->
      val attrs = s.attributes.asStringMap()
      val key = groupBy.map { it to (attrs[it] ?: NONE_VALUE) }
      groups.getOrPut(s.metric.name to key) { Agg(s.metric, s.kind) }.add(s)
    }
    return groups.entries
      .sortedWith(compareBy({ it.key.first }, { it.key.second.toString() }))
      .map { (k, a) ->
        mapOf(
          "metric" to k.first,
          "kind" to a.kind.name,
          "unit" to a.metric.unit.name,
          "key" to k.second.map { (n, v) -> mapOf("key" to n, "value" to v) },
          "value" to a.value.finiteOrNull(),
          "seriesCount" to a.seriesCount,
          "distribution" to a.stats()?.let { distMap(it) },
        )
      }
  }

  private fun eventTimeline(env: DataFetchingEnvironment): List<Map<String, Any?>> {
    val raw = eventFilter(env)
    val until = raw.until ?: Instant.now()
    val since = raw.since ?: until.minus(Duration.ofHours(24))
    require(since.isBefore(until)) { "'since' must be before 'until'" }
    val filter = raw.copy(since = since, until = until)
    val step = (env.getArgument<Int?>("bucketSeconds") ?: 3600).toLong().coerceAtLeast(1L) * 1000L
    val span = until.toEpochMilli() - since.toEpochMilli()
    require(span / step <= MAX_BUCKETS) { "bucketSeconds yields too many buckets for this range" }
    val groupBy = env.getArgument<String?>("groupBy")?.trim()?.takeIf { it.isNotEmpty() } ?: "type"

    val counts = HashMap<Pair<Long, String>, Int>()
    loadEvents(filter, MAX_EVENT_SCAN).forEach { e ->
      val t = e.timestamp.toEpochMilli()
      val start = Math.floorDiv(t, step) * step
      val key = when (groupBy) {
        "type" -> e.type.name
        "none" -> "all"
        else -> e.attributes.asStringMap()[groupBy] ?: NONE_VALUE
      }
      counts.merge(start to key, 1, Int::plus)
    }
    return counts.entries
      .sortedWith(compareBy({ it.key.first }, { it.key.second }))
      .map { (k, c) ->
        mapOf(
          "start" to Instant.ofEpochMilli(k.first).toString(),
          "startMs" to k.first.toDouble(),
          "key" to k.second,
          "count" to c,
        )
      }
  }

  // ------------------------------------------------------------------ schema

  private val graphQL: GraphQL by lazy { buildGraphQL() }

  private fun buildGraphQL(): GraphQL {
    val registry = SchemaParser().parse(SDL)
    val wiring = RuntimeWiring.newRuntimeWiring()
      .type("Query") { b ->
        b.dataFetcher("supportsQueries", DataFetcher { _ -> metrics().supportsQueries })
          .dataFetcher("metrics", DataFetcher { env -> listMetricInfo(env) })
          .dataFetcher("series", DataFetcher { env ->
            val limit = (env.getArgument<Int?>("limit") ?: 1000).coerceIn(1, MAX_SERIES)
            loadSeries(seriesFilter(env)).take(limit).map { seriesMap(it) }
          })
          .dataFetcher("aggregate", DataFetcher { env -> aggregate(env) })
          .dataFetcher("events", DataFetcher { env ->
            val limit = (env.getArgument<Int?>("limit") ?: 100).coerceIn(1, MAX_EVENT_SCAN)
            loadEvents(eventFilter(env), limit).map { eventMap(it) }
          })
          .dataFetcher("eventTimeline", DataFetcher { env -> eventTimeline(env) })
          .dataFetcher("eventTypes", DataFetcher { _ ->
            knownEvents().sortedBy { it.name }.map { t ->
              mapOf(
                "name" to t.name,
                "description" to t.description,
                "counter" to t.counter?.name,
                "attributes" to t.attributes.map { it.name }.sorted(),
              )
            }
          })
      }
      .build()
    val schema = SchemaGenerator().makeExecutableSchema(registry, wiring)
    return GraphQL.newGraphQL(schema).build()
  }

  // ------------------------------------------------------------------ HTTP

  private data class GqlRequest(val query: String?, val operationName: String?, val variables: Map<String, Any?>)

  fun handle(request: HttpServletRequest, response: HttpServletResponse) {
    val isGet = request.method.equals("GET", ignoreCase = true)
    if (!isGet && !request.method.equals("POST", ignoreCase = true)) {
      writeErrors(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Only GET and POST are supported")
      return
    }
    val user = ServiceRouter.authenticate(request)
    if (user == null) {
      writeErrors(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required")
      return
    }
    val allowed = try {
      isAllowed(user)
    } catch (e: Exception) {
      log.warn("Metrics authorization check failed for {}", user.email, e)
      false
    }
    if (!allowed) {
      writeErrors(response, HttpServletResponse.SC_FORBIDDEN, "Access denied")
      return
    }
    if (isGet && request.getParameter("query") == null) {
      response.status = HttpServletResponse.SC_OK
      response.contentType = "text/plain; charset=utf-8"
      response.writer.write(SDL)
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
      .graphQLContext(mapOf("user" to user))
      .build()
    val result = try {
      graphQL.execute(input)
    } catch (e: Exception) {
      log.warn("Metrics GraphQL execution failed", e)
      writeErrors(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Execution failed: ${e.message}")
      return
    }
    result.errors.takeIf { it.isNotEmpty() }?.let { errs ->
      log.debug("Metrics GraphQL errors: {}", errs.joinToString("; ") { it.message })
    }
    response.status = HttpServletResponse.SC_OK
    response.contentType = "application/json; charset=utf-8"
    response.setHeader("Cache-Control", "no-store")
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
      return GqlRequest(body, request.getParameter("operationName"), parseVariables(request.getParameter("variables")))
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
    return GqlRequest(json["query"] as? String, json["operationName"] as? String, toVariables(json["variables"]))
  }

  private fun parseVariables(s: String?): Map<String, Any?> =
    if (s.isNullOrBlank()) emptyMap() else toVariables(gsonIn.fromJson(s, Map::class.java))

  private fun toVariables(v: Any?): Map<String, Any?> = when (v) {
    null -> emptyMap()
    is String -> parseVariables(v)
    is Map<*, *> -> v.entries.associate { it.key.toString() to it.value }
    else -> throw IllegalArgumentException("'variables' must be a JSON object")
  }

  companion object {
    private val log = LoggerFactory.getLogger(MetricServlet::class.java)

    private const val MAX_EVENT_SCAN = 10_000
    private const val MAX_SERIES = 10_000
    private const val MAX_BUCKETS = 10_000L
    private const val NONE_VALUE = "(none)"

    private val gsonIn: Gson = Gson()

    /** Emits whole-valued doubles as integers in the JSON response. */
    private val gsonOut: Gson = GsonBuilder()
      .serializeNulls()
      .setPrettyPrinting()
      .registerTypeAdapter(Double::class.javaObjectType, JsonSerializer<Double> { src, _, _ ->
        if (!src.isNaN() && !src.isInfinite() && src == floor(src) && abs(src) < 1e15) JsonPrimitive(src.toLong())
        else JsonPrimitive(src)
      })
      .create()

    /** Default check; replace with an admin-only predicate where appropriate. */
    fun defaultAuthorization(user: User): Boolean =
      ServiceRouter.isAuthorized(null, Principal.of(user = user), OperationType.Read)

    private val BUILTIN_METRICS: List<MetricType> = listOf(
      MetricType.TOKENS_USED, MetricType.TOKEN_SPEND,
      MetricType.INPUT_CASH,
      MetricType.CREDITS_BANKED, MetricType.CREDITS_GRANTED,
      MetricType.APP_SESSIONS, MetricType.APP_ACTIVE_SESSIONS, MetricType.APP_SESSION_DURATION,
      MetricType.FILE_TRANSFERS, MetricType.FILE_TRANSFER_BYTES, MetricType.FILE_TRANSFER_DURATION,
      MetricType.FARGATE_NODES, MetricType.FARGATE_NODE_LIFECYCLE,
      MetricType.ECS_SERVICE_RUNNING_TASKS, MetricType.ECS_SERVICE_DESIRED_TASKS,
      MetricType.ECS_SERVICE_STATUS, MetricType.ECS_SERVICE_STATUS_CHANGES,
    )

    private val BUILTIN_EVENTS: List<EventType> = listOf(
      EventType.APP_STARTED, EventType.APP_COMPLETED,
      EventType.FILE_TRANSFERRED,
      EventType.PAYMENT_RECEIVED, EventType.CREDITS_GRANTED,
      EventType.FARGATE_NODE_STARTED, EventType.FARGATE_NODE_STOPPED,
      EventType.ECS_SERVICE_STATUS_CHANGED,
    )

    private val extraMetrics = CopyOnWriteArraySet<MetricType>()
    private val extraEvents = CopyOnWriteArraySet<EventType>()

    /** Adds module-defined metric types to the dashboard catalogue. */
    fun register(vararg metrics: MetricType) {
      extraMetrics.addAll(metrics)
    }

    /** Adds module-defined event types to the dashboard catalogue. */
    fun register(vararg events: EventType) {
      extraEvents.addAll(events)
    }

    private fun knownMetrics(): List<MetricType> = BUILTIN_METRICS + extraMetrics
    private fun knownEvents(): List<EventType> = (BUILTIN_EVENTS + extraEvents).distinctBy { it.name }
    private fun knownEventsByName(): Map<String, EventType> = knownEvents().associateBy { it.name }

    private fun kindOf(m: MetricType): MetricKind? = when (m) {
      is CounterType -> MetricKind.COUNTER
      is GaugeType -> MetricKind.GAUGE
      is DistributionType -> MetricKind.DISTRIBUTION
      else -> null
    }

    private fun matchesAttributes(attrs: Attributes, required: Map<String, String>): Boolean {
      if (required.isEmpty()) return true
      val actual = attrs.asStringMap()
      return required.all { (k, v) -> actual[k] == v }
    }

    private fun Double.finiteOrNull(): Double? = takeIf { it.isFinite() }

    private fun parseTime(s: String?): Instant? {
      val t = s?.trim()?.takeIf { it.isNotEmpty() } ?: return null
      if (t.length == 10) return LocalDate.parse(t).atStartOfDay(ZoneOffset.UTC).toInstant()
      return try {
        Instant.parse(t)
      } catch (e: DateTimeParseException) {
        LocalDateTime.parse(t).toInstant(ZoneOffset.UTC)
      }
    }

    val SDL: String = """
      schema { query: Query }

      enum MetricKind { COUNTER GAUGE DISTRIBUTION }

      type Attribute {
        key: String!
        value: String!
      }

      input AttributeFilter {
        key: String!
        value: String!
      }

      type MetricAttributeInfo {
        name: String!
        highCardinality: Boolean!
      }

      type MetricInfo {
        name: String!
        kind: MetricKind
        unit: String!
        description: String!
        attributes: [MetricAttributeInfo!]!
        # Number of current series matching the filter
        seriesCount: Int!
      }

      type DistributionStats {
        count: Float!
        sum: Float
        min: Float
        max: Float
        mean: Float
      }

      type Series {
        metric: String!
        kind: MetricKind!
        unit: String!
        description: String!
        attributes: [Attribute!]!
        # Counter total, gauge value, or sum of distribution observations
        value: Float
        distribution: DistributionStats
      }

      type SeriesGroup {
        metric: String!
        kind: MetricKind!
        unit: String!
        # Values of the groupBy attributes ("(none)" when absent)
        key: [Attribute!]!
        # Sum of member series values
        value: Float
        seriesCount: Int!
        # Merged distribution stats (distributions only)
        distribution: DistributionStats
      }

      input SeriesFilter {
        metric: String
        metrics: [String!]
        metricPrefix: String
        kind: MetricKind
        # Series must carry all of these attribute values
        attributes: [AttributeFilter!]
      }

      type Event {
        type: String!
        description: String!
        timestamp: String!
        timestampMs: Float!
        attributes: [Attribute!]!
      }

      type EventTypeInfo {
        name: String!
        description: String!
        counter: String
        attributes: [String!]!
      }

      input EventFilter {
        type: String
        types: [String!]
        # ISO instant or yyyy-MM-dd (UTC); since inclusive / until exclusive
        since: String
        until: String
        attributes: [AttributeFilter!]
      }

      type EventBucket {
        start: String!
        startMs: Float!
        key: String!
        count: Int!
      }

      type Query {
        # False when the metrics backend is write-only; all lists are then empty
        supportsQueries: Boolean!
        # Metric catalogue (known metrics plus any with live series)
        metrics(filter: SeriesFilter, activeOnly: Boolean): [MetricInfo!]!
        # Current series values
        series(filter: SeriesFilter, limit: Int): [Series!]!
        # Series summed per metric, grouped by attribute names
        aggregate(filter: SeriesFilter, groupBy: [String!]): [SeriesGroup!]!
        # Retained events, newest first
        events(filter: EventFilter, limit: Int): [Event!]!
        # Event counts per time bucket (defaults to the last 24 hours).
        # groupBy: "type" (default), "none", or an attribute name
        eventTimeline(filter: EventFilter, bucketSeconds: Int, groupBy: String): [EventBucket!]!
        # Event catalogue
        eventTypes: [EventTypeInfo!]!
      }
    """.trimIndent()
  }
}