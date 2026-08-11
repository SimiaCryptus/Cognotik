package com.simiacryptus.cognotik.docops.exec

    import com.simiacryptus.cognotik.docops.model.ModificationTaskConfig
    import com.simiacryptus.cognotik.docops.model.PlannedTask
    import com.simiacryptus.cognotik.docops.spec.FrontmatterParser
    import com.simiacryptus.cognotik.docops.spec.TemplateEngine
    import com.simiacryptus.cognotik.util.JsonUtil
    import com.simiacryptus.cognotik.util.jsonCast
    import org.slf4j.LoggerFactory
    import java.io.File

    /**
     * Builds the JSON-compatible execution config for one planned task. Anything that cannot be
     * derived declaratively is delegated to [DocExecutionContext.inferTaskConfig].
     *
     * ## Path handling contract
     *
     * The host resolves every relative path in the resulting config against
     * [DocTaskRequest.workingDir], which [DocTaskRunner] sets to the task's own root
     * ([ModificationTaskConfig.root] - the `folder:` override, or the workspace root).
     *
     * Therefore *every* path produced here is relativized (history) or absolutized
     * (`related_files`) against that same directory. Using any other base - e.g. the target's
     * parent folder - makes the agent write outputs into the wrong directory.
     */
    class ExecutionConfigFactory<K : DocTaskKind, S : Any>(
      private val root: File,
      private val templateVarOverrides: Map<String, String> = emptyMap(),
    ) {

      fun build(planned: PlannedTask<K>, ctx: DocExecutionContext<K, S>): MutableMap<String, Any> {
        val task = planned.task
        val kind = task.taskType
        val data = task.data
        val overrides = data.taskConfigOverrides
        val workingDir = workingDirOf(data)

        val base: Any = when {
          kind.isFileTask || kind.isTemplateTask -> {
            val declarative = LinkedHashMap<String, Any>()
            declarative["task_type"] = kind.name
            if (kind.isTemplateTask) {
              data.related_files?.firstOrNull { it.path.endsWith(".erb") }
                ?.let { declarative["template_file"] = it.absolutePath }
            }
            // Serializing `data` emits root / main_file / relative_files, all already based on data.root.
            declarative.putAll(data.jsonCast<Map<String, Any>>())
            if (overrides != null) declarative + overrides else declarative
          }

          else -> {
            val inferred = ctx.inferTaskConfig(
              DocTaskInferenceRequest(
                taskKind = kind,
                taskDescription = data.task_description,
                prompt = "Execute the following task based on the provided context. Task type: ${kind.name}",
                history = history(planned, data, workingDir),
                workingDir = workingDir,
                patchProcessor = task.patchProcessor,
                typeConfig = task.typeConfig,
              )
            )
            JsonUtil.merge(inferred, overrides ?: emptyMap<String, Any>())
          }
        }

        val config = base.jsonCast<MutableMap<String, Any>>()
        config["related_files"] = absolutePaths(config["related_files"], workingDir)
        config["task_description"] = describe(data)
        return config
      }

      /** The one directory every relative path in the execution config is resolved against. */
      private fun workingDirOf(data: ModificationTaskConfig): File =
        data.root.takeIf { it.path.isNotBlank() } ?: root

      private fun history(
        planned: PlannedTask<K>,
        data: ModificationTaskConfig,
        workingDir: File,
      ): List<String> = buildList {
        add("Task type: ${planned.task.taskType.name}")
        add("Task description: ${data.task_description}")
        data.main_file?.let { add("Output file: ${relativize(workingDir, it)}") }
        data.related_files?.forEach { related ->
          val resolved = resolve(workingDir, related)
          if (!resolved.isFile) return@forEach
          val text = runCatching { resolved.readText() }.getOrNull() ?: return@forEach
          add("Related file (${relativize(workingDir, resolved)}):\n$FENCE\n$text\n$FENCE")
        }
        val message = planned.task.message()
        if (message.isNotBlank()) add(message)
      }

      private fun resolve(workingDir: File, file: File): File =
        if (file.isAbsolute) file else workingDir.resolve(file)

      /** Working-directory-relative, `/`-normalized path; absolute fallback across filesystem roots. */
      private fun relativize(workingDir: File, file: File): String = try {
        val relative = resolve(workingDir, file).canonicalFile.relativeTo(workingDir.canonicalFile).path
        relative.ifEmpty { file.name }.replace(File.separatorChar, '/')
      } catch (e: Exception) {
        log.debug("Could not relativize ${file.absolutePath} against ${workingDir.absolutePath}", e)
        file.absolutePath.replace(File.separatorChar, '/')
      }

      /** Canonical absolute paths: unambiguous no matter how the host resolves them. */
      private fun absolutePaths(value: Any?, workingDir: File): List<String> =
        (value as? List<*>)?.filterIsInstance<String>()?.map { path ->
          val resolved = resolve(workingDir, File(path))
          runCatching { resolved.canonicalPath }.getOrElse { resolved.absolutePath }
        }?.distinct() ?: emptyList()

      /** Combined description + the body of the first doc file (frontmatter stripped, vars applied). */
      private fun describe(data: ModificationTaskConfig): String = buildString {
        appendLine(data.task_description)
        val docFile = data.doc_files.firstOrNull() ?: return@buildString
        if (!docFile.isFile) {
          log.debug("Doc file not readable while building task_description: ${docFile.absolutePath}")
          return@buildString
        }
        val content = runCatching { docFile.readText() }.getOrElse {
          log.warn("Failed to read doc file ${docFile.absolutePath}", it)
          return@buildString
        }
        val body = (FrontmatterParser.split(content)?.second ?: content).trim()
        if (body.isBlank()) return@buildString
        append(if (templateVarOverrides.isEmpty()) body else TemplateEngine.substitute(body, templateVarOverrides))
      }

      companion object {
        private val log = LoggerFactory.getLogger(ExecutionConfigFactory::class.java)
        private const val FENCE = "\u0060\u0060\u0060"
      }
    }