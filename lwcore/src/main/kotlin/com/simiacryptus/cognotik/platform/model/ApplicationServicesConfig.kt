package com.simiacryptus.cognotik.platform.model

import java.io.File

object ApplicationServicesConfig {

  @JvmStatic
  var isLocked: Boolean = false
    set(value) {
      require(!isLocked) { "ApplicationServices is locked" }
      field = value
    }

  @JvmStatic
  var dataStorageRoot: File = File(System.getProperty("user.home"), ".cognotik")
    set(value) {
      require(!isLocked) { "ApplicationServices is locked" }
      field = value
    }

}