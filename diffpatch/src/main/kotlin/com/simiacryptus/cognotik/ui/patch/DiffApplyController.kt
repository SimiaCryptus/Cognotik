package com.simiacryptus.cognotik.ui.patch

import com.simiacryptus.cognotik.diff.PatchProcessor
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

class DiffApplyController(
  private val filepath: Path,
  private val diff: String,
  private val processor: PatchProcessor,
  private val fs: FileSystem,
  /**
   * In-memory trace which also forwards to slf4j. Named `log` so that all logging in this
   * class is captured and can be surfaced in the "Patch Data" dump.
   */
  private val log: PatchTrace = PatchTrace("DiffApplyController", logger)
) {
  companion object {
    internal val logger = org.slf4j.LoggerFactory.getLogger(DiffApplyController::class.java)
  }

  private val state = AtomicReference<ApplyState>(ApplyState.Pending)

  fun currentState(): ApplyState = state.get()

  /**
   * Applies the diff. Applying is legal from [ApplyState.Pending] and [ApplyState.Reverted]
   * (so an apply/revert/re-apply cycle can be repeated), and from [ApplyState.Failed] when
   * [force] is set.
   *
   * @param force ignore patch validation errors (as long as the result is non-blank) and retry
   *   a previously failed application.
   */
  fun apply(force: Boolean = false): ApplyState {
    log.debug("Attempting to apply diff to file: {} (force={})", filepath, force)
    while (true) {
      val current = state.get()
      log.debug("Current state for {}: {}", filepath, current::class.simpleName)
      when {
        current is ApplyState.Applied -> {
          log.debug("Diff already applied to {}", filepath)
          return current
        }

        current is ApplyState.Failed && !force -> {
          log.debug(
            "Diff previously failed for {}: {} (retry with force=true to ignore)",
            filepath, current.error.message
          )
          return current
        }

        else -> {
          /* Pending, Reverted, or a forced retry of a previous failure. */
          if (diff.isBlank()) {
            log.warn("Diff content is blank for file: {}", filepath)
            val failed = ApplyState.Failed(IllegalArgumentException("Diff content is blank"))
            state.compareAndSet(current, failed)
            return failed
          }
          val originalCode = fs.readText(filepath)
          log.debug("Read original code from {}: {} chars", filepath, originalCode.length)
          return try {
            val result = processor.apply(originalCode, "```diff\n$diff\n```", filepath.fileName?.toString())
            log.debug("Patch result for {}: isValid={}, errors={}", filepath, result.isValid, result.errors.size)
            if (!result.isValid) {
              val errorMessage = result.errors.joinToString("; ") { it.message }
              if (!force) {
                log.warn("Invalid patch for {}: {}", filepath, errorMessage)
                val failed = ApplyState.Failed(
                  IllegalStateException("Invalid patch: $errorMessage")
                )
                if (state.compareAndSet(current, failed)) return failed
                continue // CAS failed, retry
              }
              log.warn("Ignoring patch validation failure for {} (force=true): {}", filepath, errorMessage)
              if (result.newCode.isBlank() && originalCode.isNotBlank()) {
                log.error("Forced apply for {} produced empty content, refusing to write", filepath)
                val failed = ApplyState.Failed(
                  IllegalStateException("Forced apply produced empty content: $errorMessage")
                )
                if (state.compareAndSet(current, failed)) return failed
                continue // CAS failed, retry
              }
            }
            if (result.newCode == originalCode) {
              log.info("Patch for {} produces no changes, skipping write", filepath)
            }
            val applied = ApplyState.Applied(
              originalCode = originalCode,
              newCode = result.newCode,
              timestamp = Instant.now()
            )
            if (state.compareAndSet(current, applied)) {
              fs.writeText(filepath, result.newCode)
              log.debug(
                "Successfully applied diff to {}: {} -> {} chars",
                filepath,
                originalCode.length,
                result.newCode.length
              )
              applied
            } else {
              log.debug("CAS failed during apply for {}, retrying", filepath)
              continue // CAS failed, retry
            }
          } catch (e: Throwable) {
            log.error("Exception applying diff to {}: {}", filepath, e.message, e)
            val failed = ApplyState.Failed(e)
            state.compareAndSet(current, failed)
            failed
          }
        }
      }
    }
  }

  /** Clears any terminal state so the diff can be attempted again from scratch. */
  fun reset(): ApplyState {
    log.debug("Resetting apply state for {}", filepath)
    state.set(ApplyState.Pending)
    return ApplyState.Pending
  }

  fun revert(): ApplyState {
    log.debug("Attempting to revert diff for file: {}", filepath)
    while (true) {
      val current = state.get()
      log.debug("Current state for revert of {}: {}", filepath, current::class.simpleName)
      when (current) {
        is ApplyState.Applied -> {
          val reverted = ApplyState.Reverted(current.originalCode)
          return if (state.compareAndSet(current, reverted)) {
            try {
              fs.writeText(filepath, current.originalCode)
              log.info("Successfully reverted {} to original ({} chars)", filepath, current.originalCode.length)
              reverted
            } catch (e: Throwable) {
              log.error("Failed to write reverted content to {}: {}", filepath, e.message, e)
              // Attempt to restore state on write failure
              state.compareAndSet(reverted, ApplyState.Failed(e))
              ApplyState.Failed(e)
            }
          } else {
            log.debug("CAS failed during revert for {}, retrying", filepath)
            continue // CAS failed, retry
          }
        }

        is ApplyState.Pending -> {
          log.debug("Nothing to revert for {} (state is Pending)", filepath)
          return current
        }

        is ApplyState.Reverted -> {
          log.debug("Already reverted for {}", filepath)
          return current
        }

        is ApplyState.Failed -> {
          log.debug("Cannot revert failed state for {}: {}", filepath, current.error.message)
          return current
        }
      }
    }
  }
}