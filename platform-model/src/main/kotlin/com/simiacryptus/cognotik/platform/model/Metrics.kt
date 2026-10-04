package com.simiacryptus.cognotik.platform.model

/*
 * Typed, open-ended vocabulary for the platform metrics service.
 *
 * Nothing here is a closed enum: every "kind of thing" is either a marker
 * interface (so a backend can dispatch on it) or an open value type whose
 * well-known values live as constants in its companion. Modules extend the
 * vocabulary by declaring their own constants, e.g.
 *
 *   val MY_COUNTER = CounterType.of("myplugin.widgets", MetricUnit.COUNT, "Widgets made")
 */

/** Unit of measure. Names follow the CloudWatch unit vocabulary where one exists. */
data class MetricUnit(val name: String) {
  override fun toString() = name

  companion object {
    val NONE = MetricUnit("None")
    val COUNT = MetricUnit("Count")
    val BYTES = MetricUnit("Bytes")
    val MILLISECONDS = MetricUnit("Milliseconds")
    val SECONDS = MetricUnit("Seconds")
    val PERCENT = MetricUnit("Percent")
    val TOKENS = MetricUnit("Tokens")
    val CREDITS = MetricUnit("Credits")
    val USD = MetricUnit("USD")
  }
}

/**
 * Typed attribute (dimension / label) key.
 *
 * Identity is [name]. Attributes marked [highCardinality] (user ids, session ids)
 * may be dropped by backends that bill per series (CloudWatch) or degrade with
 * many series (Prometheus); prefer recording them on events only.
 *
 * @property name snake_case name, valid as both a CloudWatch dimension and a Prometheus label
 * @property type runtime value type (boxed)
 */
class MetricAttribute<T : Any>(
  val name: String,
  val type: Class<T>,
  val highCardinality: Boolean = false,
  private val renderer: (T) -> String = { it.toString() },
) {
  init {
    require(NAME_REGEX.matches(name)) { "Invalid metric attribute name: $name" }
  }

  /** Renders a value for transmission to a backend. */
  fun render(value: T): String = renderer(value)

  /** Binds a value: `MetricAttribute.MODEL("gpt-4o")`. */
  operator fun invoke(value: T): AttributeValue<T> = AttributeValue(this, value)

  override fun equals(other: Any?) = other is MetricAttribute<*> && other.name == name
  override fun hashCode() = name.hashCode()
  override fun toString() = name

  companion object {
    val NAME_REGEX = Regex("[a-z][a-z0-9_]*")

    inline fun <reified T : Any> of(
      name: String,
      highCardinality: Boolean = false,
    ): MetricAttribute<T> = MetricAttribute(name, T::class.javaObjectType, highCardinality)

    // --- AI usage ---
    val MODEL = of<String>("model")
    val PROVIDER = of<String>("provider")
    val TOKEN_TYPE = of<ModelSchema.TokenTypes>("token_type")

    // --- Money ---
    val PAYMENT_TYPE = of<PaymentType>("payment_type")
    val CURRENCY = of<String>("currency")

    // --- Activity ---
    val APP = of<String>("app")
    val OUTCOME = of<String>("outcome")
    val DIRECTION = of<TransferDirection>("direction")

    // --- Infrastructure ---
    val CLUSTER = of<String>("cluster")
    val SERVICE = of<String>("service")
    val STATUS = of<ServiceStatus>("status")
    val WORKER = of<String>("worker")
     // --- Errors ---
     val ERROR_TYPE = of<String>("error_type")
     val FATAL = of<Boolean>("fatal")


    // --- High-cardinality identifiers (events only, by convention) ---
    val USER = of<String>("user", highCardinality = true)
    val SESSION = of<String>("session", highCardinality = true)
  }
}

/** A bound attribute value. */
data class AttributeValue<T : Any>(val key: MetricAttribute<T>, val value: T) {
  val rendered: String get() = key.render(value)
}

/** Immutable, typed attribute set. */
class Attributes private constructor(
  private val values: Map<MetricAttribute<*>, AttributeValue<*>>,
) {
  val keys: Set<MetricAttribute<*>> get() = values.keys
  val entries: Collection<AttributeValue<*>> get() = values.values

  fun isEmpty() = values.isEmpty()

  operator fun <T : Any> get(key: MetricAttribute<T>): T? =
    values[key]?.value?.let { key.type.cast(it) }

  operator fun plus(value: AttributeValue<*>?): Attributes =
    if (value == null) this else Attributes(values + (value.key to value))

  operator fun plus(other: Attributes): Attributes =
    if (other.isEmpty()) this else Attributes(values + other.values)

  /** Keeps only the [allowed] keys. */
  fun restrictTo(allowed: Set<MetricAttribute<*>>): Attributes =
    Attributes(values.filterKeys { it in allowed })

  /** Removes attributes flagged as high-cardinality. */
  fun withoutHighCardinality(): Attributes =
    Attributes(values.filterKeys { !it.highCardinality })
   /** True if every attribute value in [other] is also present (with an equal value) in this set. */
   fun containsAll(other: Attributes): Boolean =
     other.values.all { (k, v) -> values[k] == v }


  /** Backend-neutral string form (sorted by key for stable series identity). */
  fun asStringMap(): Map<String, String> =
    values.values.sortedBy { it.key.name }.associate { it.key.name to it.rendered }

  override fun equals(other: Any?) = other is Attributes && other.values == values
  override fun hashCode() = values.hashCode()
  override fun toString() = asStringMap().toString()

  companion object {
    val EMPTY = Attributes(emptyMap())

    /** Nulls are skipped, so optional attributes can be passed inline. */
    fun of(vararg values: AttributeValue<*>?): Attributes =
      Attributes(values.filterNotNull().associateBy { it.key })
  }
}

/**
 * Marker for a metric definition. Backends dispatch on the sub-markers
 * [CounterType], [GaugeType] and [DistributionType].
 *
 * @property name dotted name, e.g. `cognotik.tokens.spend`; Prometheus backends
 *                should map `.` to `_`
 * @property attributes the attributes this metric accepts; backends drop others
 */
interface MetricType {
  val name: String
  val unit: MetricUnit
  val description: String
  val attributes: Set<MetricAttribute<*>>

  /** Restricts [attrs] to the declared [attributes] and strips high-cardinality keys. */
  fun sanitize(attrs: Attributes): Attributes = attrs.restrictTo(attributes).withoutHighCardinality()

  companion object {
    val NAME_REGEX = Regex("[a-z][a-z0-9_]*(\\.[a-z0-9_]+)*")

    // ---------------- Token spend ----------------
    val TOKENS_USED: CounterType by lazy {
      CounterType.of(
        "cognotik.tokens.used", MetricUnit.TOKENS, "Tokens consumed by model invocations",
        MetricAttribute.MODEL, MetricAttribute.PROVIDER, MetricAttribute.TOKEN_TYPE, MetricAttribute.APP,
      )
    }
    val TOKEN_SPEND: CounterType by lazy {
      CounterType.of(
        "cognotik.tokens.spend", MetricUnit.CREDITS, "Cost of model invocations",
        MetricAttribute.MODEL, MetricAttribute.PROVIDER, MetricAttribute.APP,
      )
    }
     // ---------------- AI service errors ----------------
     val AI_ERRORS: CounterType by lazy {
       CounterType.of(
         "cognotik.ai.errors", MetricUnit.COUNT, "Errors raised by AI service calls, per error type",
         MetricAttribute.PROVIDER, MetricAttribute.MODEL, MetricAttribute.ERROR_TYPE, MetricAttribute.FATAL,
       )
     }


    // ---------------- Input cash ----------------
    val INPUT_CASH: CounterType by lazy {
      CounterType.of(
        "cognotik.cash.input", MetricUnit.USD, "Cash received, per payment type",
        MetricAttribute.PAYMENT_TYPE, MetricAttribute.CURRENCY,
      )
    }

    // ---------------- Banked credits ----------------
    val CREDITS_BANKED: GaugeType by lazy {
      GaugeType.of(
        "cognotik.credits.banked", MetricUnit.CREDITS, "Outstanding (unspent) credits across all users",
      )
    }
    val CREDITS_GRANTED: CounterType by lazy {
      CounterType.of(
        "cognotik.credits.granted", MetricUnit.CREDITS, "Credits granted (purchases, gifts, adjustments)",
        MetricAttribute.PAYMENT_TYPE,
      )
    }

    // ---------------- Activity: apps ----------------
    val APP_SESSIONS: CounterType by lazy {
      CounterType.of(
        "cognotik.apps.sessions", MetricUnit.COUNT, "App sessions started/completed",
        MetricAttribute.APP, MetricAttribute.OUTCOME,
      )
    }
    val APP_ACTIVE_SESSIONS: GaugeType by lazy {
      GaugeType.of(
        "cognotik.apps.active", MetricUnit.COUNT, "Currently active app sessions",
        MetricAttribute.APP, MetricAttribute.WORKER,
      )
    }
    val APP_SESSION_DURATION: DistributionType by lazy {
      DistributionType.of(
        "cognotik.apps.duration", MetricUnit.MILLISECONDS, "App session duration",
        MetricAttribute.APP, MetricAttribute.OUTCOME,
      )
    }

    // ---------------- Activity: file transfer ----------------
    val FILE_TRANSFERS: CounterType by lazy {
      CounterType.of(
        "cognotik.files.transfers", MetricUnit.COUNT, "File transfers",
        MetricAttribute.DIRECTION, MetricAttribute.OUTCOME,
      )
    }
    val FILE_TRANSFER_BYTES: CounterType by lazy {
      CounterType.of(
        "cognotik.files.bytes", MetricUnit.BYTES, "Bytes transferred",
        MetricAttribute.DIRECTION,
      )
    }
    val FILE_TRANSFER_DURATION: DistributionType by lazy {
      DistributionType.of(
        "cognotik.files.duration", MetricUnit.MILLISECONDS, "File transfer duration",
        MetricAttribute.DIRECTION, MetricAttribute.OUTCOME,
      )
    }

    // ---------------- Activity: Fargate nodes ----------------
    val FARGATE_NODES: GaugeType by lazy {
      GaugeType.of(
        "cognotik.fargate.nodes", MetricUnit.COUNT, "Running Fargate nodes",
        MetricAttribute.CLUSTER, MetricAttribute.SERVICE,
      )
    }
    val FARGATE_NODE_LIFECYCLE: CounterType by lazy {
      CounterType.of(
        "cognotik.fargate.lifecycle", MetricUnit.COUNT, "Fargate node starts/stops",
        MetricAttribute.CLUSTER, MetricAttribute.SERVICE, MetricAttribute.OUTCOME,
      )
    }

    // ---------------- Activity: ECS service status ----------------
    val ECS_SERVICE_RUNNING_TASKS: GaugeType by lazy {
      GaugeType.of(
        "cognotik.ecs.running_tasks", MetricUnit.COUNT, "ECS service running task count",
        MetricAttribute.CLUSTER, MetricAttribute.SERVICE,
      )
    }
    val ECS_SERVICE_DESIRED_TASKS: GaugeType by lazy {
      GaugeType.of(
        "cognotik.ecs.desired_tasks", MetricUnit.COUNT, "ECS service desired task count",
        MetricAttribute.CLUSTER, MetricAttribute.SERVICE,
      )
    }
    val ECS_SERVICE_STATUS: GaugeType by lazy {
      GaugeType.of(
        "cognotik.ecs.status", MetricUnit.NONE, "1 for the service's current status, 0 otherwise",
        MetricAttribute.CLUSTER, MetricAttribute.SERVICE, MetricAttribute.STATUS,
      )
    }
    val ECS_SERVICE_STATUS_CHANGES: CounterType by lazy {
      CounterType.of(
        "cognotik.ecs.status_changes", MetricUnit.COUNT, "ECS service status transitions",
        MetricAttribute.CLUSTER, MetricAttribute.SERVICE, MetricAttribute.STATUS,
      )
    }
  }
}

/** Monotonic sum (e.g. tokens, bytes, cash in). */
interface CounterType : MetricType {
  companion object {
    fun of(name: String, unit: MetricUnit, description: String, vararg attributes: MetricAttribute<*>): CounterType =
      SimpleCounter(name, unit, description, attributes.toSet())
  }
}

/** Point-in-time value (e.g. banked credits, node count). Last write wins. */
interface GaugeType : MetricType {
  companion object {
    fun of(name: String, unit: MetricUnit, description: String, vararg attributes: MetricAttribute<*>): GaugeType =
      SimpleGauge(name, unit, description, attributes.toSet())
  }
}

/** Distribution of observed values (e.g. durations, sizes). */
interface DistributionType : MetricType {
  companion object {
    fun of(name: String, unit: MetricUnit, description: String, vararg attributes: MetricAttribute<*>): DistributionType =
      SimpleDistribution(name, unit, description, attributes.toSet())
  }
}

private fun requireMetricName(name: String) =
  require(MetricType.NAME_REGEX.matches(name)) { "Invalid metric name: $name" }

private data class SimpleCounter(
  override val name: String,
  override val unit: MetricUnit,
  override val description: String,
  override val attributes: Set<MetricAttribute<*>>,
) : CounterType {
  init { requireMetricName(name) }
  override fun toString() = name
}

private data class SimpleGauge(
  override val name: String,
  override val unit: MetricUnit,
  override val description: String,
  override val attributes: Set<MetricAttribute<*>>,
) : GaugeType {
  init { requireMetricName(name) }
  override fun toString() = name
}

private data class SimpleDistribution(
  override val name: String,
  override val unit: MetricUnit,
  override val description: String,
  override val attributes: Set<MetricAttribute<*>>,
) : DistributionType {
  init { requireMetricName(name) }
  override fun toString() = name
}

/**
 * Marker for a discrete, notable occurrence. Events may carry high-cardinality
 * attributes (user, session); backends typically route them to a log/event sink.
 *
 * @property counter optional counter incremented once per event, so that backends
 *                   without an event sink still get a count
 */
interface EventType {
  val name: String
  val description: String
  val attributes: Set<MetricAttribute<*>>
  val counter: CounterType? get() = null

  companion object {
    fun of(
      name: String,
      description: String,
      counter: CounterType? = null,
      vararg attributes: MetricAttribute<*>,
    ): EventType = SimpleEvent(name, description, attributes.toSet(), counter)

    val AI_ERROR = of(
      "cognotik.event.ai_error", "An AI service call raised an error", MetricType.AI_ERRORS,
      MetricAttribute.PROVIDER, MetricAttribute.MODEL, MetricAttribute.ERROR_TYPE, MetricAttribute.FATAL,
    )
    val APP_STARTED = of(
      "cognotik.event.app_started", "An app session was started", MetricType.APP_SESSIONS,
      MetricAttribute.APP, MetricAttribute.USER, MetricAttribute.SESSION, MetricAttribute.WORKER,
    )
    val APP_COMPLETED = of(
      "cognotik.event.app_completed", "An app session finished", MetricType.APP_SESSIONS,
      MetricAttribute.APP, MetricAttribute.OUTCOME, MetricAttribute.USER, MetricAttribute.SESSION,
    )
    val FILE_TRANSFERRED = of(
      "cognotik.event.file_transferred", "A file was uploaded or downloaded", MetricType.FILE_TRANSFERS,
      MetricAttribute.DIRECTION, MetricAttribute.OUTCOME, MetricAttribute.USER, MetricAttribute.SESSION,
    )
    val PAYMENT_RECEIVED = of(
      "cognotik.event.payment_received", "A payment was received", null,
      MetricAttribute.PAYMENT_TYPE, MetricAttribute.CURRENCY, MetricAttribute.USER,
    )
    val CREDITS_GRANTED = of(
      "cognotik.event.credits_granted", "Credits were granted to a user", null,
      MetricAttribute.PAYMENT_TYPE, MetricAttribute.USER,
    )
    val FARGATE_NODE_STARTED = of(
      "cognotik.event.fargate_node_started", "A Fargate node started", MetricType.FARGATE_NODE_LIFECYCLE,
      MetricAttribute.CLUSTER, MetricAttribute.SERVICE, MetricAttribute.WORKER, MetricAttribute.OUTCOME,
    )
    val FARGATE_NODE_STOPPED = of(
      "cognotik.event.fargate_node_stopped", "A Fargate node stopped", MetricType.FARGATE_NODE_LIFECYCLE,
      MetricAttribute.CLUSTER, MetricAttribute.SERVICE, MetricAttribute.WORKER, MetricAttribute.OUTCOME,
    )
    val ECS_SERVICE_STATUS_CHANGED = of(
      "cognotik.event.ecs_status_changed", "An ECS service changed status", MetricType.ECS_SERVICE_STATUS_CHANGES,
      MetricAttribute.CLUSTER, MetricAttribute.SERVICE, MetricAttribute.STATUS,
    )
  }
}

private data class SimpleEvent(
  override val name: String,
  override val description: String,
  override val attributes: Set<MetricAttribute<*>>,
  override val counter: CounterType?,
) : EventType {
  init { requireMetricName(name) }
  override fun toString() = name
}

/** Open set of payment types; add new ones as constants in your module. */
data class PaymentType(val name: String) {
  override fun toString() = name

  companion object {
    val CARD = PaymentType("card")
    val CRYPTO = PaymentType("crypto")
    val INVOICE = PaymentType("invoice")
    val GIFT = PaymentType("gift")
    val PROMOTIONAL = PaymentType("promotional")
    val ADJUSTMENT = PaymentType("adjustment")
  }
}

/** Open set of transfer directions. */
data class TransferDirection(val name: String) {
  override fun toString() = name

  companion object {
    val UPLOAD = TransferDirection("upload")
    val DOWNLOAD = TransferDirection("download")
  }
}

/** Open set of service statuses (ECS vocabulary plus a derived DEGRADED). */
data class ServiceStatus(val name: String) {
  override fun toString() = name

  companion object {
    val ACTIVE = ServiceStatus("ACTIVE")
    val DRAINING = ServiceStatus("DRAINING")
    val INACTIVE = ServiceStatus("INACTIVE")
    /** Active, but running fewer tasks than desired. */
    val DEGRADED = ServiceStatus("DEGRADED")
  }
}

/** Conventional values for [MetricAttribute.OUTCOME]. */
object Outcomes {
  const val STARTED = "started"
  const val SUCCESS = "success"
  const val FAILURE = "failure"
  const val CANCELLED = "cancelled"
}