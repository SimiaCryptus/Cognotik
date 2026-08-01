package com.simiacryptus.cognotik.docops.status

import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/** Test double: same semantics as [JsonFileDocStatusStore] without touching disk. */
class InMemoryDocStatusStore(private val clock: Clock = Clock.systemUTC()) : DocStatusStore {

  private val tasks = ConcurrentHashMap<String, TaskStatusEntry>()

  override fun initialize(targetKeys: Collection<String>) {
    targetKeys.forEach { key -> tasks[key] = TaskStatusEntry(target = key, status = TaskStatus.PENDING) }
  }

  override fun set(targetKey: String, status: TaskStatus, sessionId: String?, error: String?): TaskStatusEntry {
    val now = clock.instant().toString()
    val existing = tasks[targetKey]
    val updated = TaskStatusEntry(
      target = targetKey,
      status = status,
      sessionId = sessionId ?: existing?.sessionId,
      startedAt = if (status == TaskStatus.RUNNING) now else existing?.startedAt,
      completedAt = if (status.isTerminal) now else existing?.completedAt,
      error = error,
    )
    tasks[targetKey] = updated
    return updated
  }

  override fun markAllRunningAs(status: TaskStatus, error: String?) {
    val now = clock.instant().toString()
    tasks.replaceAll { _, entry ->
      if (entry.status == TaskStatus.RUNNING) entry.copy(status = status, completedAt = now, error = error) else entry
    }
  }

  override fun read(): DocOpsStatus =
    DocOpsStatus(lastUpdated = clock.instant().toString(), tasks = tasks.toMap())
}