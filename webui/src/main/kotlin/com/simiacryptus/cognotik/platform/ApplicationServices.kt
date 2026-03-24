package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.platform.file.AuthenticationManager
import com.simiacryptus.cognotik.platform.file.AuthorizationManager
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.file.UserSettingsManager
import com.simiacryptus.cognotik.platform.hsql.HSQLMetadataStorage
import com.simiacryptus.cognotik.platform.hsql.HSQLUsageManager
import com.simiacryptus.cognotik.platform.model.*
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig.isLocked
import java.io.File

object ApplicationServices {

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
  var cloud: CloudPlatformInterface? =
    AwsPlatform.get()
    set(value) {
      require(!isLocked) { "ApplicationServices is locked" }
      field = value
    }

  @JvmStatic
  private val fileApplicationServicesCache = mutableMapOf<File, FileApplicationServices>()

  @JvmStatic
  fun fileApplicationServices(rootDir: File = ApplicationServicesConfig.dataStorageRoot) =
    fileApplicationServicesCache.getOrPut(rootDir) { FileApplicationServices(rootDir) }

}

open class FileApplicationServices(val rootDir: File?) {
  open val dataStorageFactory: DataStorage by lazy {
    DataStorage(
      dataDir = rootDir?.resolve("data") ?: throw IllegalStateException("Data storage root not configured"),
      metadataStorage = metadataStorageFactory
    )
  }
  open val metadataStorageFactory: HSQLMetadataStorage by lazy { HSQLMetadataStorage(rootDir?.resolve("metadatadb")) }
  open val usageManager: UsageInterface by lazy { HSQLUsageManager(rootDir?.resolve("usagedb")) }
  open val userSettingsManager: UserSettingsInterface by lazy {
    UserSettingsManager(
      rootDir?.resolve("user_settings") ?: throw IllegalStateException("Data storage root not configured")
    )
  }
}

fun ChatModel.instance(user: User)  = ApiChatModel(model = this,
  provider = ApplicationServices.fileApplicationServices().userSettingsManager
    .getUserSettings(user).apis.find { it.provider == this.provider })
