package com.simiacryptus.cognotik

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.application.AppEntry

object DefaultApps : CognotikPlugin {

    private val log = LoggerFactory.getLogger(DefaultApps::class.java)

    private data class AppJsonEntry(
        val id: String,
        val name: String,
        val icon: String,
        val description: String,
        val badge: String?,
        val badgeClass: String?,
        val type: String,
        val path: String,
        val appId: String? = null,
        val cardClass: String? = null
    )

    override fun init() {
        try {
            val json = DefaultApps::class.java.getResourceAsStream("/apps/apps.json")
                ?.bufferedReader()?.readText()
                ?: throw IllegalStateException("apps.json not found on classpath")
            val type = object : TypeToken<List<AppJsonEntry>>() {}.type
            val entries: List<AppJsonEntry> = Gson().fromJson(json, type)
            for (entry in entries) {
                AppEntry.register(
                    AppEntry(
                        name = entry.id,
                        id = entry.id,
                        displayName = entry.name,
                        icon = entry.icon,
                        description = entry.description,
                        badge = entry.badge,
                        badgeClass = entry.badgeClass ?: "",
                        type = entry.type,
                        path = entry.path,
                        appId = entry.appId,
                        cardClass = entry.cardClass
                    )
                )
            }
            log.info("Registered ${entries.size} app entries from apps.json")
        } catch (e: Exception) {
            log.error("Failed to load app directory from apps.json: ${e.message}", e)
        }
    }
}