package com.simiacryptus.cognotik.docops.model

import java.io.File

enum class ContributionKind {
  /** Doc declares `specifies:` for this target. */
  SPECIFIES,

  /** Doc declares a `transforms:` rule whose destination is this target. */
  TRANSFORM,

  /** Doc declares `documents:`; the doc file itself is the target. */
  DOCUMENT,

  /** Doc declares `generates:`; the declared output is the target. */
  GENERATE,

  /** Doc declares only `folder:`; the folder itself is the target. */
  FOLDER;

  /** Priority used when picking the primary source (lower wins). */
  val sourcePriority: Int
    get() = when (this) {
      TRANSFORM -> 0
      SPECIFIES -> 1
      DOCUMENT -> 2
      GENERATE -> 3
      FOLDER -> 4
    }
}

/**
 * One doc's claim on one target. The union of all contributions for a target is everything
 * [com.simiacryptus.cognotik.docops.plan.TaskBuilder] needs in order to emit a task.
 */
data class TargetContribution(
  val target: TargetPath,
  val spec: DocSpec,
  val kind: ContributionKind,
  /** Transform source file / documented sources / generate inputs. */
  val sourceFiles: List<File> = emptyList(),
  /** True when the contribution came from the transitive fixpoint pass (target may not exist yet). */
  val hypothetical: Boolean = false,
)