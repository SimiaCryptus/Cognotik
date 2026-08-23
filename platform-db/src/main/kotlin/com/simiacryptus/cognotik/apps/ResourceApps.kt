package com.simiacryptus.cognotik.apps

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simiacryptus.cognotik.platform.CognotikPlugin
import com.simiacryptus.cognotik.webui.application.AppEntry
import org.slf4j.LoggerFactory

open class ResourceApps(
    val resourcePath: String,
    val classLoader: ClassLoader = ResourceApps::class.java.classLoader
) : CognotikPlugin {

    private val log = LoggerFactory.getLogger(ResourceApps::class.java)

    private data class AppJsonEntry(
        val id: String? = null,
        val name: String,
        val icon: String,
        val description: String,
        val path: String,
        val category: String? = null,
        val tags: List<String> = emptyList(),
        val type: String,
        val badge: String?,
        val badgeClass: String?,
        val cardClass: String? = null,
        val videoUrl: String? = null,
        val exampleSessions: Map<String, String>? = null,
    )

    override fun init() {
        val json = classLoader.getResourceAsStream(resourcePath)
            ?.bufferedReader()?.readText()
            ?: throw IllegalStateException("$resourcePath not found on classpath")
        try {
            val type = object : TypeToken<List<AppJsonEntry>>() {}.type
            val entries: List<AppJsonEntry> = Gson().fromJson(json, type)
            for (entry in entries) {
                val readme = entry.path?.let { rp ->
                    loadReadme(rp)
                }
                AppEntry.register(
                    AppEntry(
                        name = "app-" + entry.id?.removePrefix("app-"),
                        id = "app-" + entry.id,
                        displayName = entry.name,
                        icon = entry.icon,
                        description = entry.description,
                        badge = entry.badge,
                        badgeClass = entry.badgeClass ?: "",
                        type = entry.type,
                        path = "/" + (entry.path?.split('/')?.lastOrNull() ?: ""),
                        appId = entry.id?.removePrefix("app-"),
                        resource_path = entry.path,
                        cardClass = entry.cardClass,
                        readme = readme,
                        hasBackground = resourceExists("${entry.path.trimEnd('/')}/background.png"),
                        hasIcon = resourceExists("${entry.path.trimEnd('/')}/icon.png"),
                        hasSocial = resourceExists("${entry.path.trimEnd('/')}/social.png"),
                        classLoader = classLoader,
                        category = entry.category,
                        tags = entry.tags,
                        videoUrl = entry.videoUrl,
                        exampleSessions = entry.exampleSessions,
                    )
                )
            }
            log.info("Registered ${entries.size} app entries from apps.json")
        } catch (e: Exception) {
            log.error("Failed to load app directory from apps.json ($resourcePath): ${e.message}", e)
        }
    }

    /**
     * Attempts to load README.md from the root of the given resource path.
     * Returns the markdown text if found, or null otherwise.
     */
    private fun loadReadme(resourcePath: String): String? {
        val normalized = resourcePath.trimEnd('/')
        val candidates = listOf(
            "$normalized/README.md",
            "$normalized/readme.md",
            "$normalized/Readme.md"
        )
        for (candidate in candidates) {
            try {
                val stream = classLoader.getResourceAsStream(candidate)
                if (stream != null) {
                    val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    log.debug("Loaded README for resource path '{}' from '{}'", resourcePath, candidate)
                    return text
                }
            } catch (e: Exception) {
                log.debug("Failed reading {}: {}", candidate, e.message)
            }
        }
        log.debug("No README.md found for resource path '{}'", resourcePath)
        return null
    }
     /**
      * Checks whether a given classpath resource exists.
      */
     private fun resourceExists(path: String): Boolean {
         return try {
             classLoader.getResource(path) != null
         } catch (e: Exception) {
             log.debug("Error checking resource '{}': {}", path, e.message)
             false
         }
     }
}