package com.simiacryptus.cognotik.docops.spec

    import com.simiacryptus.cognotik.docops.model.GenerateSpec
    import com.simiacryptus.cognotik.docops.model.TransformSpec
    import org.slf4j.LoggerFactory

    /** Typed accessors over the raw frontmatter map. Pure; no filesystem access. */
    class Frontmatter(val raw: Map<String, Any>) {

      fun string(key: String): String? = raw[key] as? String

      val specifies: List<String> get() = pathList("specifies")
      val documents: List<String> get() = pathList("documents")
      val related: List<String> get() = pathList("related")

      val taskType: String? get() = string("task_type")
      val taskConfigJson: String? get() = string("task_config_json")
      val updateMode: String? get() = string("update_mode")
      val folder: String? get() = string("folder")
      val prompt: String? get() = string("prompt")

      val transforms: List<TransformSpec>
        get() {
          val value = raw["transforms"] ?: return emptyList()
          val strings = when (value) {
            is String -> listOf(value)
            is List<*> -> value.filterIsInstance<String>()
            else -> emptyList()
          }
          return strings.mapNotNull { str ->
            val parts = str.split("->").map { it.trim() }
            if (parts.size == 2) {
              TransformSpec(
                sourcePattern = MarkdownLinks.extractPath(parts[0]),
                destinationPattern = MarkdownLinks.extractPath(parts[1]),
              )
            } else {
              log.warn("Invalid transform format: $str (expected 'pattern -> destination')")
              null
            }
          }
        }

      val generates: List<GenerateSpec>
        get() = when (val value = raw["generates"]) {
          is Map<*, *> -> listOfNotNull(generateSpec(value))
          is List<*> -> value.mapNotNull { (it as? Map<*, *>)?.let(::generateSpec) }
          else -> emptyList()
        }

      private fun pathList(key: String): List<String> = when (val value = raw[key]) {
        is String -> listOf(MarkdownLinks.extractPath(value))
        is List<*> -> MarkdownLinks.extractPaths(value.filterIsInstance<String>())
        else -> emptyList()
      }

      private fun generateSpec(map: Map<*, *>): GenerateSpec? {
        val output = (map["output"] as? String)?.let { MarkdownLinks.extractPath(it) } ?: return null
        val inputs = when (val inputsValue = map["inputs"]) {
          is String -> listOf(MarkdownLinks.extractPath(inputsValue))
          is List<*> -> MarkdownLinks.extractPaths(inputsValue.filterIsInstance<String>())
          else -> emptyList()
        }
        if (inputs.isEmpty()) {
          log.warn("Generate spec for '$output' has no inputs")
          return null
        }
        return GenerateSpec(output = output, inputs = inputs)
      }

      companion object {
        private val log = LoggerFactory.getLogger(Frontmatter::class.java)
      }
    }