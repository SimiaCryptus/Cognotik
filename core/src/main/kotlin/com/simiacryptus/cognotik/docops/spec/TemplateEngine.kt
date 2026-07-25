package com.simiacryptus.cognotik.docops.spec

    import org.slf4j.LoggerFactory
    import java.io.File

    /** `{{ NAME }}` substitution plus discovery of declared template variables. Pure apart from [listKeys]. */
    object TemplateEngine {

      private val log = LoggerFactory.getLogger(TemplateEngine::class.java)
      private val PLACEHOLDER = Regex("""\{\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*}}""")

      val TEMPLATE_VAR_KEYS = setOf("template_vars", "template_variables", "vars", "variables")

      fun parseVars(frontmatter: Map<String, Any>): Map<String, String> {
        val result = linkedMapOf<String, String>()
        for (key in TEMPLATE_VAR_KEYS) {
          when (val value = frontmatter[key] ?: continue) {
            is Map<*, *> -> value.forEach { (k, v) -> if (k != null) result[k.toString()] = v?.toString() ?: "" }
            is List<*> -> value.filterIsInstance<String>().forEach { entry -> putPair(result, entry) }
            is String -> putPair(result, value, allowBare = false)
            else -> log.warn("Unsupported template variables value type for key '$key': ${value.javaClass.name}")
          }
        }
        return result
      }

      private fun putPair(into: MutableMap<String, String>, entry: String, allowBare: Boolean = true) {
        val trimmed = entry.trim()
        val sepIdx = trimmed.indexOfAny(charArrayOf(':', '='))
        if (sepIdx > 0) {
          val k = trimmed.substring(0, sepIdx).trim()
          val v = trimmed.substring(sepIdx + 1).trim()
          if (k.isNotEmpty()) into[k] = v
        } else if (allowBare && trimmed.isNotEmpty()) {
          into[trimmed] = ""
        }
      }

      fun substitute(text: String, vars: Map<String, String>): String {
        if (vars.isEmpty()) return text
        return PLACEHOLDER.replace(text) { match ->
          val replacement = vars[match.groupValues[1]]
          if (replacement != null) Regex.escapeReplacement(replacement) else match.value
        }
      }

      fun listKeys(file: File): Map<String, String> {
        if (!file.exists() || !file.isFile) {
          log.debug("listKeys: file does not exist or is not a file: ${file.absolutePath}")
          return emptyMap()
        }
        val content = try {
          file.readText()
        } catch (e: Exception) {
          log.warn("listKeys: failed to read file: ${file.absolutePath}", e)
          return emptyMap()
        }
        val (frontmatterText, _) = FrontmatterParser.split(content) ?: return emptyMap()
        return try {
          parseVars(FrontmatterParser.parse(frontmatterText))
        } catch (e: Exception) {
          log.warn("listKeys: failed to parse frontmatter for ${file.absolutePath}", e)
          emptyMap()
        }
      }

      fun listKeys(files: Iterable<File>): Map<String, String> {
        val merged = linkedMapOf<String, String>()
        for (file in files) for ((k, v) in listKeys(file)) if (k !in merged) merged[k] = v
        return merged
      }
    }