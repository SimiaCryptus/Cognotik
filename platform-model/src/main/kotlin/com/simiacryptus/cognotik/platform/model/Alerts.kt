package com.simiacryptus.cognotik.platform.model

import java.time.Instant

/*
 * Alerting vocabulary for the platform metrics service.
 *
 * An [AlertPolicy] compares a statistic of each matching series of one metric
 * against a threshold. Every series that breaches the threshold produces its own
 * [Alert], which transitions FIRING -> RESOLVED. Backends that support alerting
 * report transitions through [com.simiacryptus.cognotik.platform.service.NotificationsInterface].
 *
 *   ServiceRouter.putAlertPolicy(
 *     AlertPolicy(
 *       id = "low-banked-credits",
 *       metric = MetricType.CREDITS_BANKED,
 *       comparison = Comparison.LESS_THAN,
 *       threshold = 100.0,
 *       severity = AlertSeverity.CRITICAL,
 *     )
 *   )
 */

enum class AlertSeverity { INFO, WARNING, CRITICAL }

/** Alert lifecycle state. */
enum class AlertState { FIRING, RESOLVED }

/** Threshold comparison. NaN values never breach. */
enum class Comparison(val symbol: String) {
  GREATER_THAN(">"),
  GREATER_OR_EQUAL(">="),
  LESS_THAN("<"),
  LESS_OR_EQUAL("<="),
  EQUAL("=="),
  NOT_EQUAL("!=");

  fun test(value: Double, threshold: Double): Boolean = !value.isNaN() && when (this) {
    GREATER_THAN -> value > threshold
    GREATER_OR_EQUAL -> value >= threshold
    LESS_THAN -> value < threshold
    LESS_OR_EQUAL -> value <= threshold
    EQUAL -> value == threshold
    NOT_EQUAL -> value != threshold
  }
}

/**
 * Which statistic of a series is compared to the threshold.
 *
 * [VALUE] is the series value (counter total, gauge value, or distribution sum).
 * The others are only valid for [DistributionType] metrics.
 */
enum class AlertStatistic { VALUE, COUNT, MEAN, MIN, MAX }

/**
 * Definition of an alert.
 *
 * Note that counters are cumulative, so a counter policy alerts on the running total,
 * not on a rate.
 *
 * @property id stable, unique identifier; re-putting a policy with the same id replaces it
 * @property filter only series containing all of these attribute values are evaluated;
 *                  keys must be declared, low-cardinality attributes of [metric]
 * @property channels opaque routing hints for the notifications backend (e.g. "ops-email", "slack:#alerts")
 * @property enabled disabled policies are kept but not evaluated; their active alerts resolve
 */
data class AlertPolicy(
  val id: String,
  val metric: MetricType,
  val comparison: Comparison,
  val threshold: Double,
  val filter: Attributes = Attributes.EMPTY,
  val statistic: AlertStatistic = AlertStatistic.VALUE,
  val severity: AlertSeverity = AlertSeverity.WARNING,
  val description: String = "",
  val channels: Set<String> = emptySet(),
  val enabled: Boolean = true,
) {
  init {
    require(ID_REGEX.matches(id)) { "Invalid alert policy id: $id" }
    require(!threshold.isNaN()) { "Alert threshold must not be NaN (policy $id)" }
    val undeclared = filter.keys - metric.attributes
    require(undeclared.isEmpty()) { "Filter attributes $undeclared are not declared by metric $metric (policy $id)" }
    require(filter.keys.none { it.highCardinality }) { "Filter must not use high-cardinality attributes (policy $id)" }
    require(statistic == AlertStatistic.VALUE || metric is DistributionType) {
      "Statistic $statistic requires a distribution metric (policy $id)"
    }
  }

  /** True if a series with [attributes] is covered by this policy. */
  fun matches(attributes: Attributes): Boolean = attributes.containsAll(filter)

  /** True if [value] breaches the threshold. */
  fun isBreached(value: Double): Boolean = comparison.test(value, threshold)

  companion object {
    val ID_REGEX = Regex("[A-Za-z0-9][A-Za-z0-9._:-]*")
  }
}

/**
 * One alert instance: a policy breached by a specific series.
 *
 * @property value statistic value at [lastEvaluatedAt]
 * @property triggeredAt when the alert started firing
 * @property resolvedAt when the alert resolved; null while firing
 */
data class Alert(
  val policy: AlertPolicy,
  val attributes: Attributes,
  val state: AlertState,
  val value: Double,
  val triggeredAt: Instant,
  val lastEvaluatedAt: Instant = triggeredAt,
  val resolvedAt: Instant? = null,
) {
  /** Stable identity of the (policy, series) pair. */
  val id: String get() = if (attributes.isEmpty()) policy.id else "${policy.id}$attributes"

  /** Human-readable one-line summary. */
  val message: String
    get() = "[${policy.severity}] ${policy.id} $state: ${policy.metric}" +
        (if (attributes.isEmpty()) "" else " $attributes") +
        " ${policy.statistic.name.lowercase()}=$value (threshold ${policy.comparison.symbol} ${policy.threshold})"
}