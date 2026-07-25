package com.simiacryptus.cognotik.docops.status

import com.simiacryptus.cognotik.util.JsonUtil
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant

/** `<root>/docops.status.json`, guarded by a process-wide lock so concurrent queues can't corrupt it. */
class JsonFileDocStatusStore(
  root: File,
  val statusFile: File = File(root, "docops.status.json"),
) : DocStatusStore {

  override fun read(): DocOpsStatus = synchronized(lock) { readLocked() }

  override fun initialize(targetKeys: Collection<String>) {
    synchronized(lock) {
      val now = now()
      val existing = readLocked()
      val merged = existing.tasks.toMutableMap()
      targetKeys.forEach { key -> merged[key] = TaskStatusEntry(target = key, status = TaskStatus.PENDING) }
      log.info(
        "Initialized status for ${targetKeys.size} task(s), preserving " +
            "${existing.tasks.count { it.value.status == TaskStatus.COMPLETED }} completed task(s)"
      )
      writeLocked(DocOpsStatus(lastUpdated = now, tasks = merged))
    }
  }

  override fun set(
    targetKey: String,
    status: TaskStatus,
    sessionId: String?,
    error: String?,
  ): TaskStatusEntry = synchronized(lock) {
    val current = readLocked()
    val existing = current.tasks[targetKey]
    val now = now()
    val updated = TaskStatusEntry(
      target = targetKey,
      status = status,
      sessionId = sessionId ?: existing?.sessionId,
      startedAt = if (status == TaskStatus.RUNNING) now else existing?.startedAt,
      completedAt = if (status.isTerminal) now else existing?.completedAt,
      error = error,
    )
    writeLocked(DocOpsStatus(lastUpdated = now, tasks = current.tasks + (targetKey to updated)))
    log.info("Updated status for target '$targetKey' to $status${error?.let { " with error: $it" } ?: ""}")
    updated
  }

  override fun markAllRunningAs(status: TaskStatus, error: String?) {
    synchronized(lock) {
      val current = readLocked()
      val now = now()
      val updated = current.tasks.mapValues { (_, entry) ->
        if (entry.status == TaskStatus.RUNNING) entry.copy(status = status, completedAt = now, error = error)
        else entry
      }
      writeLocked(DocOpsStatus(lastUpdated = now, tasks = updated))
      log.info("Marked all RUNNING tasks as $status${error?.let { " with error: $it" } ?: ""}")
    }
  }

  private fun now(): String = Instant.now().toString()

  private fun readLocked(): DocOpsStatus = try {
    if (statusFile.exists()) JsonUtil.fromJson(statusFile.readText(), DocOpsStatus::class.java)
    else DocOpsStatus(lastUpdated = now(), tasks = emptyMap())
  } catch (e: Exception) {
    log.warn("Failed to read ${statusFile.name}, starting fresh", e)
    DocOpsStatus(lastUpdated = now(), tasks = emptyMap())
  }

  private fun writeLocked(status: DocOpsStatus) {
    try {
      statusFile.parentFile?.mkdirs()
      statusFile.writeText(JsonUtil.toJson(status))
    } catch (e: Exception) {
      log.warn("Failed to write ${statusFile.absolutePath}", e)
    }
  }

  companion object {
    /** Process-wide: multiple stores may point at the same file. */
    private val lock = Any()
    private val log = LoggerFactory.getLogger(JsonFileDocStatusStore::class.java)
  }
}