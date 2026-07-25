package com.simiacryptus.cognotik.docops

import java.io.File
import java.util.concurrent.CompletableFuture

/**
 * Platform-neutral description of "a kind of work" that can be applied to a target file.
 * Implementations wrap whatever task/agent abstraction the host platform provides.
 */
interface DocTaskKind {
  /** Stable, human readable name (also used as the `task_type` discriminator in JSON configs). */
  val name: String

  /** True if the engine supplies context files itself (i.e. they must not be inlined into the prompt). */
  val isFileTask: Boolean get() = false

  /** True if this kind expands into a sub-plan rather than a single edit. */
  val isSubPlanTask: Boolean get() = false

  /** True if this kind renders a template (an `.erb` related file is meaningful). */
  val isTemplateTask: Boolean get() = false

  /** Default settings for this kind, as a JSON-compatible map, or null if there are none. */
  fun defaultConfig(): Map<String, Any>? = null
}

/** Lookup of [DocTaskKind]s by the `task_type` value found in document frontmatter. */
interface DocTaskKindResolver<K : DocTaskKind> {
  val default: K
  fun byName(name: String): K?
}

/** Minimal concurrency abstraction so the core logic does not depend on the platform thread pools. */
interface DocTaskScheduler {
  fun submit(block: () -> Unit): CompletableFuture<*>
}

/** Everything the host needs in order to actually run one planned modification. */
data class DocTaskRequest<K : DocTaskKind, P : Any>(
  val taskKind: K,
  val message: String,
  /** Fully resolved, JSON-compatible execution configuration. */
  val executionConfig: Map<String, Any>,
  /** JSON-compatible per-task-type settings. */
  val typeConfig: Map<String, Any>,
  val patchProcessor: P?,
  val workingDir: File,
  val timeoutMinutes: Int = 30,
)

/** Input for host-side inference of an execution config (used for non-file task kinds). */
data class DocTaskInferenceRequest<K : DocTaskKind, P : Any>(
  val taskKind: K,
  val taskDescription: String,
  val prompt: String,
  val history: List<String>,
  val workingDir: File,
  val patchProcessor: P?,
  val typeConfig: Map<String, Any>,
)

/** Lifecycle notifications raised by the host while a [DocTaskRequest] is executing. */
interface DocTaskCallbacks<S : Any> {
  /**
   * Called as soon as the underlying session exists.
   * Implementations may throw [java.util.concurrent.CancellationException] to abort.
   */
  fun onSessionStarted(session: S, sessionId: String)
  fun onCompleted(sessionId: String)
  fun onFailed(error: Throwable)
}

/**
 * A reusable execution scope (one per work queue). Typically wraps a single agent harness.
 */
interface DocExecutionContext<K : DocTaskKind, S : Any, P : Any> : AutoCloseable {
  /** Discard any per-task session state before the next task is run. */
  fun reset()

  /** Derive an execution config for task kinds that cannot be configured declaratively. */
  fun inferTaskConfig(request: DocTaskInferenceRequest<K, P>): Map<String, Any>

  fun execute(request: DocTaskRequest<K, P>, callbacks: DocTaskCallbacks<S>)

  override fun close() {}
}