package com.simiacryptus.cognotik.platform.service

import com.simiacryptus.cognotik.platform.ThreadPoolManager
import java.io.File

interface ApplicationServices {
  var pluginManager: PluginManagerInterface
  var authorizationManager: AuthorizationInterface
  var threadPoolManager: ThreadPoolManager

  var fileApplicationServices: (File) -> IFileApplicationServices

  fun fileApplicationServices(root: File): IFileApplicationServices

  companion object {
    @JvmStatic
    var services: ApplicationServices? = null
  }
}

