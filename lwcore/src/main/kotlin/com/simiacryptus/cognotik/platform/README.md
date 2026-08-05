# `com.simiacryptus.cognotik.platform`

**Platform ports & domain model.** This package is the *hexagonal boundary* of Cognotik: it
contains only interfaces (ports), immutable value/domain types, and exception hierarchies.
There are no concrete backends here — no JDBC, no S3, no servlet container, no Jetty.

Everything in this package exists so that application code can depend on a *narrow, testable
contract* rather than on a filesystem, a database, or a specific auth provider.

> Section references of the form `REVIEW.md §3.x` appear throughout the KDoc. They point at the
> architecture review that drove the current shape of these interfaces. Read that document before
> proposing structural changes.

---

## Table of contents

1. [Design rules](#design-rules)
2. [Package layout](#package-layout)
3. [Domain model reference](#domain-model-reference)
4. [Ports reference](#ports-reference)
5. [Cross-cutting contracts](#cross-cutting-contracts)
6. [Implementing a backend](#implementing-a-backend)
7. [⚠ Known hazards: self-recursive defaults](#-known-hazards-self-recursive-defaults)
8. [Deprecations & migration map](#deprecations--migration-map)
9. [Security notes](#security-notes)
10. [Conformance test checklist](#conformance-test-checklist)
11. [Contributing to this package](#contributing-to-this-package)

---

## Design rules

These are enforced by review, not by the compiler. Violating them is the most common cause of a
rejected PR against this package.

| # | Rule | Rationale |
|---|------|-----------|
| 1 | **No infrastructure types in port signatures.** No `java.io.File`, no `jakarta.servlet.*`, no JDBC/Jackson types (`Class<T>` in `JsonStore.getJson` is a tolerated exception). | `File` grants unrestricted authority and makes object-store backends impossible (§3.3). Servlet types belong in `platform.web`. |
| 2 | **One responsibility per interface.** `StorageInterface` is a *composition* of four ports, not a god-interface. | Consumers that only publish events must not be handed `deletePlugin` (§3.8). |
| 3 | **Typed identifiers over raw `String`.** Use the `@JvmInline value class`es in `model/Ids.kt`. | Eliminates argument-swap bugs at zero runtime cost (§4.2). |
| 4 | **Exact money.** `Credits` (integral micro-credits) — never `Double` — for any new API. | `Double` budget comparisons are unsound (§3.7). |
| 5 | **Explicit absence.** `Principal` instead of `User?`; `Patch<T>` instead of "null means skip". | `null` / `User.NULL` / `defaultUser` had three different meanings (§4.1, §3.4). |
| 6 | **Rich results over `Boolean`.** `ClaimResult`, not `true`/`false`. | Callers must be able to explain *why* something failed (§3.7). |
| 7 | **Defaults are for source compatibility, not for production.** Any `= throw UnsupportedOperationException(...)` or N+1 fallback is a migration crutch; document it as such. | See [known hazards](#-known-hazards-self-recursive-defaults). |
| 8 | **Fail secure.** Authorization returns `false` on error; exceptions are reserved for programmer/config errors. | §3.5 |
| 9 | **Paging is cursor-based.** `Page` / `PageResult<T>` on every `list*` that can grow unboundedly. | §4.3 |
| 10 | **Deprecate, don't delete.** Add the replacement, mark the old member `@Deprecated` with `ReplaceWith`, keep a bridging default. | Downstream modules compile against this package. |

---

## Package layout

```
com.simiacryptus.cognotik.platform
├── AuthenticationInterface.kt        # bearer-token session store
├── AuthorizationInterface.kt         # (ResourceRef, Principal, OperationType) → Boolean
├── GiftedCreditsInterface.kt         # promotional credit gifts & claims
├── StorageInterface.kt               # composition facade: File+Content+Message+Json
│   ├── SessionFileStore.kt           #   legacy java.io.File view (all members @Deprecated)
│   ├── SessionContentStore.kt        #   backend-agnostic byte streams  ← use this
│   ├── MessageStore.kt               #   per-session message map
│   └── JsonStore.kt                  #   per-session JSON slots
├── MetadataStorageInterface.kt       # session metadata (name, owner, worker, path, timestamps)
├── AbstractMetadataStorage.kt        # opt-in base class == "N+1 fallbacks are OK here"
├── PluginManagerInterface.kt         # facade == EventBus + PluginRegistry + PluginInstaller
│   ├── EventBus.kt                   #   pub/sub, typed via Topic<T>
│   ├── PluginRegistry.kt             #   load / unload / introspect
│   └── PluginInstaller.kt            #   irreversible filesystem side effects
├── PluginException.kt                # PluginNotFound / AlreadyLoaded / Load / Unload
├── web/
│   └── UserProvider.kt               # the *only* servlet-aware type in the package
└── model/
    ├── User.kt, Principal.kt         # identity
    ├── Session.kt                    # session id value type + validation
    ├── SessionSummary.kt             # shared read-model interface
    ├── SessionMetadata.kt            #   aggregate (implements SessionSummary)
    ├── SessionListEntry.kt           #   listing projection (implements SessionSummary)
    ├── Patch.kt                      # Patch<T> + SessionMetadataPatch + asPatch()
    ├── Page.kt                       # Page / PageResult<T> / List<T>.paginate()
    ├── Ids.kt                        # UserId, OwnerId, WorkerId, PluginId, GiftId,
    │                                 # ClaimId, AccessToken, Credits
    ├── ResourceRef.kt, OperationType.kt, AuthorizationChain.kt
    ├── Gift.kt, GiftStats, Claim.kt, ClaimResult.kt
    ├── TokenMetadata.kt              # non-secret description of an auth session
    ├── Topic.kt, PluginEvents.kt     # typed event keys
    └── ApplicationServicesConfig.kt  # process-wide config singleton
```

Dependency direction: `platform.model` ← `platform` ← `platform.web`. `model` must never import from
its parent package or from `web`.

---

## Domain model reference

### Identity: `User`, `Principal`

```kotlin
data class User(email: String, name: String = email, id: String = email)
```

* **`id` is the identity** — `equals`/`hashCode` use `id` only. It is what `Claim.userId` and
`SessionMetadata.ownerId` persist. Do not key anything off `email` (§3.2).
* `toString()` is **redacted** (`u***@host`) because `User` reaches log lines. Use `.email`
explicitly when you genuinely need the address; `redactedEmail` is available for logs.
* `User.NULL` is `@Deprecated`. Model absence with `Principal`:

```kotlin
sealed interface Principal {
  object Anonymous : Principal        // unauthenticated / public
  object System    : Principal        // background jobs, migrations
  data class Authenticated(user: User) : Principal
  companion object { fun of(user: User?): Principal }   // bridge from legacy `User?`
}
```

### `Session`

Canonical syntax: `SESSION_ID_REGEX = ([GU]-)?\d{8}-[\w+.\-]{4,12}`.

```kotlin
Session.newGlobalID()          // "G-20240115-aB3xY9pQ"
Session.newUserID()            // "U-20240115-..."
Session.isValid(raw)           // predicate
Session.tryParse(raw)          // Session? — use for untrusted input
Session.parseSessionID(raw)    // throws IllegalArgumentException
session.isGlobal() / isNull() / toGlobal()
```

* `randomId(length)` draws from `ID_ALPHABET` with `SecureRandom` and always yields exactly
`length` valid characters.
* `Session.NULL` (`sessionId == ""`) is a `@Deprecated` sentinel that overrides validation.
**Storage implementations must reject `session.isNull()` explicitly** rather than deriving a
directory from an empty id (§3.11).
* The `init` block calls the `open` `validateSessionId()` — a documented leaking-`this` hazard
retained only for `NULL`. Do not subclass `Session`.

### Money: `Credits`

```kotlin
@JvmInline value class Credits(val micros: Long)   // 1 credit = 1_000_000 micros
```

Exact `plus`/`minus`/`times`/`compareTo`. Construct with `Credits.of(BigDecimal)` or
`Credits.ofMicros(Long)`. `Credits.of(Double)` and `Credits.toDouble()` exist **only** to bridge the
deprecated `Double` fields on `Gift`; new code must not use them.

### Authorization: `ResourceRef`, `OperationType`

```kotlin
sealed interface ResourceRef {
  data class App(id: String, applicationClass: Class<*>? = null)
  data class SessionRef(id: Session, applicationClass: Class<*>? = null)
  data class GiftRef(id: GiftId)
  data class Named(type: String, id: String)      // escape hatch
}
```

Serializable and *instance-scoped*, so "may Delete **session S**" is expressible — the `Class<*>`
key could not do that (§3.5). `ResourceRef.of(Class<*>?)` bridges legacy call sites.

`OperationType` owns the implication matrix so implementations stop disagreeing about it (§3.10):

```kotlin
OperationType.Admin.implies(OperationType.Delete)   // true
OperationType.Write.implies(OperationType.Read)     // true
OperationType.Read.implies(OperationType.Write)     // false
```

`Admin` implies everything else; `Write`/`Delete`/`Share`/`Execute`/`Public` each imply `Read`.
**Implementations must consult `implies`, not re-derive the matrix.**

### Three-state updates: `Patch<T>`

```kotlin
sealed interface Patch<out T> {
  object Unchanged : Patch<Nothing>       // leave stored value alone
  data class Set<out T>(value: T)         // write value — which may be null == "clear"
}
```

Used by `SessionMetadataPatch`. Consume with `patch.field.ifSet { ... }`. `SessionMetadata.asPatch()`
reproduces the historical "null means unchanged, empty list means unchanged" convention so legacy
snapshot-style writes can delegate without behaviour change.

### Paging: `Page` / `PageResult<T>`

```kotlin
var cursor: String? = null
do {
  val page = store.listSessionEntries(user, Page(limit = 200, cursor = cursor))
  page.items.forEach(::render)
  cursor = page.nextCursor
} while (cursor != null)
```

`Page.cursor` is **opaque to callers**. The in-memory fallback `List<T>.paginate(page)` interprets it
as a decimal offset; a real backend is free to use a keyset cursor instead. `Page(limit = 0)` throws.

### Events: `Topic<T>`

```kotlin
val MY_TOPIC = Topic.of<MyPayload>("myplugin.thing.happened")

bus.publish(MY_TOPIC, MyPayload(...))
bus.subscribe(MY_TOPIC) { payload -> /* payload is MyPayload?; null on type mismatch */ }
```

`Topic.cast` returns `null` on a shape mismatch instead of throwing `ClassCastException` inside a
plugin. Well-known topics live in `PluginEvents`.

---

## Ports reference

### `AuthenticationInterface`

Opaque bearer-token session store.

| Member | Notes |
|---|---|
| `getUser(String?)` / `getUser(AccessToken?)` | Return `null` for unknown, expired, or absent tokens. |
| `putUser(token, user)` / `putUser(token, user, ttl)` | Override the `ttl` overload if you support expiry; the default ignores it. |
| `listTokens(user): List<TokenMetadata>` | Non-secret session descriptions. Default `emptyList()`. |
| `logoutIfMatching(token, user): Boolean` | Idempotent; `false` for unknown/expired/other-user. **Default is self-recursive — must override.** |
| `revokeAll(user): Int` | Default throws `UnsupportedOperationException`. |

**Mandatory contract:** store only a *hash* of the token at rest; treat tokens as opaque; prefer
supporting expiry and revocation (§3.6). There is deliberately **no reverse lookup**
(`user → token`); use `TokenMetadata.tokenId` to build revocation UIs.

`AUTH_COOKIE = "sessionId"` is a deprecated transport concern (and is unrelated to
`Session.sessionId`). Cookies carrying it must be `HttpOnly`, `Secure` outside local dev, and
`SameSite=Lax` or stricter.

### `AuthorizationInterface`

```kotlin
fun isAuthorized(resource: ResourceRef?, principal: Principal, operationType: OperationType): Boolean
fun authorizedOperations(resource: ResourceRef?, principal: Principal): Set<OperationType>
```

* `resource == null` means a global/tenant-wide check.
* **Fail secure:** never propagate an infrastructure exception as "allow"; catch and return `false`.
* `authorizedOperations` exists so a UI can render permission-gated controls in one call. The default
does `OperationType.values().filter { isAuthorized(...) }` — override it if that is N round trips.
* `AuthorizationChain` is the framework-free chain-link contract (`Boolean?`: allow / deny / defer)
used by `PluginEvents.AuthChainRegistration`.

### Session storage: the four ports

`StorageInterface : SessionFileStore, SessionContentStore, MessageStore, JsonStore` plus session
listing/deletion. **Depend on the narrowest sub-port you actually need.**

#### `SessionContentStore` — preferred content API

```kotlin
fun openRead(user: User?, session: Session, path: String): InputStream
fun openWrite(user: User?, session: Session, path: String): OutputStream
fun list(user: User?, session: Session, prefix: String = ""): List<String>
fun exists(user: User?, session: Session, path: String): Boolean
fun delete(user: User?, session: Session, path: String): Boolean
```

Paths are `/`-separated, relative to the session root, and **must not escape it**. `StorageInterface`
supplies default implementations over the legacy `File` API, including a private
`resolveSessionFile` traversal guard (`require(target.startsWith(base))`). If you implement
`SessionContentStore` directly against S3/GCS/blobs, **you own the traversal check**.

`openWrite` creates parent directories; `openRead` throws `FileNotFoundException`; `delete` returns
`false` when the path was already absent.

#### `SessionFileStore` — legacy, entirely `@Deprecated`

`getUserDir`, `getSystemDir`, `userRoot` all leak `java.io.File` and hand callers unrestricted
authority over a directory. `userRootFor(user: User)` is the null-safe replacement for
`userRoot(user: User?)`. New code must not call any of these.

#### `MessageStore`

`getMessageMap` must preserve insertion order (return a `LinkedHashMap`) and is
**immutable by contract**. `updateMessage` is a read-modify-write that also appends to the message-id
list; implementations must apply it atomically per session.
⚠ `getMessageMap`'s default body is self-recursive — override it.

#### `JsonStore`

`setJson(user, session, filename, value)` returns the value it saved. `getJson(..., type)` defaults to
`UnsupportedOperationException`; **implement it**, otherwise callers fall back to raw filesystem
access just to read what they wrote (§3.3).

### `MetadataStorageInterface`

Per-session metadata: name, message ids, timestamp, owner id, worker id (`ip:port`), path.

Must-know details:

* `getSessionPath` / `setSessionPath` default to `null` / no-op. If you persist a path you **must**
override both, otherwise `SessionMetadata.path` is write-only (§3.4).
* `exists(user, session)` defaults to "has a timestamp" — a compatibility heuristic. Override with a
real existence check so callers can distinguish *absent* from *default*.
* Prefer `updateSessionMetadata(user, session, SessionMetadataPatch)` over snapshot writes; it is the
only way to *clear* a field.
* `getSessionMetadataMap(user, ids)` **omits** ids with no recorded metadata — that is intentional.
* Bulk/listing defaults (`listSessionMetadata`, `listSessionEntries`, `getSessionMetadataMap`,
`deleteAllForUser`) are deliberately N+1. DB-backed implementations must override them with
single-round-trip projections; use `SessionListEntry` to avoid loading `messageIds`.
* Extending **`AbstractMetadataStorage`** is an explicit, reviewable statement that the N+1 fallbacks
are acceptable for that backend (§3.4). Implement the interface directly if they are not.
* Read-modify-write helpers are **not** atomic unless your implementation documents that they are.

### `GiftedCreditsInterface`

Promotional credit gifts.

**Transaction contract**
* `createGift` debits the creator and creates the gift **atomically**.
* `claim` debits the gift's shared budget and credits the user **atomically**.
* Both take `idempotencyKey: String?`. Repeating a call with the same key **must** return the original
result without charging again (§3.7).

**Validation contract** — reject with `IllegalArgumentException` (or `IllegalStateException` for
balance):
* `amountGranted <= 0`
* `totalBudget < amountGranted`
* `grantDuration <= 0`
* insufficient creator balance

**Results.** `claim` returns `ClaimResult`: `Granted(amount, expiresAt, claimId)`, `AlreadyClaimed`,
`BudgetExhausted`, `GiftExpired`, `GiftNotFound`, `GiftRevoked`, `Failed(reason)`. `Failed` exists
only to bridge legacy `Boolean` implementations — do not return it from new code.

`Gift` still exposes `Double` money for compatibility; use `amountGrantedCredits`,
`totalBudgetCredits`, `spentBudgetCredits`, `remainingBudgetCredits`, `canGrantAnother()` and
`stats()`/`GiftStats` for anything arithmetic. `Claim` snapshots `grantedAmount` and `expiresAt` so
history stays auditable after the gift definition changes.

`revokeGift` / `expireGift` / `deleteGift` default to `UnsupportedOperationException`. Note the
semantic difference: **revoke** blocks future claims, **expire** additionally refunds unspent budget
to the creator, **delete** destroys claim history.

### Plugin subsystem

```
PluginManagerInterface  ==  EventBus + PluginRegistry + PluginInstaller
```

* **`EventBus`** — `publish`/`subscribe`/`unsubscribe` (+ typed `Topic<T>` overloads),
`onChange { }` / `triggerChangeNotification()` sugar over `PluginEvents.CHANGE_NOTIFICATION`.
`subscribe` returns a subscription id; hand it back to `unsubscribe`.
* **`PluginRegistry`** — `loadPlugin(jar)` (ServiceLoader discovery of `CognotikPlugin`),
`loadPlugin(jar, entryPointClass)`, `loadPluginsFromDirectory(dir)`, `unloadPlugin(jar)`,
`getLoadedPlugins()` (keyed by absolute JAR path) / `getLoadedPluginsById()` (`PluginId` keys),
`isLoaded(jar)`, `shutdown()` (default no-op — override to drain subscribers and close loaders).
* **`PluginInstaller`** — `deletePlugin(jar)` (unloads first; on unload failure the file stays and the
failure propagates), `installPlugin(jar): File`.

Failures use `PluginException` subtypes: `PluginNotFoundException`, `PluginAlreadyLoadedException`,
`PluginLoadException`, `PluginUnloadException`. During migration, callers should catch both these and
the legacy `IllegalArgumentException`/`IllegalStateException`.

**Unspecified (tracked in §3.8):** API version compatibility, load ordering, inter-plugin
dependencies, classloader isolation guarantees. Do not assume any of them.

### `platform.web.UserProvider`

```kotlin
fun authenticate(request: HttpServletRequest, response: HttpServletResponse?): User?
```

The single servlet-aware type, deliberately isolated so `platform.model` stays free of
`jakarta.servlet`. Return `null` for unauthenticated requests; `response` is nullable because some
call sites cannot issue a challenge/redirect.

### `ApplicationServicesConfig`

Process-wide singleton. All fields are `@Volatile` so a late write is visible to other threads and the
lock cannot be bypassed by a benign race (§3.9).

```kotlin
ApplicationServicesConfig.dataStorageRoot = File("/var/lib/cognotik")
ApplicationServicesConfig.lock()                       // no further writes
val root = ApplicationServicesConfig.requireDataStorageRoot()  // creates + validates, fails fast
```

* `lock()` is **not** idempotent — a second call throws `IllegalArgumentException`. Call it exactly
once from bootstrap.
* `isLocked`'s setter is deprecated; the property only ever transitions `false → true`.
* ⚠ `defaultUser` currently has **no lock check** — it is writable after `lock()`. Treat that as a bug,
not a feature.

---

## Cross-cutting contracts

**Threading.** All storage and metadata implementations must be thread-safe and *may block*. Never
call these ports from a coroutine dispatcher that cannot tolerate blocking (use `Dispatchers.IO` or a
dedicated pool).

**Atomicity.** Only two things are guaranteed atomic, and only when the implementation says so:
`MessageStore.updateMessage` per session, and the `GiftedCreditsInterface` create/claim transactions.
`MetadataStorageInterface`'s read-modify-write helpers are explicitly **not** atomic by default.

**Nullability of `User?`.** In the storage/metadata ports, `user == null` means *global / anonymous
scope* (e.g. a `G-` session), not "unknown". Authorization uses `Principal` instead and should be
preferred for new APIs.

**Error strategy.**

| Port | Convention |
|---|---|
| Authorization | never throw for denial — return `false` |
| Authentication | `null` for unknown token; `false` from `logoutIfMatching` |
| Gifts | `ClaimResult` for expected outcomes; `IllegalArgumentException`/`IllegalStateException` for validation |
| Content store | `FileNotFoundException` (missing), `IllegalArgumentException` (path escape) |
| Plugins | `PluginException` subtypes |
| Unimplemented optional member | `UnsupportedOperationException("... is not implemented by ${javaClass.name}")` |

---

## Implementing a backend

Minimal in-memory `MetadataStorageInterface`. Note that it overrides **every self-recursive default**
and both `getSessionPath`/`setSessionPath`.

```kotlin
class InMemoryMetadataStorage : MetadataStorageInterface {

  private data class Row(
    var name: String? = null,
    var messageIds: List<String> = emptyList(),
    var timestamp: Instant? = null,
    var ownerId: String? = null,
    var workerId: String? = null,
    var path: String? = null,
  )

  private val rows = ConcurrentHashMap<String, Row>()
  private fun row(s: Session) = rows.getOrPut(s.sessionId) { Row() }

  override fun getSessionName(user: User?, session: Session) =
    rows[session.sessionId]?.name ?: session.sessionId
  override fun setSessionName(user: User?, session: Session, name: String) { row(session).name = name }

  override fun getMessageIds(user: User?, session: Session) =
    rows[session.sessionId]?.messageIds ?: emptyList()
  override fun setMessageIds(user: User?, session: Session, ids: List<String>) {
    row(session).messageIds = ids.toList()
  }

  // MUST override: the interface default calls itself.
  override fun getSessionTimestamp(user: User?, session: Session): Instant? =
    rows[session.sessionId]?.timestamp
  override fun setSessionTimestamp(user: User?, session: Session, time: Instant) {
    row(session).timestamp = time
  }

  override fun getSessionOwner(session: Session) = rows[session.sessionId]?.ownerId
  override fun setSessionOwner(session: Session, ownerId: String?) { row(session).ownerId = ownerId }

  override fun getSessionWorker(session: Session) = rows[session.sessionId]?.workerId
  override fun setSessionWorker(session: Session, ownerId: String?) { row(session).workerId = ownerId }

  // MUST override: SessionMetadata.path is write-only otherwise.
  override fun getSessionPath(user: User?, session: Session) = rows[session.sessionId]?.path
  override fun setSessionPath(user: User?, session: Session, path: String?) { row(session).path = path }

  // MUST override: defaults call themselves.
  override fun listSessionsByPath(path: String) =
    rows.entries.filter { it.value.path == path }.map { it.key }
  override fun listSessionsForUser(user: User) =
    rows.entries.filter { it.value.ownerId == user.id }.map { it.key }

  // Override the timestamp heuristic with a real existence check.
  override fun exists(user: User?, session: Session) = rows.containsKey(session.sessionId)

  override fun deleteSession(user: User?, session: Session) { rows.remove(session.sessionId) }
}
```

Consuming it with a patch (the only way to *clear* a field):

```kotlin
storage.updateSessionMetadata(
  user, session,
  SessionMetadataPatch(
    name     = Patch.Set("Renamed session"),
    workerId = Patch.Set(null),              // clear the worker assignment
    // sessionTime left Unchanged
  )
)
```

Registering a typed plugin auth chain over the event bus:

```kotlin
class DenyDeletes : AuthorizationChain {
  override val name = "deny-deletes"
  override fun isAuthorized(r: ResourceRef?, p: Principal, op: OperationType) =
    if (op == OperationType.Delete) false else null   // null == defer to next link
}

bus.publish(
  PluginEvents.REGISTER_AUTH_CHAIN_TOPIC,
  PluginEvents.AuthChainRegistration("deny-deletes", DenyDeletes())
)
// subscriber side: registration.typedChain ?: log.warn("plugin supplied a non-AuthorizationChain")
```

---

## ⚠ Known hazards: self-recursive defaults

Several interface defaults were written during the `Deprecated`-overload migration and currently
**delegate to themselves**, which means an implementation that does not override them will
`StackOverflowError` at runtime. They are `@Suppress("DEPRECATION")`-annotated, so the compiler is
silent. Treat every row below as *effectively abstract* until it is fixed.

| Interface | Member | Symptom |
|---|---|---|
| `StorageInterface` | `listSessionsForUser(User?, String)` | infinite recursion |
| `MetadataStorageInterface` | `getSessionTimestamp(User?, Session)` | infinite recursion (also breaks default `exists`) |
| `MetadataStorageInterface` | `listSessionsByPath(String)` | infinite recursion (also breaks `listSessionMetadata(path)`) |
| `MetadataStorageInterface` | `listSessionsForUser(User)` | infinite recursion (also breaks `deleteAllForUser`, `listSessionMetadata(user)`) |
| `MessageStore` | `getMessageMap(User?, Session)` | infinite recursion (also breaks default `getMessage`) |
| `AuthenticationInterface` | `logoutIfMatching(String, User)` | infinite recursion |
| `AuthorizationInterface` | `isAuthorized(ResourceRef?, Principal, OperationType)` | default delegates to the *same* overload |
| `GiftedCreditsInterface` | `createGift(...)` | body resolves back to itself via default parameters |
| `GiftedCreditsInterface` | `claim(User, String, String?)` | calls `claim(user, giftId)`, i.e. itself |

Other rough edges worth knowing:

* `StorageInterface.deleteSessionIfExists` always returns `true` — it cannot actually detect absence.
* `ApplicationServicesConfig.defaultUser` ignores the lock (see above).
* `MetadataStorageInterface`'s KDoc references a `setSessionMetadata` member that no longer exists on
the interface; `SessionMetadata.asPatch()` is the intended bridge.
* `Session`'s `init` calls an `open` member (documented leaking-`this`).

If you fix one of these, the correct shape is: the default keeps the **new** signature and delegates
to the **deprecated** one (or vice versa) — never to itself. Add a regression test that calls the
default on a minimal implementation.

---

## Deprecations & migration map

| Deprecated | Replacement | Why |
|---|---|---|
| `SessionFileStore.getUserDir` / `getSystemDir` | `SessionContentStore.openRead/openWrite/list/exists/delete` | `File` leaks the local FS and grants directory-wide authority (§3.3) |
| `SessionFileStore.userRoot(User?)` | `userRootFor(User)` | accepted `null` then threw on it |
| `User.NULL` | `Principal.Anonymous` / `Principal.System` | overlapping semantics vs `null` and `defaultUser` (§3.2, §4.1) |
| `Session.NULL` | nullable `Session` | invalid empty id; storage must reject it (§3.11) |
| `Class<*>` authorization keys | `ResourceRef` (`ResourceRef.of(Class<*>?)` bridges) | not serializable, no instance scope (§3.5) |
| `User?` "who is acting" parameters | `Principal` (`Principal.of(User?)` bridges) | tri-state ambiguity (§4.1) |
| `Double` money on `Gift` | `Credits` / `*Credits` accessors / `GiftStats` | unsound comparisons (§3.7) |
| `Boolean` from `claimGift` | `ClaimResult` | no failure reason (§3.7) |
| snapshot metadata writes | `SessionMetadataPatch` + `updateSessionMetadata` | could not clear fields (§3.4) |
| `AuthenticationInterface.getAccessToken` (removed) | `TokenMetadata` + `listTokens` | reverse token lookup implies recoverable secrets (§3.6) |
| `ApplicationServicesConfig.isLocked = true` | `ApplicationServicesConfig.lock()` | one-way transition (§3.9) |
| `AuthenticationInterface.AUTH_COOKIE` | web-layer constant | transport concern |
| unbounded `list*` | `Page` / `PageResult` overloads | unbounded result sets (§4.3) |

---

## Security notes

* **Plugin loading executes untrusted code with full JVM privileges.** There is no sandbox, no
classloader isolation guarantee, and no signature verification in this package. Deployments must
install only trusted (ideally signed) artifacts (§3.8).
* **Path traversal.** Every `SessionContentStore` implementation must reject paths that escape the
session root. The default `StorageInterface` implementations do this via `resolveSessionFile`;
reimplement the check if you bypass them.
* **Tokens.** Hash at rest. `AccessToken.toString()` renders `AccessToken(***)` specifically so naive
logging and string interpolation cannot leak it — do not add a `value`-revealing `toString`.
* **PII in logs.** `User.toString()` is redacted. Prefer `user.id` or `user.redactedEmail` in log
statements; only touch `user.email` where the address is functionally required.
* **Fail-secure authorization.** A backend outage must produce *deny*, never *allow*.
* **Session ids** come from `SecureRandom` via `Session.randomId`. Never construct ids from
predictable data, and always `tryParse` untrusted ids before using them as storage keys.

---

## Conformance test checklist

Write these as a shared abstract test class per port so every backend runs the same suite.

**`SessionContentStore`**
- [ ] `openWrite` creates missing parent directories
- [ ] `openRead` on a missing path throws `FileNotFoundException`
- [ ] `list(prefix)` returns `/`-separated, session-root-relative paths and honours the prefix
- [ ] `list` on a nonexistent session returns `emptyList()`
- [ ] `delete` returns `false` for a missing path, `true` after a write
- [ ] `../`, absolute paths, and symlink escapes all throw `IllegalArgumentException`
- [ ] `session.isNull()` is rejected

**`MessageStore` / `JsonStore`**
- [ ] `getMessageMap` preserves insertion order and is safe to mutate by the caller
- [ ] concurrent `updateMessage` calls for one session lose no messages
- [ ] `getJson` round-trips `setJson`; returns `null` for a missing slot

**`MetadataStorageInterface`**
- [ ] every accessor round-trips, **including `path`**
- [ ] `exists` is `false` before any write and `true` after (not just after a timestamp write)
- [ ] `Patch.Set(null)` clears `name`/`ownerId`/`workerId`/`path`; `Patch.Unchanged` preserves them
- [ ] `getSessionMetadataMap` omits unknown ids
- [ ] `deleteSession` / `deleteAllForUser` remove everything and report correct counts
- [ ] paged `listSessionEntries` enumerates each row exactly once across pages

**`AuthenticationInterface`**
- [ ] `getUser(null)` → `null`; unknown token → `null`
- [ ] expired token → `null` (if `ttl` is supported)
- [ ] `logoutIfMatching` → `true` once, then `false`; `false` for a mismatched user
- [ ] the raw token is not recoverable from storage; `listTokens` exposes no secret
- [ ] `revokeAll` invalidates every token for the user

**`AuthorizationInterface`**
- [ ] `Principal.Anonymous` is denied anything not explicitly `Public`
- [ ] `Admin` grants every implied operation; `Read` does not grant `Write`
- [ ] instance scope works: allowed on `SessionRef(A)`, denied on `SessionRef(B)`
- [ ] a backend failure yields `false`, not an exception
- [ ] `authorizedOperations` agrees with per-operation `isAuthorized`

**`GiftedCreditsInterface`**
- [ ] rejects `amountGranted <= 0`, `totalBudget < amountGranted`, non-positive `grantDuration`
- [ ] insufficient creator balance → `IllegalStateException`
- [ ] repeated `idempotencyKey` neither double-charges nor double-grants (create *and* claim)
- [ ] concurrent claims never overspend `totalBudget` (exercise `canGrantAnother` under contention)
- [ ] `AlreadyClaimed` past `maxClaimsPerUser`; `GiftExpired`, `GiftRevoked`, `GiftNotFound` all reachable
- [ ] `expireGift` refunds unspent budget; `revokeGift` does not
- [ ] `Claim.grantedAmount`/`expiresAt` stay pinned after the gift is edited

**Plugin subsystem**
- [ ] loading the same JAR twice → `PluginAlreadyLoadedException`
- [ ] missing/non-JAR file → `PluginNotFoundException`
- [ ] `unloadPlugin` closes the classloader; `isLoaded` flips to `false`
- [ ] `deletePlugin` leaves the file in place when unload fails
- [ ] `unsubscribe` stops delivery; a throwing subscriber does not break other subscribers
- [ ] `Topic` payload of the wrong type is delivered as `null`, not a `ClassCastException`
- [ ] `shutdown()` drains subscribers and is safe to call twice

---

## Contributing to this package

**Adding a member to an existing port**

1. Prefer a *new* narrow interface over widening an existing one.
2. If you must add to an existing interface, supply a default so downstream implementations keep
compiling — either a genuine fallback (document its cost, e.g. "N+1, override me") or
`throw UnsupportedOperationException("<name> is not implemented by ${this.javaClass.name}")`.
3. **Never** write a default that delegates to a same-signature overload. See
[known hazards](#-known-hazards-self-recursive-defaults).
4. Full KDoc: every parameter, the return value, every thrown exception, and the thread-safety /
atomicity expectation.
5. Cite the driving review section (`REVIEW.md §x.y`) in the KDoc when the change is architectural.

**Adding a `list*` method**

Provide both the unbounded overload (for compatibility) and a `Page`-taking overload whose default is
`unbounded().paginate(page)`.

**Adding an identifier**

Add a `@JvmInline value class` to `model/Ids.kt` with `override fun toString() = value`, and a
typed overload alongside the `String` one (e.g. `getGift(GiftId)` next to `getGift(String)`).

**Adding a money field**

`Credits` only. If you must expose `Double` for an external API, keep the `Credits` accessor as the
source of truth and mark the `Double` form deprecated on arrival.

**Adding an event topic**

Declare the `String` constant *and* a `@JvmField val ..._TOPIC: Topic<T>` in `PluginEvents`, and give
the payload a real data class — never `Any`.