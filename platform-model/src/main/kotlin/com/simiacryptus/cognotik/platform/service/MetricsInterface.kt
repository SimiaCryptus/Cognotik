package com.simiacryptus.cognotik.platform.service

import com.simiacryptus.cognotik.platform.model.Alert
import com.simiacryptus.cognotik.platform.model.AlertPolicy
import com.simiacryptus.cognotik.platform.model.AlertSeverity
import com.simiacryptus.cognotik.platform.model.AlertState
import com.simiacryptus.cognotik.platform.model.AlertStatistic
import com.simiacryptus.cognotik.platform.model.Attributes
import com.simiacryptus.cognotik.platform.model.CounterType
import com.simiacryptus.cognotik.platform.model.DistributionType
import com.simiacryptus.cognotik.platform.model.EventType
import com.simiacryptus.cognotik.platform.model.GaugeType
import com.simiacryptus.cognotik.platform.model.MetricType
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.DoubleAdder

/** The kind of a metric series. */
enum class MetricKind { COUNTER, GAUGE, DISTRIBUTION }

/** Summary statistics of a distribution series. */
data class DistributionStats(
  val count: Long,
  val sum: Double,
  val min: Double,
  val max: Double,
) {
  val mean: Double get() = if (count == 0L) Double.NaN else sum / count
}

/**
 * Point-in-time value of one series.
 *
 * For [MetricKind.DISTRIBUTION] series, [value] is the sum of observations and
 * [distribution] carries the full summary; for other kinds [distribution] is null.
 */
data class SeriesSnapshot(
  val metric: MetricType,
  val kind: MetricKind,
  val attributes: Attributes,
  val value: Double,
  val distribution: DistributionStats? = null,
)

/**
 * Filter for [MetricsInterface.querySeries]. Null fields match everything.
 *
 * @param attributes if non-null, only series whose (sanitized) attributes equal these are returned
 */
data class MetricQuery(
  val metric: MetricType? = null,
  val kind: MetricKind? = null,
  val attributes: Attributes? = null,
)

/**
 * Filter for [MetricsInterface.queryEvents]. Null fields match everything.
 *
 * @param since inclusive lower bound on the event timestamp
 * @param until exclusive upper bound on the event timestamp
 * @param limit maximum number of events returned (newest first)
 */
data class EventQuery(
  val type: EventType? = null,
  val since: Instant? = null,
  val until: Instant? = null,
  val limit: Int = 100,
)

/**
 * Filter for [MetricsInterface.listAlerts]. Null fields match everything.
 *
 * @param limit maximum number of alerts returned (firing first, then most recently resolved)
 */
data class AlertQuery(
  val state: AlertState? = null,
  val policyId: String? = null,
  val severity: AlertSeverity? = null,
  val limit: Int = 100,
)

/** A discrete event as recorded by a backend that retains events. */
data class RecordedEvent(val type: EventType, val attributes: Attributes, val timestamp: Instant)

/**
 * Central metrics service.
 *
 * Backends (CloudWatch, Prometheus, ...) implement this port and are registered via
 * [com.simiacryptus.cognotik.platform.ServiceKey.METRICS]. Several backends can be
 * combined with [CompositeMetrics].
 *
 * Contract (write side):
 * - All methods are thread-safe and MUST NOT throw on the recording path; a metrics
 *   failure must never break a user request. Implementations log and drop instead.
 * - Recording should be cheap and non-blocking; implementations buffer and export
 *   asynchronously, with [flush] forcing an export.
 * - Implementations should apply [MetricType.sanitize] so undeclared and
 *   high-cardinality attributes do not explode the series count.
 *
 * Contract (read side, optional):
 * - Reading is optional because many backends are write-only. Check [supportsQueries];
 *   when it is false, all query methods return empty results.
 * - Query methods are thread-safe. Unlike recording, they may throw if a queryable
 *   backend fails; callers (e.g. a dashboard) are expected to handle that.
 *
 * Contract (alerting, optional):
 * - Check [supportsAlerting]; when false, [putAlertPolicy] throws
 *   [UnsupportedOperationException] and the other alerting methods return empty results.
 * - Backends evaluate policies (on write, on [evaluateAlerts], or natively, e.g. as
 *   CloudWatch alarms) and report every FIRING/RESOLVED transition to
 *   [NotificationsInterface] (see [NotificationsInterface.resolve]).
 * - [evaluateAlerts] MUST NOT throw; policy management may throw on invalid input.
 */
interface MetricsInterface {

  /** Adds [amount] (must be >= 0) to a counter. */
  fun increment(metric: CounterType, amount: Double = 1.0, attributes: Attributes = Attributes.EMPTY)

  /** Sets a gauge to [value]. */
  fun gauge(metric: GaugeType, value: Double, attributes: Attributes = Attributes.EMPTY)

  /** Records one observation in a distribution. */
  fun record(metric: DistributionType, value: Double, attributes: Attributes = Attributes.EMPTY)

  /**
   * Registers a gauge whose value is pulled at export time (useful for Prometheus
   * scrapes and periodic CloudWatch pushes). A null from [supplier] means "no sample".
   *
   * @return a handle that unregisters the gauge when closed
   */
  fun registerGauge(
    metric: GaugeType,
    attributes: Attributes = Attributes.EMPTY,
    supplier: () -> Double?,
  ): AutoCloseable

  /**
   * Records a discrete event. The default increments [EventType.counter] (if any);
   * backends with an event/log sink should override and also emit the event with
   * its full (including high-cardinality) attributes.
   */
  fun event(type: EventType, attributes: Attributes = Attributes.EMPTY, timestamp: Instant = Instant.now()) {
    type.counter?.let { increment(it, 1.0, attributes) }
  }

  /** Forces export of buffered data. Default no-op. */
  fun flush() {}

  /** Flushes and releases resources. Default delegates to [flush]. */
  fun shutdown() = flush()

  // ---------------- Read side (optional) ----------------

  /** True if this backend can answer the query methods below. Default false (write-only). */
  val supportsQueries: Boolean get() = false

  /** Current values of all series matching [query]. Default: empty (unsupported). */
  fun querySeries(query: MetricQuery = MetricQuery()): List<SeriesSnapshot> = emptyList()

  /** Retained events matching [query], newest first. Default: empty (unsupported). */
  fun queryEvents(query: EventQuery = EventQuery()): List<RecordedEvent> = emptyList()

  /** Metrics that currently have at least one series. Derived from [querySeries] by default. */
  fun listMetrics(): List<MetricType> = querySeries().map { it.metric }.distinct()

  // ---------------- Alerting (optional) ----------------

  /** True if this backend evaluates alert policies. Default false. */
  val supportsAlerting: Boolean get() = false

  /**
   * Creates or replaces (by [AlertPolicy.id]) an alert policy.
   * Alerts already firing for a replaced policy keep firing if still breached.
   *
   * @return the previous policy with the same id, if any
   * @throws UnsupportedOperationException if [supportsAlerting] is false
   */
  fun putAlertPolicy(policy: AlertPolicy): AlertPolicy? =
    throw UnsupportedOperationException("${javaClass.simpleName} does not support alerting")

  /** Removes a policy; its active alerts are dropped without notification. @return true if it existed */
  fun removeAlertPolicy(id: String): Boolean = false

  fun getAlertPolicy(id: String): AlertPolicy? = listAlertPolicies().firstOrNull { it.id == id }

  fun listAlertPolicies(): List<AlertPolicy> = emptyList()

  /**
   * Evaluates all policies now (sampling registered gauges). Backends that evaluate
   * only on demand need this to be called periodically. Never throws. Default no-op.
   */
  fun evaluateAlerts() {}

  /** Alerts matching [query]: firing alerts first, then recently resolved, newest first. */
  fun listAlerts(query: AlertQuery = AlertQuery()): List<Alert> = emptyList()

  /** All currently firing alerts. */
  fun triggeredAlerts(): List<Alert> = listAlerts(AlertQuery(state = AlertState.FIRING, limit = Int.MAX_VALUE))
}

/** Discards everything; the safe default when no backend is configured. */
object NoOpMetrics : MetricsInterface {
  override fun increment(metric: CounterType, amount: Double, attributes: Attributes) {}
  override fun gauge(metric: GaugeType, value: Double, attributes: Attributes) {}
  override fun record(metric: DistributionType, value: Double, attributes: Attributes) {}
  override fun registerGauge(metric: GaugeType, attributes: Attributes, supplier: () -> Double?) =
    AutoCloseable {}
  override fun event(type: EventType, attributes: Attributes, timestamp: Instant) {}

  /** Policies are discarded too, so startup code registering policies does not fail. */
  override fun putAlertPolicy(policy: AlertPolicy): AlertPolicy? = null
}

/**
 * Fans out to several backends (e.g. CloudWatch and Prometheus). A failure in one
 * delegate is logged and does not affect the others.
 *
 * Reads are served by the first delegate that [supportsQueries]; if it fails, the next
 * queryable delegate is tried. Results are never merged across delegates, since all
 * delegates receive the same writes and merging would double-count.
 *
 * Alerting is delegated to the first delegate that [supportsAlerting] only; installing
 * policies in several backends would produce duplicate notifications.
 */
class CompositeMetrics(private val delegates: List<MetricsInterface>) : MetricsInterface {

  constructor(vararg delegates: MetricsInterface) : this(delegates.toList())

  private inline fun each(op: String, block: (MetricsInterface) -> Unit) {
    delegates.forEach {
      try {
        block(it)
       } catch (e: VirtualMachineError) {
         throw e
       } catch (e: Throwable) {
         // Includes LinkageErrors so one broken backend/metric cannot starve the other delegates.
        log.warn("Metrics backend ${it.javaClass.simpleName} failed on $op", e)
      }
    }
  }

  private inline fun <T> firstQueryable(op: String, block: (MetricsInterface) -> List<T>): List<T> {
    for (delegate in delegates) {
      if (!delegate.supportsQueries) continue
      try {
        return block(delegate)
      } catch (e: Exception) {
        log.warn("Metrics backend ${delegate.javaClass.simpleName} failed on $op", e)
      }
    }
    return emptyList()
  }

  private val alerting: MetricsInterface? get() = delegates.firstOrNull { it.supportsAlerting }

  override fun increment(metric: CounterType, amount: Double, attributes: Attributes) =
    each("increment $metric") { it.increment(metric, amount, attributes) }

  override fun gauge(metric: GaugeType, value: Double, attributes: Attributes) =
    each("gauge $metric") { it.gauge(metric, value, attributes) }

  override fun record(metric: DistributionType, value: Double, attributes: Attributes) =
    each("record $metric") { it.record(metric, value, attributes) }

  override fun registerGauge(metric: GaugeType, attributes: Attributes, supplier: () -> Double?): AutoCloseable {
    val handles = mutableListOf<AutoCloseable>()
    each("registerGauge $metric") { handles += it.registerGauge(metric, attributes, supplier) }
    return AutoCloseable { handles.forEach { h -> runCatching { h.close() } } }
  }

  override fun event(type: EventType, attributes: Attributes, timestamp: Instant) =
    each("event $type") { it.event(type, attributes, timestamp) }

  override fun flush() = each("flush") { it.flush() }

  override fun shutdown() = each("shutdown") { it.shutdown() }

  override val supportsQueries: Boolean get() = delegates.any { it.supportsQueries }

  override fun querySeries(query: MetricQuery): List<SeriesSnapshot> =
    firstQueryable("querySeries") { it.querySeries(query) }

  override fun queryEvents(query: EventQuery): List<RecordedEvent> =
    firstQueryable("queryEvents") { it.queryEvents(query) }

  override val supportsAlerting: Boolean get() = alerting != null

  override fun putAlertPolicy(policy: AlertPolicy): AlertPolicy? =
    (alerting ?: throw UnsupportedOperationException("No delegate supports alerting")).putAlertPolicy(policy)

  override fun removeAlertPolicy(id: String): Boolean = alerting?.removeAlertPolicy(id) ?: false

  override fun getAlertPolicy(id: String): AlertPolicy? = alerting?.getAlertPolicy(id)

  override fun listAlertPolicies(): List<AlertPolicy> = alerting?.listAlertPolicies() ?: emptyList()

  override fun evaluateAlerts() {
    val delegate = alerting ?: return
    try {
      delegate.evaluateAlerts()
    } catch (e: Exception) {
      log.warn("Metrics backend ${delegate.javaClass.simpleName} failed on evaluateAlerts", e)
    }
  }

  override fun listAlerts(query: AlertQuery): List<Alert> = alerting?.listAlerts(query) ?: emptyList()

  override fun triggeredAlerts(): List<Alert> = alerting?.triggeredAlerts() ?: emptyList()

  companion object {
    private val log = LoggerFactory.getLogger(CompositeMetrics::class.java)
  }
}

/**
 * In-process reference implementation, for tests, local development and as the
 * behavioural reference for real backends. Applies [MetricType.sanitize] to all
 * series; events retain their full attributes. Supports queries and alerting.
 *
 * Alerting: policies for a metric are re-evaluated on every write to that metric.
 * Values from [registerGauge] suppliers are only sampled by [evaluateAlerts], which
 * should therefore be called periodically if such gauges are alerted on.
 *
 * @param maxEvents number of most recent events retained
 * @param maxResolvedAlerts number of most recently resolved alerts retained for [listAlerts]
 * @param notifications resolves the notification sink at delivery time
 */
class InMemoryMetrics(
  private val maxEvents: Int = 10_000,
  private val maxResolvedAlerts: Int = 1_000,
  private val notifications: () -> NotificationsInterface = { NotificationsInterface.resolve() },
) : MetricsInterface {

  data class SeriesKey(val metric: MetricType, val attributes: Attributes)

  /** Count/sum/min/max summary for a distribution series. */
  class DistributionSummary {
    var count: Long = 0; private set
    var sum: Double = 0.0; private set
    var min: Double = Double.NaN; private set
    var max: Double = Double.NaN; private set

    @Synchronized
    fun add(value: Double) {
      count++
      sum += value
      min = if (min.isNaN()) value else minOf(min, value)
      max = if (max.isNaN()) value else maxOf(max, value)
    }

    val mean: Double @Synchronized get() = if (count == 0L) Double.NaN else sum / count

    /** Consistent copy of the current statistics. */
    @Synchronized
    fun stats(): DistributionStats = DistributionStats(count, sum, min, max)
  }

  private val counters = ConcurrentHashMap<SeriesKey, DoubleAdder>()
  private val gauges = ConcurrentHashMap<SeriesKey, Double>()
  private val gaugeSuppliers = ConcurrentHashMap<SeriesKey, () -> Double?>()
  private val distributions = ConcurrentHashMap<SeriesKey, DistributionSummary>()
  private val events = ConcurrentLinkedDeque<RecordedEvent>()
  private val eventCount = AtomicInteger()

  private val policies = ConcurrentHashMap<String, AlertPolicy>()
  private val alertLock = Any()
  /** Firing alerts keyed by (policy id, series attributes). Guarded by [alertLock]. */
  private val activeAlerts = LinkedHashMap<Pair<String, Attributes>, Alert>()
  /** Recently resolved alerts, oldest first. Guarded by [alertLock]. */
  private val resolvedAlerts = ArrayDeque<Alert>()

  private fun key(metric: MetricType, attributes: Attributes) = SeriesKey(metric, metric.sanitize(attributes))

  override fun increment(metric: CounterType, amount: Double, attributes: Attributes) {
    if (amount < 0 || amount.isNaN()) {
      log.warn("Ignoring invalid counter increment $amount for $metric")
      return
    }
    counters.computeIfAbsent(key(metric, attributes)) { DoubleAdder() }.add(amount)
    evaluateFor(metric)
  }

  override fun gauge(metric: GaugeType, value: Double, attributes: Attributes) {
    gauges[key(metric, attributes)] = value
    evaluateFor(metric)
  }

  override fun record(metric: DistributionType, value: Double, attributes: Attributes) {
    distributions.computeIfAbsent(key(metric, attributes)) { DistributionSummary() }.add(value)
    evaluateFor(metric)
  }

  override fun registerGauge(metric: GaugeType, attributes: Attributes, supplier: () -> Double?): AutoCloseable {
    val k = key(metric, attributes)
    gaugeSuppliers[k] = supplier
    return AutoCloseable { gaugeSuppliers.remove(k, supplier) }
  }

  override fun event(type: EventType, attributes: Attributes, timestamp: Instant) {
    events.addLast(RecordedEvent(type, attributes.restrictTo(type.attributes), timestamp))
    if (eventCount.incrementAndGet() > maxEvents) {
      events.pollFirst()?.let { eventCount.decrementAndGet() }
    }
    super.event(type, attributes, timestamp)
  }

  // ---------------- Read side ----------------

  override val supportsQueries: Boolean get() = true

  override fun querySeries(query: MetricQuery): List<SeriesSnapshot> {
    fun matches(k: SeriesKey, kind: MetricKind): Boolean =
      (query.metric == null || k.metric == query.metric) &&
        (query.kind == null || query.kind == kind) &&
        (query.attributes == null || k.metric.sanitize(query.attributes) == k.attributes)

    val result = mutableListOf<SeriesSnapshot>()

    counters.forEach { (k, v) ->
      if (matches(k, MetricKind.COUNTER)) {
        result += SeriesSnapshot(k.metric, MetricKind.COUNTER, k.attributes, v.sum())
      }
    }

    // Registered suppliers are sampled now (only those that match); explicit values take precedence.
    val gaugeValues = LinkedHashMap<SeriesKey, Double>()
    gaugeSuppliers.forEach { (k, s) ->
      if (matches(k, MetricKind.GAUGE)) runCatching(s).getOrNull()?.let { gaugeValues[k] = it }
    }
    gauges.forEach { (k, v) -> if (matches(k, MetricKind.GAUGE)) gaugeValues[k] = v }
    gaugeValues.forEach { (k, v) ->
      result += SeriesSnapshot(k.metric, MetricKind.GAUGE, k.attributes, v)
    }

    distributions.forEach { (k, v) ->
      if (matches(k, MetricKind.DISTRIBUTION)) {
        val stats = v.stats()
        result += SeriesSnapshot(k.metric, MetricKind.DISTRIBUTION, k.attributes, stats.sum, stats)
      }
    }
    return result
  }

  override fun queryEvents(query: EventQuery): List<RecordedEvent> {
    if (query.limit <= 0) return emptyList()
    return events.descendingIterator().asSequence()
      .filter { query.type == null || it.type == query.type }
      .filter { query.since == null || !it.timestamp.isBefore(query.since) }
      .filter { query.until == null || it.timestamp.isBefore(query.until) }
      .take(query.limit)
      .toList()
  }

  // ---------------- Alerting ----------------

  override val supportsAlerting: Boolean get() = true

  override fun putAlertPolicy(policy: AlertPolicy): AlertPolicy? {
    val previous = policies.put(policy.id, policy)
    evaluate(listOf(policy))
    return previous
  }

  override fun removeAlertPolicy(id: String): Boolean {
    policies.remove(id) ?: return false
    synchronized(alertLock) { activeAlerts.keys.removeIf { it.first == id } }
    return true
  }

  override fun getAlertPolicy(id: String): AlertPolicy? = policies[id]

  override fun listAlertPolicies(): List<AlertPolicy> = policies.values.sortedBy { it.id }

  override fun evaluateAlerts() = evaluate(policies.values.toList())

  override fun listAlerts(query: AlertQuery): List<Alert> {
    if (query.limit <= 0) return emptyList()
    val all = synchronized(alertLock) {
      activeAlerts.values.sortedByDescending { it.triggeredAt } + resolvedAlerts.reversed()
    }
    return all.asSequence()
      .filter { query.state == null || it.state == query.state }
      .filter { query.policyId == null || it.policy.id == query.policyId }
      .filter { query.severity == null || it.policy.severity == query.severity }
      .take(query.limit)
      .toList()
  }

  private fun evaluateFor(metric: MetricType) {
    if (policies.isEmpty()) return
    val targets = policies.values.filter { it.metric == metric }
    if (targets.isNotEmpty()) evaluate(targets)
  }

  /** Evaluates [targets], updates alert state and dispatches transitions. Never throws. */
  private fun evaluate(targets: Collection<AlertPolicy>) {
    if (targets.isEmpty()) return
    val transitions = mutableListOf<Alert>()
    try {
      // Sample outside the lock: gauge suppliers may be slow.
      val samples = targets.associateWith { sample(it) }
      val now = Instant.now()
      synchronized(alertLock) {
        for ((policy, values) in samples) {
          if (policies[policy.id] != policy) continue // removed or replaced concurrently
          val breached = values.filterValues { policy.isBreached(it) }
          for ((attrs, value) in breached) {
            val k = policy.id to attrs
            val existing = activeAlerts[k]
            if (existing == null) {
              val alert = Alert(policy, attrs, AlertState.FIRING, value, triggeredAt = now)
              activeAlerts[k] = alert
              transitions += alert
            } else {
              activeAlerts[k] = existing.copy(policy = policy, value = value, lastEvaluatedAt = now)
            }
          }
          val it = activeAlerts.entries.iterator()
          while (it.hasNext()) {
            val (k, alert) = it.next()
            if (k.first != policy.id || k.second in breached) continue
            it.remove()
            val resolved = alert.copy(
              policy = policy,
              state = AlertState.RESOLVED,
              value = values[k.second] ?: alert.value,
              lastEvaluatedAt = now,
              resolvedAt = now,
            )
            resolvedAlerts.addLast(resolved)
            while (resolvedAlerts.size > maxResolvedAlerts) resolvedAlerts.removeFirst()
            transitions += resolved
          }
        }
      }
    } catch (e: Exception) {
      log.warn("Alert evaluation failed", e)
    }
    transitions.forEach { dispatch(it) }
  }

  /** Current statistic value per matching series; empty for disabled policies. */
  private fun sample(policy: AlertPolicy): Map<Attributes, Double> {
    if (!policy.enabled) return emptyMap()
    return querySeries(MetricQuery(metric = policy.metric))
      .filter { policy.matches(it.attributes) }
      .mapNotNull { s -> statistic(policy.statistic, s)?.let { s.attributes to it } }
      .toMap()
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
      log.warn("Alert notification failed for ${alert.id}", e)
    }
  }

  // ---------------- Test helpers ----------------

  fun counter(metric: CounterType, attributes: Attributes = Attributes.EMPTY): Double =
    counters[key(metric, attributes)]?.sum() ?: 0.0

  /** Sum of a counter across all attribute combinations. */
  fun counterTotal(metric: CounterType): Double =
    counters.filterKeys { it.metric == metric }.values.sumOf { it.sum() }

  /** Explicit value if set, otherwise the registered supplier's current value. */
  fun gaugeValue(metric: GaugeType, attributes: Attributes = Attributes.EMPTY): Double? {
    val k = key(metric, attributes)
    return gauges[k] ?: gaugeSuppliers[k]?.let { runCatching(it).getOrNull() }
  }

  fun distribution(metric: DistributionType, attributes: Attributes = Attributes.EMPTY): DistributionSummary? =
    distributions[key(metric, attributes)]

  fun events(type: EventType? = null): List<RecordedEvent> =
    events.filter { type == null || it.type == type }

  /** All current series values, keyed by series. Gauge suppliers are sampled now. */
  fun snapshot(): Map<SeriesKey, Double> = buildMap {
    counters.forEach { (k, v) -> put(k, v.sum()) }
    gaugeSuppliers.forEach { (k, s) -> runCatching(s).getOrNull()?.let { put(k, it) } }
    gauges.forEach { (k, v) -> put(k, v) }
    distributions.forEach { (k, v) -> put(k, v.sum) }
  }

  /** Clears all series, events, alert policies and alert state. */
  fun reset() {
    counters.clear(); gauges.clear(); gaugeSuppliers.clear(); distributions.clear()
    events.clear(); eventCount.set(0)
    policies.clear()
    synchronized(alertLock) { activeAlerts.clear(); resolvedAlerts.clear() }
  }

  companion object {
    private val log = LoggerFactory.getLogger(InMemoryMetrics::class.java)
  }
}