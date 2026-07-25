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
    val resource_path: String?,
    val category: String? = null,
    val tags: List<String> = emptyList(),
    val cardClass: String? = null,
    val readme: String? = null,
    val hasBackground: Boolean = false,
    val hasIcon: Boolean = false,
    val classLoader: ClassLoader = this.javaClass.classLoader,
    val videoUrl: String? = null,
    val exampleSessions: Map<String, String>? = null,
) : DynamicEnum<AppEntry>(name) {

    companion object {
        @JvmStatic
        fun register(entry: AppEntry) {
            DynamicEnum.register(AppEntry::class.java, entry)
        }

        @JvmStatic
        @Suppress("unused")
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