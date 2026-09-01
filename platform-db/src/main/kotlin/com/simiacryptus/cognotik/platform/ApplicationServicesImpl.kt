package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.file.AuthenticationManager
import com.simiacryptus.cognotik.platform.file.AuthorizationManager
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig.isLocked
import com.simiacryptus.cognotik.util.LazyReference
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class ApplicationServicesImpl : ApplicationServices {

  companion object {
    val log = org.slf4j.LoggerFactory.getLogger(ApplicationServicesImpl::class.java)
    init {
      ApplicationServices.services = ApplicationServicesImpl()
    }

    var pluginManager: PluginManagerInterface
      get() = (ApplicationServices.services
        ?: throw IllegalStateException("ApplicationServices not initialized")).pluginManager
      set(value) { ApplicationServices.services?.pluginManager = value }
    var authorizationManager: AuthorizationInterface
      get() = (ApplicationServices.services
        ?: throw IllegalStateException("ApplicationServices not initialized")).authorizationManager
      set(value) { ApplicationServices.services?.authorizationManager = value }
    var authenticationManager: AuthenticationInterface
      get() = (ApplicationServices.services
        ?: throw IllegalStateException("ApplicationServices not initialized")).authenticationManager
      set(value) { ApplicationServices.services?.authenticationManager = value }
    var threadPoolManager: ThreadPoolManager
      get() = (ApplicationServices.services
        ?: throw IllegalStateException("ApplicationServices not initialized")).threadPoolManager
      set(value) { ApplicationServices.services?.threadPoolManager = value }
    fun fileApplicationServices(root: File = ApplicationServicesConfig.dataStorageRoot) =
      (ApplicationServices.services
        ?: throw IllegalStateException("ApplicationServices not initialized")).fileApplicationServices(root)
    var fileApplicationServices: (File) -> IFileApplicationServices
      get() = (ApplicationServices.services
        ?: throw IllegalStateException("ApplicationServices not initialized")).fileApplicationServices
      set(value) { ApplicationServices.services?.fileApplicationServices = value }
  }
  private val isInitialized = AtomicBoolean(false)

  private val _pluginManager: LazyReference<PluginManagerInterface> = LazyReference(isInitialized) { PluginManager() }
  override var pluginManager: PluginManagerInterface
    get() = _pluginManager()
    set(value) {
      _pluginManager.value = value
    }

  private val _authorizationManager: LazyReference<AuthorizationInterface> =
    LazyReference(isInitialized) { AuthorizationManager() }
  override var authorizationManager: AuthorizationInterface
    get() = _authorizationManager()
    set(value) {
      _authorizationManager.value = value
    }

  private val _authenticationManager: LazyReference<AuthenticationInterface> =
    LazyReference(isInitialized) { AuthenticationManager() }
  override var authenticationManager: AuthenticationInterface
    get() = _authenticationManager()
    set(value) {
      _authenticationManager.value = value
    }

  private val _threadPoolManager: LazyReference<ThreadPoolManager> =
    LazyReference(isInitialized) { ThreadPoolManager() }
  override var threadPoolManager: ThreadPoolManager
    get() = _threadPoolManager()
    set(value) {
      _threadPoolManager.value = value
    }

  private val fileApplicationServicesCache = ConcurrentHashMap<File, FileApplicationServices>()

  override fun fileApplicationServices(root: File) =
    fileApplicationServices.invoke(root)

  override var fileApplicationServices: (File) -> IFileApplicationServices =
    { rootDir -> fileApplicationServicesCache.getOrPut(rootDir) { FileApplicationServices(rootDir) } }
    set(value) {
      require(!isLocked) { "ApplicationServices is locked" }
      field = value
    }
}

