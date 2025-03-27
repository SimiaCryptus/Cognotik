package com.simiacryptus.skyenet.core.platform

import com.simiacryptus.skyenet.core.platform.file.*
import com.simiacryptus.skyenet.core.platform.hsql.HSQLMetadataStorage
import com.simiacryptus.skyenet.core.platform.hsql.HSQLUsageManager
import com.simiacryptus.skyenet.core.platform.model.ApplicationServicesConfig.dataStorageRoot
import com.simiacryptus.skyenet.core.platform.model.ApplicationServicesConfig.isLocked
import com.simiacryptus.skyenet.core.platform.model.AuthenticationInterface
import com.simiacryptus.skyenet.core.platform.model.AuthorizationInterface
import com.simiacryptus.skyenet.core.platform.model.CloudPlatformInterface
import com.simiacryptus.skyenet.core.platform.model.MetadataStorageInterface
import com.simiacryptus.skyenet.core.platform.model.StorageInterface
import com.simiacryptus.skyenet.core.platform.model.UsageInterface
import com.simiacryptus.skyenet.core.platform.model.UserSettingsInterface
import com.simiacryptus.skyenet.core.util.Selenium
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ExecutorService

object ApplicationServices {
  private val log = LoggerFactory.getLogger(ApplicationServices::class.java)

  var authorizationManager: AuthorizationInterface = AuthorizationManager()
    set(value) {
      require(!isLocked) { "ApplicationServices is locked" }
      field = value
    }
  var userSettingsManager: UserSettingsInterface = UserSettingsManager()
    private set(value) {
      require(!isLocked) { "ApplicationServices is locked" }
      field = value
    }
  var authenticationManager: AuthenticationInterface = AuthenticationManager()
    set(value) {
      require(!isLocked) { "ApplicationServices is locked" }
      field = value
    }
  var dataStorageFactory: (File) -> StorageInterface = { DataStorage(it) }
    private set(value) {
      require(!isLocked) { "ApplicationServices is locked" }
      field = value
    }
  var metadataStorageFactory: (File) -> MetadataStorageInterface = { HSQLMetadataStorage(it) }
    private set(value) {
      require(!isLocked) { "ApplicationServices is locked" }
      field = value
    }
  var clientManager: ClientManager = ClientManager()
    set(value) {
      require(!isLocked) { "ApplicationServices is locked" }
      field = value
    }
  var cloud: CloudPlatformInterface? = AwsPlatform.get()
    set(value) {
      require(!isLocked) { "ApplicationServices is locked" }
      field = value
    }
  var usageManager: UsageInterface = HSQLUsageManager(File(dataStorageRoot, "usage"))
    private set(value) {
      require(!isLocked) { "ApplicationServices is locked" }
      field = value
    }

}