package com.simiacryptus.cognotik.webui.servlet

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Small, file-backed store for gift attributes that the gift database does not (yet) persist:
 * management description, recipient message, and cancellation/refund records.
 *
 * Writes are atomic (temp file + move) and happen before the in-memory view is updated,
 * so a failed write never leaves a cancellation half-recorded.
 */
class GiftExtrasStore(private val file: File?) {

  data class Entry(
    val description: String? = null,
    val message: String? = null,
    val cancelledAt: Instant? = null,
    val cancelledBy: String? = null,
    val refunded: Double? = null,
  )

  private val entries = ConcurrentHashMap<String, Entry>()

  init {
    load()
  }

  operator fun get(giftId: String): Entry? = entries[giftId]

  @Synchronized
  fun update(giftId: String, fn: (Entry) -> Entry): Entry {
    val next = fn(entries[giftId] ?: Entry())
    val snapshot = HashMap(entries)
    snapshot[giftId] = next
    write(snapshot)
    entries[giftId] = next
    return next
  }

  fun markCancelled(giftId: String, at: Instant, by: String?, refunded: Double) =
    update(giftId) { it.copy(cancelledAt = at, cancelledBy = by, refunded = refunded) }

  fun clearCancellation(giftId: String) =
    update(giftId) { it.copy(cancelledAt = null, cancelledBy = null, refunded = null) }

  fun setRefunded(giftId: String, refunded: Double) =
    update(giftId) { it.copy(refunded = refunded) }

  private fun load() {
    val f = file ?: return
    if (!f.isFile) return
    try {
      val raw = mapper.readValue(f, Map::class.java) as Map<*, *>
      raw.forEach { (k, v) ->
        val m = v as? Map<*, *> ?: return@forEach
        entries[k.toString()] = Entry(
          description = m["description"] as? String,
          message = m["message"] as? String,
          cancelledAt = (m["cancelledAt"] as? Number)?.toLong()?.let(Instant::ofEpochMilli),
          cancelledBy = m["cancelledBy"] as? String,
          refunded = (m["refunded"] as? Number)?.toDouble(),
        )
      }
      log.info("Loaded {} gift extras from {}", entries.size, f)
    } catch (e: Exception) {
      // Never overwrite a file we could not read: move it aside for manual recovery.
      val aside = File(f.parentFile, "${f.name}.corrupt-${System.currentTimeMillis()}")
      log.error("Failed to read gift extras from {}; moving it to {}", f, aside, e)
      f.renameTo(aside)
    }
  }

  private fun write(data: Map<String, Entry>) {
    val f = file ?: return
    val serializable = data.toSortedMap().mapValues { (_, e) ->
      linkedMapOf(
        "description" to e.description,
        "message" to e.message,
        "cancelledAt" to e.cancelledAt?.toEpochMilli(),
        "cancelledBy" to e.cancelledBy,
        "refunded" to e.refunded,
      )
    }
    f.absoluteFile.parentFile?.mkdirs()
    val tmp = File(f.absoluteFile.parentFile, "${f.name}.tmp")
    mapper.writerWithDefaultPrettyPrinter().writeValue(tmp, serializable)
    try {
      Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (e: AtomicMoveNotSupportedException) {
      Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(GiftExtrasStore::class.java)
    private val mapper = ObjectMapper()

    const val FILE_PROPERTY = "cognotik.gifts.extrasFile"

    fun default(): GiftExtrasStore {
      val path = System.getProperty(FILE_PROPERTY)?.takeIf { it.isNotBlank() }
      val file = if (path != null) File(path)
      else File(System.getProperty("user.home"), ".cognotik/gifts/gift-extras.json")
      return GiftExtrasStore(file)
    }
  }
}