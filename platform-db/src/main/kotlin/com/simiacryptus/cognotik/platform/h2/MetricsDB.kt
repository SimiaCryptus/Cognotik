package com.simiacryptus.cognotik.platform.h2

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.simiacryptus.cognotik.platform.model.Alert
import com.simiacryptus.cognotik.platform.model.AlertPolicy
import com.simiacryptus.cognotik.platform.model.AlertSeverity
import com.simiacryptus.cognotik.platform.model.AlertState
import com.simiacryptus.cognotik.platform.model.AlertStatistic
import com.simiacryptus.cognotik.platform.model.Attributes
import com.simiacryptus.cognotik.platform.model.AttributeValue
import com.simiacryptus.cognotik.platform.model.Comparison
import com.simiacryptus.cognotik.platform.model.CounterType
import com.simiacryptus.cognotik.platform.model.DistributionType
import com.simiacryptus.cognotik.platform.model.EventType
import com.simiacryptus.cognotik.platform.model.GaugeType
import com.simiacryptus.cognotik.platform.model.MetricAttribute
import com.simiacryptus.cognotik.platform.model.MetricType
import com.simiacryptus.cognotik.platform.model.MetricUnit
import com.simiacryptus.cognotik.platform.service.AlertQuery
import com.simiacryptus.cognotik.platform.service.DistributionStats
import com.simiacryptus.cognotik.platform.service.EventQuery
import com.simiacryptus.cognotik.platform.service.MetricKind
import com.simiacryptus.cognotik.platform.service.MetricQuery
import com.simiacryptus.cognotik.platform.service.MetricsInterface
import com.simiacryptus.cognotik.platform.service.NotificationsInterface
import com.simiacryptus.cognotik.platform.service.RecordedEvent
import com.simiacryptus.cognotik.platform.service.SeriesSnapshot
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Database-backed [MetricsInterface] using Exposed on top of [DatabaseFacet].
 *
 * Write path: recording only touches in-memory buffers (never the database) and never
 * throws. Series updates are pre-aggregated per (metric, sanitized attributes) and
 * exported by a background flusher every [flushIntervalMillis], or on [flush].
 * Exports apply *deltas* (counter += x, count += n, min/max conditional updates), so
 * several nodes may share one database safely. Gauges are last-write-wins.
 *
 * Read path: supported. Queries flush first so callers observe their own writes.
 * Metric/event names are resolved back to types via a registry seeded with the
 * built-in vocabulary and every type seen on write; unknown names (written by other
 * nodes or previous runs) are synthesized with string-typed attributes.
 *
 * Alerting: supported. Policies and alert state are persisted, so they survive restarts
 * and are shared by all nodes using the same database. Policies are evaluated against the
 * persisted (aggregated) series after every background flush, on [evaluateAlerts] and when
 * a policy is put. Transitions are claimed transactionally (insert/delete of the active
 * alert row), so each FIRING/RESOLVED transition is notified by exactly one node.
 *
 * @param flushIntervalMillis background export period; <= 0 disables the background flusher
 *                            (and with it periodic alert evaluation)
 * @param eventRetention events and resolved alerts older than this are purged (null keeps them forever)
 * @param maxBufferedEvents upper bound on events buffered between flushes (oldest dropped)
 * @param notifications resolves the notification sink at delivery time
 */
class MetricsDB(
  private val flushIntervalMillis: Long =
    System.getProperty("cognotik.metrics.flushIntervalMillis", "10000").toLongOrNull() ?: 10_000L,
  private val eventRetention: Duration? = Duration.ofDays(30),
  private val maxBufferedEvents: Int = 10_000,
  private val notifications: () -> NotificationsInterface = { NotificationsInterface.resolve() },
) : MetricsInterface {

  object SeriesTable : Table("metric_series") {
    val metric = varchar("metric", 255)
    val attrs = varchar("attrs", 1024)
    val kind = varchar("kind", 16)
    val unit = varchar("unit", 64)
    val description = varchar("description", 1024)
    val metricValue = double("metric_value").default(0.0)
    val obsCount = long("obs_count").default(0L)
    val obsMin = double("obs_min").nullable()
    val obsMax = double("obs_max").nullable()
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(metric, attrs)
  }

  object EventsTable : Table("metric_events") {
    val id = long("id").autoIncrement()
    val eventType = varchar("event_type", 255)
    val attrs = text("attrs")
    val ts = timestamp("ts")
    override val primaryKey = PrimaryKey(id)

    init {
      index("idx_metric_events_ts", false, ts)
      index("idx_metric_events_type_ts", false, eventType, ts)
    }
  }

  /** Alert policies; [spec] is the JSON-encoded policy. */
  object AlertPoliciesTable : Table("metric_alert_policies") {
    val id = varchar("id", 255)
    val metric = varchar("metric", 255)
    val spec = text("spec")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
  }

  /** Currently firing alerts, one row per (policy, series). */
  object ActiveAlertsTable : Table("metric_alerts_active") {
    val policyId = varchar("policy_id", 255)
    val attrs = varchar("attrs", 1024)
    val alertValue = double("alert_value")
    val triggeredAt = timestamp("triggered_at")
    val lastEvaluatedAt = timestamp("last_evaluated_at")
    override val primaryKey = PrimaryKey(policyId, attrs)
  }

  /** Resolved alerts, with a snapshot of the policy at resolution time. */
  object ResolvedAlertsTable : Table("metric_alerts_resolved") {
    val id = long("id").autoIncrement()
    val policyId = varchar("policy_id", 255)
    val severity = varchar("severity", 16)
    val attrs = varchar("attrs", 1024)
    val policySpec = text("policy_spec")
    val alertValue = double("alert_value")
    val triggeredAt = timestamp("triggered_at")
    val resolvedAt = timestamp("resolved_at")
    override val primaryKey = PrimaryKey(id)

    init {
      index("idx_metric_alerts_resolved_ts", false, resolvedAt)
      index("idx_metric_alerts_resolved_policy", false, policyId, resolvedAt)
    }
  }

  // ---------------- Buffers ----------------

  /** Pending (unexported) aggregate for one series. Only mutated inside ConcurrentHashMap.compute. */
  private class Pending(val metric: MetricType, val kind: MetricKind, val attrsKey: String) {
    var sum = 0.0
    var count = 0L
    var min = Double.NaN
    var max = Double.NaN
    var last = Double.NaN
  }

  private data class PendingEvent(val typeName: String, val attrsJson: String, val timestamp: Instant)

  /** Serialized form of an [AlertPolicy]. Fields are nullable because Gson may leave them unset. */
  private data class PolicySpec(
    val id: String?,
    val metric: String?,
    val kind: String?,
    val unit: String?,
    val metricDescription: String?,
    val comparison: String?,
    val threshold: Double?,
    val filter: Map<String, String>?,
    val statistic: String?,
    val severity: String?,
    val description: String?,
    val channels: List<String>?,
    val enabled: Boolean?,
  )

  private data class ActiveRow(
    val policyId: String,
    val attrsKey: String,
    val value: Double,
    val triggeredAt: Instant,
  )

  private val pending = ConcurrentHashMap<String, Pending>()
  private val pendingEvents = ConcurrentLinkedDeque<PendingEvent>()
  private val pendingEventCount = AtomicInteger()
  private val droppedEvents = AtomicLong()
  private val gaugeSuppliers = ConcurrentHashMap<String, Pair<GaugeType, Pair<Attributes, () -> Double?>>>()
  private val flushLock = Any()
  /** Serializes alert evaluation and policy changes on this node. */
  private val alertLock = Any()
  @Volatile private var lastPurgeNanos = 0L
  @Volatile private var shutDown = false

  // ---------------- Type registry ----------------

  private val metricTypes = ConcurrentHashMap<String, MetricType>()
  private val eventTypes = ConcurrentHashMap<String, EventType>()

  private val database get() = ExposedDatabase.get(facet)

  private val scheduler: ScheduledExecutorService? =
    if (flushIntervalMillis > 0) Executors.newSingleThreadScheduledExecutor { r ->
      Thread(r, "metrics-db-flush").apply { isDaemon = true }
    } else null

  init {
    builtinMetricTypes().forEach { metricTypes.putIfAbsent(it.name, it) }
    builtinEventTypes().forEach { eventTypes.putIfAbsent(it.name, it) }
    try {
      transaction(database) {
        SeriesTable.selectAll().limit(1).toList()
        EventsTable.selectAll().limit(1).toList()
        AlertPoliciesTable.selectAll().limit(1).toList()
        ActiveAlertsTable.selectAll().limit(1).toList()
        ResolvedAlertsTable.selectAll().limit(1).toList()
      }
    } catch (e: Exception) {
      // Non-fatal: metrics must never break startup. Later flushes will retry.
      log.error("Failed to initialize metrics schema", e)
    }
    scheduler?.scheduleWithFixedDelay({
      try {
        flush()
      } catch (t: Throwable) {
        log.warn("Background metrics flush failed", t)
      }
      try {
        evaluateAlerts()
      } catch (t: Throwable) {
        log.warn("Background alert evaluation failed", t)
      }
    }, flushIntervalMillis, flushIntervalMillis, TimeUnit.MILLISECONDS)
  }

  /** Registers metric types so that reads resolve them by name (with typed attributes). */
  fun register(vararg types: MetricType) = types.forEach { metricTypes[it.name] = it }

  /** Registers event types so that reads resolve them by name (with typed attributes). */
  fun registerEvents(vararg types: EventType) = types.forEach { eventTypes[it.name] = it }

  /** Number of events dropped because the buffer overflowed. */
  fun droppedEventCount(): Long = droppedEvents.get()

  // ---------------- Write side ----------------

  private fun seriesKey(metric: MetricType, attributes: Attributes): Pair<String, String> {
    val attrsKey = encodeMap(metric.sanitize(attributes).asStringMap())
    return (metric.name + "\u0000" + attrsKey) to attrsKey
  }

  private inline fun accumulate(
    metric: MetricType,
    kind: MetricKind,
    attributes: Attributes,
    crossinline op: (Pending) -> Unit,
  ) {
    metricTypes.putIfAbsent(metric.name, metric)
    val (key, attrsKey) = seriesKey(metric, attributes)
    pending.compute(key) { _, existing ->
      (existing ?: Pending(metric, kind, attrsKey)).also(op)
    }
  }

  override fun increment(metric: CounterType, amount: Double, attributes: Attributes) {
    try {
      if (amount < 0 || amount.isNaN() || amount.isInfinite()) {
        log.warn("Ignoring invalid counter increment {} for {}", amount, metric)
        return
      }
      accumulate(metric, MetricKind.COUNTER, attributes) { it.sum += amount; it.count++ }
    } catch (e: Exception) {
      log.warn("Failed to record counter {}", metric, e)
    }
  }

  override fun gauge(metric: GaugeType, value: Double, attributes: Attributes) {
    try {
      if (value.isNaN()) return
      accumulate(metric, MetricKind.GAUGE, attributes) { it.last = value }
    } catch (e: Exception) {
      log.warn("Failed to record gauge {}", metric, e)
    }
  }

  override fun record(metric: DistributionType, value: Double, attributes: Attributes) {
    try {
      if (value.isNaN() || value.isInfinite()) return
      accumulate(metric, MetricKind.DISTRIBUTION, attributes) {
        it.count++
        it.sum += value
        it.min = if (it.min.isNaN()) value else minOf(it.min, value)
        it.max = if (it.max.isNaN()) value else maxOf(it.max, value)
      }
    } catch (e: Exception) {
      log.warn("Failed to record distribution {}", metric, e)
    }
  }

  override fun registerGauge(metric: GaugeType, attributes: Attributes, supplier: () -> Double?): AutoCloseable {
    return try {
      metricTypes.putIfAbsent(metric.name, metric)
      val (key, _) = seriesKey(metric, attributes)
      val entry = metric to (attributes to supplier)
      gaugeSuppliers[key] = entry
      AutoCloseable { gaugeSuppliers.remove(key, entry) }
    } catch (e: Exception) {
      log.warn("Failed to register gauge {}", metric, e)
      AutoCloseable {}
    }
  }

  override fun event(type: EventType, attributes: Attributes, timestamp: Instant) {
    try {
      eventTypes.putIfAbsent(type.name, type)
      val json = encodeMap(attributes.restrictTo(type.attributes).asStringMap())
      pendingEvents.addLast(PendingEvent(type.name, json, timestamp))
      if (pendingEventCount.incrementAndGet() > maxBufferedEvents) {
        if (pendingEvents.pollFirst() != null) {
          pendingEventCount.decrementAndGet()
          if (droppedEvents.incrementAndGet() % 1000 == 1L) {
            log.warn("Metrics event buffer full; dropped {} event(s) so far", droppedEvents.get())
          }
        }
      }
      super.event(type, attributes, timestamp)
    } catch (e: Exception) {
      log.warn("Failed to record event {}", type, e)
    }
  }

  // ---------------- Export ----------------

  override fun flush() {
    synchronized(flushLock) {
      try {
        flushLocked()
      } catch (e: Exception) {
        log.warn("Metrics flush failed", e)
      }
    }
  }

  override fun shutdown() {
    if (shutDown) return
    shutDown = true
    scheduler?.shutdown()
    flush()
    try {
      scheduler?.awaitTermination(5, TimeUnit.SECONDS)
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
    }
  }

  private fun sampleRegisteredGauges() {
    gaugeSuppliers.values.forEach { (metric, rest) ->
      val (attrs, supplier) = rest
      val v = try {
        supplier()
      } catch (e: Exception) {
        log.debug("Gauge supplier for {} failed: {}", metric, e.message, e)
        null
      }
      if (v != null) gauge(metric, v, attrs)
    }
  }

  private fun flushLocked() {
    sampleRegisteredGauges()
    val series = ArrayList<Pending>()
    for (k in pending.keys.toList()) pending.remove(k)?.let { series += it }
    val events = ArrayList<PendingEvent>()
    while (true) {
      val e = pendingEvents.pollFirst() ?: break
      pendingEventCount.decrementAndGet()
      events += e
    }
    if (series.isNotEmpty() || events.isNotEmpty()) {
      val now = Instant.now()
      var lastError: Exception? = null
      // A concurrent insert by another node aborts the whole transaction; nothing was
      // committed, so retrying the batch once is safe (the row then exists -> UPDATE).
      for (attempt in 1..2) {
        try {
          transaction(database) {
            series.forEach { writeSeries(it, now) }
            if (events.isNotEmpty()) {
              EventsTable.batchInsert(events) { e ->
                this[EventsTable.eventType] = e.typeName
                this[EventsTable.attrs] = e.attrsJson
                this[EventsTable.ts] = e.timestamp
              }
            }
          }
          lastError = null
          break
        } catch (e: Exception) {
          lastError = e
          log.debug("Metrics export attempt {}/2 failed: {}", attempt, e.message, e)
        }
      }
      if (lastError != null) {
        log.warn(
          "Dropping {} series update(s) and {} event(s) after failed metrics export",
          series.size, events.size, lastError
        )
      }
    }
    maybePurge()
  }

  private fun keyOp(p: Pending): Op<Boolean> =
    (SeriesTable.metric eq p.metric.name) and (SeriesTable.attrs eq p.attrsKey)

  /** Must be invoked inside a transaction. */
  private fun writeSeries(p: Pending, now: Instant) {
    when (p.kind) {
      MetricKind.COUNTER -> {
        val updated = SeriesTable.update({ keyOp(p) }) {
          it.update(SeriesTable.metricValue, SeriesTable.metricValue + p.sum)
          it.update(SeriesTable.obsCount, SeriesTable.obsCount + p.count)
          it[SeriesTable.updatedAt] = now
        }
        if (updated == 0) insertSeries(p, now, value = p.sum, count = p.count, min = null, max = null)
      }

      MetricKind.GAUGE -> {
        val updated = SeriesTable.update({ keyOp(p) }) {
          it[SeriesTable.metricValue] = p.last
          it[SeriesTable.updatedAt] = now
        }
        if (updated == 0) insertSeries(p, now, value = p.last, count = 0L, min = null, max = null)
      }

      MetricKind.DISTRIBUTION -> {
        val updated = SeriesTable.update({ keyOp(p) }) {
          it.update(SeriesTable.metricValue, SeriesTable.metricValue + p.sum)
          it.update(SeriesTable.obsCount, SeriesTable.obsCount + p.count)
          it[SeriesTable.updatedAt] = now
        }
        if (updated == 0) {
          insertSeries(p, now, value = p.sum, count = p.count, min = p.min, max = p.max)
        } else {
          val mn = p.min
          val mx = p.max
          SeriesTable.update({
            keyOp(p) and (SeriesTable.obsMin.isNull() or (SeriesTable.obsMin greater mn))
          }) { it[SeriesTable.obsMin] = mn }
          SeriesTable.update({
            keyOp(p) and (SeriesTable.obsMax.isNull() or (SeriesTable.obsMax less mx))
          }) { it[SeriesTable.obsMax] = mx }
        }
      }
    }
  }

  private fun insertSeries(p: Pending, now: Instant, value: Double, count: Long, min: Double?, max: Double?) {
    SeriesTable.insert {
      it[SeriesTable.metric] = p.metric.name
      it[SeriesTable.attrs] = p.attrsKey
      it[SeriesTable.kind] = p.kind.name
      it[SeriesTable.unit] = p.metric.unit.name.take(64)
      it[SeriesTable.description] = p.metric.description.take(1024)
      it[SeriesTable.metricValue] = value
      it[SeriesTable.obsCount] = count
      it[SeriesTable.obsMin] = min
      it[SeriesTable.obsMax] = max
      it[SeriesTable.updatedAt] = now
    }
  }

  private fun maybePurge() {
    val retention = eventRetention ?: return
    val nowNanos = System.nanoTime()
    if (lastPurgeNanos != 0L && nowNanos - lastPurgeNanos < PURGE_INTERVAL_NANOS) return
    lastPurgeNanos = nowNanos
    val cutoff = Instant.now().minus(retention)
    try {
      val (events, alerts) = transaction(database) {
        EventsTable.deleteWhere { EventsTable.ts less cutoff } to
          ResolvedAlertsTable.deleteWhere { ResolvedAlertsTable.resolvedAt less cutoff }
      }
      if (events > 0) log.info("Purged {} metric event(s) older than {}", events, cutoff)
      if (alerts > 0) log.info("Purged {} resolved alert(s) older than {}", alerts, cutoff)
    } catch (e: Exception) {
      log.warn("Failed to purge old metric events: {}", e.message, e)
    }
  }

  // ---------------- Read side ----------------

  override val supportsQueries: Boolean get() = true

  override fun querySeries(query: MetricQuery): List<SeriesSnapshot> {
    flush()
    return transaction(database) { readSeries(query) }
  }

  /** Reads persisted series without flushing. Must be invoked inside a transaction. */
  private fun readSeries(query: MetricQuery): List<SeriesSnapshot> {
    val q = SeriesTable.selectAll()
    query.metric?.let { m -> q.andWhere { SeriesTable.metric eq m.name } }
    query.kind?.let { k -> q.andWhere { SeriesTable.kind eq k.name } }
    return q.mapNotNull { row -> toSnapshot(row, query) }
  }

  private fun toSnapshot(row: ResultRow, query: MetricQuery): SeriesSnapshot? {
    val name = row[SeriesTable.metric]
    val kind = runCatching { MetricKind.valueOf(row[SeriesTable.kind]) }.getOrNull() ?: return null
    val raw = decodeMap(row[SeriesTable.attrs])
    val type = query.metric
      ?: metricTypes[name]
      ?: synthesizeMetric(name, kind, row[SeriesTable.unit], row[SeriesTable.description], raw.keys)
      ?: return null
    if (query.attributes != null && type.sanitize(query.attributes!!).asStringMap() != raw) return null
    val attrs = decodeAttributes(type.attributes, raw)
    val value = row[SeriesTable.metricValue]
    val stats = if (kind == MetricKind.DISTRIBUTION) DistributionStats(
      count = row[SeriesTable.obsCount],
      sum = value,
      min = row[SeriesTable.obsMin] ?: Double.NaN,
      max = row[SeriesTable.obsMax] ?: Double.NaN,
    ) else null
    return SeriesSnapshot(type, kind, attrs, value, stats)
  }

  override fun queryEvents(query: EventQuery): List<RecordedEvent> {
    if (query.limit <= 0) return emptyList()
    flush()
    return transaction(database) {
      val q = EventsTable.selectAll()
      query.type?.let { t -> q.andWhere { EventsTable.eventType eq t.name } }
      query.since?.let { s -> q.andWhere { EventsTable.ts greaterEq s } }
      query.until?.let { u -> q.andWhere { EventsTable.ts less u } }
      q.orderBy(EventsTable.ts to SortOrder.DESC, EventsTable.id to SortOrder.DESC)
        .limit(query.limit)
        .mapNotNull { row ->
          val name = row[EventsTable.eventType]
          val raw = decodeMap(row[EventsTable.attrs])
          val type = query.type ?: eventTypes[name] ?: synthesizeEvent(name, raw.keys) ?: return@mapNotNull null
          RecordedEvent(type, decodeAttributes(type.attributes, raw), row[EventsTable.ts])
        }
    }
  }

  // ---------------- Alerting ----------------

  override val supportsAlerting: Boolean get() = true

  override fun putAlertPolicy(policy: AlertPolicy): AlertPolicy? {
    require(kindOf(policy.metric) != null) {
      "Metric ${policy.metric} is not a counter, gauge or distribution (policy ${policy.id})"
    }
    metricTypes.putIfAbsent(policy.metric.name, policy.metric)
    val spec = encodePolicy(policy)
    val previousSpec = synchronized(alertLock) {
      transaction(database) {
        val prev = AlertPoliciesTable.selectAll()
          .where { AlertPoliciesTable.id eq policy.id }
          .firstOrNull()?.get(AlertPoliciesTable.spec)
        val now = Instant.now()
        if (prev == null) {
          AlertPoliciesTable.insert {
            it[AlertPoliciesTable.id] = policy.id
            it[AlertPoliciesTable.metric] = policy.metric.name
            it[AlertPoliciesTable.spec] = spec
            it[AlertPoliciesTable.updatedAt] = now
          }
        } else {
          AlertPoliciesTable.update({ AlertPoliciesTable.id eq policy.id }) {
            it[AlertPoliciesTable.metric] = policy.metric.name
            it[AlertPoliciesTable.spec] = spec
            it[AlertPoliciesTable.updatedAt] = now
          }
        }
        prev
      }
    }
    evaluate(listOf(policy))
    return previousSpec?.let { decodePolicy(it) }
  }

  override fun removeAlertPolicy(id: String): Boolean = synchronized(alertLock) {
    transaction(database) {
      ActiveAlertsTable.deleteWhere { ActiveAlertsTable.policyId eq id }
      AlertPoliciesTable.deleteWhere { AlertPoliciesTable.id eq id } > 0
    }
  }

  override fun getAlertPolicy(id: String): AlertPolicy? =
    transaction(database) {
      AlertPoliciesTable.selectAll()
        .where { AlertPoliciesTable.id eq id }
        .firstOrNull()?.get(AlertPoliciesTable.spec)
    }?.let { decodePolicy(it) }

  override fun listAlertPolicies(): List<AlertPolicy> = loadPolicies()

  override fun evaluateAlerts() {
    val targets = try {
      loadPolicies()
    } catch (e: Exception) {
      log.warn("Failed to load alert policies: {}", e.message, e)
      return
    }
    evaluate(targets)
  }

  override fun listAlerts(query: AlertQuery): List<Alert> {
    if (query.limit <= 0) return emptyList()
    return transaction(database) {
      val firing = if (query.state == null || query.state == AlertState.FIRING) {
        val pq = AlertPoliciesTable.selectAll()
        query.policyId?.let { id -> pq.andWhere { AlertPoliciesTable.id eq id } }
        val policies = pq.mapNotNull { decodePolicy(it[AlertPoliciesTable.spec]) }
          .filter { query.severity == null || it.severity == query.severity }
          .associateBy { it.id }
        if (policies.isEmpty()) emptyList() else {
          ActiveAlertsTable.selectAll()
            .where { ActiveAlertsTable.policyId inList policies.keys.toList() }
            .mapNotNull { row ->
              val policy = policies[row[ActiveAlertsTable.policyId]] ?: return@mapNotNull null
              Alert(
                policy = policy,
                attributes = decodeAttributes(policy.metric.attributes, decodeMap(row[ActiveAlertsTable.attrs])),
                state = AlertState.FIRING,
                value = row[ActiveAlertsTable.alertValue],
                triggeredAt = row[ActiveAlertsTable.triggeredAt],
                lastEvaluatedAt = row[ActiveAlertsTable.lastEvaluatedAt],
              )
            }
            .sortedByDescending { it.triggeredAt }
        }
      } else emptyList()

      val remaining = query.limit - firing.size
      val resolved = if ((query.state == null || query.state == AlertState.RESOLVED) && remaining > 0) {
        val rq = ResolvedAlertsTable.selectAll()
        query.policyId?.let { id -> rq.andWhere { ResolvedAlertsTable.policyId eq id } }
        query.severity?.let { s -> rq.andWhere { ResolvedAlertsTable.severity eq s.name } }
        rq.orderBy(ResolvedAlertsTable.resolvedAt to SortOrder.DESC, ResolvedAlertsTable.id to SortOrder.DESC)
          .limit(remaining)
          .mapNotNull { row ->
            val policy = decodePolicy(row[ResolvedAlertsTable.policySpec]) ?: return@mapNotNull null
            val resolvedAt = row[ResolvedAlertsTable.resolvedAt]
            Alert(
              policy = policy,
              attributes = decodeAttributes(policy.metric.attributes, decodeMap(row[ResolvedAlertsTable.attrs])),
              state = AlertState.RESOLVED,
              value = row[ResolvedAlertsTable.alertValue],
              triggeredAt = row[ResolvedAlertsTable.triggeredAt],
              lastEvaluatedAt = resolvedAt,
              resolvedAt = resolvedAt,
            )
          }
      } else emptyList()

      (firing + resolved).take(query.limit)
    }
  }

  private fun loadPolicies(): List<AlertPolicy> =
    transaction(database) { AlertPoliciesTable.selectAll().map { it[AlertPoliciesTable.spec] } }
      .mapNotNull { decodePolicy(it) }
      .sortedBy { it.id }

  /** Evaluates [targets], persists transitions and dispatches them. Never throws. */
  private fun evaluate(targets: Collection<AlertPolicy>) {
    if (targets.isEmpty()) return
    val transitions = try {
      synchronized(alertLock) { evaluateLocked(targets) }
    } catch (e: Exception) {
      log.warn("Alert evaluation failed: {}", e.message, e)
      emptyList()
    }
    transitions.forEach { dispatch(it) }
  }

  private fun evaluateLocked(targets: Collection<AlertPolicy>): List<Alert> {
    flush()
    val metrics = targets.filter { it.enabled }.map { it.metric }.distinctBy { it.name }
    val series: Map<String, List<SeriesSnapshot>> = if (metrics.isEmpty()) emptyMap() else
      transaction(database) { metrics.associate { m -> m.name to readSeries(MetricQuery(metric = m)) } }
    val samples = targets.associateWith { sample(it, series[it.metric.name].orEmpty()) }
    val now = Instant.now()
    var lastError: Exception? = null
    // A concurrent transition by another node (duplicate active row) aborts the transaction;
    // nothing was committed, so retrying once re-reads the now-current alert state.
    for (attempt in 1..2) {
      try {
        return transaction(database) { applySamples(samples, now) }
      } catch (e: Exception) {
        lastError = e
        log.debug("Alert evaluation attempt {}/2 failed: {}", attempt, e.message, e)
      }
    }
    throw lastError!!
  }

  /** Must be invoked inside a transaction. Returns the committed transitions. */
  private fun applySamples(
    samples: Map<AlertPolicy, Map<String, Pair<Attributes, Double>>>,
    now: Instant,
  ): List<Alert> {
    val transitions = mutableListOf<Alert>()
    val ids = samples.keys.map { it.id }.distinct()
    val live = AlertPoliciesTable.selectAll()
      .where { AlertPoliciesTable.id inList ids }
      .map { it[AlertPoliciesTable.id] }
      .toSet()
    val active = ActiveAlertsTable.selectAll()
      .where { ActiveAlertsTable.policyId inList ids }
      .map {
        ActiveRow(
          it[ActiveAlertsTable.policyId],
          it[ActiveAlertsTable.attrs],
          it[ActiveAlertsTable.alertValue],
          it[ActiveAlertsTable.triggeredAt],
        )
      }
      .groupBy { it.policyId }

    for ((policy, values) in samples) {
      if (policy.id !in live) continue // removed concurrently
      val existing = active[policy.id].orEmpty().associateBy { it.attrsKey }
      val breached = values.filterValues { (_, v) -> policy.isBreached(v) }

      for ((key, sv) in breached) {
        val (attrs, value) = sv
        if (existing[key] == null) {
          ActiveAlertsTable.insert {
            it[ActiveAlertsTable.policyId] = policy.id
            it[ActiveAlertsTable.attrs] = key
            it[ActiveAlertsTable.alertValue] = value
            it[ActiveAlertsTable.triggeredAt] = now
            it[ActiveAlertsTable.lastEvaluatedAt] = now
          }
          transitions += Alert(policy, attrs, AlertState.FIRING, value, triggeredAt = now)
        } else {
          ActiveAlertsTable.update({
            (ActiveAlertsTable.policyId eq policy.id) and (ActiveAlertsTable.attrs eq key)
          }) {
            it[ActiveAlertsTable.alertValue] = value
            it[ActiveAlertsTable.lastEvaluatedAt] = now
          }
        }
      }

      for ((key, row) in existing) {
        if (key in breached) continue
        val deleted = ActiveAlertsTable.deleteWhere {
          (ActiveAlertsTable.policyId eq policy.id) and (ActiveAlertsTable.attrs eq key)
        }
        if (deleted == 0) continue // resolved by another node
        val sv = values[key]
        val value = sv?.second ?: row.value
        val attrs = sv?.first ?: decodeAttributes(policy.metric.attributes, decodeMap(key))
        ResolvedAlertsTable.insert {
          it[ResolvedAlertsTable.policyId] = policy.id
          it[ResolvedAlertsTable.severity] = policy.severity.name
          it[ResolvedAlertsTable.attrs] = key
          it[ResolvedAlertsTable.policySpec] = encodePolicy(policy)
          it[ResolvedAlertsTable.alertValue] = value
          it[ResolvedAlertsTable.triggeredAt] = row.triggeredAt
          it[ResolvedAlertsTable.resolvedAt] = now
        }
        transitions += Alert(
          policy = policy,
          attributes = attrs,
          state = AlertState.RESOLVED,
          value = value,
          triggeredAt = row.triggeredAt,
          lastEvaluatedAt = now,
          resolvedAt = now,
        )
      }
    }
    return transitions
  }

  /** Statistic per matching series, keyed by the canonical attribute encoding; empty for disabled policies. */
  private fun sample(policy: AlertPolicy, series: List<SeriesSnapshot>): Map<String, Pair<Attributes, Double>> {
    if (!policy.enabled) return emptyMap()
    val filter = policy.filter.asStringMap()
    return series.mapNotNull { s ->
      val raw = s.attributes.asStringMap()
      if (!filter.all { (k, v) -> raw[k] == v }) return@mapNotNull null
      val value = statistic(policy.statistic, s) ?: return@mapNotNull null
      encodeMap(raw) to (s.attributes to value)
    }.toMap()
  }

  private fun statistic(stat: AlertStatistic, s: SeriesSnapshot): Double? = when (stat) {
    AlertStatistic.VALUE -> s.value
    AlertStatistic.COUNT -> s.distribution?.count?.toDouble()
    AlertStatistic.MEAN -> s.distribution?.mean
    AlertStatistic.MIN -> s.distribution?.min
    AlertStatistic.MAX -> s.distribution?.max
  }?.takeUnless { it.isNaN() }

  private fun dispatch(alert: Alert) {
    try {
      notifications().notifyAlert(alert)
    } catch (e: Exception) {
      log.warn("Alert notification failed for {}", alert.id, e)
    }
  }

  private fun encodePolicy(policy: AlertPolicy): String {
    val kind = kindOf(policy.metric)
      ?: throw IllegalArgumentException("Metric ${policy.metric} has no supported kind (policy ${policy.id})")
    return specGson.toJson(
      PolicySpec(
        id = policy.id,
        metric = policy.metric.name,
        kind = kind.name,
        unit = policy.metric.unit.name,
        metricDescription = policy.metric.description,
        comparison = policy.comparison.name,
        threshold = policy.threshold,
        filter = policy.filter.asStringMap(),
        statistic = policy.statistic.name,
        severity = policy.severity.name,
        description = policy.description,
        channels = policy.channels.sorted(),
        enabled = policy.enabled,
      )
    )
  }

  private fun decodePolicy(json: String): AlertPolicy? = try {
    val s = specGson.fromJson(json, PolicySpec::class.java)
      ?: throw IllegalArgumentException("empty policy spec")
    val name = s.metric ?: throw IllegalArgumentException("missing metric")
    val kind = MetricKind.valueOf(s.kind ?: throw IllegalArgumentException("missing kind"))
    val filterRaw = s.filter.orEmpty()
    val metric = metricTypes[name]?.takeIf { kindOf(it) == kind }
      ?: synthesizeMetric(name, kind, s.unit ?: "None", s.metricDescription ?: "", filterRaw.keys)
      ?: throw IllegalArgumentException("cannot resolve metric $name")
    AlertPolicy(
      id = s.id ?: throw IllegalArgumentException("missing id"),
      metric = metric,
      comparison = Comparison.valueOf(s.comparison ?: throw IllegalArgumentException("missing comparison")),
      threshold = s.threshold ?: throw IllegalArgumentException("missing threshold"),
      filter = decodeAttributes(metric.attributes, filterRaw),
      statistic = s.statistic?.let { AlertStatistic.valueOf(it) } ?: AlertStatistic.VALUE,
      severity = s.severity?.let { AlertSeverity.valueOf(it) } ?: AlertSeverity.WARNING,
      description = s.description ?: "",
      channels = s.channels.orEmpty().toSet(),
      enabled = s.enabled ?: true,
    )
  } catch (e: Exception) {
    log.warn("Failed to decode alert policy '{}': {}", json.take(200), e.message)
    null
  }

  // ---------------- Admin ----------------

  /** Deletes all persisted series, events, alert policies and alert state, and clears buffers. Intended for tests/admin. */
  fun clear() {
    synchronized(alertLock) {
      synchronized(flushLock) {
        pending.clear()
        pendingEvents.clear()
        pendingEventCount.set(0)
        transaction(database) {
          SeriesTable.deleteAll()
          EventsTable.deleteAll()
          ActiveAlertsTable.deleteAll()
          ResolvedAlertsTable.deleteAll()
          AlertPoliciesTable.deleteAll()
        }
      }
    }
  }

  // ---------------- Decoding helpers ----------------

  private fun synthesizeMetric(
    name: String, kind: MetricKind, unit: String, description: String, attrNames: Collection<String>,
  ): MetricType? = runCatching {
    val attrs = attrNames.mapNotNull { stringAttribute(it) }.toTypedArray()
    val u = MetricUnit(unit)
    when (kind) {
      MetricKind.COUNTER -> CounterType.of(name, u, description, *attrs)
      MetricKind.GAUGE -> GaugeType.of(name, u, description, *attrs)
      MetricKind.DISTRIBUTION -> DistributionType.of(name, u, description, *attrs)
    }
  }.onFailure { log.debug("Cannot synthesize metric type '{}': {}", name, it.message) }.getOrNull()

  private fun synthesizeEvent(name: String, attrNames: Collection<String>): EventType? = runCatching {
    EventType.of(name, "", null, *attrNames.mapNotNull { stringAttribute(it) }.toTypedArray())
  }.onFailure { log.debug("Cannot synthesize event type '{}': {}", name, it.message) }.getOrNull()

  private fun stringAttribute(name: String): MetricAttribute<String>? =
    runCatching { MetricAttribute(name, String::class.java) }.getOrNull()

  @Suppress("UNCHECKED_CAST")
  private fun decodeAttributes(declared: Set<MetricAttribute<*>>, raw: Map<String, String>): Attributes {
    val values = raw.mapNotNull { (name, rendered) ->
      val attr = declared.firstOrNull { it.name == name }
      val typed = attr?.let { parseValue(it.type, rendered) }
      if (attr != null && typed != null) {
        (attr as MetricAttribute<Any>)(typed)
      } else {
        stringAttribute(name)?.let { it(rendered) }
      }
    }
    return Attributes.of(*values.toTypedArray<AttributeValue<*>?>())
  }

  private fun parseValue(type: Class<*>, raw: String): Any? = runCatching {
    when {
      type == String::class.java -> raw
      type.isEnum -> type.enumConstants.firstOrNull { (it as Enum<*>).name == raw || it.toString() == raw }
      type == java.lang.Double::class.java -> raw.toDouble()
      type == java.lang.Long::class.java -> raw.toLong()
      type == java.lang.Integer::class.java -> raw.toInt()
      type == java.lang.Boolean::class.java -> raw.toBooleanStrict()
      else -> type.getConstructor(String::class.java).newInstance(raw)
    }
  }.getOrNull()

  companion object {
    private val log = LoggerFactory.getLogger(MetricsDB::class.java)
    private val gson = Gson()
    /** Policy thresholds may be infinite, which plain JSON cannot represent. */
    private val specGson: Gson = GsonBuilder().serializeSpecialFloatingPointValues().create()
    private val mapType = object : TypeToken<Map<String, String>>() {}.type
    private val PURGE_INTERVAL_NANOS = Duration.ofHours(1).toNanos()

    /** Sorted, canonical encoding; used as part of the series primary key. */
    private fun encodeMap(map: Map<String, String>): String =
      gson.toJson(map.toSortedMap())

    private fun decodeMap(json: String?): Map<String, String> {
      if (json.isNullOrBlank()) return emptyMap()
      return try {
        gson.fromJson<Map<String, String>>(json, mapType) ?: emptyMap()
      } catch (e: Exception) {
        log.warn("Failed to decode metric attributes '{}': {}", json.take(200), e.message)
        emptyMap()
      }
    }

    private fun kindOf(m: MetricType): MetricKind? = when (m) {
      is CounterType -> MetricKind.COUNTER
      is GaugeType -> MetricKind.GAUGE
      is DistributionType -> MetricKind.DISTRIBUTION
      else -> null
    }

    private fun builtinMetricTypes(): List<MetricType> = runCatching {
      MetricType.Companion::class.java.methods
        .filter { it.parameterCount == 0 && MetricType::class.java.isAssignableFrom(it.returnType) }
        .mapNotNull { runCatching { it.invoke(MetricType.Companion) as? MetricType }.getOrNull() }
    }.getOrElse { emptyList() }

    private fun builtinEventTypes(): List<EventType> = runCatching {
      EventType.Companion::class.java.methods
        .filter { it.parameterCount == 0 && EventType::class.java.isAssignableFrom(it.returnType) }
        .mapNotNull { runCatching { it.invoke(EventType.Companion) as? EventType }.getOrNull() }
    }.getOrElse { emptyList() }

    internal val facet by lazy {
      DatabaseFacet(
        name = "metrics",
        tables = listOf(SeriesTable, EventsTable, AlertPoliciesTable, ActiveAlertsTable, ResolvedAlertsTable),
      )
    }
  }
}