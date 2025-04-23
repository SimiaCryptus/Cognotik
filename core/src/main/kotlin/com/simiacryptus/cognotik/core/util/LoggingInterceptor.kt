package com.simiacryptus.cognotik.core.util

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import org.slf4j.LoggerFactory

@Suppress("unused")
class LoggingInterceptor(
  private val stringBuffer: StringBuffer = StringBuffer(),
) : AppenderBase<ILoggingEvent>() {
  companion object {
    fun <T : Any> withIntercept(
      stringBuffer: StringBuffer = StringBuffer(),
      vararg loggerPrefixes: String,
      fn: () -> T,
    ): T {
      val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
      val loggers = loggerPrefixes.flatMap { loggerPrefix ->
        loggerContext.loggerList.filter { it.name.startsWith(loggerPrefix) }
      }
      return withIntercept(
        stringBuffer = stringBuffer,
        loggers = loggers.toTypedArray(),
        fn = fn
      )
    }

    private fun <T : Any> withIntercept(
      stringBuffer: StringBuffer,
      vararg loggers: Logger,
      fn: () -> T,
    ): T {
      // Save the original log level and appender list
      val originalLevels = loggers.map { it.level }
      val originalAppenders = loggers.map { it.iteratorForAppenders().asSequence().toList() }

      // Create and attach the custom StringBufferAppender
      val stringBufferAppender = LoggingInterceptor(stringBuffer)
      stringBufferAppender.context = LoggerFactory.getILoggerFactory() as LoggerContext
      stringBufferAppender.start()
      loggers.forEach { it.detachAndStopAllAppenders() }
      loggers.forEach { it.addAppender(stringBufferAppender) }

      try {
        return fn()
      } finally {
        // Restore the original log level and appender list
        loggers.zip(originalLevels.zip(originalAppenders)).forEach { (jsEngineLogger, t) ->
          val (originalLevel, originalAppender) = t
          jsEngineLogger.level = originalLevel
          jsEngineLogger.detachAndStopAllAppenders()
          originalAppender.forEach { jsEngineLogger.addAppender(it) }
        }
      }
    }
  }

  override fun addInfo(msg: String?, ex: Throwable?) {
    super.addInfo(msg, ex)
  }

  override fun append(event: ILoggingEvent) {
    stringBuffer.append(event.formattedMessage)
    event.throwableProxy?.let {
      stringBuffer.append(System.lineSeparator())
      stringBuffer.append(ch.qos.logback.classic.pattern.ThrowableProxyConverter().convert(event))
    }
    stringBuffer.append(System.lineSeparator())
  }

  fun getStringBuffer(): StringBuffer = stringBuffer

}
