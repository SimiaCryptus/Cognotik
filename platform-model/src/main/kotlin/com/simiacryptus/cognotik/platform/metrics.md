# Metrics Integration Guide

This guide explains how to report metrics from the rest of the Cognotik platform: which API to call,
where in the codebase to call it, and which rules keep dashboards cheap and accurate.

Metrics are reached through the `MetricsInterface` port, which `ServiceRouter` exposes alongside the
other platform services.

| File                           | Role                                                                                                                                                                                           |
|--------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `model/Metrics.kt`             | Typed vocabulary: `MetricType` (`CounterType`, `GaugeType`, `DistributionType`), `EventType`, `MetricAttribute`, `Attributes`, `PaymentType`, `TransferDirection`, `ServiceStatus`, `Outcomes` |
| `service/MetricsInterface.kt`  | The port, plus `NoOpMetrics`, `CompositeMetrics`, `InMemoryMetrics`                                                                                                                            |
| `service/MetricsExtensions.kt` | Domain helpers (`recordTokenUsage`, `appStarted`, ...) that compose the vocabulary                                                                                                             |
| `ServiceKey.METRICS`           | Registration point for the backend (CloudWatch, Prometheus, or a `CompositeMetrics` of both)                                                                                                   |
| `ServiceRouter.kt`             | Static facade; implements `MetricsInterface` and forwards to the registered backend                                                                                                            |

---

## 1. Architecture

```
 call sites (apps, servlets, billing, usage, infra pollers)
        │  domain helpers:  recordTokenUsage / appStarted / recordPayment ...
        ▼
 MetricsInterface  ◄── ServiceRouter (static facade)  ◄── ServiceKey.METRICS
        │
        ├── CloudWatch backend ┐
        ├── Prometheus backend ├─ optionally combined by CompositeMetrics
        └── InMemoryMetrics (tests) / NoOpMetrics (default)
```

Call sites only ever depend on `MetricsInterface` and the vocabulary in `Metrics.kt`.
They never know which backend is configured.

---

## 2. Contract

Everything you write must respect the contract of `MetricsInterface`:

- **Never throws on the recording path.** A metrics failure must never break a user request.
  Backends log and drop. Do not add `try/catch` around every call; do keep metric-related
  computations (lookups, string building) cheap and side-effect free.
- **Thread-safe and non-blocking.** Backends buffer and export asynchronously. Call freely from
  request threads, but never block on `flush()` in a hot path.
- **Attributes are sanitized by the backend.** `MetricType.sanitize` drops attributes the metric
  does not declare and strips high-cardinality ones. Passing extra attributes is harmless, and passing
  too few simply loses a dimension.
- **Counters are monotonic.** Amounts must be `>= 0` and not `NaN`. Invalid increments are dropped.

---

## 3. Getting a metrics handle

### 3.1 Through `ServiceRouter`

`ServiceRouter` implements `MetricsInterface`, so every domain helper works on it directly:

```kotlin
import com.simiacryptus.cognotik.platform.ServiceRouter
import com.simiacryptus.cognotik.platform.service.recordTokenUsage

ServiceRouter.recordTokenUsage(model, usage, app = "chat")
ServiceRouter.increment(MetricType.TOKEN_SPEND, 0.03)
```

Each call resolves `ServiceMap[ServiceKey.METRICS]` at invocation time, so late or overridden
factory registrations are honoured.

### 3.2 When no backend is registered

`ServiceKey.create()` throws `UnsupportedOperationException` when neither `factory` nor
`defaultFactory` is set. If `ServiceMap` surfaces that, a deployment without a metrics backend
would throw from `ServiceRouter.increment(...)`, which violates the "must not throw" rule.
Until the router falls back to `NoOpMetrics` itself, do one of the following:

1. **Make sure a backend (or `NoOpMetrics`) is always registered at startup** (preferred; see section 8), or
2. Use a guarded accessor in code that may run in minimal deployments:

```kotlin
object AppMetrics {
  /** Resolved once; call after service registration at startup. */
  val metrics: MetricsInterface by lazy {
    runCatching { ServiceMap[ServiceKey.METRICS] }.getOrElse { NoOpMetrics }
  }
}
```

`ServiceKey.METRICS` documents the intent: callers should create the instance once, cache it,
and fall back to `NoOpMetrics` when unregistered.

### 3.3 Constructor injection (recommended for new, testable classes)

Because every helper is an extension on `MetricsInterface`, classes can take the interface as a
parameter, defaulting to the router:

```kotlin
class ChatApp(private val metrics: MetricsInterface = ServiceRouter) { /* ... */ }
```

Tests then pass an `InMemoryMetrics` (section 9).

---

## 4. The vocabulary

### 4.1 Metric kinds

| Kind                 | Method                                        | Semantics                                                                                         | Examples                      |
|----------------------|-----------------------------------------------|---------------------------------------------------------------------------------------------------|-------------------------------|
| `CounterType`        | `increment(metric, amount = 1.0, attributes)` | Monotonic sum                                                                                     | tokens, spend, bytes, cash in |
| `GaugeType`          | `gauge(metric, value, attributes)`            | Last write wins                                                                                   | banked credits, node count    |
| `GaugeType` (pulled) | `registerGauge(metric, attributes) { value }` | Sampled at export; `null` means no sample                                                         | active sessions, queue depth  |
| `DistributionType`   | `record(metric, value, attributes)`           | One observation                                                                                   | durations, sizes              |
| `EventType`          | `event(type, attributes, timestamp)`          | Discrete occurrence; may carry high-cardinality attributes; also increments `type.counter` if set | app started, payment received |

### 4.2 Built-in metrics (`MetricType` companion)

| Dashboard item  | Metric                       | Kind         | Unit         | Accepted attributes              |
|-----------------|------------------------------|--------------|--------------|----------------------------------|
| Token spend     | `TOKENS_USED`                | counter      | Tokens       | model, provider, token_type, app |
| Token spend     | `TOKEN_SPEND`                | counter      | Credits      | model, provider, app             |
| Input cash      | `INPUT_CASH`                 | counter      | USD          | payment_type, currency           |
| Banked credits  | `CREDITS_BANKED`             | gauge        | Credits      | none                             |
| Credits granted | `CREDITS_GRANTED`            | counter      | Credits      | payment_type                     |
| Apps            | `APP_SESSIONS`               | counter      | Count        | app, outcome                     |
| Apps            | `APP_ACTIVE_SESSIONS`        | gauge        | Count        | app, worker                      |
| Apps            | `APP_SESSION_DURATION`       | distribution | Milliseconds | app, outcome                     |
| File transfer   | `FILE_TRANSFERS`             | counter      | Count        | direction, outcome               |
| File transfer   | `FILE_TRANSFER_BYTES`        | counter      | Bytes        | direction                        |
| File transfer   | `FILE_TRANSFER_DURATION`     | distribution | Milliseconds | direction, outcome               |
| Fargate         | `FARGATE_NODES`              | gauge        | Count        | cluster, service                 |
| Fargate         | `FARGATE_NODE_LIFECYCLE`     | counter      | Count        | cluster, service, outcome        |
| ECS             | `ECS_SERVICE_RUNNING_TASKS`  | gauge        | Count        | cluster, service                 |
| ECS             | `ECS_SERVICE_DESIRED_TASKS`  | gauge        | Count        | cluster, service                 |
| ECS             | `ECS_SERVICE_STATUS`         | gauge        | None         | cluster, service, status         |
| ECS             | `ECS_SERVICE_STATUS_CHANGES` | counter      | Count        | cluster, service, status         |

### 4.3 Built-in events (`EventType` companion)

| Event                               | Backing counter              | Attributes (full, including high-cardinality) |
|-------------------------------------|------------------------------|-----------------------------------------------|
| `APP_STARTED`                       | `APP_SESSIONS`               | app, user, session, worker                    |
| `APP_COMPLETED`                     | `APP_SESSIONS`               | app, outcome, user, session                   |
| `FILE_TRANSFERRED`                  | `FILE_TRANSFERS`             | direction, outcome, user, session             |
| `PAYMENT_RECEIVED`                  | none                         | payment_type, currency, user                  |
| `CREDITS_GRANTED`                   | none                         | payment_type, user                            |
| `FARGATE_NODE_STARTED` / `_STOPPED` | `FARGATE_NODE_LIFECYCLE`     | cluster, service, worker, outcome             |
| `ECS_SERVICE_STATUS_CHANGED`        | `ECS_SERVICE_STATUS_CHANGES` | cluster, service, status                      |

### 4.4 Attributes

Build attributes by binding values; `null` values are skipped, so optional attributes can be inline:

```kotlin
val attrs = Attributes.of(
  MetricAttribute.APP("chat"),
  MetricAttribute.OUTCOME(Outcomes.SUCCESS),
  session?.let { MetricAttribute.SESSION(it.sessionId) },   // null-safe
)
val more = attrs + MetricAttribute.WORKER("node-7")          // immutable; returns a new set
```

Known attributes: `MODEL`, `PROVIDER`, `TOKEN_TYPE`, `PAYMENT_TYPE`, `CURRENCY`, `APP`, `OUTCOME`,
`DIRECTION`, `CLUSTER`, `SERVICE`, `STATUS`, `WORKER` (low cardinality), and `USER`, `SESSION`
(**high cardinality**; stripped from series, kept on events).

For `OUTCOME`, use the constants in `Outcomes`: `STARTED`, `SUCCESS`, `FAILURE`, `CANCELLED`.

---

## 5. Integration points: what to instrument and where

Use the domain helpers from `MetricsExtensions.kt` wherever one exists. They already pick the right
metric, attributes and event.

| Dashboard checklist item | Hook location (look for...)                                                                                                                                                                   | Call                                                          |
|--------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------|
| **Token spend**          | Wherever a model call completes and a `ModelSchema.Usage` is available, typically the same place `UsageInterface.incrementUsage(session, user, model, usage, data)` is invoked or implemented | `recordTokenUsage(model, usage, app)`                         |
| **Input cash**           | Payment webhooks / checkout completion handlers (card, crypto, invoice)                                                                                                                       | `recordPayment(PaymentType.CARD, amount, "USD", user)`        |
| **Credits granted**      | `UsageInterface.creditUser(...)`, gift claim (`GiftedCreditsInterface.claim`), admin adjustments                                                                                              | `recordCreditsGranted(source, credits, user)`                 |
| **Banked credits**       | A periodic job summing outstanding balances (or after each credit/spend if cheap)                                                                                                             | `setBankedCredits(total)`                                     |
| **Apps**                 | App/session lifecycle: where a session starts and where it ends (success, failure, cancel)                                                                                                    | `appStarted(...)`, `appCompleted(...)`                        |
| **Active apps**          | The registry/counter of live sessions per worker                                                                                                                                              | `registerGauge(MetricType.APP_ACTIVE_SESSIONS, ...)`          |
| **File transfer**        | Upload and download servlets / `SessionContentStore` `openRead`/`openWrite` callers at the web layer                                                                                          | `recordFileTransfer(...)`                                     |
| **Fargate nodes**        | The worker manager or autoscaler that launches/stops tasks                                                                                                                                    | `setFargateNodes`, `fargateNodeStarted`, `fargateNodeStopped` |
| **ECS service status**   | A scheduled poller calling ECS `DescribeServices`                                                                                                                                             | `reportEcsService(...)`                                       |

Choose the hook location carefully:

- **Instrument where the facts are known**, i.e. where you have the model, usage, outcome and
  duration in hand. Avoid re-deriving them in the facade layer.
- **Instrument once per fact.** If `ServiceRouter.incrementUsage` and the real usage
  implementation both record tokens, spend will be double counted. Pick one site.
- **Record in `finally` / completion paths**, not only on the happy path, so failures and cancellations
  are visible.

---

## 6. Recipes

### 6.1 Token usage and spend

```kotlin
val usage: ModelSchema.Usage = completion.usage
ServiceRouter.recordTokenUsage(model = model, usage = usage, app = appName)
```

Emits one `TOKENS_USED` increment per token type with a non-zero count (labelled `token_type`), and one
`TOKEN_SPEND` increment if `usage.cost > 0`. `MODEL` falls back to `"unknown"`, and `PROVIDER` is omitted
if the model has none.

### 6.2 App session lifecycle with duration and outcome

```kotlin
val started = Instant.now()
metrics.appStarted(app = "chat", session = session, user = user, worker = workerId)
var outcome = Outcomes.SUCCESS
try {
  runApp()
} catch (e: CancellationException) {
  outcome = Outcomes.CANCELLED; throw e
} catch (e: Throwable) {
  outcome = Outcomes.FAILURE; throw e
} finally {
  metrics.appCompleted(
    app = "chat", outcome = outcome,
    duration = Duration.between(started, Instant.now()),
    session = session, user = user,
  )
}
```

`appCompleted` records `APP_SESSION_DURATION` only when a `duration` is supplied.

### 6.3 Pulled (live) gauges

```kotlin
private val active = AtomicInteger()

private val handle: AutoCloseable = metrics.registerGauge(
  metric = MetricType.APP_ACTIVE_SESSIONS,
  attributes = Attributes.of(MetricAttribute.APP("chat"), MetricAttribute.WORKER(workerId)),
) { active.get().toDouble() }

// on shutdown:
handle.close()
```

Prefer `registerGauge` over repeated `gauge(...)` for values that already live in memory. Return `null`
from the supplier when there is no meaningful sample. Always close the handle when the owner goes away.

### 6.4 File transfers

```kotlin
val t0 = System.nanoTime()
val outcome = try {
  copy(); Outcomes.SUCCESS
} catch (e: Exception) {
  Outcomes.FAILURE
}
metrics.recordFileTransfer(
  direction = TransferDirection.UPLOAD,
  bytes = bytesCopied,
  duration = Duration.ofNanos(System.nanoTime() - t0),
  outcome = outcome,
  session = session, user = user,
)
```

### 6.5 Payments and credits

```kotlin
metrics.recordPayment(PaymentType.CARD, amount = 25.0, currency = "USD", user = user)
metrics.recordCreditsGranted(source = PaymentType.GIFT, amount = gift.amountGranted, user = claimant)
metrics.setBankedCredits(totalOutstanding)
```

`recordCreditsGranted` always emits the event, but only increments the counter when the amount is positive.

### 6.6 Fargate and ECS

```kotlin
metrics.setFargateNodes(cluster, service, runningCount)
metrics.fargateNodeStarted(cluster, service, worker = taskId)
metrics.fargateNodeStopped(cluster, service, worker = taskId, outcome = Outcomes.FAILURE)

// scheduled poller
metrics.reportEcsService(
  cluster = cluster, service = service,
  status = if (running < desired) ServiceStatus.DEGRADED else ServiceStatus.ACTIVE,
  runningTasks = running, desiredTasks = desired,
  previousStatus = lastStatus,   // emits a status-changed event only when it differs
)
```

`reportEcsService` publishes the status gauge as `1` for the current status and `0` for every other
known status, so a stacked graph works.

---

## 7. Extending the vocabulary

Nothing in `Metrics.kt` is a closed enum. Modules and plugins add their own definitions as constants.
Declare each once (top-level or in a companion), never inline per call.

```kotlin
object MyPluginMetrics {
  val WIDGET_KIND = MetricAttribute.of<String>("widget_kind")

  val WIDGETS_MADE: CounterType = CounterType.of(
    "myplugin.widgets", MetricUnit.COUNT, "Widgets made", WIDGET_KIND, MetricAttribute.OUTCOME,
  )
  val QUEUE_DEPTH: GaugeType = GaugeType.of(
    "myplugin.queue_depth", MetricUnit.COUNT, "Pending widget jobs",
  )
  val WIDGET_LATENCY: DistributionType = DistributionType.of(
    "myplugin.widget_latency", MetricUnit.MILLISECONDS, "Time to make a widget", WIDGET_KIND,
  )
  val WIDGET_FAILED: EventType = EventType.of(
    "myplugin.event.widget_failed", "A widget job failed", WIDGETS_MADE,
    WIDGET_KIND, MetricAttribute.USER, MetricAttribute.SESSION,
  )
}
```

Rules:

- **Metric names**: dotted, lowercase: `[a-z][a-z0-9_]*(\.[a-z0-9_]+)*`. Prefix with `cognotik.` for
  core, or your plugin id. Invalid names fail at construction.
- **Attribute names**: snake_case `[a-z][a-z0-9_]*`. They must be valid as CloudWatch dimensions and
  Prometheus labels. Attribute identity is the name.
- **Declare every attribute you want to keep** on the metric. Undeclared attributes are dropped.
- **Pick the right unit** from `MetricUnit` (or add one: `MetricUnit("Requests")`).
- **Open value types** (`PaymentType`, `TransferDirection`, `ServiceStatus`) take new constants:
  `val WIRE = PaymentType("wire")`. Keep the set small and bounded.
- An attribute whose value type isn't `String` renders with `toString()` unless you pass a `renderer`.

---

## 8. Cardinality rules

Each unique attribute combination is a separate time series. CloudWatch bills per series, and
Prometheus degrades under many.

1. **Never put user ids, session ids, request ids, file names, or free text on a metric.** Mark such
   attributes `highCardinality = true`. They are stripped from series automatically and retained on
   events.
2. **Attributes are for bounded sets**: model ids, app names, outcomes, directions, clusters, services.
3. **Use events for "who/which"**, metrics for "how much".
4. Keep `app`, `model` and `worker` values stable. Avoid embedding versions, hashes or timestamps.

---

## 9. Backend registration and lifecycle

Backends register a factory against `ServiceKey.METRICS`:

```kotlin
// In the module that owns the backend (default)
ServiceKey.METRICS.defaultFactory = { CloudWatchMetrics(config) }

// Both backends at once
ServiceKey.METRICS.defaultFactory = { CompositeMetrics(CloudWatchMetrics(cfg), PrometheusMetrics(cfg)) }

// Disable explicitly (safe default)
ServiceKey.METRICS.defaultFactory = { NoOpMetrics }
```

Notes:

- `factory` is the global user override and `defaultFactory` is the module's default. `create()` prefers
  `factory`.
- Both setters accept a single registration. `factory` ignores duplicates with a log line, while
  `defaultFactory` rejects duplicates (throws only if the key was built with `failFast = true`, otherwise logs
  a warning).
- Register during startup, **before any code records a metric**.
- **Flush on shutdown** so buffered data isn't lost. See the `shutdown()` caveat in section 11.

### Implementing a new backend

- Implement `MetricsInterface` and apply `metric.sanitize(attributes)` to every counter/gauge/distribution
  series.
- Dispatch on the sub-markers `CounterType`, `GaugeType`, `DistributionType`.
- Map dotted names to your target (`.` becomes `_` for Prometheus) and `MetricUnit.name` to the target
  unit vocabulary (CloudWatch units match by design).
- Override `event(...)` if you have an event or log sink. Emit the event with its **full** attributes (restricted to
  `type.attributes`), then call `super.event(...)` so the backing counter still increments.
- Buffer and export asynchronously. Never throw from recording methods. Make `flush()` and `shutdown()`
  drain the buffer.
- `CompositeMetrics` isolates failures per delegate, so a throwing backend does not affect the others.

---

## 10. Testing

`InMemoryMetrics` is the reference implementation and the test double. Because the helpers are
extensions on `MetricsInterface`, no registration is needed. Pass it directly:

```kotlin
@Test
fun `chat reports token spend`() {
  val metrics = InMemoryMetrics()
  val app = ChatApp(metrics)

  app.handle(request)

  assertEquals(1500.0, metrics.counterTotal(MetricType.TOKENS_USED))
  assertEquals(
    1000.0,
    metrics.counter(
      MetricType.TOKENS_USED, Attributes.of(
        MetricAttribute.MODEL("gpt-4o"),
        MetricAttribute.TOKEN_TYPE(ModelSchema.TokenTypes.Input)
      )
    ),
  )
  val started = metrics.events(EventType.APP_STARTED).single()
  assertEquals("chat", started.attributes[MetricAttribute.APP])
  assertNotNull(started.attributes[MetricAttribute.SESSION])   // events keep high-cardinality attrs
}
```

Read side: `counter`, `counterTotal`, `gaugeValue` (explicit value, or the registered supplier),
`distribution` (count/sum/min/max/mean), `events(type?)`, `snapshot()`, `reset()`.

Tips:

- Reads apply the same sanitization as writes. Query a counter using only attributes the metric declares.
- Call `reset()` between tests if the instance is shared.
- Test failure paths too: assert `Outcomes.FAILURE` / `CANCELLED` events fire.
- `ServiceKey.factory` accepts only the first registration, so prefer injecting `InMemoryMetrics` over
  registering it globally in tests.

---

## 11. Known gaps in `ServiceRouter` (read before relying on it)

`ServiceRouter` forwards `increment`, `gauge`, `record` and `registerGauge` to the registered backend,
but:

| Member       | Current behaviour via `ServiceRouter`                                            | Consequence                                                                                                                                                                                                      |
|--------------|----------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `event(...)` | Not overridden, so the interface default runs: it only increments `type.counter` | Events never reach a backend's event sink; high-cardinality attributes (user/session) are lost. All the event-emitting helpers (`appStarted`, `recordPayment`, ...) are affected when called on `ServiceRouter`. |
| `flush()`    | Not overridden (default no-op)                                                   | Cannot force an export through the router                                                                                                                                                                        |
| `shutdown()` | Resolves to `PluginManagerInterface.shutdown()` only (the signatures collide)    | Metrics backend is never flushed/released on router shutdown                                                                                                                                                     |

**Until fixed**, for event-emitting helpers, resolve the backend directly:

```kotlin
val metrics = ServiceMap[ServiceKey.METRICS]    // or the guarded AppMetrics.metrics from section 3.2
metrics.appStarted("chat", session, user, worker)
```

and call `ServiceMap[ServiceKey.METRICS].shutdown()` explicitly in your application shutdown path.

**Proposed fix** in `ServiceRouter` (forwards the missing members and shuts down both services):

```kotlin
override fun event(type: EventType, attributes: Attributes, timestamp: Instant) =
  metrics.event(type, attributes, timestamp)

override fun flush() = metrics.flush()

override fun shutdown() {
  try {
    pluginManager.shutdown()
  } finally {
    metrics.shutdown()
  }
}
```

(Add imports for `EventType`; `Instant` is already imported.) A secondary improvement is to have the
`metrics` accessor fall back to `NoOpMetrics` when no factory is registered, to honour the
"never throw" contract.

---

## 12. Integration checklist

Use this when adding metrics to a module or reviewing a PR:

- [ ] Uses an existing domain helper, or declares new metric/attribute/event constants once (not inline).
- [ ] Each fact is recorded at exactly one site (no double counting between facade and implementation).
- [ ] Failure and cancellation paths record an outcome (`finally`, not only on success).
- [ ] No high-cardinality value (user, session, ids, paths) on a non-event metric.
- [ ] Event-emitting calls go to the real backend (`ServiceMap[ServiceKey.METRICS]`), not the router, until section 11
  is resolved.
- [ ] Pulled gauges return `null` when there is no sample, and their `AutoCloseable` is closed on teardown.
- [ ] Counter amounts are non-negative; durations are in milliseconds (`MetricUnit.MILLISECONDS`).
- [ ] A backend or `NoOpMetrics` is registered at startup, and `shutdown()` is invoked on exit.
- [ ] A test using `InMemoryMetrics` asserts the expected series and events.