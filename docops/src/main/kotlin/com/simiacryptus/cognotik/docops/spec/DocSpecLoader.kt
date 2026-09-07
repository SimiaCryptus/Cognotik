package com.simiacryptus.cognotik.docops.spec

import com.simiacryptus.cognotik.docops.model.DocSpec
import org.slf4j.LoggerFactory
import java.io.File
private val markdownLinkRegex = Regex(
  """(?<!!)\[[^\]]*]\(\s*(?:<([^>]+)>|([^)\s]+))(?:\s+["'][^"']*["'])?\s*\)"""
)


fun interface DocSpecLoader {
  /** Returns null when the file is not a doc-ops document. */
  fun load(file: File): DocSpec?

  fun loadAll(files: Iterable<File>): List<DocSpec> = files.mapNotNull { load(it) }
}

class MarkdownDocSpecLoader(
  private val templateVarOverrides: Map<String, String> = emptyMap(),
) : DocSpecLoader {

  override fun load(file: File): DocSpec? {
    if (!file.exists() || !file.isFile) {
      log.warn("File does not exist or is not a file: ${file.absolutePath}")
      return null
    }
    val content = try {
      file.readText()
    } catch (e: Exception) {
      log.error("Failed to read file: ${file.absolutePath}", e)
      return null
    }
    val (frontmatterText, bodyText) = FrontmatterParser.split(content) ?: run {
      log.info(
        "Not a doc-ops document (no leading '---' frontmatter block, or no closing '---'): ${file.absolutePath}"
      )
      return null
    }

    val rawFrontmatter = FrontmatterParser.parse(frontmatterText)
    val templateVars = mergeVars(TemplateEngine.parseVars(rawFrontmatter))

    val frontmatterMap = if (templateVars.isEmpty()) rawFrontmatter else {
      val stripped = rawFrontmatter.filterKeys { it !in TemplateEngine.TEMPLATE_VAR_KEYS }
      FrontmatterParser.parse(
        TemplateEngine.substitute(FrontmatterParser.render(stripped), templateVars)
      )
    }
    val body = if (templateVars.isEmpty()) bodyText else TemplateEngine.substitute(bodyText, templateVars)

    val fm = Frontmatter(frontmatterMap)
    val markdownLinks = extractMarkdownLinks(body)
    val related = (fm.related + markdownLinks).distinct()
    val spec = DocSpec(
      docFile = file,
      specifies = fm.specifies,
      documents = fm.documents,
      transforms = fm.transforms,
      generates = fm.generates,
      related = related,
      content = body,
      frontmatter = frontmatterMap,
      taskType = fm.taskType,
      taskConfigJson = fm.taskConfigJson,
      updateMode = fm.updateMode,
      targetFolder = fm.folder,
      prompt = fm.prompt,
    )
    if (!spec.hasTargets) {
      log.info(
        "Skipping ${file.absolutePath}: frontmatter declares no targets. Parsed keys=${frontmatterMap.keys}; " +
            "expected at least one of specifies:/transforms:/documents:/generates:/folder:"
      )
      return null
    }
    log.info(
      "Parsed doc spec ${file.absolutePath}: specifies=${spec.specifies.size}, transforms=${spec.transforms.size}, " +
          "documents=${spec.documents.size}, generates=${spec.generates.size}, folder=${spec.targetFolder}, " +
          "related=${related.size}" +
          (if (markdownLinks.isEmpty()) "" else ", markdownLinks=${markdownLinks.size}") +
          (if (templateVars.isEmpty()) "" else ", templateVars=${templateVars.keys}")
    )
    return spec
  }

  private fun mergeVars(declared: Map<String, String>): Map<String, String> {
    if (templateVarOverrides.isEmpty()) return declared
    val merged = linkedMapOf<String, String>()
    merged.putAll(declared)
    merged.putAll(templateVarOverrides)
    return merged
  }
  private fun extractMarkdownLinks(markdown: String): List<String> =
    markdownLinkRegex.findAll(markdown)
      .mapNotNull { match ->
        val destination = match.groupValues[1].takeIf { it.isNotEmpty() } ?: match.groupValues[2]
        destination.trim().takeIf { it.isNotEmpty() }
      }
      .filterNot { link ->
        link.startsWith("#") || link.contains("://") || link.startsWith("mailto:")
      }
      .toList()


  companion object {
    private val log = LoggerFactory.getLogger(MarkdownDocSpecLoader::class.java)
  }
}