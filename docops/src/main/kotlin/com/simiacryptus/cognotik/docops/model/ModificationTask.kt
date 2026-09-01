package com.simiacryptus.cognotik.docops.model

import com.simiacryptus.cognotik.docops.exec.DocTaskKind
import com.simiacryptus.cognotik.text.patch.PatchProcessor
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
  /** Aggregated frontmatter from the documents that contributed to this task. */
  val frontmatter: Map<String, Any?> = emptyMap(),
) {
  /** JSON-compatible per-task-type settings. */
  val typeConfig: Map<String, Any>
    get() = data.taskConfigOverrides
      ?: taskType.defaultConfig()
      ?: mapOf("task_type" to taskType.name)

  fun rebase(prevRoot: File, newRoot: File): ModificationTask<K> =
    if (newRoot == prevRoot) this else copy(data = data.copy(root = newRoot))

  fun message(): String = message(data.root)
}