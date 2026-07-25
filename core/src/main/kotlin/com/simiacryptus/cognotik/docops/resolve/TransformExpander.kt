package com.simiacryptus.cognotik.docops.resolve

    import com.simiacryptus.cognotik.docops.model.DocSpec
    import com.simiacryptus.cognotik.docops.model.TransformMatch
    import com.simiacryptus.cognotik.docops.model.TransformSpec
    import org.slf4j.LoggerFactory
    import java.io.File
    import java.util.regex.Matcher
    import java.util.regex.Pattern

    /** Regex `transforms:` expansion plus `$n` / `$n+k` / `$n-k` backreference arithmetic. */
    object TransformExpander {

      private val log = LoggerFactory.getLogger(TransformExpander::class.java)
      private val BACKREF = Regex("""\$(\d+)([+-]\d+)?""")

      fun compile(pattern: String): Pattern? = try {
        Pattern.compile(pattern)
      } catch (e: Exception) {
        log.warn("Invalid regex pattern: $pattern", e)
        null
      }

      /** Candidate path made relative to the doc's own directory, `\` normalized to `/`. */
      fun relativize(spec: DocSpec, candidate: File): String? = try {
        candidate.absoluteFile.relativeTo(spec.baseDir).path.replace("\\", "/")
      } catch (_: IllegalArgumentException) {
        null
      }

      fun destinationOf(spec: DocSpec, destPattern: String, matcher: Matcher): File {
        val destPath = applyBackreferences(destPattern, matcher)
        return try {
          spec.baseDir.resolve(destPath).canonicalFile
        } catch (_: Exception) {
          spec.baseDir.resolve(destPath)
        }
      }

      /** Full expansion against every file under [root]. */
      fun expand(
        root: File,
        transform: TransformSpec,
        spec: DocSpec,
        lister: (File) -> List<File> = GlobExpander.defaultLister,
      ): List<TransformMatch> {
        val sourceRegex = compile(transform.sourcePattern) ?: return emptyList()
        return lister(root).filter { it.isFile }.mapNotNull { sourceFile ->
          val relativePath = relativize(spec, sourceFile) ?: return@mapNotNull null
          val matcher = sourceRegex.matcher(relativePath)
          if (!matcher.matches()) return@mapNotNull null
          TransformMatch(
            sourceFile = runCatching { sourceFile.canonicalFile }.getOrDefault(sourceFile),
            destinationFile = destinationOf(spec, transform.destinationPattern, matcher),
            spec = spec,
          )
        }
      }

      /**
       * Would [candidate] (which may not exist yet) match this transform? Used by the planner's
       * fixpoint loop to discover chained pipelines (`a.proto -> a.kt -> a.docs.md`).
       */
      fun destinationForHypothetical(spec: DocSpec, transform: TransformSpec, candidate: File): File? {
        val sourceRegex = compile(transform.sourcePattern) ?: return null
        val relativePath = relativize(spec, candidate) ?: return null
        val matcher = sourceRegex.matcher(relativePath)
        return if (matcher.matches()) destinationOf(spec, transform.destinationPattern, matcher) else null
      }

      fun applyBackreferences(destPattern: String, matcher: Matcher): String {
        var result = destPattern
        for (match in BACKREF.findAll(destPattern).toList().sortedByDescending { it.range.first }) {
          val groupIndex = match.groupValues[1].toInt()
          if (groupIndex > matcher.groupCount()) continue
          val arithmetic = match.groupValues[2]
          val groupValue = matcher.group(groupIndex) ?: ""
          val replacement = when {
            arithmetic.isEmpty() -> groupValue
            else -> {
              val numeric = groupValue.toLongOrNull()
              if (numeric == null) groupValue + arithmetic else {
                val operand = arithmetic.substring(1).toLongOrNull() ?: 0L
                when (arithmetic[0]) {
                  '+' -> numeric + operand
                  '-' -> numeric - operand
                  else -> numeric
                }.toString()
              }
            }
          }
          result = result.substring(0, match.range.first) + replacement + result.substring(match.range.last + 1)
        }
        return result
      }
    }