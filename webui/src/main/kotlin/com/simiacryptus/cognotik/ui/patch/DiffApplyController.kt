package com.simiacryptus.cognotik.ui.patch

import com.simiacryptus.cognotik.diff.PatchProcessor
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

class DiffApplyController(
  private val filepath: Path,
  private val diff: String,
  private val processor: PatchProcessor,
  private val fs: FileSystem
) {
  private val state = AtomicReference<ApplyState>(ApplyState.Pending)

  fun currentState(): ApplyState = state.get()

  fun apply(): ApplyState {
    while (true) {
      val current = state.get()
      when (current) {
        is ApplyState.Pending, is ApplyState.Reverted -> {
          val originalCode = fs.readText(filepath)
          return try {
            val result = processor.apply(originalCode, "```diff\n$diff\n```", filepath.fileName?.toString())
            if (!result.isValid) {
              val failed = ApplyState.Failed(
                IllegalStateException("Invalid patch: ${result.errors.joinToString("; ") { it.message }}")
              )
              if (state.compareAndSet(current, failed)) return failed
              continue // CAS failed, retry
            }
            val applied = ApplyState.Applied(
              originalCode = originalCode,
              newCode = result.newCode,
              timestamp = Instant.now()
            )
            if (state.compareAndSet(current, applied)) {
              fs.writeText(filepath, result.newCode)
              applied
            } else {
              continue // CAS failed, retry
            }
          } catch (e: Throwable) {
            val failed = ApplyState.Failed(e)
            state.compareAndSet(current, failed)
            failed
          }
        }

        is ApplyState.Applied -> return current // Already applied
        is ApplyState.Failed -> return current   // Already failed
      }
    }
  }

  fun revert(): ApplyState {
    while (true) {
      val current = state.get()
      when (current) {
        is ApplyState.Applied -> {
          val reverted = ApplyState.Reverted(current.originalCode)
          return if (state.compareAndSet(current, reverted)) {
            try {
              fs.writeText(filepath, current.originalCode)
              reverted
            } catch (e: Throwable) {
              // Attempt to restore state on write failure
              state.compareAndSet(reverted, ApplyState.Failed(e))
              ApplyState.Failed(e)
            }
          } else {
            continue // CAS failed, retry
          }
        }

        is ApplyState.Pending -> return current  // Nothing to revert
        is ApplyState.Reverted -> return current  // Already reverted
        is ApplyState.Failed -> return current     // Can't revert a failure
      }
    }
  }
}