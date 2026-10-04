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
| `ServiceRouter.kt`             | Static facade. It implements `MetricsInterface` and forwards every member to the registered backend. It also **automatically records** metrics for the platform calls it forwards (section 6).  |

---

## 1. Architecture

```
 call sites (apps, servlets, billing, infra pollers)
        │  domain helpers:  appStarted / recordPayment / reportEcsService ...
        ▼
 MetricsInterface  ◄── ServiceRouter (static facade)  ◄── ServiceKey.METRICS
        │                 │  (falls back to NoOpMetrics when unregistered)
        │                 └─ auto-records: token usage, credit grants,
        │                    gift claims, session file transfers
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
  Backends log and drop. Do not add `try/catch` around every call. Do keep metric-related
  computations (lookups, string building) cheap and free of side effects.
- **Thread-safe and non-blocking.** Backends buffer and export asynchronously. Call freely from
  request threads, but never block on `flush()` in a hot path.
- **Attributes are sanitized by the backend.** `MetricType.sanitize` drops attributes the metric
  does not declare and strips high-cardinality ones. Passing extra attributes is harmless. Passing
  too few simply loses a dimension.
- **Counters are monotonic.** Amounts must be `>= 0` and not `NaN`. Invalid increments are dropped.

---

## 3. Getting a metrics handle

### 3.1 Through `ServiceRouter` (default)

`ServiceRouter` implements `MetricsInterface` and forwards **every** member to the backend:

- recording: `increment`, `gauge`, `record`, `registerGauge`, `event`
- lifecycle: `flush`, `shutdown`
- queries: `supportsQueries`, `querySeries`, `queryEvents`, `listMetrics`
- alerting: `supportsAlerting`, `putAlertPolicy`, `removeAlertPolicy`, `getAlertPolicy`,
  `listAlertPolicies`, `evaluateAlerts`, `listAlerts`, `triggeredAlerts`

All domain helpers, including the event-emitting ones, therefore work on the router directly:

```kotlin
import com.simiacryptus.cognotik.platform.ServiceRouter
import com.simiacryptus.cognotik.platform.service.appStarted

ServiceRouter.appStarted(app = "chat", session = session, user = user, worker = workerId)
ServiceRouter.increment(MetricType.TOKEN_SPEND, 0.03)
```

Each call resolves `ServiceMap[ServiceKey.METRICS]` at invocation time, so late or overridden
factory registrations are honoured.

### 3.2 When no backend is registered

The router's `metrics` accessor catches the `UnsupportedOperationException` that
`ServiceKey.create()` throws when neither `factory` nor `defaultFactory` is set. In that case it uses
`NoOpMetrics`. Calls through `ServiceRouter` are therefore safe in minimal deployments.

Resolving `ServiceMap[ServiceKey.METRICS]` **directly** has no such fallback. If you must bypass the
router, guard the lookup:

```kotlin
val metrics: MetricsInterface =
  runCatching { ServiceMap[ServiceKey.METRICS] }.getOrElse { NoOpMetrics }
```

### 3.3 Constructor injection (recommended for new, testable classes)

Every helper is an extension on `MetricsInterface`, so classes can take the interface as a
parameter, defaulting to the router:

```kotlin
class ChatApp(private val metrics: MetricsInterface = ServiceRouter) { /* ... */ }
```

Tests then pass an `InMemoryMetrics` (section 11).

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
| Auth            | `AUTH_LOGINS`                | counter      | Count        | login_method, outcome, reason    |
| Auth            | `AUTH_LOGOUTS`               | counter      | Count        | outcome, reason                  |
| Auth            | `AUTH_REGISTRATIONS`         | counter      | Count        | outcome, reason                  |
| Auth            | `AUTH_SESSION_VERIFICATIONS` | counter      | Count        | outcome, reason                  |
| Auth            | `AUTH_CALLBACKS`             | counter      | Count        | login_method, outcome, reason    |
| Auth            | `AUTH_FLOW_DURATION`         | distribution | Milliseconds | login_method, outcome            |

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
| `LOGIN_ATTEMPTED`                   | `AUTH_LOGINS`                | login_method, outcome, reason, user           |
| `LOGGED_OUT`                        | `AUTH_LOGOUTS`               | outcome, reason, user                         |
| `USER_REGISTERED`                   | `AUTH_REGISTRATIONS`         | outcome, reason, user                         |

### 4.4 Attributes

Build attributes by binding values. `null` values are skipped, so optional attributes can be written inline:

```kotlin
val attrs = Attributes.of(
  MetricAttribute.APP("chat"),
  MetricAttribute.OUTCOME(Outcomes.SUCCESS),
  session?.let { MetricAttribute.SESSION(it.sessionId) },   // null-safe
)
val more = attrs + MetricAttribute.WORKER("node-7")          // immutable; returns a new set
```

Known attributes:

- **Low cardinality:** `MODEL`, `PROVIDER`, `TOKEN_TYPE`, `PAYMENT_TYPE`, `CURRENCY`, `APP`, `OUTCOME`,
- **High cardinality:** `USER`, `SESSION`. These are stripped from series and kept on events.

For `OUTCOME`, use the constants in `Outcomes`: `STARTED`, `SUCCESS`, `FAILURE`, `CANCELLED`.

---

## 5. Integration points: what to instrument and where

Use the domain helpers from `MetricsExtensions.kt` wherever one exists. They already pick the right
metric, attributes and event.

Several dashboard items are **recorded automatically by `ServiceRouter`** (section 6). Do not
instrument them again.

| Dashboard checklist item | Hook location (look for...)                                                                                  | Call                                                          |
|--------------------------|--------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------|
| **Token spend**          | **Automatic** via `ServiceRouter.incrementUsage(...)`. Only instrument manually if usage bypasses the router  | (`recordTokenUsage(model, usage, app)`)                       |
| **Input cash**           | Payment webhooks / checkout completion handlers (card, crypto, invoice)                                      | `recordPayment(PaymentType.CARD, amount, "USD", user)`        |
| **Credits granted**      | **Automatic** via `ServiceRouter.creditUser(...)` and `ServiceRouter.claim(...)`                             | (pass `payment_type` in `creditUser` metadata, see 6.2)       |
| **Banked credits**       | A periodic job summing outstanding balances (or after each credit/spend if cheap)                            | `setBankedCredits(total)`                                     |
| **Apps**                 | App/session lifecycle: where a session starts and where it ends (success, failure, cancel)                   | `appStarted(...)`, `appCompleted(...)`                        |
| **Active apps**          | The registry/counter of live sessions per worker                                                             | `registerGauge(MetricType.APP_ACTIVE_SESSIONS, ...)`          |
| **File transfer**        | **Automatic** for `ServiceRouter.openRead`/`openWrite`. Instrument manually only for transfers that bypass it | (`recordFileTransfer(...)`)                                   |
| **Fargate nodes**        | The worker manager or autoscaler that launches/stops tasks                                                   | `setFargateNodes`, `fargateNodeStarted`, `fargateNodeStopped` |
| **ECS service status**   | A scheduled poller calling ECS `DescribeServices`                                                            | `reportEcsService(...)`                                       |
| **Login / auth**         | `LoginServlet`, `LogoutServlet`, `AuthCallbackServlet` and each `LoginMethod` (OAuth, QR)                     | `recordLogin`, `recordLogout`, `recordRegistration`, ...      |

Choose the hook location carefully:

- **Instrument where the facts are known**, i.e. where you have the model, usage, outcome and
  duration in hand.
- **Instrument once per fact.** The router already records token usage, credit grants and session
  file transfers. Neither backends (`UsageInterface`, `StorageInterface`, `GiftedCreditsInterface`
  implementations) nor callers of the router may record these again, or they will be double counted.
- **Record in `finally` / completion paths**, not only on the happy path, so failures and cancellations
  are visible.

---

## 6. Automatic interception in `ServiceRouter`

The router records the following facts for calls it forwards. Each recording runs inside a `safely`
block: it never throws and never affects the result of the intercepted call. Failures are logged
at `WARN` as `Metrics recording failed: <op>`.

| Router call                                      | Recorded                                                                                                  |
|--------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| `incrementUsage(session, user, model, usage, …)` | `recordTokenUsage(model, usage)` (cost from `model.pricing(usage)`), after the usage backend succeeds. Note: **no `app` attribute** |
| `creditUser(user, amount, comment, metadata)`    | `recordCreditsGranted(source, amount, user)` when `amount > 0` and not inside a gift claim                 |
| `claim(user, giftId, …)` (both overloads)        | One `recordCreditsGranted(PaymentType.GIFT, total, user)` for all credits granted during the claim         |
| `openRead(user, session, path)`                  | `recordFileTransfer(DOWNLOAD, …)` when the stream is closed, or immediately (0 bytes, failure) if open fails |
| `openWrite(user, session, path)`                 | `recordFileTransfer(UPLOAD, …)` when the stream is closed, or immediately (0 bytes, failure) if open fails   |

### 6.1 Token usage

The router does not know the app name, so router-recorded `TOKENS_USED` / `TOKEN_SPEND` series carry no
`app` dimension. If you need per-app spend, record it at a site that knows the app. Do not also call
the router's `incrementUsage` for the same usage, or spend is double counted.

### 6.2 Credit grants

`creditUser` derives the source from `metadata["payment_type"]` and falls back to
`PaymentType.ADJUSTMENT` when the key is missing or blank. Pass it so grants are attributed correctly:

```kotlin
ServiceRouter.creditUser(user, 25.0, "Stripe checkout", mapOf("payment_type" to PaymentType.CARD.toString()))
```

(Use the string value of your `PaymentType`.)

During `claim(...)`, a thread-local flag suppresses per-call `creditUser` recording. Credits granted on the
same thread are summed and reported once as `PaymentType.GIFT` after the claim returns (or throws). The
claim's implementation must therefore credit through `ServiceRouter.creditUser` **on the calling thread**.
Credits granted on another thread are reported as `ADJUSTMENT`. Credits granted directly on the usage backend
are not reported at all.

### 6.3 Session file transfers

`openRead`/`openWrite` return a `MeteredInputStream`/`MeteredOutputStream` that counts bytes and records
the transfer **once, on `close()`**. Duration is measured from the `open*` call. The consequences:

- **Always close the stream** (`use { }`). An unclosed stream records nothing.
- The outcome is `FAILURE` only if `open*` throws or `close()` throws an `IOException`. An exception during
  `read`/`write` that is followed by a normal close is recorded as `SUCCESS` with the bytes moved so far.
- Set `ServiceRouter.trackFileTransfers = false` to disable wrapping, e.g. when a web-layer servlet records
  transfers itself.

---

## 7. Recipes

### 7.1 Token usage outside the router

Only needed when usage is not reported through `ServiceRouter.incrementUsage`:

```kotlin
metrics.recordTokenUsage(model = model, usage = completion.usage, app = appName)
```

This emits one `TOKENS_USED` increment per token type with a non-zero count (labelled `token_type`). It emits
one `TOKEN_SPEND` increment with the cost computed by `model.pricing(usage)`. If pricing yields no positive
value (or throws), it falls back to `usage.cost`. Nothing is emitted when the cost is zero. `MODEL` falls back
to `"unknown"`, and `PROVIDER` is omitted if the model has none.

### 7.2 App session lifecycle with duration and outcome

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

### 7.3 Pulled (live) gauges

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

### 7.4 File transfers outside the router

Only for transfers that do not go through `ServiceRouter.openRead`/`openWrite`:

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

### 7.5 Payments and banked credits

```kotlin
metrics.recordPayment(PaymentType.CARD, amount = 25.0, currency = "USD", user = user)
metrics.setBankedCredits(totalOutstanding)
```

Credit grants are recorded by the router (section 6.2). Call `recordCreditsGranted` yourself only for
grants that bypass `ServiceRouter.creditUser`. It always emits the event, but only increments the counter
when the amount is positive.

### 7.6 Fargate and ECS

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

### 7.7 Queries and alerts

Read and alerting calls are optional backend capabilities. Check the flag first:

```kotlin
if (ServiceRouter.supportsQueries) {
  val series = ServiceRouter.querySeries(query)
}
if (ServiceRouter.supportsAlerting) {
  ServiceRouter.putAlertPolicy(policy)
  ServiceRouter.evaluateAlerts()           // never throws through the router
  ServiceRouter.triggeredAlerts().forEach(ServiceRouter::notifyAlert)
}
```

How these calls are routed:

- `evaluateAlerts` goes to the metrics backend and is wrapped so it never throws.
- `notifyAlert` goes to the `NotificationsInterface` from `NotificationsInterface.resolve()` and is also wrapped.
- Read calls return empty results on write-only backends.

---

## 8. Extending the vocabulary

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

- **Metric names**: dotted and lowercase, matching `[a-z][a-z0-9_]*(\.[a-z0-9_]+)*`. Prefix with `cognotik.` for
  core, or with your plugin id. Invalid names fail at construction.
- **Attribute names**: snake_case, matching `[a-z][a-z0-9_]*`. They must be valid as CloudWatch dimensions and
  Prometheus labels. Attribute identity is the name.
- **Declare every attribute you want to keep** on the metric. Undeclared attributes are dropped.
- **Pick the right unit** from `MetricUnit`, or add one: `MetricUnit("Requests")`.
- **Open value types** (`PaymentType`, `TransferDirection`, `ServiceStatus`) take new constants, e.g.
  `val WIRE = PaymentType("wire")`. Keep the set small and bounded.
- An attribute whose value type isn't `String` renders with `toString()` unless you pass a `renderer`.

---

## 9. Cardinality rules

Each unique attribute combination is a separate time series. CloudWatch bills per series, and
Prometheus degrades under many.

1. **Never put user ids, session ids, request ids, file names, or free text on a metric.** Mark such
   attributes `highCardinality = true`. They are stripped from series automatically and retained on
   events.
2. **Attributes are for bounded sets**: model ids, app names, outcomes, directions, clusters, services.
3. **Use events for "who/which"**, metrics for "how much".
4. **Keep `app`, `model` and `worker` values stable.** Avoid embedding versions, hashes or timestamps.

---

## 10. Backend registration and lifecycle

Backends register a factory against `ServiceKey.METRICS`:

```kotlin
// In the module that owns the backend (default)
ServiceKey.METRICS.defaultFactory = { CloudWatchMetrics(config) }

// Both backends at once
ServiceKey.METRICS.defaultFactory = { CompositeMetrics(CloudWatchMetrics(cfg), PrometheusMetrics(cfg)) }

// Disable explicitly
ServiceKey.METRICS.defaultFactory = { NoOpMetrics }
```

Notes:

- `factory` is the global user override and `defaultFactory` is the module's default. `create()` prefers
  `factory`.
- Both setters accept a single registration:
  - `factory` ignores duplicates with a log line.
  - `defaultFactory` rejects duplicates. It throws only if the key was built with `failFast = true`, and
    otherwise logs a warning.
- Register during startup, **before any code records a metric**. Until then, router calls go to `NoOpMetrics`
  and are lost.
- **Flush on shutdown.** `ServiceRouter.shutdown()` shuts down the plugin manager and then, in a `finally`,
  the metrics backend. A single `ServiceRouter.shutdown()` in the application shutdown path is enough.
  `ServiceRouter.flush()` forces an export without shutting down.

### Implementing a new backend

- Implement `MetricsInterface` and apply `metric.sanitize(attributes)` to every counter/gauge/distribution
  series.
- Dispatch on the sub-markers `CounterType`, `GaugeType`, `DistributionType`.
- Map dotted names to your target (`.` becomes `_` for Prometheus). Map `MetricUnit.name` to the target
  unit vocabulary (CloudWatch units match by design).
- Override `event(...)` if you have an event or log sink. Emit the event with its **full** attributes (restricted to
  `type.attributes`), then call `super.event(...)` so the backing counter still increments.
- Optionally implement the query side (`supportsQueries`, `querySeries`, `queryEvents`, `listMetrics`) and
  alerting (`supportsAlerting`, policy management, `evaluateAlerts`, `listAlerts`, `triggeredAlerts`).
- Buffer and export asynchronously. Never throw from recording methods. Make `flush()` and `shutdown()`
  drain the buffer.
- `CompositeMetrics` isolates failures per delegate, so a throwing backend does not affect the others.
- Service backends (usage, storage, gifted credits) must **not** record token usage, credit grants or
  session file transfers. The router does (section 6).

---

## 11. Testing

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

Read-side methods:

- `counter`, `counterTotal`
- `gaugeValue` (explicit value, or the registered supplier)
- `distribution` (count/sum/min/max/mean)
- `events(type?)`, `snapshot()`, `reset()`

Tips:

- Reads apply the same sanitization as writes. Query a counter using only attributes the metric declares.
- Call `reset()` between tests if the instance is shared.
- Test failure paths too: assert that `Outcomes.FAILURE` / `CANCELLED` events fire.
- `ServiceKey.factory` accepts only the first registration, so prefer injecting `InMemoryMetrics` over
  registering it globally.
- To test the router's automatic interception, register `InMemoryMetrics` (and stub service backends) once
  for the test JVM. Call `reset()` between tests.

---

## 12. Integration checklist

Use this when adding metrics to a module or reviewing a PR:

- [ ] Uses an existing domain helper, or declares new metric/attribute/event constants once (not inline).
- [ ] Each fact is recorded at exactly one site. Token usage, credit grants, gift claims and session file
  transfers are already recorded by `ServiceRouter`, so neither the backend nor the caller records them again.
- [ ] `creditUser` calls pass `metadata["payment_type"]` when the source is not an adjustment.
- [ ] Streams from `ServiceRouter.openRead`/`openWrite` are always closed (`use { }`).
- [ ] Failure and cancellation paths record an outcome (`finally`, not only on success).
- [ ] No high-cardinality value (user, session, ids, paths) on a non-event metric.
- [ ] Pulled gauges return `null` when there is no sample, and their `AutoCloseable` is closed on teardown.
- [ ] Counter amounts are non-negative; durations are in milliseconds (`MetricUnit.MILLISECONDS`).
- [ ] A backend (or `NoOpMetrics`) is registered at startup, and `ServiceRouter.shutdown()` is invoked on exit.
- [ ] Query/alerting code checks `supportsQueries` / `supportsAlerting` first.
- [ ] A test using `InMemoryMetrics` asserts the expected series and events.
  `DIRECTION`, `CLUSTER`, `SERVICE`, `STATUS`, `WORKER`, `LOGIN_METHOD`, `REASON`.
### 7.8 Login and authentication
```kotlin
metrics.recordLogin(method.name, Outcomes.STARTED)                                   // interactive flow begun
metrics.recordLogin(method.name, Outcomes.SUCCESS, user = user, duration = elapsed)  // session issued
metrics.recordLogin(method.name, Outcomes.FAILURE, AuthReasons.INVALID_TOKEN)
metrics.recordLogout(Outcomes.SUCCESS, user = user)
metrics.recordRegistration(Outcomes.FAILURE, AuthReasons.THROTTLED)
metrics.recordSessionVerification(Outcomes.FAILURE, "expired")  // counter only (hot path)
metrics.recordAuthCallback("github", Outcomes.SUCCESS)
```
Rules:
- `login_method` must be a registered method name (or a fixed derived label such as `qr_cli`).
  Never use raw request input.
- `reason` must come from `AuthReasons` (or another bounded set). Never use exception messages.
- Record each attempt once, at the site that decides the outcome. Login methods record their own
  outcomes; `LoginServlet` only records failures it decides itself (disabled, dispatch errors).