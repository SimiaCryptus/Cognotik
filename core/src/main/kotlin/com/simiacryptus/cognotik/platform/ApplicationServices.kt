package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.file.AuthenticationManager
import com.simiacryptus.cognotik.platform.file.AuthorizationManager
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.file.UserSettingsManager
import com.simiacryptus.cognotik.platform.hsql.HSQLMetadataStorage
import com.simiacryptus.cognotik.platform.hsql.HSQLUsageManager
import com.simiacryptus.cognotik.platform.model.*
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig.dataStorageRoot
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig.isLocked
import java.io.File

object ApplicationServices {

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

    private val storageCache = mutableMapOf<File, StorageInterface>()
    var dataStorageFactory: (File) -> StorageInterface = { file ->
        storageCache.getOrPut(file) { DataStorage(file) }
    }
        private set(value) {
            require(!isLocked) { "ApplicationServices is locked" }
            field = value
        }

    private val metadataStorageCache = mutableMapOf<File, MetadataStorageInterface>()
    var metadataStorageFactory: (File) -> MetadataStorageInterface = { file ->
        metadataStorageCache.getOrPut(file) { HSQLMetadataStorage(file) }
    }
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