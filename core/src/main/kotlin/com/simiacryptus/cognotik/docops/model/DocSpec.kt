package com.simiacryptus.cognotik.docops.model

import java.io.File

data class TransformSpec(
  val sourcePattern: String,
  val destinationPattern: String,
)

data class GenerateSpec(
  val output: String,
  val inputs: List<String>,
)

/** One parsed markdown document (frontmatter + body, template variables already applied). */
data class DocSpec(
  val docFile: File,
  val specifies: List<String> = emptyList(),
  val documents: List<String> = emptyList(),
  val transforms: List<TransformSpec> = emptyList(),
  val generates: List<GenerateSpec> = emptyList(),
  val related: List<String> = emptyList(),
  val content: String = "",
  val frontmatter: Map<String, Any> = emptyMap(),
  val taskConfigJson: String? = null,
  val taskType: String? = null,
  val updateMode: String? = null,
  val targetFolder: String? = null,
  /** Typed accessor for `prompt:` (previously read straight out of [frontmatter]). */
  val prompt: String? = null,
) {
  /** Every path in a doc is resolved relative to the doc's own directory. */
  val baseDir: File get() = docFile.absoluteFile.parentFile ?: docFile.absoluteFile

  val hasTargets: Boolean
    get() = specifies.isNotEmpty() || transforms.isNotEmpty() ||
        documents.isNotEmpty() || generates.isNotEmpty() || targetFolder != null

  val hasOnlyFolderTarget: Boolean
    get() = targetFolder != null && specifies.isEmpty() && transforms.isEmpty() &&
        generates.isEmpty() && documents.isEmpty()
}

/** A single (source -> destination) pair produced by a regex `transforms:` rule. */
data class TransformMatch(
  val sourceFile: File,
  val destinationFile: File,
  val spec: DocSpec,
)