package com.simiacryptus.cognotik.platform.service

import com.simiacryptus.cognotik.platform.model.AIModel
import com.simiacryptus.cognotik.platform.model.Attributes
import com.simiacryptus.cognotik.platform.model.Credits
import com.simiacryptus.cognotik.platform.model.EventType
import com.simiacryptus.cognotik.platform.model.MetricAttribute
import com.simiacryptus.cognotik.platform.model.MetricType
import com.simiacryptus.cognotik.platform.model.ModelSchema
import com.simiacryptus.cognotik.platform.model.Outcomes
import com.simiacryptus.cognotik.platform.model.PaymentType
import com.simiacryptus.cognotik.platform.model.ServiceStatus
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.TransferDirection
import com.simiacryptus.cognotik.platform.model.User
import java.time.Duration

/*
 * Domain helpers covering the dashboard checklist. They only compose the typed
 * vocabulary in Metrics.kt, so backends never need to know about them.
 */

// ---------------- Token spend ----------------

/** Records token counts (per token type) and cost for one model invocation. */
fun MetricsInterface.recordTokenUsage(
  model: AIModel,
  usage: ModelSchema.Usage,
  app: String? = null,
) {
  val base = Attributes.of(
    MetricAttribute.MODEL(model.modelId ?: "unknown"),
    model.provider?.let { MetricAttribute.PROVIDER(it.name) },
    app?.let { MetricAttribute.APP(it) },
  )
  usage.counts.forEach { (type, count) ->
    if (count > 0) increment(MetricType.TOKENS_USED, count.toDouble(), base + MetricAttribute.TOKEN_TYPE(type))
  }
  if (usage.cost > 0) increment(MetricType.TOKEN_SPEND, usage.cost, base)
}

// ---------------- Input cash / credits ----------------

/** Records cash received. [amount] is in currency units (e.g. dollars). */
fun MetricsInterface.recordPayment(
  paymentType: PaymentType,
  amount: Double,
  currency: String = "USD",
  user: User? = null,
) {
  val attrs = Attributes.of(
    MetricAttribute.PAYMENT_TYPE(paymentType),
    MetricAttribute.CURRENCY(currency),
    user?.let { MetricAttribute.USER(it.id) },
  )
  increment(MetricType.INPUT_CASH, amount, attrs)
  event(EventType.PAYMENT_RECEIVED, attrs)
}

/** Records credits granted to a user (purchase, gift, adjustment). */
fun MetricsInterface.recordCreditsGranted(source: PaymentType, amount: Credits, user: User? = null) {
  val attrs = Attributes.of(MetricAttribute.PAYMENT_TYPE(source), user?.let { MetricAttribute.USER(it.id) })
  if (amount.isPositive) increment(MetricType.CREDITS_GRANTED, amount.toDouble(), attrs)
  event(EventType.CREDITS_GRANTED, attrs)
}

/** Sets the platform-wide banked (outstanding) credit total. */
fun MetricsInterface.setBankedCredits(total: Credits) =
  gauge(MetricType.CREDITS_BANKED, total.toDouble())

// ---------------- Activity: apps ----------------

fun MetricsInterface.appStarted(app: String, session: Session? = null, user: User? = null, worker: String? = null) =
  event(
    EventType.APP_STARTED, Attributes.of(
      MetricAttribute.APP(app),
      MetricAttribute.OUTCOME(Outcomes.STARTED),
      session?.let { MetricAttribute.SESSION(it.sessionId) },
      user?.let { MetricAttribute.USER(it.id) },
      worker?.let { MetricAttribute.WORKER(it) },
    )
  )

fun MetricsInterface.appCompleted(
  app: String,
  outcome: String = Outcomes.SUCCESS,
  duration: Duration? = null,
  session: Session? = null,
  user: User? = null,
) {
  val attrs = Attributes.of(
    MetricAttribute.APP(app),
    MetricAttribute.OUTCOME(outcome),
    session?.let { MetricAttribute.SESSION(it.sessionId) },
    user?.let { MetricAttribute.USER(it.id) },
  )
  duration?.let { record(MetricType.APP_SESSION_DURATION, it.toMillis().toDouble(), attrs) }
  event(EventType.APP_COMPLETED, attrs)
}

// ---------------- Activity: file transfer ----------------

fun MetricsInterface.recordFileTransfer(
  direction: TransferDirection,
  bytes: Long,
  duration: Duration? = null,
  outcome: String = Outcomes.SUCCESS,
  session: Session? = null,
  user: User? = null,
) {
  val attrs = Attributes.of(
    MetricAttribute.DIRECTION(direction),
    MetricAttribute.OUTCOME(outcome),
    session?.let { MetricAttribute.SESSION(it.sessionId) },
    user?.let { MetricAttribute.USER(it.id) },
  )
  if (bytes > 0) increment(MetricType.FILE_TRANSFER_BYTES, bytes.toDouble(), attrs)
  duration?.let { record(MetricType.FILE_TRANSFER_DURATION, it.toMillis().toDouble(), attrs) }
  event(EventType.FILE_TRANSFERRED, attrs)
}

// ---------------- Activity: Fargate nodes ----------------

fun MetricsInterface.setFargateNodes(cluster: String, service: String, count: Int) =
  gauge(
    MetricType.FARGATE_NODES, count.toDouble(),
    Attributes.of(MetricAttribute.CLUSTER(cluster), MetricAttribute.SERVICE(service))
  )

fun MetricsInterface.fargateNodeStarted(cluster: String, service: String, worker: String? = null) =
  event(
    EventType.FARGATE_NODE_STARTED, Attributes.of(
      MetricAttribute.CLUSTER(cluster), MetricAttribute.SERVICE(service),
      MetricAttribute.OUTCOME(Outcomes.STARTED), worker?.let { MetricAttribute.WORKER(it) },
    )
  )

fun MetricsInterface.fargateNodeStopped(
  cluster: String,
  service: String,
  worker: String? = null,
  outcome: String = Outcomes.SUCCESS,
) = event(
  EventType.FARGATE_NODE_STOPPED, Attributes.of(
    MetricAttribute.CLUSTER(cluster), MetricAttribute.SERVICE(service),
    MetricAttribute.OUTCOME(outcome), worker?.let { MetricAttribute.WORKER(it) },
  )
)

// ---------------- Activity: ECS service status ----------------

/**
 * Reports an ECS service's task counts and status. The status gauge is emitted as
 * 1 for [status] and 0 for every other [knownStatuses] value, so dashboards can
 * graph status as a stacked series.
 *
 * @param previousStatus when supplied and different from [status], a status-change event is emitted
 */
fun MetricsInterface.reportEcsService(
  cluster: String,
  service: String,
  status: ServiceStatus,
  runningTasks: Int,
  desiredTasks: Int,
  previousStatus: ServiceStatus? = null,
  knownStatuses: Collection<ServiceStatus> = listOf(
    ServiceStatus.ACTIVE, ServiceStatus.DRAINING, ServiceStatus.INACTIVE, ServiceStatus.DEGRADED,
  ),
) {
  val base = Attributes.of(MetricAttribute.CLUSTER(cluster), MetricAttribute.SERVICE(service))
  gauge(MetricType.ECS_SERVICE_RUNNING_TASKS, runningTasks.toDouble(), base)
  gauge(MetricType.ECS_SERVICE_DESIRED_TASKS, desiredTasks.toDouble(), base)
  (knownStatuses + status).distinct().forEach { s ->
    gauge(MetricType.ECS_SERVICE_STATUS, if (s == status) 1.0 else 0.0, base + MetricAttribute.STATUS(s))
  }
  if (previousStatus != null && previousStatus != status) {
    event(EventType.ECS_SERVICE_STATUS_CHANGED, base + MetricAttribute.STATUS(status))
  }
}