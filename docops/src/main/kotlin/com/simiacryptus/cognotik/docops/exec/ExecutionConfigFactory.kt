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

    val base: Any = when {
      kind.isFileTask || kind.isTemplateTask -> {
        val declarative = LinkedHashMap<String, Any>()
        declarative["task_type"] = kind.name
        if (kind.isTemplateTask) {
          data.related_files?.firstOrNull { it.path.endsWith(".erb") }
            ?.let { declarative["template_file"] = it.absolutePath }
        }
        declarative.putAll(data.jsonCast<Map<String, Any>>())
        if (overrides != null) declarative + overrides else declarative
      }

      else -> {
        val workingDir = data.main_file?.parentFile ?: root
        val rebased = data.copy(root = workingDir)
        val inferred = ctx.inferTaskConfig(
          DocTaskInferenceRequest(
            taskKind = kind,
            taskDescription = rebased.task_description,
            prompt = "Execute the following task based on the provided context. Task type: ${kind.name}",
            history = history(planned, rebased, workingDir),
            workingDir = workingDir,
            patchProcessor = task.patchProcessor,
            typeConfig = task.typeConfig,
          )
        )
        JsonUtil.merge(inferred, overrides ?: emptyMap<String, Any>())
      }
    }

    val config = base.jsonCast<MutableMap<String, Any>>()
    config["related_files"] = (config["related_files"] as? List<*>)?.filterIsInstance<String>() ?: emptyList<String>()
    config["task_description"] = describe(data)
    return config
  }

  private fun history(
    planned: PlannedTask<K>,
    data: ModificationTaskConfig,
    workingDir: File,
  ): List<String> = buildList {
    add("Task type: ${planned.task.taskType.name}")
    add("Task description: ${data.task_description}")
    data.relative_files?.forEach { add("Output file: $it") }
    data.relative_related_files?.forEach { relative ->
      val resolved = File(relative).let { if (it.isAbsolute) it else workingDir.resolve(relative) }
      if (resolved.exists()) {
        val text = runCatching { resolved.readText() }.getOrNull() ?: return@forEach
        add("Related file ($relative):\n$FENCE\n$text\n$FENCE")
      }
    }
    val message = planned.task.message()
    if (message.isNotBlank()) add(message)
  }

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