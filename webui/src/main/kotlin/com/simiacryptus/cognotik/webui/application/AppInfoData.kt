package com.simiacryptus.cognotik.webui.application

data class AppInfoData(
  val applicationName: String,
  val singleInput: Boolean,
  val stickyInput: Boolean,
  val loadImages: Boolean,
  val showMenubar: Boolean
) {

  fun toMap(): Map<String, Any> {
    return this::class.java.declaredFields.associate { it.name to it.get(this) }
  }
}