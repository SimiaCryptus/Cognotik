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
      log.warn(
        "Target is outside the workspace root and will be skipped: $target (root=${root.absolutePath}; " +
            "declared by ${contributions.joinToString { it.spec.docFile.name }})"
      )
      return BuildOutcome.Skipped(target, "outside root ${root.absolutePath}")
    }
    return try {
      log.info(
        "Planning ${target.relativeToOrAbsolute(root)} from ${contributions.size} contribution(s): " +
            contributions.joinToString(", ") { "${it.spec.docFile.name}/${it.kind}" }
      )
      val source = related.primarySource(contributions) ?: run {
        log.warn(
          "No primary source file could be selected for $target; contributions=" +
              contributions.joinToString { "${it.spec.docFile.name}/${it.kind}(sources=${it.sourceFiles.size})" }
        )
        return BuildOutcome.Skipped(target, "no primary source")
      }
      val relatedFiles = related.relatedFiles(target.file, contributions, ctx)
      log.info(
        "  primary source: ${source.absolutePath}; ${relatedFiles.size} related file(s)" +
            (if (relatedFiles.isEmpty()) " (no context files resolved!)"
            else ": " + relatedFiles.take(20).joinToString { it.name })
      )
      relatedFiles.filter { !it.exists() }.takeIf { it.isNotEmpty() }?.let { missing ->
        log.info("  ${missing.size} related file(s) do not exist yet: ${missing.joinToString { it.absolutePath }}")
      }
      val updateMode = updateModePolicy.resolve(contributions)
      val preparation = updateMode.prepare(source, target.file, relatedFiles)
        ?: run {
          log.info(
            "Update mode '$updateMode' declined $target (source=${source.absolutePath}, " +
                "targetExists=${target.file.exists()}); skipping"
          )
          return BuildOutcome.Skipped(target, "update mode '$updateMode' declined")
        }
      val kind = taskKindPolicy.resolve(contributions)
      val effectiveRoot = rootPolicy.resolve(contributions)
      log.info(
        "  task kind=${kind.name}, updateMode=$updateMode, effectiveRoot=${effectiveRoot.absolutePath}, " +
            "deleteTargetBeforeRun=${preparation.shouldDeleteTarget}"
      )

      val task = ModificationTask(
        data = ModificationTaskConfig(
          root = effectiveRoot,
          main_file = target.file.absoluteFile,
          related_files = relatedFiles.map { runCatching { it.canonicalFile }.getOrDefault(it.absoluteFile) }
            .distinct(),
          doc_files = contributions.map { it.spec.docFile.absoluteFile }.distinct(),
           // The description names the target relative to the *effective* root, which is also the
           // working directory handed to the host - otherwise `folder:` tasks write to the wrong place.
           task_description = descriptions.compose(contributions, target, kind, effectiveRoot),
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