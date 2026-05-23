package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.platform.file.AuthenticationManager
import com.simiacryptus.cognotik.platform.file.AuthorizationManager
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.hsql.MetadataStorageDB
import com.simiacryptus.cognotik.platform.hsql.UsageDB
import com.simiacryptus.cognotik.platform.hsql.UserSettingsDB
import com.simiacryptus.cognotik.platform.model.*
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig.isLocked
import com.simiacryptus.cognotik.util.PluginManager
import java.io.File

object ApplicationServices {
    @JvmStatic
    var _pluginManager: PluginManagerInterface? = null

    @JvmStatic
    var pluginManager: PluginManagerInterface
        get() {
            if(_pluginManager == null) { _pluginManager = PluginManager() }
            return _pluginManager!!
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
    var cloud: CloudPlatformInterface? =
        AwsPlatform.get()
        set(value) {
            require(!isLocked) { "ApplicationServices is locked" }
            field = value
        }

    @JvmStatic
    private val fileApplicationServicesCache = mutableMapOf<File, FileApplicationServices>()

    @JvmStatic
    fun fileApplicationServices(root: File = ApplicationServicesConfig.dataStorageRoot) = fileApplicationServices.invoke(root)

    @JvmStatic
    var fileApplicationServices: (File) -> FileApplicationServices =
        { rootDir -> fileApplicationServicesCache.getOrPut(rootDir) { FileApplicationServices(rootDir) } }
        set(value) {
            require(!isLocked) { "ApplicationServices is locked" }
            field = value
        }
}

open class FileApplicationServices(val rootDir: File) {
    open val dataStorageFactory: DataStorage by lazy { DataStorage(dataDir = rootDir.resolve("data"), metadataStorage = metadataDB) }
    open val metadataDB: MetadataStorageDB by lazy { MetadataStorageDB() }
    open val usageDB: UsageInterface by lazy { UsageDB().apply {
        ChatModel.ON_USAGE = { model, usage, user, session -> this.incrementUsage(session, user, model, usage) }
    } }
    open val userSettingsManager: UserSettingsInterface by lazy { UserSettingsDB() }
}

fun ChatModel.instance(user: User) = ApiChatModel(
    model = this,
    provider = ApplicationServices.fileApplicationServices().userSettingsManager
        .getUserSettings(user).apis.find { it.provider == this.provider })