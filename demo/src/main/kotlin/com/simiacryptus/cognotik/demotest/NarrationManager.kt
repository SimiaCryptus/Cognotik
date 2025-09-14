package com.simiacryptus.cognotik.demotest

import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import java.io.InputStream

data class NarrationEntry(
    val text: String? = null,
    val audio: String? = null
)

data class NarrationMap(
    val narrations: Map<String, NarrationEntry>? = emptyMap()
)


class NarrationManager(private val narrationFile: String) {

    private val narrations: Map<String, NarrationEntry>

    companion object {
        private val log = LoggerFactory.getLogger(NarrationManager::class.java)
    }

    init {
        narrations = loadNarrations()
    }

    private fun loadNarrations(): Map<String, NarrationEntry> {
        return try {
            val resourceStream =
                this::class.java.classLoader.getResourceAsStream(narrationFile)?.readAllBytes()?.let { String(it) }
                    ?: throw IllegalArgumentException("Narration file not found: $narrationFile")
            val map =
                JsonUtil.fromJson<NarrationMap>("{'narrations':$resourceStream}", NarrationMap::class.java).narrations
                    ?: emptyMap()
            map
        } catch (e: Exception) {
            log.error("Failed to load narrations from $narrationFile", e)
            emptyMap()
        }
    }

    fun getNarration(key: String): NarrationEntry? {
        return narrations[key]?.also {
            log.debug("Retrieved narration for key: $key")
        } ?: run {
            log.warn("Narration not found for key: $key")
            null
        }
    }

    fun getAudioPath(key: String): String? {
        return getNarration(key)?.audio
    }

    val resourceBase = narrationFile.substringBefore(".")

    fun getAudioStream(key: String): InputStream? {
        val audioPath = getAudioPath(key) ?: return null
        return try {
            this::class.java.classLoader.getResourceAsStream("$resourceBase/$audioPath")?.also {
                log.debug("Retrieved audio stream for key: $key, path: $audioPath")
            } ?: run {
                log.warn("Audio stream not found for key: $key, path: $audioPath")
                null
            }
        } catch (e: Exception) {
            log.error("Failed to load audio for key: $key, path: $audioPath", e)
            null
        }
    }
}