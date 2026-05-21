package com.simiacryptus.cognotik.util

import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant

abstract class DocStatus(root: File) {

    /**
     * Status of a single target generation task
     */
    enum class TaskStatus {
        PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
    }

    /**
     * Status entry for a single target generation task in docops.status.json
     */
    data class TaskStatusEntry(
        val target: String,
        val status: TaskStatus,
        val sessionId: String? = null,
        val startedAt: String? = null,
        val completedAt: String? = null,
        val error: String? = null
    )

    /**
     * Root structure for docops.status.json
     */
    data class DocOpsStatus(
        val lastUpdated: String,
        val tasks: Map<String, TaskStatusEntry>
    )
    protected val statusFile = File(root, "docops.status.json")
    protected fun nowTimestamp(): String = Instant.now().toString()

    protected fun readStatusLocked(): DocOpsStatus {
        return try {
            if (statusFile.exists()) {
                JsonUtil.fromJson(statusFile.readText(), DocOpsStatus::class.java)
            } else {
                DocOpsStatus(lastUpdated = nowTimestamp(), tasks = emptyMap())
            }
        } catch (e: Exception) {
            log.warn("Failed to read docops.status.json, starting fresh", e)
            DocOpsStatus(lastUpdated = nowTimestamp(), tasks = emptyMap())
        }
    }
    /**
     * Safely write the full status to the status file under the shared lock.
     */
    protected fun writeStatusLocked(status: DocOpsStatus) {
        synchronized(statusLock) {
            statusFile.writeText(JsonUtil.toJson(status))
        }
    }
    /**
     * Safely set the status of a single task in a thread-safe manner.
     * Reads the current status, updates (or inserts) the entry for [targetKey],
     * and writes the result back. Returns the updated [TaskStatusEntry].
     *
     * @param targetKey The identifier for the task whose status should be updated.
     * @param status The new status to assign.
     * @param sessionId Optional session id; if null, the existing session id (if any) is preserved.
     * @param error Optional error message to record.
     */
    protected fun setTaskStatus(
        targetKey: String,
        status: TaskStatus,
        sessionId: String? = null,
        error: String? = null
    ): TaskStatusEntry {
        synchronized(statusLock) {
            val current = readStatusLocked()
            val existing = current.tasks[targetKey]
            val now = nowTimestamp()
            val updatedEntry = TaskStatusEntry(
                target = targetKey,
                status = status,
                sessionId = sessionId ?: existing?.sessionId,
                startedAt = if (status == TaskStatus.RUNNING) now else existing?.startedAt,
                completedAt = if (status in setOf(
                        TaskStatus.COMPLETED,
                        TaskStatus.FAILED,
                        TaskStatus.CANCELLED
                    )
                ) now else existing?.completedAt,
                error = error
            )
            val updatedTasks = current.tasks.toMutableMap()
            updatedTasks[targetKey] = updatedEntry
            writeStatusLocked(DocOpsStatus(lastUpdated = now, tasks = updatedTasks))
            log.info("Updated status for target '$targetKey' to $status${error?.let { " with error: $it" } ?: ""}")
            return updatedEntry
        }
    }

    companion object {
        internal val statusLock = Any()
        private val log = LoggerFactory.getLogger(DocProcessor::class.java)
    }
}