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
import org.slf4j.LoggerFactory
import java.time.Duration

/*
 * Domain helpers covering the dashboard checklist. They only compose the typed
 * vocabulary in Metrics.kt, so backends never need to know about them.
 */
private val extLog = LoggerFactory.getLogger("com.simiacryptus.cognotik.platform.service.MetricsExtensions")
/** Runs [block], logging and swallowing any non-fatal throwable (including LinkageErrors). */
internal inline fun guardMetrics(op: String, block: () -> Unit) {
   try {
     block()
   } catch (e: VirtualMachineError) {
     throw e
   } catch (e: Throwable) {
     extLog.warn("Metrics recording failed: $op", e)
   }
}


// ---------------- Token spend ----------------

/**
  * Records token counts (per token type) and cost for one model invocation.
  *
  * Cost is computed with [AIModel.pricing]. If pricing is unavailable (non-positive,
  * non-finite, or throws), it falls back to [ModelSchema.Usage.cost].
   * Token counts and cost are recorded independently: a failure computing or recording
   * the cost never prevents token counts from being reported (and vice versa).
  */
fun MetricsInterface.recordTokenUsage(
  model: AIModel,
  usage: ModelSchema.Usage,
  app: String? = null,
) {
   val modelId = runCatching { model.modelId }.getOrNull() ?: "unknown"
   val providerName = runCatching { model.provider?.name }.getOrNull()
   val base = Attributes.of(
     MetricAttribute.MODEL(modelId),
     providerName?.let { MetricAttribute.PROVIDER(it) },
     app?.let { MetricAttribute.APP(it) },
   )
   // Token counts first, each type isolated so one bad entry cannot drop the others.
   guardMetrics("recordTokenUsage(tokens, $modelId)") {
     usage.counts.forEach { (type, count) ->
       guardMetrics("recordTokenUsage(tokens, $modelId, $type)") {
         if (count > 0) increment(MetricType.TOKENS_USED, count.toDouble(), base + MetricAttribute.TOKEN_TYPE(type))
       }
     }
   }
   // Cost in its own failure domain.
   guardMetrics("recordTokenUsage(cost, $modelId)") {
     val cost = usageCost(model, usage)
     if (cost > 0) increment(MetricType.TOKEN_SPEND, cost, base)
   }
}
/** Cost of [usage] per [AIModel.pricing], falling back to [ModelSchema.Usage.cost]. Never throws. */
internal fun usageCost(model: AIModel, usage: ModelSchema.Usage): Double {
   val priced = try {
     model.pricing(usage)
   } catch (e: VirtualMachineError) {
     throw e
   } catch (e: Throwable) {
     null
   }
   if (priced != null && priced.isFinite() && priced > 0) return priced
   val reported = try {
     usage.cost
   } catch (e: VirtualMachineError) {
     throw e
   } catch (e: Throwable) {
     null
   }
   return if (reported != null && reported.isFinite() && reported > 0) reported else 0.0
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
// ---------------- Authentication ----------------
/**
* Records a login attempt. Use [Outcomes.STARTED] when an interactive flow is initiated,
* [Outcomes.SUCCESS] when a session is issued and [Outcomes.FAILURE] (with a bounded
* [reason] from `AuthReasons`) otherwise.
*
* @param duration time since the interactive flow was initiated, if known
*/
fun MetricsInterface.recordLogin(
  method: String,
  outcome: String,
  reason: String? = null,
  user: User? = null,
  duration: Duration? = null,
) {
  val attrs = Attributes.of(
    MetricAttribute.LOGIN_METHOD(method),
    MetricAttribute.OUTCOME(outcome),
    reason?.let { MetricAttribute.REASON(it) },
    user?.let { MetricAttribute.USER(it.id) },
  )
  duration?.let { record(MetricType.AUTH_FLOW_DURATION, it.toMillis().toDouble(), attrs) }
  event(EventType.LOGIN_ATTEMPTED, attrs)
}
/** Records a logout request. */
fun MetricsInterface.recordLogout(outcome: String, reason: String? = null, user: User? = null) =
  event(
    EventType.LOGGED_OUT, Attributes.of(
      MetricAttribute.OUTCOME(outcome),
      reason?.let { MetricAttribute.REASON(it) },
      user?.let { MetricAttribute.USER(it.id) },
    )
  )
/** Records a local account registration attempt. */
fun MetricsInterface.recordRegistration(outcome: String, reason: String? = null, user: User? = null) =
  event(
    EventType.USER_REGISTERED, Attributes.of(
      MetricAttribute.OUTCOME(outcome),
      reason?.let { MetricAttribute.REASON(it) },
      user?.let { MetricAttribute.USER(it.id) },
    )
  )
/** Records a session token verification (hot path: counter only, no event). */
fun MetricsInterface.recordSessionVerification(outcome: String, reason: String? = null) =
  increment(
    MetricType.AUTH_SESSION_VERIFICATIONS, 1.0, Attributes.of(
      MetricAttribute.OUTCOME(outcome),
      reason?.let { MetricAttribute.REASON(it) },
    )
  )
/** Records an OAuth callback received by the callback servlet. */
fun MetricsInterface.recordAuthCallback(method: String, outcome: String, reason: String? = null) =
  increment(
    MetricType.AUTH_CALLBACKS, 1.0, Attributes.of(
      MetricAttribute.LOGIN_METHOD(method),
      MetricAttribute.OUTCOME(outcome),
      reason?.let { MetricAttribute.REASON(it) },
    )
  )