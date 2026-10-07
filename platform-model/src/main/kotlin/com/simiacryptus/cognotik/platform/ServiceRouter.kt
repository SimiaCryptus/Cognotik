package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.model.*
import com.simiacryptus.cognotik.platform.service.*
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import java.io.*
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

/**
  * Static facade over the services resolved through their [ServiceKey]s.
 *
 * Every call resolves its backing service at invocation time, so late or overridden
 * factory registrations are honoured. All interface members — including those with
 * default implementations — are forwarded, so optimized overrides in the concrete
 * implementation are always used.
 *
 * [SessionMetadataInterface] is routed through the nested [Metadata] object rather
 * than implemented here: its `deleteSession(User, Session)` has the same JVM signature
 * as [StorageInterface.deleteSession] `(User?, Session)`, so one class cannot implement both.
 *
 * Metrics interception: the router also reports metrics for the calls it forwards
 * (token usage, credit grants, gift claims, session file transfers). Recording never
 * throws and never affects the result of the intercepted call. Backends must therefore
 * NOT also record these same facts, or they will be double counted.
  *
  * Alerting: alert policy management and status are forwarded to the metrics backend;
  * [notifyAlert] is forwarded to the registered [NotificationsInterface].
 */
@Suppress("unused")
object ServiceRouter : PluginManagerInterface, AuthorizationInterface, StorageInterface, SessionMetadataInterface,
  UsageInterface, UserSettingsInterface, UserProvider, AuthenticationInterface, GiftedCreditsInterface,
   MetricsInterface, NotificationsInterface {

  /* ---------------------------------------------------------------- resolution */

   private val pluginManager: PluginManagerInterface get() = ServiceKey.PLUGIN_MANAGER.get()
   private val authorization: AuthorizationInterface get() = ServiceKey.AUTHORIZATION_MANAGER.get()
   private val storage: StorageInterface get() = ServiceKey.DATA_STORAGE.get()
   private val metadata: SessionMetadataInterface get() = ServiceKey.METADATA_DB.get()
   private val usage: UsageInterface get() = ServiceKey.USAGE_DB.get()
   private val userSettings: UserSettingsInterface get() = ServiceKey.USER_SETTINGS.get()
   private val userResolver: UserProvider get() = ServiceKey.USER_RESOLVER.get()
   private val authentication: AuthenticationInterface get() = ServiceKey.AUTHENTICATION.get()
   private val giftedCredits: GiftedCreditsInterface get() = ServiceKey.GIFTED_CREDITS.get()
  private val metrics: MetricsInterface
    get() = try {
       ServiceKey.METRICS.get()
    } catch (e: UnsupportedOperationException) {
      NoOpMetrics
    }
   private val notifications: NotificationsInterface
     get() = try {
       ServiceKey.NOTIFICATIONS.get()
     } catch (e: UnsupportedOperationException) {
       LoggingNotifications
     }

  /* ---------------------------------------------------------------- metrics interception helpers */
  private val log = LoggerFactory.getLogger(ServiceRouter::class.java)

  /** When false, session file transfers are not wrapped/metered. */
  @Volatile
  var trackFileTransfers: Boolean = true

  /** True while a gift claim is executing on this thread (suppresses ADJUSTMENT credit recording). */
  private val inGiftClaim: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

  /** Credits granted through [creditUser] during the current gift claim on this thread. */
  private val giftClaimCredits: ThreadLocal<Double> = ThreadLocal.withInitial { 0.0 }

  /** Runs a metrics recording block; never throws. */
  private inline fun safely(op: String, block: () -> Unit) {
    try {
      block()
     } catch (e: VirtualMachineError) {
       throw e
     } catch (e: Throwable) {
       // Errors (e.g. AbstractMethodError from an old AIModel without pricing, or a failed
       // MetricType initialisation) must not escape into the intercepted call.
      log.warn("Metrics recording failed: $op", e)
    }
  }

  private fun recordTransfer(
    direction: TransferDirection,
    user: User?,
    session: Session,
    startNanos: Long,
    bytes: Long,
    failed: Boolean,
  ) = safely("recordFileTransfer") {
    metrics.recordFileTransfer(
      direction = direction,
      bytes = bytes,
      duration = Duration.ofNanos(System.nanoTime() - startNanos),
      outcome = if (failed) Outcomes.FAILURE else Outcomes.SUCCESS,
      session = session,
      user = user,
    )
  }

  /**
   * Runs a gift claim. Credits granted via [creditUser] during the claim are
   * accumulated and reported once as [PaymentType.GIFT] (instead of ADJUSTMENT).
   */
  private inline fun trackClaim(user: User, block: () -> ClaimResult): ClaimResult {
    val prevFlag = inGiftClaim.get()
    val prevCredits = giftClaimCredits.get()
    inGiftClaim.set(true)
    giftClaimCredits.set(0.0)
    val granted: Double
    val result = try {
      block()
    } finally {
      granted = giftClaimCredits.get()
      inGiftClaim.set(prevFlag)
      giftClaimCredits.set(prevCredits)
    }
    if (granted > 0) {
      safely("recordCreditsGranted(gift)") {
        metrics.recordCreditsGranted(PaymentType.GIFT, Credits.of(granted), user)
      }
    }
    return result
  }


  /* ---------------------------------------------------------------- AuthorizationInterface */

  override fun isAuthorized(
    resource: ResourceRef?,
    principal: Principal,
    operationType: OperationType
  ): Boolean = authorization.isAuthorized(resource, principal, operationType)

  override fun authorizedOperations(
    resource: ResourceRef?,
    principal: Principal
  ): Set<OperationType> = authorization.authorizedOperations(resource, principal)

  /* ---------------------------------------------------------------- StorageInterface */

  override val metadataStorage: SessionMetadataInterface
    get() = Metadata

  override fun listSessionsForUser(user: User?, path: String): List<Session> =
    storage.listSessionsForUser(user, path)

  override fun listSessionsForUser(user: User?, path: String, page: Page): PageResult<Session> =
    storage.listSessionsForUser(user, path, page)

  override fun deleteSessionData(user: User?, session: Session) =
    storage.deleteSessionData(user, session)

  override fun deleteSessionIfExists(user: User?, session: Session): Boolean =
    storage.deleteSessionIfExists(user, session)

  /* SessionContentStore */

  override fun openRead(user: User?, session: Session, path: String): InputStream {
    val start = System.nanoTime()
    val delegate = try {
      storage.openRead(user, session, path)
    } catch (e: Exception) {
      if (trackFileTransfers) recordTransfer(TransferDirection.DOWNLOAD, user, session, start, 0, true)
      throw e
    }
    return if (trackFileTransfers) {
      MeteredInputStream(delegate) { bytes, failed ->
        recordTransfer(TransferDirection.DOWNLOAD, user, session, start, bytes, failed)
      }
    } else delegate
  }

  override fun openWrite(user: User?, session: Session, path: String): OutputStream = run {
    val start = System.nanoTime()
    val delegate = try {
      storage.openWrite(user, session, path)
    } catch (e: Exception) {
      if (trackFileTransfers) recordTransfer(TransferDirection.UPLOAD, user, session, start, 0, true)
      throw e
    }
    if (trackFileTransfers) {
      MeteredOutputStream(delegate) { bytes, failed ->
        recordTransfer(TransferDirection.UPLOAD, user, session, start, bytes, failed)
      }
    } else delegate
  }

  override fun list(user: User?, session: Session, prefix: String): List<String> =
    storage.list(user, session, prefix)

  override fun exists(user: User?, session: Session, path: String): Boolean =
    storage.exists(user, session, path)

  override fun delete(user: User?, session: Session, path: String): Boolean =
    storage.delete(user, session, path)

  override fun listEntries(user: User?, session: Session, prefix: String): List<SessionContentStore.ContentEntry> =
    storage.listEntries(user, session, prefix)


  /* SessionFileStore (deprecated) */

  @Deprecated(
    "Exposes the local filesystem and grants callers unrestricted authority over the " +
        "directory; use SessionContentStore (openRead/openWrite/list/delete)."
  )
  @Suppress("DEPRECATION")
  override fun getUserDir(user: User?, session: Session): File =
    storage.getUserDir(user, session)

  @Deprecated("Exposes the local filesystem; use SessionContentStore for content access.")
  @Suppress("DEPRECATION")
  override fun getSystemDir(user: User?, session: Session): File =
    storage.getSystemDir(user, session)

  override fun userRootFor(user: User): File =
    storage.userRootFor(user)

  /* MessageStore */

  override fun getMessageMap(user: User?, session: Session): Map<String, String> =
    storage.getMessageMap(user, session)

  override fun getMessage(user: User?, session: Session, messageId: String): String? =
    storage.getMessage(user, session, messageId)

  override fun getMessages(user: User?, session: Session, messageIds: Collection<String>): Map<String, String> =
    storage.getMessages(user, session, messageIds)


  override fun updateMessage(user: User?, session: Session, messageId: String, value: String) =
    storage.updateMessage(user, session, messageId, value)

  /* JsonStore */

  override fun <T : Any> setJson(user: User?, session: Session, filename: String, settings: T): T =
    storage.setJson(user, session, filename, settings)

  override fun <T : Any> getJson(user: User?, session: Session, filename: String, type: Class<T>): T? =
    storage.getJson(user, session, filename, type)

  /* ---------------------------------------------------------------- SessionMetadataInterface */

  /**
   * Router for [SessionMetadataInterface]; see the class KDoc for why this is nested.
   */
  object Metadata : SessionMetadataInterface {

    override fun getSessionName(user: User, session: Session): String =
      metadata.getSessionName(user, session)

    override fun setSessionName(user: User, session: Session, name: String) =
      metadata.setSessionName(user, session, name)

    override fun getMessageIds(user: User, session: Session): List<String> =
      metadata.getMessageIds(user, session)

    override fun setMessageIds(user: User, session: Session, ids: List<String>) =
      metadata.setMessageIds(user, session, ids)

    override fun getSessionTimestamp(user: User, session: Session): Instant? =
      metadata.getSessionTimestamp(user, session)

    override fun setSessionTimestamp(user: User, session: Session, time: Instant) =
      metadata.setSessionTimestamp(user, session, time)

    override fun listSessionsByPath(user: User, path: String): List<String> =
      metadata.listSessionsByPath(user = user, path = path)

    override fun listSessionsForUser(user: User): List<String> =
      metadata.listSessionsForUser(user)

    override fun setSessionOwner(session: Session, user: User) =
      metadata.setSessionOwner(session = session, user = user)

    override fun getSessionOwner(user: User, session: Session): String? =
      metadata.getSessionOwner(user, session)

    override fun setSessionWorker(session: Session, user: User) =
      metadata.setSessionWorker(session = session, user = user)

    override fun getSessionWorker(user: User, session: Session): String? =
      metadata.getSessionWorker(user, session)

    override fun getSessionPath(user: User, session: Session): String? =
      metadata.getSessionPath(user, session)

    override fun setSessionPath(user: User, session: Session, path: String?) =
      metadata.setSessionPath(user, session, path)

    override fun exists(user: User, session: Session): Boolean =
      metadata.exists(user, session)

    override fun deleteSession(user: User, session: Session) =
      metadata.deleteSession(user, session)

    override fun deleteAllForUser(user: User): Int =
      metadata.deleteAllForUser(user)

    override fun getSessionMetadata(user: User, session: Session): SessionMetadata =
      metadata.getSessionMetadata(user, session)

    override fun updateSessionMetadata(user: User, session: Session, patch: SessionMetadataPatch) =
      metadata.updateSessionMetadata(user, session, patch)

    override fun listSessionMetadata(user: User): List<SessionMetadata> =
      metadata.listSessionMetadata(user)

    override fun listSessionMetadata(user: User, path: String): List<SessionMetadata> =
      metadata.listSessionMetadata(user, path)

    override fun getSessionMetadataMap(user: User, sessionIds: Collection<String>): Map<String, SessionMetadata> =
      metadata.getSessionMetadataMap(user, sessionIds)

    override fun listSessionEntries(user: User): List<SessionListEntry> =
      metadata.listSessionEntries(user)

    override fun listSessionEntries(user: User, path: String): List<SessionListEntry> =
      metadata.listSessionEntries(user, path)

    override fun listSessionEntries(user: User, page: Page): PageResult<SessionListEntry> =
      metadata.listSessionEntries(user, page)

    override fun listSessionEntries(user: User, path: String, page: Page): PageResult<SessionListEntry> =
      metadata.listSessionEntries(user, path, page)

    override fun getSessionEntries(user: User, sessionIds: Collection<String>): Map<String, SessionListEntry> =
      metadata.getSessionEntries(user, sessionIds)

    override fun querySessions(user: User, query: SessionQuery): List<SessionListEntry> =
      metadata.querySessions(user, query)

    override fun querySessions(user: User, query: SessionQuery, page: Page): PageResult<SessionListEntry> =
      metadata.querySessions(user, query, page)

    override fun countSessions(user: User, query: SessionQuery): Int =
      metadata.countSessions(user, query)

    override fun listSessionPaths(user: User): List<String> =
      metadata.listSessionPaths(user)

    override fun getMessageCount(user: User, session: Session): Int =
      metadata.getMessageCount(user, session)
  }

  override fun getUserUsageSummary(user: User, from: LocalDate, to: LocalDate): Map<String, ModelSchema.Usage> =
    usage.getUserUsageSummary(user, from, to)

  override fun getSessionUsageSummary(user: User, session: Session): Map<String, ModelSchema.Usage> =
    usage.getSessionUsageSummary(user, session)

  override fun getSessionUsageSummaryBulk(
    user: User,
    sessionIds: Collection<Session>
  ): Map<Session, Map<String, ModelSchema.Usage>> =
    usage.getSessionUsageSummaryBulk(user, sessionIds)

  override fun incrementUsage(
    session: Session,
    user: User,
    model: AIModel,
    usage: ModelSchema.Usage,
    data: ModelSchema.UsageData?
  ) {
    this.usage.incrementUsage(session, user, model, usage, data)
    safely("recordTokenUsage") { metrics.recordTokenUsage(model, usage) }
  }

  override fun clear() = usage.clear()

  override fun setParentSession(user: User, child: Session, parent: Session) =
    usage.setParentSession(user, child, parent)

  override fun getParentSession(user: User, child: Session): Session? =
    usage.getParentSession(user, child)

  override fun getAvailableBudget(user: User): Double =
    usage.getAvailableBudget(user)

  override fun creditUser(user: User, amount: Double, comment: String?, metadata: Map<String, String>?): Double {
    val balance = usage.creditUser(user, amount, comment, metadata)
    if (amount > 0 && inGiftClaim.get() == true) {
      giftClaimCredits.set(giftClaimCredits.get() + amount)
    } else if (amount > 0) {
      safely("recordCreditsGranted") {
        val source = metadata?.get("payment_type")?.takeIf { it.isNotBlank() }?.let { PaymentType(it) }
          ?: PaymentType.ADJUSTMENT
        metrics.recordCreditsGranted(source, Credits.of(amount), user)
      }
    }
    return balance
  }

  override fun getUserDailyUsage(user: User, from: LocalDate, to: LocalDate): List<UsageInterface.DailyUsage> =
    usage.getUserDailyUsage(user, from, to)

  override fun getUserCredits(user: User, from: LocalDate, to: LocalDate): List<UsageInterface.CreditEntry> =
    usage.getUserCredits(user, from, to)

  override fun getUserBalance(user: User): Double =
    usage.getUserBalance(user)

  override fun getSessionUsageRows(session: Session, user: User): List<UsageInterface.UsageRow> =
    usage.getSessionUsageRows(session, user)

  override fun getUserUsageRows(
    user: User,
    from: Instant,
    to: Instant,
    includeText: Boolean
  ): List<UsageInterface.UsageRow> =
    usage.getUserUsageRows(user, from, to, includeText)

  override fun listChildSessions(user: User, parent: Session): List<Session> =
    usage.listChildSessions(user, parent)

  override fun getParentSessions(user: User, children: Collection<Session>): Map<Session, Session?> =
    usage.getParentSessions(user, children)

  override fun listChildSessionsBulk(user: User, parents: Collection<Session>): Map<Session, List<Session>> =
    usage.listChildSessionsBulk(user, parents)

  override fun listDescendantSessions(
    user: User,
    root: Session,
    maxDepth: Int,
    maxSessions: Int
  ): UsageInterface.SessionTree = usage.listDescendantSessions(user, root, maxDepth, maxSessions)


  /* ---------------------------------------------------------------- PluginManagerInterface */

  /* EventBus */

  override fun publish(topic: String, data: Any?) =
    pluginManager.publish(topic, data)

  override fun <T : Any> publish(topic: Topic<T>, data: T?) =
    pluginManager.publish(topic, data)

  override fun subscribe(topic: String, handler: (Any?) -> Unit): String =
    pluginManager.subscribe(topic, handler)

  override fun <T : Any> subscribe(topic: Topic<T>, handler: (T?) -> Unit): String =
    pluginManager.subscribe(topic, handler)

  override fun unsubscribe(subscriptionId: String) =
    pluginManager.unsubscribe(subscriptionId)

  override fun onChange(subscriber: () -> Unit): String =
    pluginManager.onChange(subscriber)

  override fun triggerChangeNotification() =
    pluginManager.triggerChangeNotification()

  /* PluginRegistry */

  override fun loadPlugin(jarFile: File): List<CognotikPlugin> =
    pluginManager.loadPlugin(jarFile)

  override fun loadPlugin(jarFile: File, entryPointClass: String): CognotikPlugin =
    pluginManager.loadPlugin(jarFile, entryPointClass)

  override fun loadPluginsFromDirectory(directory: File): Map<File, List<CognotikPlugin>> =
    pluginManager.loadPluginsFromDirectory(directory)

  override fun unloadPlugin(jarFile: File) =
    pluginManager.unloadPlugin(jarFile)

  override fun getLoadedPlugins(): Map<String, List<CognotikPlugin>> =
    pluginManager.getLoadedPlugins()

  override fun getLoadedPluginsById(): Map<PluginId, List<CognotikPlugin>> =
    pluginManager.getLoadedPluginsById()

  override fun isLoaded(jarFile: File): Boolean =
    pluginManager.isLoaded(jarFile)

  override fun increment(
    metric: CounterType,
    amount: Double,
    attributes: Attributes
  ) = metrics.increment(metric, amount, attributes)

  override fun gauge(
    metric: GaugeType,
    value: Double,
    attributes: Attributes
  ) = metrics.gauge(metric, value, attributes)

  override fun record(
    metric: DistributionType,
    value: Double,
    attributes: Attributes
  ) = metrics.record(metric, value, attributes)

  override fun registerGauge(
    metric: GaugeType,
    attributes: Attributes,
    supplier: () -> Double?
  ) = metrics.registerGauge(metric, attributes, supplier)

  override fun event(type: EventType, attributes: Attributes, timestamp: Instant) =
    metrics.event(type, attributes, timestamp)

  override fun flush() = metrics.flush()

  /* Metrics read side (optional; empty when the backend is write-only) */
  override val supportsQueries: Boolean
    get() = metrics.supportsQueries

  override fun querySeries(query: MetricQuery): List<SeriesSnapshot> =
    metrics.querySeries(query)

  override fun queryEvents(query: EventQuery): List<RecordedEvent> =
    metrics.queryEvents(query)

  override fun listMetrics(): List<MetricType> =
    metrics.listMetrics()
   /* Alerting (optional; unsupported when the backend does not evaluate policies) */
   override val supportsAlerting: Boolean
     get() = metrics.supportsAlerting
   override fun putAlertPolicy(policy: AlertPolicy): AlertPolicy? =
     metrics.putAlertPolicy(policy)
   override fun removeAlertPolicy(id: String): Boolean =
     metrics.removeAlertPolicy(id)
   override fun getAlertPolicy(id: String): AlertPolicy? =
     metrics.getAlertPolicy(id)
   override fun listAlertPolicies(): List<AlertPolicy> =
     metrics.listAlertPolicies()
   override fun evaluateAlerts() =
     safely("evaluateAlerts") { metrics.evaluateAlerts() }
   override fun listAlerts(query: AlertQuery): List<Alert> =
     metrics.listAlerts(query)
   override fun triggeredAlerts(): List<Alert> =
     metrics.triggeredAlerts()
   /* ---------------------------------------------------------------- NotificationsInterface */
   override fun notifyAlert(alert: Alert) =
     safely("notifyAlert ${alert.id}") { notifications.notifyAlert(alert) }


  /** Shuts down both the plugin manager and the metrics backend (the signatures collide). */
  override fun shutdown() {
    try {
      pluginManager.shutdown()
    } finally {
      metrics.shutdown()
    }
  }

  /* PluginInstaller */

  override fun deletePlugin(jarFile: File) =
    pluginManager.deletePlugin(jarFile)

  override fun installPlugin(jarFile: File): File =
    pluginManager.installPlugin(jarFile)

  /* ---------------------------------------------------------------- UserSettingsInterface */

  override fun getUserSettings(user: User): UserSettings =
    userSettings.getUserSettings(user)

  override fun updateUserSettings(user: User, settings: UserSettings) =
    userSettings.updateUserSettings(user, settings)

  /* ---------------------------------------------------------------- UserProvider */

  override fun authenticate(request: HttpServletRequest): User? =
    userResolver.authenticate(request)

  /* ---------------------------------------------------------------- AuthenticationInterface */

  override fun getUser(accessToken: String?): User? =
    authentication.getUser(accessToken)

  override fun getUser(accessToken: AccessToken?): User? =
    authentication.getUser(accessToken)

  override fun listTokens(user: User): List<AuthenticationInterface.TokenMetadata> =
    authentication.listTokens(user)

  override fun putUser(accessToken: String, user: User): User =
    authentication.putUser(accessToken, user)

  override fun putUser(accessToken: String, user: User, ttl: Duration?): User =
    authentication.putUser(accessToken, user, ttl)

  override fun putUser(accessToken: AccessToken, user: User, ttl: Duration?): User =
    authentication.putUser(accessToken, user, ttl)

  override fun logoutIfMatching(accessToken: String, user: User): Boolean =
    authentication.logoutIfMatching(accessToken, user)

  override fun revokeAll(user: User): Int =
    authentication.revokeAll(user)

  /* ---------------------------------------------------------------- GiftedCreditsInterface */

  override fun createGift(
    creator: User,
    amountGranted: Credits,
    grantDuration: Duration,
    totalBudget: Credits,
    theme: String?,
    idempotencyKey: String?,
    expiresAt: Instant?,
    maxClaimsPerUser: Int?,
  ): Gift = giftedCredits.createGift(
    creator = creator,
    amountGranted = amountGranted,
    grantDuration = grantDuration,
    totalBudget = totalBudget,
    theme = theme,
    idempotencyKey = idempotencyKey,
    expiresAt = expiresAt,
    maxClaimsPerUser = maxClaimsPerUser,
  )

  override fun getGift(id: String): Gift? =
    giftedCredits.getGift(id)

  override fun getGift(id: GiftId): Gift? =
    giftedCredits.getGift(id)

  override fun getGiftStats(id: GiftId): GiftStats? =
    giftedCredits.getGiftStats(id)

  override fun claim(user: User, giftId: String, idempotencyKey: String?): ClaimResult =
    trackClaim(user) { giftedCredits.claim(user, giftId, idempotencyKey) }

  override fun claim(user: User, giftId: GiftId, idempotencyKey: String?): ClaimResult =
    trackClaim(user) { giftedCredits.claim(user, giftId, idempotencyKey) }

  override fun listGifts(): List<Gift> =
    giftedCredits.listGifts()

  override fun listGifts(createdBy: String?): List<Gift> =
    giftedCredits.listGifts(createdBy)

  override fun listGifts(createdBy: String?, page: Page): PageResult<Gift> =
    giftedCredits.listGifts(createdBy, page)

  override fun revokeGift(giftId: GiftId): Boolean =
    giftedCredits.revokeGift(giftId)

  override fun expireGift(giftId: GiftId): Boolean =
    giftedCredits.expireGift(giftId)

  override fun deleteGift(giftId: GiftId): Boolean =
    giftedCredits.deleteGift(giftId)

  override fun listClaims(giftId: String?, userId: String?): List<Claim> =
    giftedCredits.listClaims(giftId, userId)

  override fun listClaims(giftId: String?, userId: String?, page: Page): PageResult<Claim> =
    giftedCredits.listClaims(giftId, userId, page)

  override fun getSessionName(
    user: User,
    session: Session
   ): String = metadata.getSessionName(user, session)

  override fun setSessionName(
    user: User,
    session: Session,
    name: String
   ) = metadata.setSessionName(user, session, name)

  override fun getMessageIds(
    user: User,
    session: Session
   ): List<String> = metadata.getMessageIds(user, session)

  override fun setMessageIds(
    user: User,
    session: Session,
    ids: List<String>
   ) = metadata.setMessageIds(user, session, ids)

  override fun getSessionTimestamp(
    user: User,
    session: Session
   ): Instant? = metadata.getSessionTimestamp(user, session)

  override fun setSessionTimestamp(
    user: User,
    session: Session,
    time: Instant
   ) = metadata.setSessionTimestamp(user, session, time)

  override fun listSessionsByPath(
    user: User,
    path: String
   ) = metadata.listSessionsByPath(user, path)

   override fun listSessionsForUser(user: User) = metadata.listSessionsForUser(user)

  override fun setSessionOwner(
    session: Session,
    user: User
   ) = metadata.setSessionOwner(session, user)

  override fun getSessionOwner(
    user: User,
    session: Session
   ): String? = metadata.getSessionOwner(user, session)

  override fun setSessionWorker(
    session: Session,
    user: User
   ) = metadata.setSessionWorker(session, user)

  override fun getSessionWorker(
    user: User,
    session: Session
   ): String? = metadata.getSessionWorker(user, session)

  override fun deleteSession(
    user: User,
    session: Session
   ) = metadata.deleteSession(user, session)

  override fun getSessionPath(user: User, session: Session): String? =
    metadata.getSessionPath(user, session)

  override fun setSessionPath(user: User, session: Session, path: String?) =
    metadata.setSessionPath(user, session, path)

  override fun exists(user: User, session: Session): Boolean =
    metadata.exists(user, session)

  override fun deleteAllForUser(user: User): Int =
    metadata.deleteAllForUser(user)

  override fun getSessionMetadata(user: User, session: Session): SessionMetadata =
    metadata.getSessionMetadata(user, session)

  override fun updateSessionMetadata(user: User, session: Session, patch: SessionMetadataPatch) =
    metadata.updateSessionMetadata(user, session, patch)

  override fun listSessionMetadata(user: User): List<SessionMetadata> =
    metadata.listSessionMetadata(user)

  override fun listSessionMetadata(user: User, path: String): List<SessionMetadata> =
    metadata.listSessionMetadata(user, path)

  override fun getSessionMetadataMap(user: User, sessionIds: Collection<String>): Map<String, SessionMetadata> =
    metadata.getSessionMetadataMap(user, sessionIds)

  override fun listSessionEntries(user: User): List<SessionListEntry> =
    metadata.listSessionEntries(user)

  override fun listSessionEntries(user: User, path: String): List<SessionListEntry> =
    metadata.listSessionEntries(user, path)

  override fun listSessionEntries(user: User, page: Page): PageResult<SessionListEntry> =
    metadata.listSessionEntries(user, page)

  override fun listSessionEntries(user: User, path: String, page: Page): PageResult<SessionListEntry> =
    metadata.listSessionEntries(user, path, page)

  override fun getSessionEntries(user: User, sessionIds: Collection<String>): Map<String, SessionListEntry> =
    metadata.getSessionEntries(user, sessionIds)

  override fun querySessions(user: User, query: SessionQuery): List<SessionListEntry> =
    metadata.querySessions(user, query)

  override fun querySessions(user: User, query: SessionQuery, page: Page): PageResult<SessionListEntry> =
    metadata.querySessions(user, query, page)

  override fun countSessions(user: User, query: SessionQuery): Int =
    metadata.countSessions(user, query)

  override fun listSessionPaths(user: User): List<String> =
    metadata.listSessionPaths(user)

  override fun getMessageCount(user: User, session: Session): Int =
    metadata.getMessageCount(user, session)
  
  class MeteredOutputStream(delegate: OutputStream, function: (bytes: Long, failed: Boolean) -> Unit) :
    FilterOutputStream(delegate) {
    private var bytesWritten: Long = 0
    private val function: (bytes: Long, failed: Boolean) -> Unit = function
    private val closed = AtomicBoolean(false)

    override fun write(b: Int) {
      super.write(b)
      bytesWritten++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
      super.write(b, off, len)
      bytesWritten += len
    }

    @Throws(IOException::class)
    override fun close() {
      if (closed.compareAndSet(false, true)) {
        try {
          super.close()
        } catch (e: IOException) {
          function(bytesWritten, true)
          throw e
        }
        function(bytesWritten, false)
      }
    }

  }

  class MeteredInputStream(delegate: InputStream, function: (bytes: Long, failed: Boolean) -> Unit) :
    FilterInputStream(delegate) {
    private var bytesRead: Long = 0
    private val function: (bytes: Long, failed: Boolean) -> Unit = function
    private val closed = AtomicBoolean(false)

    override fun read(): Int {
      val result = super.read()
      if (result != -1) bytesRead++
      return result
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
      val result = super.read(b, off, len)
      if (result != -1) bytesRead += result
      return result
    }

    @Throws(IOException::class)
    override fun close() {
      if (closed.compareAndSet(false, true)) {
        try {
          super.close()
        } catch (e: IOException) {
          function(bytesRead, true)
          throw e
        }
        function(bytesRead, false)
      }
    }

  }
}