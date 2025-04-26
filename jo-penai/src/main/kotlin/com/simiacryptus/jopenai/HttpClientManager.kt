package com.simiacryptus.jopenai

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.simiacryptus.jopenai.exceptions.*
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager
import org.apache.hc.core5.http.HttpHeaders
import org.apache.hc.core5.http.io.SocketConfig
import org.apache.hc.core5.util.Timeout
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.io.IOException
import java.time.Duration
import java.util.*
import java.util.concurrent.*
import java.util.function.Function
import kotlin.math.pow

open class HttpClientManager(
    private val logLevel: Level = Level.INFO,
    val logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    private val workPool: ExecutorService,
) : API() {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(HttpClientManager::class.java)

        val scheduledPool: ListeningScheduledExecutorService =
            MoreExecutors.listeningDecorator(
                ScheduledThreadPoolExecutor(
                    0,
                    ThreadFactoryBuilder().setNameFormat("API Scheduler %d").build()
                )
            )

        private const val DEFAULT_USER_AGENT = "Cognotik/1.0"
        val client by lazy { createHttpClient(DEFAULT_USER_AGENT) }
        fun createHttpClient(userAgent: String = DEFAULT_USER_AGENT): CloseableHttpClient = HttpClientBuilder.create()
            .setRetryStrategy(DefaultHttpRequestRetryStrategy(0, Timeout.ofSeconds(1)))
            .setConnectionManager(with(PoolingHttpClientConnectionManager()) {
                defaultSocketConfig = with(SocketConfig.custom()) {
                    setSoTimeout(Timeout.ofSeconds(600))
                    setSoReuseAddress(true)
                    setSoLinger(Timeout.ofSeconds(10))
                    setDefaultConnectionConfig(ConnectionConfig.custom().apply {
                        setConnectTimeout(Timeout.ofSeconds(600))
                        setSocketTimeout(Timeout.ofSeconds(600))
                        setSoKeepAlive(true)
                        setTimeToLive(Timeout.ofSeconds(600))
                    }.build())
                    setSoKeepAlive(true)
                    build()
                }
                defaultMaxPerRoute = 16
                maxTotal = 16
                this
            })
            .setUserAgent(userAgent)
            .setDefaultHeaders(listOf(org.apache.hc.core5.http.message.BasicHeader(HttpHeaders.USER_AGENT, userAgent)))
            .build()

        val startTime by lazy { System.currentTimeMillis() }

        fun modelMaxException(e: Throwable?): ModelMaxException? = when {
            e == null -> null
            e is ModelMaxException -> e
            e.cause != null && e.cause != e -> modelMaxException(e.cause)
            else -> null
        }

        fun rateLimitException(e: Throwable?): RateLimitException? = when {
            e == null -> null
            e is RateLimitException -> e
            e.cause != null && e.cause != e -> rateLimitException(e.cause)
            else -> null
        }

        fun quotaLimitException(e: Throwable?): QuotaException? = when {
            e == null -> null
            e is QuotaException -> e
            e.cause != null && e.cause != e -> quotaLimitException(e.cause)
            else -> null
        }

        fun invalidModelException(e: Throwable?): InvalidModelException? = when {
            e == null -> null
            e is InvalidModelException -> e
            e.cause != null && e.cause != e -> invalidModelException(e.cause)
            else -> null
        }

        fun apiKeyException(e: Throwable?): IOException? = when {
            e == null -> null
            e is IOException && true == e.message?.contains("Incorrect API key") -> e
            e.cause != null && e.cause != e -> apiKeyException(e.cause)
            else -> null
        }

    }

    protected fun captureCallerStack(): String {
        // Skip the frames in withPool and this utility
        var stack = Throwable().stackTrace
            .dropWhile { it.methodName == "withPool" || it.className.contains("HttpClientManager") }
            .joinToString("\n") { "\tat $it" }
        if (stackCalls.containsKey(Thread.currentThread())) {
            stack += "\n\tPrevious stack:\n${stackCalls[Thread.currentThread()]}"
        }
        return stack
    }

    val stackCalls: MutableMap<Thread, String> = ConcurrentHashMap()

    private fun <T> withPool(fn: () -> T): T {
        val callerStack = captureCallerStack()  // capture caller stack before switching threads
        val future = workPool.submit(Callable {
            stackCalls[Thread.currentThread()] = callerStack
            return@Callable fn()
        })

        fun handleException(future: Future<*>, e: Throwable, callerStack: String): Nothing {
            future.cancel(true)
            when (e) {
                is InterruptedException -> {
                    log(Level.INFO, "InterruptedException in withPool. Caller stack:\n$callerStack")
                    throw e
                }

                is ExecutionException -> {
                    log(Level.WARN, "ExecutionException in withPool. Caller stack:\n$callerStack")
                    handleException(future, e.cause ?: throw e, callerStack)
                }

                is CancellationException -> {
                    log(Level.INFO, "CancellationException in withPool. Caller stack:\n$callerStack")
                    throw e
                }

                is TimeoutException -> {
                    log(Level.WARN, "TimeoutException in withPool. Caller stack:\n$callerStack")
                    throw e
                }

                else -> {
                    log(Level.WARN, "Exception in withPool. Caller stack:\n$callerStack\n${e.message}")
                    throw e
                }
            }
        }
        return try {
            future.get()
        } catch (e: Exception) {
            handleException(future, e, callerStack)
        }
    }


    private fun <T> withExpBackoffRetry(
        retryCount: Int,
        sleepScale: Long = TimeUnit.SECONDS.toMillis(5),
        fn: () -> T
    ): T {
        var lastException: Throwable? = null
        var i = 0
        while (i++ <= retryCount) {
            val sleepPeriod = sleepScale * 2.0.pow(i.toDouble()).toLong()
            try {
                return fn()
            } catch (e: Throwable) {
                val exception = unwrapException(e)
                throwIfNonrecoverable(exception, sleepPeriod)
                this.log(Level.DEBUG, "Request failed; retrying ($i/$retryCount): " + exception.message)
                Thread.sleep(sleepPeriod)
                lastException = exception
            }
        }
        throw lastException!!
    }

    open fun throwIfNonrecoverable(exception: Throwable, sleepPeriod: Long) {
        when (exception) {
            is RateLimitException -> Thread.sleep((TimeUnit.SECONDS.toMillis(exception.delay)).coerceAtLeast(sleepPeriod))
            is AIServiceException -> if (exception.isFatal) throw exception
            is Exception -> return
            else -> throw exception
        }
    }

    protected open fun unwrapException(e: Throwable): Throwable {
        val modelMaxException = modelMaxException(e)
        if (null != modelMaxException) return modelMaxException
        val rateLimitException = rateLimitException(e)
        if (null != rateLimitException) return rateLimitException
        val apiKeyException = apiKeyException(e)
        if (null != apiKeyException) return apiKeyException
        val quotaException = quotaLimitException(e)
        if (null != quotaException) return quotaException
        val invalidModelException = invalidModelException(e)
        if (null != invalidModelException) return invalidModelException
        return e
    }


    private fun <T> withTimeout(duration: Duration, fn: () -> T): T {
        var thread = Thread.currentThread()
        val start = Date()
        val cancellationFuture = scheduledPool.schedule({
            log(
                Level.WARN,
                "Request timed out after $duration at ${Date()} (started $start); closing client for thread $thread"
            )
            thread.interrupt()
        }, duration.toMillis(), TimeUnit.MILLISECONDS)
        try {
            return withPool { fn() }
        } finally {
            cancellationFuture.cancel(false)
        }
    }

    fun <T> withReliability(
        requestTimeoutSeconds: Long = (5 * 60),
        retryCount: Int = 0,
        fn: () -> T
    ): T =
        withExpBackoffRetry(retryCount) { withTimeout(Duration.ofSeconds(requestTimeoutSeconds), fn) }

    fun <T> withPerformanceLogging(fn: () -> T): T {
        val start = Date()
        try {
            return fn()
        } finally {
            log(Level.DEBUG, "Request completed in ${Date().time - start.time}ms")
        }
    }

    fun <T> withClient(fn: Function<CloseableHttpClient, T>): T = fn.apply(client)

    protected open fun log(level: Level = logLevel, msg: String) {
        val message = msg.trim().lineSequence()
            .map {
                when {
                    it.isBlank() -> {
                        when {
                            it.length < "\t".length -> "\t"
                            else -> it
                        }
                    }

                    else -> "\t" + it
                }
            }
            .joinToString("\n")
        logStreams.forEach { stream ->
            stream.write(
                "[$level] [${"%.3f".format((System.currentTimeMillis() - startTime) / 1000.0)}] ${
                    message.replace(
                        "\n",
                        "\n\t"
                    )
                }\n".toByteArray()
            )
            stream.flush()
        }
        when (level) {
            Level.ERROR -> log.error(message)
            Level.WARN -> log.warn(message)
            Level.INFO -> log.info(message)
            Level.DEBUG -> log.debug(message)
            Level.TRACE -> log.trace(message)
            else -> log.debug(message)
        }
    }

}