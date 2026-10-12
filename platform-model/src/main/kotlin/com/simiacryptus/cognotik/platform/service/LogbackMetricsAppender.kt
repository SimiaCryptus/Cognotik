package com.simiacryptus.cognotik.platform.service

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.AppenderBase
import com.simiacryptus.cognotik.platform.ServiceRouter
import com.simiacryptus.cognotik.platform.model.Attributes
import com.simiacryptus.cognotik.platform.model.MetricAttribute
import com.simiacryptus.cognotik.platform.model.MetricType
import org.slf4j.LoggerFactory

/**
 * Logback appender that monitors SLF4J logging events and reports message counts and byte counts to the metrics service.
 *
 * Programmatic installation:
 * ```
 * val handle = LogbackMetricsAppender.install()
 * // ... later
 * handle.close()
 * ```
 *
 * Or declare it in `logback.xml` :
 * ```
 * <appender name="METRICS" class="com.simiacryptus.cognotik.platform.service.LogbackMetricsAppender">
 *   <includeStackTraces>true</includeStackTraces>
 * </appender>
 * <root level="INFO"><appender-ref ref="METRICS"/></root>
 * ```
 *
 * Only events that pass the logger's level threshold reach the appender, so the counts
 * reflect what is actually logged.
 */
class LogbackMetricsAppender(
) : AppenderBase<ILoggingEvent>() {

  /** If true, rendered stack traces are included in the byte count. Configurable from logback.xml. */
  var includeStackTraces: Boolean = true
  fun utf8Length(s: CharSequence?): Long {
    if (s == null) return 0
    var bytes = 0L
    var i = 0
    val n = s.length
    while (i < n) {
      val c = s[i]
      when {
        c.code < 0x80 -> bytes += 1
        c.code < 0x800 -> bytes += 2
        Character.isHighSurrogate(c) && i + 1 < n && Character.isLowSurrogate(s[i + 1]) -> {
          bytes += 4
          i++
        }
        else -> bytes += 3
      }
      i++
    }
    return bytes
  }

  override fun append(event: ILoggingEvent) {
    val loggerName = event.loggerName
    val level = event.level.levelStr
    val bytes = {
      var bytes = utf8Length(event.formattedMessage)
      if (includeStackTraces) {
        event.throwableProxy?.let { bytes += utf8Length(ThrowableProxyUtil.asString(it)) }
      }
      bytes
    }
    try {
      val name = loggerName ?: "unknown"
      val attrs = Attributes.of(
        MetricAttribute.LOG_CLASS(name),
        MetricAttribute.LOG_LEVEL(level.lowercase()),
      )
      val size = bytes()
      ServiceRouter.increment(MetricType.LOG_MESSAGES, 1.0, attrs)
      if (size > 0) ServiceRouter.increment(MetricType.LOG_BYTES, size.toDouble(), attrs)
    } catch (e: VirtualMachineError) {
      throw e
    } catch (_: Throwable) {
      // Swallowed deliberately: logging about a logging-metrics failure would recurse.
    }

  }

  companion object {

    /**
     * Attaches a metrics appender to [loggerName] (the root logger by default).
     *
     * @return a handle that detaches and stops the appender when closed; a no-op handle
     *         if SLF4J is not bound to Logback
     */
    @JvmStatic
    @JvmOverloads
    fun install(
      loggerName: String = Logger.ROOT_LOGGER_NAME,
      includeStackTraces: Boolean = true,
    ): AutoCloseable {
      val context = LoggerFactory.getILoggerFactory() as? LoggerContext
      if (context == null) {
        LoggerFactory.getLogger(LogbackMetricsAppender::class.java)
          .warn("SLF4J is not bound to Logback; log metrics are disabled")
        return AutoCloseable {}
      }
      val appender = LogbackMetricsAppender().apply {
        this.context = context
        this.name = "COGNOTIK_LOG_METRICS"
        this.includeStackTraces = includeStackTraces
        start()
      }
      val logger = context.getLogger(loggerName)
      logger.addAppender(appender)
      return AutoCloseable {
        logger.detachAppender(appender)
        appender.stop()
      }
    }
  }
}