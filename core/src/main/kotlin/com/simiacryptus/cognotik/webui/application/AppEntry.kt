package com.simiacryptus.cognotik.webui.application

import com.simiacryptus.cognotik.util.DynamicEnum

class AppEntry(
    name: String,
    val id: String = name,
    val displayName: String = name,
    val icon: String = "📱",
    val description: String = "",
    val badge: String? = null,
    val badgeClass: String? = null,
    val type: String = "docops",
    val path: String = "/$name",
    val appId: String? = null,
    val cardClass: String? = null,
    val classLoader: ClassLoader = this.javaClass.classLoader,
) : DynamicEnum<AppEntry>(name) {

    companion object {
        @JvmStatic
        fun register(entry: AppEntry) {
            DynamicEnum.register(AppEntry::class.java, entry)
        }

        @JvmStatic
        fun unregister(name: String): Boolean {
            return DynamicEnum.unregister(AppEntry::class.java, name)
        }

        @JvmStatic
        fun values(): List<AppEntry> {
            return DynamicEnum.values(AppEntry::class.java)
        }

        @JvmStatic
        fun valueOf(name: String): AppEntry {
            return DynamicEnum.valueOf(AppEntry::class.java, name)
        }
    }
}