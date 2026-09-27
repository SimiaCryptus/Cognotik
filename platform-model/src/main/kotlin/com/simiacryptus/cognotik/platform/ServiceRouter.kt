package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.model.AIModel
import com.simiacryptus.cognotik.platform.model.AccessToken
import com.simiacryptus.cognotik.platform.model.Claim
import com.simiacryptus.cognotik.platform.model.ClaimResult
import com.simiacryptus.cognotik.platform.model.Credits
import com.simiacryptus.cognotik.platform.model.Gift
import com.simiacryptus.cognotik.platform.model.GiftId
import com.simiacryptus.cognotik.platform.model.GiftStats
import com.simiacryptus.cognotik.platform.model.ModelSchema
import com.simiacryptus.cognotik.platform.model.OperationType
import com.simiacryptus.cognotik.platform.model.Page
import com.simiacryptus.cognotik.platform.model.PageResult
import com.simiacryptus.cognotik.platform.model.PluginId
import com.simiacryptus.cognotik.platform.model.Principal
import com.simiacryptus.cognotik.platform.model.ResourceRef
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.SessionListEntry
import com.simiacryptus.cognotik.platform.model.SessionMetadata
import com.simiacryptus.cognotik.platform.model.SessionMetadataPatch
import com.simiacryptus.cognotik.platform.model.Topic
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.model.UserSettings
import com.simiacryptus.cognotik.platform.service.AuthenticationInterface
import com.simiacryptus.cognotik.platform.service.AuthorizationInterface
import com.simiacryptus.cognotik.platform.service.GiftedCreditsInterface
import com.simiacryptus.cognotik.platform.service.PluginManagerInterface
import com.simiacryptus.cognotik.platform.service.SessionMetadataInterface
import com.simiacryptus.cognotik.platform.service.StorageInterface
import com.simiacryptus.cognotik.platform.service.UsageInterface
import com.simiacryptus.cognotik.platform.service.UserProvider
import com.simiacryptus.cognotik.platform.service.UserSettingsInterface
import jakarta.servlet.http.HttpServletRequest
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Static facade over the services registered in [ServiceMap].
 *
 * Every call resolves its backing service at invocation time, so late or overridden
 * factory registrations are honoured. All interface members — including those with
 * default implementations — are forwarded, so optimized overrides in the concrete
 * implementation are always used.
 *
 * [SessionMetadataInterface] is routed through the nested [Metadata] object rather
 * than implemented here: its `deleteSession(User, Session)` has the same JVM signature
 * as [StorageInterface.deleteSession] `(User?, Session)`, so one class cannot implement both.
 */
@Suppress("unused")
object ServiceRouter : PluginManagerInterface, AuthorizationInterface, StorageInterface, SessionMetadataInterface,
  UsageInterface, UserSettingsInterface, UserProvider, AuthenticationInterface, GiftedCreditsInterface {

  /* ---------------------------------------------------------------- resolution */

  private val pluginManager: PluginManagerInterface get() = ServiceMap[ServiceKey.PLUGIN_MANAGER]
  private val authorization: AuthorizationInterface get() = ServiceMap[ServiceKey.AUTHORIZATION_MANAGER]
  private val storage: StorageInterface get() = ServiceMap[ServiceKey.DATA_STORAGE]
  private val metadata: SessionMetadataInterface get() = ServiceMap[ServiceKey.METADATA_DB]
  private val usage: UsageInterface get() = ServiceMap[ServiceKey.USAGE_DB]
  private val userSettings: UserSettingsInterface get() = ServiceMap[ServiceKey.USER_SETTINGS]
  private val userResolver: UserProvider get() = ServiceMap[ServiceKey.USER_RESOLVER]
  private val authentication: AuthenticationInterface get() = ServiceMap[ServiceKey.AUTHENTICATION]
  private val giftedCredits: GiftedCreditsInterface get() = ServiceMap[ServiceKey.GIFTED_CREDITS]

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

  override fun openRead(user: User?, session: Session, path: String): InputStream =
    storage.openRead(user, session, path)

  override fun openWrite(user: User?, session: Session, path: String): OutputStream =
    storage.openWrite(user, session, path)

  override fun list(user: User?, session: Session, prefix: String): List<String> =
    storage.list(user, session, prefix)

  override fun exists(user: User?, session: Session, path: String): Boolean =
    storage.exists(user, session, path)

  override fun delete(user: User?, session: Session, path: String): Boolean =
    storage.delete(user, session, path)

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

    override fun setSessionOwner(session: Session, user: User, ownerId: String?) =
      metadata.setSessionOwner(session = session, user = user, ownerId = ownerId)

    override fun getSessionOwner(user: User, session: Session): String? =
      metadata.getSessionOwner(user, session)

    override fun setSessionWorker(session: Session, user: User, ownerId: String?) =
      metadata.setSessionWorker(session = session, user = user, ownerId = ownerId)

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
  ) = this.usage.incrementUsage(session, user, model, usage, data)

  override fun clear() = usage.clear()

  override fun setParentSession(user: User, child: Session, parent: Session) =
    usage.setParentSession(user, child, parent)

  override fun getParentSession(user: User, child: Session): Session? =
    usage.getParentSession(user, child)

  override fun getAvailableBudget(user: User): Double =
    usage.getAvailableBudget(user)

  override fun creditUser(user: User, amount: Double, comment: String?, metadata: Map<String, String>?): Double =
    usage.creditUser(user, amount, comment, metadata)

  override fun getUserDailyUsage(user: User, from: LocalDate, to: LocalDate): List<UsageInterface.DailyUsage> =
    usage.getUserDailyUsage(user, from, to)

  override fun getUserCredits(user: User, from: LocalDate, to: LocalDate): List<UsageInterface.CreditEntry> =
    usage.getUserCredits(user, from, to)

  override fun getUserBalance(user: User): Double =
    usage.getUserBalance(user)

  override fun getSessionUsageRows(session: Session, user: User): List<UsageInterface.UsageRow> =
    usage.getSessionUsageRows(session, user)

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

  override fun shutdown() =
    pluginManager.shutdown()

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
    giftedCredits.claim(user, giftId, idempotencyKey)

  override fun claim(user: User, giftId: GiftId, idempotencyKey: String?): ClaimResult =
    giftedCredits.claim(user, giftId, idempotencyKey)

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
  ): String = ServiceMap[ServiceKey.METADATA_DB].getSessionName(user, session)

  override fun setSessionName(
    user: User,
    session: Session,
    name: String
  ) = ServiceMap[ServiceKey.METADATA_DB].setSessionName(user, session, name)

  override fun getMessageIds(
    user: User,
    session: Session
  ): List<String> = ServiceMap[ServiceKey.METADATA_DB].getMessageIds(user, session)

  override fun setMessageIds(
    user: User,
    session: Session,
    ids: List<String>
  ) = ServiceMap[ServiceKey.METADATA_DB].setMessageIds(user, session, ids)

  override fun setSessionTimestamp(
    user: User,
    session: Session,
    time: Instant
  ) = ServiceMap[ServiceKey.METADATA_DB].setSessionTimestamp(user, session, time)

  override fun setSessionOwner(
    session: Session,
    user: User,
    ownerId: String?
  ) = ServiceMap[ServiceKey.METADATA_DB].setSessionOwner(session, user, ownerId)

  override fun getSessionOwner(
    user: User,
    session: Session
  ): String? = ServiceMap[ServiceKey.METADATA_DB].getSessionOwner(user, session)

  override fun setSessionWorker(
    session: Session,
    user: User,
    ownerId: String?
  ) = ServiceMap[ServiceKey.METADATA_DB].setSessionWorker(session, user, ownerId)

  override fun deleteSession(
    user: User,
    session: Session
  ) = ServiceMap[ServiceKey.METADATA_DB].deleteSession(user, session)
}