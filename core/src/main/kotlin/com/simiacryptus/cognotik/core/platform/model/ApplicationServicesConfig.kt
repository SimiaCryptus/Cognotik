package com.simiacryptus.cognotik.core.platform.model

import java.io.File

object ApplicationServicesConfig {

  var isLocked: Boolean = false
    set(value) {
      require(!isLocked) { "ApplicationServices is locked" }
      field = value
    }
    var dataStorageRoot: File = File(System.getProperty("user.home"), ".cognotik")
    set(value) {
      require(!isLocked) { "ApplicationServices is locked" }
      field = value
    }
}