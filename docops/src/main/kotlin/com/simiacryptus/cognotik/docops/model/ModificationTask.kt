package com.simiacryptus.cognotik.docops.model

import com.simiacryptus.cognotik.docops.exec.DocTaskKind
import com.simiacryptus.cognotik.docops.spec.FrontmatterParser
import com.simiacryptus.cognotik.text.patch.PatchProcessor
import org.slf4j.LoggerFactory
import java.io.File

data class ModificationTaskConfig(
  val root: File,
  val main_file: File? = null,
  val related_files: List<File>? = null,
  val task_description: String = "",
  val taskConfigOverrides: Map<String, Any>? = null,
  val doc_files: List<File> = emptyList(),
) {
  val relative_files: List<String>?
    get() = main_file?.let { listOf(relativize(it)) }

  val relative_related_files: List<String>?
    get() = related_files?.map { relativize(it) }

  private fun relativize(file: File): String = try {
    file.canonicalFile.relativeTo(root.canonicalFile).toString()
  } catch (_: IllegalArgumentException) {
    file.canonicalFile.absolutePath
  } catch (_: Exception) {
    file.absolutePath
  }
   /**
    * Frontmatter re-read from [doc_files]. This is only a *fallback* for planners that forget to
    * propagate [DocSpec.frontmatter]; the parsed values are raw (no template substitution).
    */
   fun docFrontmatter(): Map<String, Any?> {
     if (doc_files.isEmpty()) return emptyMap()
     val merged = linkedMapOf<String, Any?>()
     for (doc in doc_files) {
       if (!doc.isFile) continue
       val content = try {
         doc.readText()
       } catch (e: Exception) {
         log.debug("Failed to read doc file while deriving frontmatter: ${doc.absolutePath}", e)
         null
       } ?: continue
       val frontmatterText = FrontmatterParser.split(content)?.first ?: continue
       val parsed = try {
         FrontmatterParser.parse(frontmatterText)
       } catch (e: Exception) {
         log.warn("Failed to parse frontmatter of ${doc.absolutePath}", e)
         null
       } ?: continue
       for ((key, value) in parsed) if (key !in merged) merged[key] = value
     }
     return merged
   }
   companion object {
     private val log = LoggerFactory.getLogger(ModificationTaskConfig::class.java)
   }
}

/**
 * The planned unit of work. Note that the patch processor is now strongly typed - the erased
 * `Any?` field and the unchecked `patchProcessorAs()` cast are gone.
 */
data class ModificationTask<K : DocTaskKind>(
  val data: ModificationTaskConfig,
  val taskType: K,
  val message: (File) -> String = { "" },
  val patchProcessor: PatchProcessor? = null,
   /**
    * Aggregated frontmatter from the documents that contributed to this task, as supplied by the
    * planner. Prefer [resolvedFrontmatter] when reading - it falls back to parsing the doc files.
    */
  val frontmatter: Map<String, Any?> = emptyMap(),
) {
   /**
    * [frontmatter] when the planner propagated it, otherwise the frontmatter parsed straight out of
    * [ModificationTaskConfig.doc_files]. Never silently empty when a doc file is available.
    */
   val resolvedFrontmatter: Map<String, Any?> by lazy {
     frontmatter.ifEmpty {
       data.docFrontmatter().also {
         if (it.isNotEmpty()) {
           log.debug(
             "Task frontmatter was empty; derived {} key(s) from doc files {}",
             it.size, data.doc_files.map { f -> f.name })
         }
       }
     }
   }

  /** JSON-compatible per-task-type settings. */
  val typeConfig: Map<String, Any>
    get() = data.taskConfigOverrides
      ?: taskType.defaultConfig()
      ?: mapOf("task_type" to taskType.name)

  fun rebase(prevRoot: File, newRoot: File): ModificationTask<K> =
    if (newRoot == prevRoot) this else copy(data = data.copy(root = newRoot))

  fun message(): String = message(data.root)
   companion object {
     private val log = LoggerFactory.getLogger(ModificationTask::class.java)
   }
}