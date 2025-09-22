package com.simiacryptus.cognotik.webui.application

data class AppInfoData(
    val applicationName: String,
    val inputCnt: Int,
    val stickyInput: Boolean,
    val loadImages: Boolean = true,
    val showMenubar: Boolean
) {

    fun toMap(): Map<String, Any> {
        return this::class.java.declaredFields.associate { it.name to it.get(this) }
    }
}