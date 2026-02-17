package com.simiacryptus.cognotik.ui.patch


val log = org.slf4j.LoggerFactory.getLogger("DiffInstrumentor")

fun normalizeFilename(filename: String, maxIterations: Int = 10): String {
  if (filename.isBlank()) {
    log.debug("normalizeFilename called with blank input")
    return ""
  }
  val result = repeat(filename, maxIterations) {
    it.trim()
      // Remove common prefixes
      .removePrefix("Code:")
      .removePrefix("code:")
      .removePrefix("File:")
      .removePrefix("file:")
      .removePrefix("Path:")
      .removePrefix("path:")
      .removePrefix("Filename:")
      .removePrefix("filename:")
      .removePrefix("Modified:")
      .removePrefix("modified:")
      .removePrefix("Updated:")
      .removePrefix("updated:")
      .removePrefix("Changed:")
      .removePrefix("changed:")
      .removePrefix("Edit:")
      .removePrefix("edit:")
      .removePrefix("Patch:")
      .removePrefix("patch:")
      // Remove common suffixes
      .removeSuffix(":")
      .removeSuffix(".")
      // Remove quotes and backticks
      .removePrefix("\"").removeSuffix("\"")
      .removePrefix("'").removeSuffix("'")
      .removePrefix("`").removeSuffix("`")
      // Remove any leading number with a period (e.g. "1. foo.kt")
      .replace(Regex("^\\d+\\.\\s*"), "")
      // Remove markdown formatting
      .replace("**", "")
      .replace("*", "")
      // Clean up whitespace
      .trim()
      // Remove code block language indicators mistaken for filenames
      .let { name ->
        if (name.matches(
            Regex(
              "^(java|kotlin|kt|js|javascript|python|py|cpp|c|cs|go|rust|rs|php|rb|ruby|swift|scala|clj|clojure|sh|bash|sql|html|css|xml|json|yaml|yml|toml|ini|cfg|conf|config|properties|gradle|maven|pom|dockerfile|docker|makefile|make|cmake|bazel|build)$",
              RegexOption.IGNORE_CASE
            )
          )
        ) "" else name
      }
      .trim()
  }
  if (result != filename) {
    log.debug("normalizeFilename: '{}' -> '{}'", filename, result)
  }
  return result
}

fun <T> repeat(
  original: T,
  maxIterations: Int,
  function: (T) -> T
): T {
  var current = original
  repeat(maxIterations) {
    val next = (function)(current)
    if (next == current) return current
    current = next
  }
  log.debug("repeat() reached max iterations ({})", maxIterations)
  return current
}