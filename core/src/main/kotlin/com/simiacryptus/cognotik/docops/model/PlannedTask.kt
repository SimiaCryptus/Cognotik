package com.simiacryptus.cognotik.docops.model

  import com.simiacryptus.cognotik.docops.exec.DocTaskKind

  /**
   * Side effects that must be applied *immediately before* the task runs.
   * Planning itself no longer mutates the workspace.
   */
  data class TargetPreparation(
    val deleteTargetBeforeRun: Boolean = false,
  )

  data class PlannedTask<K : DocTaskKind>(
    val target: TargetPath,
    val task: ModificationTask<K>,
    val preparation: TargetPreparation = TargetPreparation(),
  )

  sealed interface BuildOutcome<K : DocTaskKind> {
    val target: TargetPath

    data class Planned<K : DocTaskKind>(val planned: PlannedTask<K>) : BuildOutcome<K> {
      override val target: TargetPath get() = planned.target
    }

    data class Skipped<K : DocTaskKind>(
      override val target: TargetPath,
      val reason: String,
    ) : BuildOutcome<K>

    data class Failed<K : DocTaskKind>(
      override val target: TargetPath,
      val error: Throwable,
    ) : BuildOutcome<K>
  }