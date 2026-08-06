package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.platform.file.AuthenticationManager
import com.simiacryptus.cognotik.platform.file.AuthorizationManager
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.hsql.GiftedCreditsDB
import com.simiacryptus.cognotik.platform.hsql.MetadataStorageDB
import com.simiacryptus.cognotik.platform.hsql.UsageDB
import com.simiacryptus.cognotik.platform.hsql.UserSettingsDB
import com.simiacryptus.cognotik.platform.model.*
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig.isLocked
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object ApplicationServices {
  @JvmStatic
  var _pluginManager: PluginManagerInterface? = null

  @JvmStatic
  var pluginManager: PluginManagerInterface
    get() = _pluginManager ?: synchronized(this) {
      _pluginManager ?: PluginManager().also { _pluginManager = it }
    }
    set(value) {
      require(!isLocked) { "ApplicationServices is locked" }
      _pluginManager = value
    }

  @JvmStatic
  var authorizationManager: AuthorizationInterface = AuthorizationManager()
    set(value) {
      require(!isLocked) { "ApplicationServices is locked" }
      field = value
    }

  @JvmStatic
  var authenticationManager: AuthenticationInterface = AuthenticationManager()
    set(value) {
      require(!isLocked) { "ApplicationServices is locked" }
      field = value
    }

  @JvmStatic
  var threadPoolManager: ThreadPoolManager =
    ThreadPoolManager()
    private set(value) {
      require(!isLocked) { "ApplicationServices is locked" }
      field = value
    }

  @JvmStatic
  private val fileApplicationServicesCache = ConcurrentHashMap<File, FileApplicationServices>()

  @JvmStatic
  fun fileApplicationServices(root: File = ApplicationServicesConfig.dataStorageRoot) =
    fileApplicationServices.invoke(root)

  @JvmStatic
  var fileApplicationServices: (File) -> FileApplicationServices =
    { rootDir -> fileApplicationServicesCache.getOrPut(rootDir) { FileApplicationServices(rootDir) } }
    set(value) {
      require(!isLocked) { "ApplicationServices is locked" }
      field = value
    }
}

open class FileApplicationServices(val rootDir: File) {

  open val dataStorageFactory: DataStorage by lazy {
    DataStorage(
      dataDir = rootDir.resolve("data"),
      metadataStorage = metadataDB
    )
  }
  open val metadataDB: MetadataStorageInterface by lazy { MetadataStorageDB() }
  open val usageDB: UsageInterface by lazy {
    UsageDB().apply {
      ChatModel.ON_USAGE =
        { model, usage, user, session, data -> this.incrementUsage(session, user, model, usage, data) }
    }
  }
  open val userSettingsManager: UserSettingsInterface by lazy { UserSettingsDB() }

  /**
   * Promotional credit gifts. Exposed here so callers do not have to construct
   * [GiftedCreditsDB] (and therefore its [DatabaseFacet]) themselves.
   */
  open val giftedCreditsDB: GiftedCreditsInterface by lazy { GiftedCreditsDB(rootDir.resolve("giftsdb")) }
}

fun ChatModel.instance(user: User) = ApiChatModel(
  model = this,
  provider = ApplicationServices.fileApplicationServices().userSettingsManager
    .getUserSettings(user).apis.find { it.provider == this.provider })