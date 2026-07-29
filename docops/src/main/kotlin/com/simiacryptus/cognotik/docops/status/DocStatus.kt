package com.simiacryptus.cognotik.docops.status

enum class TaskStatus {
  PENDING, RUNNING, COMPLETED, FAILED, CANCELLED;

  val isTerminal: Boolean get() = this == COMPLETED || this == FAILED || this == CANCELLED
}

data class TaskStatusEntry(
  val target: String,
  val status: TaskStatus,
  val sessionId: String? = null,
  val startedAt: String? = null,
  val completedAt: String? = null,
  val error: String? = null,
)

data class DocOpsStatus(
  val lastUpdated: String,
  val tasks: Map<String, TaskStatusEntry>,
)

/** Composition, not inheritance: the planner/runner hold one of these instead of extending it. */
interface DocStatusStore {
  /** Seed [targetKeys] as PENDING, preserving entries from previous runs. */
  fun initialize(targetKeys: Collection<String>)

  fun set(
    targetKey: String,
    status: TaskStatus,
    sessionId: String? = null,
    error: String? = null,
  ): TaskStatusEntry

  fun markAllRunningAs(status: TaskStatus, error: String? = null)

  fun read(): DocOpsStatus
}

class NullDocStatusStore : DocStatusStore {
  override fun initialize(targetKeys: Collection<String>) {}
  override fun set(
    targetKey: String,
    status: TaskStatus,
    sessionId: String?,
    error: String?,
  ): TaskStatusEntry = TaskStatusEntry(targetKey, status, sessionId, null, null, error)

  override fun markAllRunningAs(status: TaskStatus, error: String?) {}
  override fun read(): DocOpsStatus = DocOpsStatus(lastUpdated = "", tasks = emptyMap())
}