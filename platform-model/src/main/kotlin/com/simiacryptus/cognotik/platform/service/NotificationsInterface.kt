package com.simiacryptus.cognotik.platform.service

import com.simiacryptus.cognotik.platform.ServiceKey

import com.simiacryptus.cognotik.platform.model.Alert
import com.simiacryptus.cognotik.platform.model.AlertSeverity
import com.simiacryptus.cognotik.platform.model.AlertState
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Delivery port for alert notifications (email, Slack, SNS, PagerDuty, ...).
 *
 * Metrics backends call [notifyAlert] on every alert state transition: once when an
 * alert starts FIRING and once when it is RESOLVED. Registered via
 * [ServiceKey.NOTIFICATIONS]; backends should obtain it via [resolve].
 *
 * Contract:
 * - Thread-safe, and SHOULD NOT throw: callers are on the metrics recording path.
 *   Callers still guard against exceptions.
 * - Should be cheap; slow deliveries (HTTP, email) should be queued asynchronously.
 * - [Alert.policy] `channels` are routing hints; how they map to destinations is up to
 *   the implementation.
 */
interface NotificationsInterface {

  /** Delivers an alert transition. [Alert.state] tells FIRING from RESOLVED. */
  fun notifyAlert(alert: Alert)

}

/** Discards all notifications. */
object NoOpNotifications : NotificationsInterface {
  override fun notifyAlert(alert: Alert) {}
}

/** Writes notifications to the application log; the fallback when nothing is registered. */
object LoggingNotifications : NotificationsInterface {
  private val log = LoggerFactory.getLogger(LoggingNotifications::class.java)

  override fun notifyAlert(alert: Alert) {
    when {
      alert.state == AlertState.RESOLVED -> log.info(alert.message)
      alert.policy.severity == AlertSeverity.INFO -> log.info(alert.message)
      alert.policy.severity == AlertSeverity.WARNING -> log.warn(alert.message)
      else -> log.error(alert.message)
    }
  }
}

/** Fans out to several notification backends; a failure in one does not affect the others. */
class CompositeNotifications(private val delegates: List<NotificationsInterface>) : NotificationsInterface {

  constructor(vararg delegates: NotificationsInterface) : this(delegates.toList())

  override fun notifyAlert(alert: Alert) {
    delegates.forEach {
      try {
        it.notifyAlert(alert)
      } catch (e: Exception) {
        log.warn("Notifications backend ${it.javaClass.simpleName} failed for alert ${alert.id}", e)
      }
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(CompositeNotifications::class.java)
  }
}

/** Records notifications in memory; for tests and local development. */
class InMemoryNotifications : NotificationsInterface {
  private val received = CopyOnWriteArrayList<Alert>()

  override fun notifyAlert(alert: Alert) {
    received += alert
  }

  /** All notifications received, oldest first, optionally filtered by state. */
  fun alerts(state: AlertState? = null): List<Alert> = received.filter { state == null || it.state == state }

  fun reset() = received.clear()
}