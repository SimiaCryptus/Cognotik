package com.simiacryptus.cognotik.docops.spec

/**
 * Deliberately tiny hand-rolled YAML subset parser (pure, no I/O).
 *
 *  * `key: value`          -> String
 *  * `key:` + `  - item`   -> List<String>
 */
object FrontmatterParser {

  /** Splits `---\n<frontmatter>\n---\n<body>` or returns null when there is no frontmatter. */
  fun split(content: String): Pair<String, String>? {
    if (!content.startsWith("---")) return null
    val end = content.indexOf("---", 3)
    if (end == -1) return null
    return content.substring(3, end).trim() to content.substring(end + 3).trim()
  }

  fun parse(text: String): Map<String, Any> {
    val result = linkedMapOf<String, Any>()
    val lines = text.lines()
    var i = 0
    while (i < lines.size) {
      val line = lines[i]
      val colonIndex = line.indexOf(":")
      if (colonIndex >= 0) {
        val key = line.substring(0, colonIndex).trim()
        val valueAfterColon = line.substring(colonIndex + 1).trim()
        if (valueAfterColon.isEmpty()) {
          val listItems = mutableListOf<String>()
          i++
          while (i < lines.size && lines[i].trimStart().startsWith("- ")) {
            listItems.add(lines[i].trimStart().removePrefix("- ").trim())
            i++
          }
          if (listItems.isNotEmpty()) result[key] = listItems
          continue
        } else {
          result[key] = valueAfterColon
        }
      }
      i++
    }
    return result
  }

  /** Re-render a (possibly filtered) frontmatter map so it can be template-substituted and re-parsed. */
  fun render(frontmatter: Map<String, Any>): String {
    val sb = StringBuilder()
    for ((key, value) in frontmatter) {
      when (value) {
        is List<*> -> {
          sb.append(key).append(":\n")
          for (item in value) sb.append("  - ").append(item?.toString() ?: "").append('\n')
        }

        is Map<*, *> -> {
          sb.append(key).append(":\n")
          for ((k, v) in value) {
            sb.append("  ").append(k?.toString() ?: "").append(": ").append(v?.toString() ?: "").append('\n')
          }
        }

        else -> sb.append(key).append(": ").append(value.toString()).append('\n')
      }
    }
    return sb.toString()
  }
}