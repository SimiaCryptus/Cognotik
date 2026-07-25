package com.simiacryptus.cognotik.docops.spec

    import com.simiacryptus.cognotik.docops.model.DocSpec
    import org.slf4j.LoggerFactory
    import java.io.File

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
        val (frontmatterText, bodyText) = FrontmatterParser.split(content) ?: return null

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
        val spec = DocSpec(
          docFile = file,
          specifies = fm.specifies,
          documents = fm.documents,
          transforms = fm.transforms,
          generates = fm.generates,
          related = fm.related,
          content = body,
          frontmatter = frontmatterMap,
          taskType = fm.taskType,
          taskConfigJson = fm.taskConfigJson,
          updateMode = fm.updateMode,
          targetFolder = fm.folder,
          prompt = fm.prompt,
        )
        return if (spec.hasTargets) spec else null
      }

      private fun mergeVars(declared: Map<String, String>): Map<String, String> {
        if (templateVarOverrides.isEmpty()) return declared
        val merged = linkedMapOf<String, String>()
        merged.putAll(declared)
        merged.putAll(templateVarOverrides)
        return merged
      }

      companion object {
        private val log = LoggerFactory.getLogger(MarkdownDocSpecLoader::class.java)
      }
    }