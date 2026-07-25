package com.simiacryptus.cognotik.docops.plan.policy

    import com.simiacryptus.cognotik.docops.UpdateMode
    import com.simiacryptus.cognotik.docops.UpdateModes
    import com.simiacryptus.cognotik.docops.exec.DocTaskKind
    import com.simiacryptus.cognotik.docops.exec.DocTaskKindResolver
    import com.simiacryptus.cognotik.docops.model.ContributionKind
    import com.simiacryptus.cognotik.docops.model.DocSpec
    import com.simiacryptus.cognotik.docops.model.TargetContribution
    import com.simiacryptus.cognotik.util.JsonUtil
    import org.slf4j.LoggerFactory
    import java.io.File

    /**
     * Precedence used by every policy: `specifies` > `transforms` > `documents` > `generates` > `folder`.
     * (Distinct from [ContributionKind.sourcePriority], which decides the *primary source* file.)
     */
    private val POLICY_ORDER = listOf(
      ContributionKind.SPECIFIES,
      ContributionKind.TRANSFORM,
      ContributionKind.DOCUMENT,
      ContributionKind.GENERATE,
      ContributionKind.FOLDER,
    )

    fun List<TargetContribution>.inPolicyOrder(): List<TargetContribution> =
      sortedBy { POLICY_ORDER.indexOf(it.kind) }

    fun List<TargetContribution>.specsInPolicyOrder(): List<DocSpec> =
      inPolicyOrder().map { it.spec }.distinct()

    class UpdateModePolicy(private val global: UpdateMode) {
      fun resolve(contributions: List<TargetContribution>): UpdateMode {
        val declared = contributions.specsInPolicyOrder().firstNotNullOfOrNull { it.updateMode } ?: return global
        val resolved = UpdateModes.fromName(declared)
        return if (resolved != null) {
          log.info("Using per-doc update mode: $declared")
          resolved
        } else {
          log.warn("Unknown per-doc update mode '$declared', falling back to global: $global")
          global
        }
      }

      companion object { private val log = LoggerFactory.getLogger(UpdateModePolicy::class.java) }
    }

    class TaskKindPolicy<K : DocTaskKind>(private val kinds: DocTaskKindResolver<K>) {
      fun resolve(contributions: List<TargetContribution>): K {
        val name = contributions.specsInPolicyOrder().firstNotNullOfOrNull { it.taskType } ?: return kinds.default
        return kinds.byName(name) ?: run {
          log.warn("Unknown task type '$name', defaulting to ${kinds.default.name}")
          kinds.default
        }
      }

      companion object { private val log = LoggerFactory.getLogger(TaskKindPolicy::class.java) }
    }

    /** `folder:` overrides the effective task root; it must stay at or under the workspace root. */
    class RootPolicy(private val root: File) {
      fun resolve(contributions: List<TargetContribution>): File {
        val spec = contributions.specsInPolicyOrder().firstOrNull { it.targetFolder != null } ?: return root
        val resolved = spec.baseDir.resolve(spec.targetFolder!!).canonicalFile
        val canonicalRoot = root.canonicalFile
        val ok = resolved.canonicalPath == canonicalRoot.canonicalPath ||
            resolved.canonicalPath.startsWith(canonicalRoot.canonicalPath + File.separator)
        require(ok) {
          "folder '${spec.targetFolder}' resolves to '${resolved.canonicalPath}' which is not under " +
              "the root '${canonicalRoot.canonicalPath}'."
        }
        log.info("Using root override: ${resolved.canonicalPath} (from ${spec.docFile.name})")
        return resolved
      }

      companion object { private val log = LoggerFactory.getLogger(RootPolicy::class.java) }
    }

    class TaskConfigPolicy {
      fun resolve(contributions: List<TargetContribution>): Map<String, Any>? {
        val file = contributions.specsInPolicyOrder()
          .firstNotNullOfOrNull { spec -> spec.taskConfigJson?.let { spec.baseDir.resolve(it) } } ?: return null
        if (!file.exists()) {
          log.warn("Task config JSON file not found: ${file.absolutePath}")
          return null
        }
        return try {
          JsonUtil.fromJson<Map<String, Any>>(file.readText(), Map::class.java)
        } catch (e: Exception) {
          log.warn("Failed to parse task config JSON: ${file.absolutePath}", e)
          null
        }
      }

      companion object { private val log = LoggerFactory.getLogger(TaskConfigPolicy::class.java) }
    }