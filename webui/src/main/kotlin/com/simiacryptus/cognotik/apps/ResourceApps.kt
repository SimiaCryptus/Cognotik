package com.simiacryptus.cognotik.apps

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simiacryptus.cognotik.CognotikPlugin
import com.simiacryptus.cognotik.webui.application.AppEntry
import org.slf4j.LoggerFactory

open class ResourceApps(
  val resourcePath: String,
  val classLoader: ClassLoader = ResourceApps::class.java.classLoader
) : CognotikPlugin {

    private val log = LoggerFactory.getLogger(ResourceApps::class.java)

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
        val cardClass: String? = null,
        val resource_path: String? = null
    )

    override fun init() {
        try {
            val json = classLoader.getResourceAsStream(resourcePath)
                ?.bufferedReader()?.readText()
                ?: throw IllegalStateException("$resourcePath not found on classpath")
            val type = object : TypeToken<List<AppJsonEntry>>() {}.type
            val entries: List<AppJsonEntry> = Gson().fromJson(json, type)
            for (entry in entries) {
                 val readme = entry.resource_path?.let { rp ->
                     loadReadme(rp)
                 }
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
                    resource_path = entry.resource_path,
                    cardClass = entry.cardClass,
                     readme = readme,
                    classLoader = classLoader
                  )
                )
            }
            log.info("Registered ${entries.size} app entries from apps.json")
        } catch (e: Exception) {
            log.error("Failed to load app directory from apps.json: ${e.message}", e)
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
}