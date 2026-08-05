# Platform API Review

Scope: `com.simiacryptus.cognotik.platform` service interfaces and
`com.simiacryptus.cognotik.platform.model` value types.

Severity legend: **[H]** high (correctness/security), **[M]** medium (design/maintainability), **[L]** low
(polish/docs).

---

## 1. Executive Summary

This is a well-intentioned "ports" layer: a small set of interfaces that decouple the application from storage, auth,
and plugin backends. The KDoc coverage is unusually good for this kind of module, and the use of interfaces + default
methods makes the layer swappable and testable.

The main problems are architectural rather than syntactic:

1. **Leaky abstractions** — `java.io.File`, `jakarta.servlet.*`, and `Class<*>` appear in port interfaces, which pins
   implementations to a local filesystem, a servlet container, and a single JVM classloader respectively.
2. **God interfaces** — `StorageInterface` and `PluginManagerInterface` each own 3–4 unrelated responsibilities.
3. **Convenient-but-dangerous default methods** — the N+1 fallbacks in
   `MetadataStorageInterface` silently turn one listing into `4 × N` backend calls, which is exactly the problem they
   were introduced to fix.
4. **Model inconsistencies** — money as `Double`, time as both `Date` and `Instant`, two different `equals` strategies,
   global mutable state (`defaultUser`,
   `ApplicationServicesConfig`), and metadata fields that can be written but never read.
5. **Weak security contracts** — non-cryptographic session ID generation, reverse token lookup, no token expiry, and
   authorization that cannot express per-resource permissions.

Nothing here is unrecoverable; most items are additive or mechanical refactors.

---

## 2. What Is Good

* **Ports-and-adapters shape.** Behaviour is described by interfaces with no implementation leakage into call sites.
  This is the right foundation.
* **Documentation discipline.** `AuthorizationInterface`, `AuthenticationInterface`, and
  `MetadataStorageInterface` document parameters, nullability intent, and failure modes.
  `AuthorizationInterface` even documents the *fail-securely* expectation, which is the kind of contract that usually
  only lives in someone's head.
* **Deliberate evolution path.** `SessionMetadata` / `SessionListEntry` / `listSessionEntries`
  show an explicit, backwards-compatible response to an N+1 problem, with the older
  `StorageInterface` accessors marked `@Deprecated`. Good instinct.
* **Projection types.** `SessionListEntry` is a proper read-model for a listing page rather than reusing the full
  aggregate. This is a pattern worth applying elsewhere.
* **Config locking.** `ApplicationServicesConfig.isLocked` is a pragmatic guard against late reconfiguration of global
  services.
* **Small, immutable models.** `Gift`, `Claim`, `SessionMetadata`, `SessionListEntry` are
  `data class`es with sensible defaults.
* **Plugin event decoupling.** `PluginEvents` avoids plugins depending on servlet classes for auth-chain registration —
  the intent is correct even if the typing is weak.

---

## 3. Findings by File

### 3.1 `model/Session.kt`

* **[H] Non-cryptographic ID generation.** `long64()` uses `kotlin.random.Random`. Session IDs are bearer-ish
  identifiers (they appear in URLs, directory names, and global-share links); a predictable PRNG allows session
  enumeration. Use `java.security.SecureRandom`.
* **[H] `id2()` can produce an invalid ID.** It base64-encodes 8 random bytes, *filters out*
  all non-alphanumerics, then `take(8)`. Base64 of 8 bytes yields ~11 chars, so a run with many `-`/`.`/`+` characters
  can leave fewer than the 4 characters demanded by
  `validateSessionId`, causing a random `IllegalArgumentException` at construction. Generate from an alphabet directly
  instead of filtering.
* **[H] Open method called from constructor.** `init { validateSessionId() }` invokes an
  `open` member; a subclass's fields are not yet initialised at that point. `Session.NULL`
  exploits this on purpose, which makes the hazard permanent.
* **[H] `Session.NULL` is a landmine.** Its `sessionId` is `""`, so `isGlobal()` is false,
  `toGlobal()` yields `"G-"`, and any storage implementation that derives a directory from it produces a path pointing
  at a parent directory. `SessionMetadata.id` even *defaults*
  to `NULL`, so a partially-constructed metadata object silently carries it around. Prefer a `sealed`/nullable model
  over a sentinel, or at minimum reject `NULL` explicitly at every storage boundary.
* **[M] Regex permits IDs the storage layer rejects.** `([GU]-)?\d{8}-[\w+.\-]{4,12}` makes the prefix optional, but
  `StorageInterface.getSystemDir` documents that the layout is derived from a `G-`/`U-` prefix. Either require the
  prefix or document the third case.
* **[M] `internal open fun validateSessionId`.** `internal` + `open` means only in-module subclasses can override — the
  extensibility is illusory. Also, `Session` is `open` with no documented extension contract.
* **[L] Two validation entry points** (`Session.validateSessionId(session)` and
  `parseSessionID`, which double-validates) with no `tryParse`/`Result` variant, so callers handling untrusted input
  must use exceptions for control flow.

### 3.2 `model/User.kt`

* **[H] Domain model depends on `jakarta.servlet`.** `UserProvider` lives in the same file as `User`, dragging the
  servlet API into `platform.model`. Move `UserProvider` to the web module; the model package should have no framework
  dependencies.
* **[H] Global mutable `defaultUser`.** A top-level `var` is shared, unsynchronised state. It makes tests
  order-dependent and is a plausible source of cross-request identity bleed. Also, `@JsonIgnore` on a top-level property
  does nothing, and `@JvmField` on a `var` with a custom global is questionable.
* **[M] `data class` with hand-written `equals`/`hashCode`.** Equality uses only `email`, but `copy()`, `toString()`,
  and destructuring still include `id`/`name`. Two users with the same email but different `id` compare equal —
  surprising, and inconsistent with the fact that `id` is what `Claim.userId` and `SessionMetadata.ownerId` store. Pick
  one identity (`id`) and either drop `data` or make all components part of identity.
* **[M] Two "magic" users.** `User.NULL` (`id="0"`) and `defaultUser` (`id="1"`) have overlapping, undocumented
  semantics versus plain `null`. Combined with the pervasive
  `User?` parameters, there are now four ways to say "no user".
* **[L] PII in `toString()`.** `override fun toString() = email` means every log line that interpolates a user leaks an
  email address. Return a redacted form or the `id`.

### 3.3 `StorageInterface.kt`

* **[H] Filesystem leaks into the port.** `getUserDir`, `getSystemDir`, and `userRoot`
  return `java.io.File`. This makes an S3/GCS/database-backed implementation impossible without a local scratch mount,
  and it hands callers unrestricted read/write/delete authority over the directory. Replace with a narrow content API
  (`openRead(path): InputStream`, `openWrite(path): OutputStream`, `list(prefix)`, `delete`)
  or an abstract `StoragePath` type.
* **[H] Four responsibilities in one interface:** session directory resolution, message store, JSON blob store, and
  (deprecated) metadata. Split into
  `SessionFileStore`, `MessageStore`, and `JsonStore`.
* **[M] Deprecated members still part of the contract.** Six `@Deprecated` members remain abstract, so every
  implementation must still implement them. Give them `default`
  implementations that delegate to `MetadataStorageInterface`, add
  `ReplaceWith(...)`, set `DeprecationLevel.WARNING → ERROR`, and record a removal target.
* **[M] Documentation drift.**
* `getUserDir` says it "delegates to `getDataDir`" — no such method exists.
* `getSystemDir` is documented as "Gets the data directory", contradicting its name.
* **[M] Asymmetric API.** `setJson` exists with no `getJson`, so reads happen through
  `getUserDir` + manual file access — precisely the leak described above.
* **[M] Confusing overloads.** `listSessions(user, path): List<Session>` and
  `listSessions(dir, path): List<String>` differ in both semantics and return type. Rename to `listSessionsForUser` /
  (delete the `File` variant).
* **[M] `getMessages` returns `LinkedHashMap`.** Returning a concrete mutable implementation both leaks internals and
  invites callers to mutate the result. Return
  `Map<String, String>` and document insertion-order guarantee, or return
  `List<Pair<String,String>>` if order is the point.
* **[M] `userRoot(user: User?)` accepts null then throws on null.** Make the parameter non-null and let the type system
  enforce it.
* **[L] No atomicity/concurrency contract.** `updateMessage`, `setMessageIds`, and
  `deleteSession` are read-modify-write operations with no documented isolation. Two concurrent `updateMessage` calls
  can lose a message ID.
* **[L] `deleteSession` returns `Unit`** — callers cannot distinguish "deleted" from
  "did not exist" from "partially deleted".

### 3.4 `MetadataStorageInterface.kt`

* **[H] `workerId` and `path` are write-only.** `setSessionMetadata` writes
  `metadata.workerId`, but `getSessionMetadata` never populates `workerId` or `path`. Therefore `listSessionEntries`'
  default implementation *always* reports
  `workerId = null, path = null`, silently. There is also no `setSessionPath` at all, so
  `SessionMetadata.path` can never be persisted through this interface. This is a real bug, not just an inconsistency.
* **[H] Default methods reintroduce N+1.** `listSessionEntries` → `listSessionMetadata` →
  `N × getSessionMetadata` → `4 × N` backend round-trips. The KDoc says "DB-backed implementations should override this
  for efficiency", but a default that is quietly quadratic-ish is the wrong default. Either make these abstract, or move
  the fallbacks into an `AbstractMetadataStorage` base class so opting in is explicit.
* **[M] `setSessionMetadata` semantics are unusable for clearing values.** "Only non-null fields are updated" plus "
  `messageIds` is written only if non-empty" means you cannot reset a name or clear a message list through this method,
  and the KDoc has to explain a workaround. Introduce an explicit patch type where "absent" and "set to null" are
  distinguishable (`Optional<T>`, a sealed `Patch<T>`, or a `Set<Field>` mask).
* **[M] Inconsistent signatures.** `getSessionOwner`/`setSessionOwner`/`getSessionWorker`/
  `setSessionWorker` omit the `user` parameter that every sibling method requires. Either
  `user` is meaningful for scoping or it isn't — decide once.
* **[M] Absence is indistinguishable from default.** `getSessionName` returns the session ID when unset, and
  `getSessionMetadataBulk` returns defaults for unknown sessions. Callers can never detect "this session does not
  exist". Add an `exists(session): Boolean` or make bulk lookups return `Map<String, SessionMetadata>` with missing keys
  omitted.
* **[M] `listSessions(path)` vs `listSessions(user)`** are overloads whose behaviour differs entirely; rename to
  `listSessionsByPath` / `listSessionsForUser`.
* **[M] `java.util.Date`.** Mutable, deprecated-in-spirit, and inconsistent with
  `Claim.claimedAt: Instant`. Migrate to `java.time.Instant`.
* **[M] `setSessionOwner(session, ownerId: String?)`** accepts null (clear) while
  `setSessionMetadata` only forwards non-null — so the one method that *can* clear is unreachable from the bulk path.
* **[L] No pagination, sorting, or filtering** on any listing method, and no
  `deleteAllForUser`. Listing UIs will be forced to fetch everything.
* **[L] `getSessionWorker`/`setSessionWorker` are undocumented**, and the parameter is misnamed `ownerId`.
* **[L] Ordering claim is unverifiable.** `getSessionMetadataBulk` promises "same order as
  `sessionIds`"; a `Map`-returning signature would sidestep duplicate/ordering questions.

### 3.5 `AuthorizationInterface.kt`

* **[H] No per-resource authorization.** Authorization is keyed on
  `(applicationClass, user, operationType)`. There is no way to express "user X may `Delete`
  *session S*" or "may `Share` *gift G*", yet `Share`/`Delete`/`Write` are inherently instance-scoped. Add a resource
  parameter:
  `isAuthorized(resource: ResourceRef?, user: User?, op: OperationType)`.
* **[M] `Class<*>` as an authorization key.** This couples policy to JVM type identity, so policy cannot be expressed in
  configuration, sent over the wire, or survive a rename. Use a stable string/typed identifier.
* **[M] Mixed error strategy.** The contract says "return false to fail securely" *and*
  documents `@throws SecurityException`. Callers cannot reasonably handle both. Pick one.
* **[L] No bulk query.** UIs typically need "which operations may this user perform here?". Add
  `authorizedOperations(resource, user): Set<OperationType>` to avoid 7 calls per render.
* **[L] Dangling `@see AuthorizationManager`** — the referenced class is not in this module, so the link will not
  resolve in generated docs.

### 3.6 `AuthenticationInterface.kt`

* **[H] `getAccessToken(user: User): String?` is a reverse lookup.** It implies tokens are stored recoverably (i.e. not
  hashed) and that a user has at most one token — which breaks multiple concurrent devices/sessions and makes token
  theft catastrophic. Remove it, or replace with `listSessions(user): List<TokenMetadata>` returning non-secret metadata
  only.
* **[H] No expiry, rotation, or revoke-all.** `putUser(accessToken, user)` has no TTL, and there is no `refresh`,
  `revokeAll(user)`, or last-used tracking. Sessions are effectively immortal.
* **[M] Undocumented method.** `getAccessToken` is the only member with no KDoc — in the security-critical interface.
* **[M] `logout` throws `IllegalArgumentException` on user mismatch.** This is an oracle (attacker learns a token is
  valid but belongs to someone else) and an odd exception type. Make logout idempotent and return `Boolean`.
* **[M] Raw `String` tokens.** Use a `@JvmInline value class AccessToken` so tokens cannot be accidentally logged,
  interpolated, or swapped with user IDs, and document that implementations must store a hash.
* **[L] Transport concern in a domain port.** `AUTH_COOKIE = "sessionId"` belongs in the web layer, and the name
  collides conceptually with `Session.sessionId` (they are unrelated). Cookie attributes (`HttpOnly`, `Secure`,
  `SameSite`) are undocumented.

### 3.7 `GiftedCreditsInterface.kt` + `model/Gift.kt` + `model/Claim.kt`

* **[H] Money as `Double`.** `amountGranted`, `totalBudget`, `spentBudget` are binary floating point. Repeated
  `spentBudget += amountGranted` accumulates error, and
  `spentBudget == totalBudget` comparisons are unreliable — i.e. the budget-exhaustion check is unsound. Use
  `BigDecimal` or an integral minor-unit type (`Long` micro-credits).
* **[H] `claimGift` returns `Boolean`.** The KDoc lists at least three distinct failure causes (budget exhausted,
  already claimed, not found) that the caller cannot distinguish, so the UI cannot explain the failure. Return a sealed
  result:
  `Granted(amount, expiresAt)` / `AlreadyClaimed` / `BudgetExhausted` / `Expired` /
  `NotFound`.
* **[H] No idempotency or concurrency contract.** `createGift` debits the creator's account and `claimGift` debits a
  shared budget, but neither documents atomicity, and `createGift`
  takes no idempotency key — a retried request double-charges. Document the transaction boundary explicitly.
* **[M] Mutable counters inside an immutable aggregate.** `Gift.claimants` and `spentBudget`
  are derived, contended values living in the same immutable snapshot as the definition. Split into `Gift` (definition)
  and `GiftStats`/`GiftBalance`.
* **[M] Claims lack history.** `Claim` has no claim ID, no granted amount, and no expiry snapshot. If a gift's
  `amountGranted` is ever edited, past claims become unauditable.
* **[M] Missing lifecycle operations.** No `revokeGift`, `expireGift`, `deleteGift`, no
  `expiresAt` on the gift itself, and no `maxClaimsPerUser`. There is also no
  `listGifts(createdBy)` filter despite `createdBy` existing on the model.
* **[M] No pagination.** `listGifts()` and `listClaims()` are unbounded.
* **[L] Undocumented validation contract.** What happens for `amountGranted <= 0`,
  `totalBudget < amountGranted`, negative/zero `grantDuration`, or an insufficient creator balance? Specify the
  exception type.
* **[L] `theme: String?` is an untyped magic string** with no registry; a typo silently yields a default theme. Use an
  enum or validate against a known set.
* **[L] `createdBy`/`theme` documented as "may be null for legacy gifts"** — carry a migration plan rather than
  permanent nullability.

### 3.8 `PluginManagerInterface.kt` + `model/PluginEvents.kt`

* **[H] Two interfaces wearing one hat.** Pub/sub (`publish`/`subscribe`/`unsubscribe`/
  `subscribeToChanges`/`triggerChangeNotification`) has nothing to do with plugin lifecycle (`loadPlugin`/
  `unloadPlugin`/`deletePlugin`/`getLoadedPlugins`). Split into `EventBus` and
  `PluginRegistry`; consumers that only need events should not see `deletePlugin`.
* **[H] `subscribeToChanges` has no unsubscribe.** Every caller leaks a lambda (and its captured receiver) for the
  lifetime of the manager. Return a subscription handle like
  `subscribe` does, or fold it into the topic-based API as a well-known topic.
* **[H] `deletePlugin` deletes files from disk** from inside a manager port. That is an irreversible filesystem side
  effect in an interface otherwise about loading; it belongs in an installer/repository component, and the contract says
  nothing about what happens if the unload fails first.
* **[M] Inconsistent plugin identity.** `loadPlugin`/`unloadPlugin`/`isLoaded`/`deletePlugin`
  key on `File`; `getLoadedPlugins(): Map<String, ...>` keys on an unspecified `String`. Introduce a `PluginId` (or
  document exactly what the `String` is).
* **[M] Untyped events.** `publish(topic: String, data: Any?)` plus
  `AuthChainRegistration.chain: Any` means every subscriber must blind-cast, and a payload shape change fails at runtime
  in a plugin. Introduce `Topic<T>` keys and declare a minimal
  `AuthorizationChain`-like interface in `platform.model` so `chain` can be typed.
* **[M] No versioning or dependency contract.** Nothing describes API version compatibility, load ordering, inter-plugin
  dependencies, or classloader isolation guarantees. Plugin systems that skip this are very hard to retrofit.
* **[M] Untrusted-code hazard is undocumented.** Loading arbitrary JARs grants full JVM privileges. Even if sandboxing
  is out of scope, the contract should say so explicitly, and signature/checksum verification should be considered.
* **[L] Generic exception types.** `IllegalArgumentException`/`IllegalStateException` for plugin failures; a
  `PluginException` hierarchy would let callers respond meaningfully.
* **[L] Wrong package in KDoc.** `loadPlugin` references
  `com.simiacryptus.cognotik.platform.CognotikPlugin`, but the import is
  `com.simiacryptus.cognotik.CognotikPlugin`.
* **[L] No `AutoCloseable`/shutdown hook** for draining subscribers and closing classloaders deterministically.

### 3.9 `model/ApplicationServicesConfig.kt`

* **[M] Not thread-safe.** `isLocked` and `dataStorageRoot` are plain `var`s read from many threads with no `@Volatile`/
  `AtomicBoolean`. A late write is not guaranteed visible, so the lock can be bypassed under a race.
* **[M] Global singleton config.** Parallel tests cannot use different roots; there is no way to reset for a fresh test.
  Prefer an injected immutable `PlatformConfig` data class, keeping the singleton only as a compatibility shim.
* **[L] Self-referential setter.** `isLocked`'s setter does `require(!isLocked)`, so
  `isLocked = true` works once and `isLocked = false` is impossible — correct, but obscure enough to warrant a
  `fun lock()` instead.
* **[L] No validation** that `dataStorageRoot` exists / is writable / is absolute, so failures surface far from the
  misconfiguration.

### 3.10 `model/OperationType.kt`

* **[M] No implication semantics.** The KDoc says `Admin` "typically grants all other permissions" and `Write` implies
  modification, but nothing encodes this, so every implementation re-derives the matrix (and they will disagree). Add
  `fun implies(other: OperationType): Boolean` or an explicit `implied: Set<OperationType>`.
* **[L] `Public` is not an operation.** It describes a *resource visibility state*, not an action a user performs, which
  makes `isAuthorized(..., Public)` semantically odd.

### 3.11 `model/SessionMetadata.kt` / `SessionListEntry.kt`

* **[M] Duplicated `@property ownerId`.** The KDoc documents `ownerId` twice; the first entry ("The worker ip:port")
  clearly describes `workerId`, which is otherwise undocumented.
* **[M] `id: Session = Session.NULL` default.** A metadata object silently defaulting to the invalid sentinel session
  hides construction bugs. Make `id` required.
* **[L] `SessionListEntry` duplicates six fields of `SessionMetadata`** with no shared interface, so the two will drift.
  Extract a `SessionSummary` interface, or derive one from the other with a single `toEntry()`.

---

## 4. Cross-Cutting Concerns

1. **`User?` is overloaded.** Across these interfaces, `null` means variously "anonymous",
   "global session", or "not applicable" — and `User.NULL` / `defaultUser` add two more encodings. Replace with an
   explicit sealed type:

```kt
 sealed interface Principal {
  data object Anonymous : Principal
  data object System : Principal
  data class Authenticated(val user: User) : Principal
}
```

2. **Inconsistent primitives.** `Date` vs `Instant`; `Double` for money; raw `String` for tokens, session IDs, owner
   IDs, worker IDs, plugin IDs, and theme IDs. Introduce
   `@JvmInline value class` wrappers (`UserId`, `OwnerId`, `WorkerId`, `AccessToken`,
   `PluginId`) — they are free at runtime and eliminate a whole class of argument-swap bugs.
3. **No pagination anywhere.** Every `list*` returns an unbounded `List`. Add
   `Page(limit, cursor)` parameters before the data grows.
4. **Blocking I/O with no concurrency contract.** All of these methods do network/disk I/O synchronously, and none
   document thread-safety, atomicity, or whether they may be called from a coroutine dispatcher. At minimum, document
   "implementations must be thread-safe and may block"; ideally offer `suspend` variants.
5. **Exceptions vs results.** Some methods throw (`Session` validation, `logout`,
   `userRoot`), some return `Boolean` (`claimGift`, `isAuthorized`), some return `null`
   (`getGift`, `getUser`). Adopone convention: `Result`/sealed types for expected failures, exceptions for programmer
   errors only.
6. **No contract tests.** Nothing pins the behaviour these KDocs promise, so implementations will diverge (e.g. does
   `getSessionName` really default to the session ID?). Ship an abstract TCK per interface.
7. **`REVIEW.md` had no content.** Repository docs should be real; this file replaces the placeholder.

---

## 5. Prioritised Recommendations

### Phase 1 — Correctness & security (do first, low blast radius)

1. `Session`: switch to `SecureRandom`; generate IDs from an explicit alphabet instead of filtering base64 (fixes the
   possible-invalid-ID bug).
2. `MetadataStorageInterface`: make `getSessionMetadata` read `workerId` and `path`; add
   `getSessionPath`/`setSessionPath`. This fixes the write-only-field bug.
3. Gifted credits: move money to `BigDecimal`/minor units; change `claimGift` to a sealed result; document the
   transaction boundary and add an idempotency key to `createGift`.
4. `AuthenticationInterface`: remove/replace `getAccessToken`; add TTL and
   `revokeAll(user)`; make `logout` idempotent.
5. `ApplicationServicesConfig`: mark fields `@Volatile` (or use `AtomicReference`) and replace the `isLocked` setter
   with `fun lock()`.
6. Remove `defaultUser` global mutable state; inject it.

### Phase 2 — Decoupling

7. Move `UserProvider` out of `platform.model` into the web module (removes
   `jakarta.servlet` from the model).
8. Split `StorageInterface` into `SessionFileStore` + `MessageStore` + `JsonStore`; give the deprecated metadata methods
   default delegating implementations with `ReplaceWith`.
9. Split `PluginManagerInterface` into `EventBus` + `PluginRegistry`; move `deletePlugin`
   into an installer; give `subscribeToChanges` a subscription handle.
10. Replace `java.io.File` in storage ports with a stream/path abstraction.

### Phase 3 — Model & API quality

11. Introduce `Principal`, and the `value class` ID wrappers.
12. Replace `Class<*>` in `AuthorizationInterface` with a `ResourceRef` supporting instance scope; add
    `authorizedOperations(...)`; add `OperationType.implies`.
13. Convert `Date` → `Instant` throughout metadata.
14. Replace `setSessionMetadata`'s null-means-skip convention with an explicit patch type.
15. Move the N+1 default implementations from `MetadataStorageInterface` into
    `AbstractMetadataStorage`; make the bulk methods abstract on the interface.
16. Add pagination to all `list*` methods.
17. Type the plugin event bus (`Topic<T>`), and declare a real interface for
    `AuthChainRegistration.chain`.
18. Rename overloads: `listSessionsByPath` / `listSessionsForUser`; fix the KDoc drift in
    `getUserDir`/`getSystemDir`, the duplicated `@property ownerId`, and the wrong
    `CognotikPlugin` package reference.

### Phase 4 — Assurance

19. Write abstract TCK test suites per interface (`AbstractMetadataStorageContractTest`, etc.) and run every
    implementation against them.
20. Add a concurrency test for `claimGift` budget exhaustion and for
    `updateMessage`/`setMessageIds` interleaving.
21. Add a Konsist/ArchUnit rule asserting `platform.model` has no framework imports.

---

## 6. Illustrative Sketches

Not prescriptive — just to make the recommendations concrete.

```kt
  // Phase 1: expressive claim results instead of Boolean
sealed interface ClaimResult {
  data class Granted(val amount: Credits, val expiresAt: Instant) : ClaimResult
  data object AlreadyClaimed : ClaimResult
  data object BudgetExhausted : ClaimResult
  data object GiftExpired : ClaimResult
  data object GiftNotFound : ClaimResult
}

fun claimGift(user: User, giftId: GiftId): ClaimResult
```

```kt
  // Phase 3: resource-scoped authorization with a bulk query
sealed interface ResourceRef {
  data class App(val id: String) : ResourceRef
  data class SessionRef(val id: Session) : ResourceRef
  data class GiftRef(val id: GiftId) : ResourceRef
}

interface AuthorizationInterface {
  fun isAuthorized(resource: ResourceRef?, principal: Principal, op: OperationType): Boolean
  fun authorizedOperations(resource: ResourceRef?, principal: Principal): Set<OperationType>
}
```

```kt
  // Phase 3: patch type that can distinguish "absent" from "set to null"
sealed interface Patch<out T> {
  data object Unchanged : Patch<Nothing>
  data class Set<T>(val value: T) : Patch<T>
}

data class SessionMetadataPatch(
  val name: Patch<String?> = Patch.Unchanged,
  val messageIds: Patch<List<String>> = Patch.Unchanged,
  val sessionTime: Patch<Instant?> = Patch.Unchanged,
  val ownerId: Patch<OwnerId?> = Patch.Unchanged,
  val workerId: Patch<WorkerId?> = Patch.Unchanged,
  val path: Patch<String?> = Patch.Unchanged,
)
```

---

## 7. Summary Table

| Area                        | Verdict                      | Highest-priority action                                        |
|-----------------------------|------------------------------|----------------------------------------------------------------|
| `Session`                   | Needs fixes                  | `SecureRandom`; fix `id2()` length bug; retire `NULL` sentinel |
| `User`                      | Needs fixes                  | Remove `defaultUser` global; move `UserProvider` out of model  |
| `StorageInterface`          | Needs refactor               | Split responsibilities; remove `File` from the port            |
| `MetadataStorageInterface`  | Good direction, one real bug | Read `workerId`/`path`; move N+1 defaults to a base class      |
| `AuthorizationInterface`    | Under-powered                | Add resource scope + bulk query                                |
| `AuthenticationInterface`   | Security gaps                | Drop reverse token lookup; add TTL/revocation                  |
| `GiftedCreditsInterface`    | Needs fixes                  | `BigDecimal` money; sealed claim result; idempotency           |
| `PluginManagerInterface`    | Needs refactor               | Split event bus from lifecycle; fix subscription leak          |
| `ApplicationServicesConfig` | Acceptable shim              | Add memory-visibility guarantees                               |
| `OperationType`             | Acceptable                   | Encode implication semantics                                   |

---

## 8. Implementation Status

The refactor has been applied **additively**: no existing member was removed. Members scheduled for removal carry
`@Deprecated` with a `ReplaceWith` proxy where a mechanical upgrade exists, and every newly introduced member has a
default implementation so existing implementors keep compiling. Landed:

* **Session** — `SecureRandom`; `randomId()` generated from an explicit alphabet (fixes the possible-invalid-id bug);
  `isValid`/`tryParse`; `long64()` and `NULL` deprecated; `isNull()`.
* **User** — identity is now `id`; redacted `toString()`; `UserProvider` moved to
  `platform.web` (the model alias remains, deprecated); `defaultUser` proxies to
  `ApplicationServicesConfig.defaultUser`.
* **Value types** — `UserId`, `OwnerId`, `WorkerId`, `AccessToken`, `PluginId`, `GiftId`,
  `ClaimId`, `Credits` (integral micro-credits), `Principal`, `ResourceRef`, `Patch`/
  `SessionMetadataPatch`, `Page`/`PageResult`, `Topic<T>`, `ClaimResult`, `TokenMetadata`,
  `AuthorizationChain`, `SessionSummary`, `GiftStats`.
* **StorageInterface** — split into `SessionFileStore` + `SessionContentStore` +
  `MessageStore` + `JsonStore`; `File` accessors deprecated with a stream API defaulted on top of them; deprecated
  metadata accessors now default to delegating to `metadataStorage`;
  `getJson`, `userRootFor`, `deleteSessionIfExists`, `listSessionsForUser` added.
* **MetadataStorageInterface** — `getSessionMetadata` now reads `workerId` and `path`;
  `get/setSessionPath` added (fixes the write-only-field bug); `Instant` accessors;
  `exists`; `getSessionMetadataMap`; `updateSessionMetadata(patch)`; user-scoped owner/worker overloads;
  `deleteAllForUser`; paged listings; `AbstractMetadataStorage`
  added as the opt-in home for the N+1 fallbacks.
* **AuthorizationInterface** — resource-scoped `isAuthorized(ResourceRef?, Principal, …)`
  plus `authorizedOperations`; `OperationType.implies`.
* **AuthenticationInterface** — `getAccessToken` and `logout` deprecated in favour of
  `listTokens`, `logoutIfMatching`; TTL-aware `putUser`; `revokeAll`.
* **GiftedCreditsInterface** — `Credits`-based `createGift` with idempotency key/expiry/ per-user limits; `claim`
  returning `ClaimResult`; gift lifecycle and paging; transaction/validation contracts documented.
* **Plugins** — `EventBus` / `PluginRegistry` / `PluginInstaller` split;
  `subscribeToChanges` deprecated in favour of `onChange` (returns a handle); typed
  `Topic<T>` publish/subscribe; `PluginException` hierarchy; `shutdown()`.
* **ApplicationServicesConfig** — `@Volatile` fields, `lock()`, `requireDataStorageRoot()`. Deliberately deferred to
  follow-ups (defaults currently delegate or throw
  `UnsupportedOperationException`): real implementations of `JsonStore.getJson`,
  `revokeGift`/`expireGift`/`deleteGift`, `revokeAll`, `installPlugin`, DB-side overrides of the bulk metadata queries,
  push-down of paging, the TCK suites (§4.6) and the Konsist/ArchUnit rule (Phase 4 item 21).