package com.simiacryptus.cognotik.platform

import java.io.File

interface ApplicationServices {
  var pluginManager: PluginManagerInterface
  var authorizationManager: AuthorizationInterface
  var authenticationManager: AuthenticationInterface
  var threadPoolManager: ThreadPoolManager

  var fileApplicationServices: (File) -> IFileApplicationServices

  fun fileApplicationServices(root: File): IFileApplicationServices

  companion object {
    @JvmStatic
    var services: ApplicationServices? = null
  }
}

