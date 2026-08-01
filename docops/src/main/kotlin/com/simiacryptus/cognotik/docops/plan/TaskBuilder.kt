package com.simiacryptus.cognotik.docops.plan

import com.simiacryptus.cognotik.docops.exec.DocTaskKind
import com.simiacryptus.cognotik.docops.model.*
import com.simiacryptus.cognotik.docops.plan.policy.*
import org.slf4j.LoggerFactory
import java.io.File

class TaskBuilder<K : DocTaskKind>(
  private val root: File,
  private val updateModePolicy: UpdateModePolicy,
  private val taskKindPolicy: TaskKindPolicy<K>,
  private val rootPolicy: RootPolicy,
  private val taskConfigPolicy: TaskConfigPolicy,
  private val descriptions: TaskDescriptionComposer<K>,
  private val messages: ContextMessageComposer<K>,
  private val related: RelatedFileCollector,
) {

  fun build(
    target: TargetPath,
    contributions: List<TargetContribution>,
    ctx: ResolveContext,
  ): BuildOutcome<K> {
    if (!target.isUnder(root)) {
      log.warn("Target file is outside root: $target")
      return BuildOutcome.Skipped(target, "outside root")
    }
    return try {
      log.info(
        "Planning ${target.relativeToOrAbsolute(root)} from ${contributions.size} contribution(s): " +
            contributions.joinToString(", ") { "${it.spec.docFile.name}/${it.kind}" }
      )
      val source = related.primarySource(contributions)
        ?: return BuildOutcome.Skipped(target, "no primary source")
      val relatedFiles = related.relatedFiles(target.file, contributions, ctx)
      val updateMode = updateModePolicy.resolve(contributions)
      val preparation = updateMode.prepare(source, target.file, relatedFiles)
        ?: run {
          log.debug("Update mode returned null for {}, skipping", target)
          return BuildOutcome.Skipped(target, "update mode declined")
        }
      val kind = taskKindPolicy.resolve(contributions)
      val effectiveRoot = rootPolicy.resolve(contributions)

      val task = ModificationTask(
        data = ModificationTaskConfig(
          root = effectiveRoot,
          main_file = target.file.absoluteFile,
          related_files = relatedFiles.map { runCatching { it.canonicalFile }.getOrDefault(it.absoluteFile) }
            .distinct(),
          doc_files = contributions.map { it.spec.docFile.absoluteFile }.distinct(),
          task_description = descriptions.compose(contributions, target, kind),
          taskConfigOverrides = taskConfigPolicy.resolve(contributions),
        ),
        taskType = kind,
        message = messages.compose(kind, relatedFiles),
        patchProcessor = preparation.patchProcessor,
      )
      BuildOutcome.Planned(
        PlannedTask(
          target = target,
          task = task,
          preparation = TargetPreparation(deleteTargetBeforeRun = preparation.shouldDeleteTarget),
        )
      )
    } catch (e: Throwable) {
      log.error("Error planning $target", e)
      BuildOutcome.Failed(target, e)
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(TaskBuilder::class.java)
  }
}