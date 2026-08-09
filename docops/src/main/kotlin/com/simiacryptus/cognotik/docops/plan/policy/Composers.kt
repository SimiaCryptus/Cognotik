package com.simiacryptus.cognotik.docops.plan.policy

    import com.simiacryptus.cognotik.docops.exec.DocTaskKind
    import com.simiacryptus.cognotik.docops.model.ContributionKind
    import com.simiacryptus.cognotik.docops.model.TargetContribution
    import com.simiacryptus.cognotik.docops.model.TargetPath
    import java.io.File

    class TaskDescriptionComposer<K : DocTaskKind> {

      /**
       * @param root the *effective* task root (the `folder:` override, otherwise the workspace
       *   root). The target is always named by its path relative to this root, because that is the
       *   directory the agent resolves relative paths against - naming it by bare file name made
       *   `folder:`-rooted tasks write their output into the root instead of the target's folder.
       */
      fun compose(
        contributions: List<TargetContribution>,
        target: TargetPath,
        kind: K,
        root: File,
      ): String {
        val specs = contributions.specsInPolicyOrder()
        val kinds = contributions.map { it.kind }.toSet()
        val singlePrompt = specs.singleOrNull()?.prompt
        val path = displayPath(target, root)
        return buildString {
          when {
            singlePrompt != null -> appendLine(singlePrompt)

            kind.isSubPlanTask -> {
              appendLine("Perform ${kind.name} generation to produce the file $path.")
              appendLine("Use the provided documentation and specifications as context for the processing.")
            }

            !kind.isFileTask -> {
              appendLine("Produce the file $path according to the task type ${kind.name}.")
              appendLine("Use the provided documentation and specifications as context for the processing.")
            }

            ContributionKind.SPECIFIES in kinds || ContributionKind.TRANSFORM in kinds ||
                ContributionKind.FOLDER in kinds -> {
              appendLine("Update the file $path based on the included documentation and specifications.")
              appendLine("Ensure the file conforms to all the patterns, standards, and requirements described.")
              appendLine("If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.")
            }

            ContributionKind.DOCUMENT in kinds -> {
              appendLine("Update the documentation file $path based on the supporting source files included as context.")
              appendLine("The documentation should accurately reflect the current state of the code.")
              appendLine("Update any outdated information, add documentation for new features, and ensure consistency with the actual implementation.")
            }

            ContributionKind.GENERATE in kinds -> {
              appendLine("Generate or update the file $path based on the documentation and input files provided as context.")
              appendLine("The output should follow the patterns and requirements described in the documentation.")
              appendLine("Use the input files as source material to create the appropriate output.")
            }
          }
        }
      }

      /** Root-relative, `/`-normalized target path; falls back to the bare name when relativization fails. */
      private fun displayPath(target: TargetPath, root: File): String {
        val relative = target.relativeToOrAbsolute(root).replace(File.separatorChar, '/')
        return relative.ifBlank { target.name }
      }
    }

    /** Builds the prompt body: either `"Execute task."` or every related file inlined. */
    class ContextMessageComposer<K : DocTaskKind> {

      fun compose(kind: K, relatedFiles: List<File>): (File) -> String = { taskRoot ->
        buildString {
          if (kind.isFileTask) {
            appendLine("Execute task.")
          } else {
            relatedFiles.forEach { relatedFile ->
              val resolved = if (relatedFile.isAbsolute) relatedFile else taskRoot.resolve(relatedFile)
              appendLine("# Context file: ${resolved.relativeToOrSelf(taskRoot)}")
              appendLine(FENCE)
              if (resolved.exists()) {
                var text = runCatching { resolved.readText().trim() }.getOrDefault("")
                if (relatedFile.name.endsWith(".md") && text.startsWith("---\n")) {
                  val idx = text.indexOf("\n---\n")
                  if (idx >= 0) text = text.substring(idx)
                }
                appendLine(text)
              } else {
                appendLine("<!-- File not found: $relatedFile -->")
              }
              appendLine(FENCE)
            }
          }
        }
      }

      companion object {
        private const val FENCE = "\u0060\u0060\u0060"
      }
    }