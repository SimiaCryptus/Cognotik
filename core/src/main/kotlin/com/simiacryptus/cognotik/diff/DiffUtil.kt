package com.simiacryptus.cognotik.diff

import com.simiacryptus.cognotik.diff.PatchLineType.*
import org.slf4j.LoggerFactory

enum class PatchLineType {
  Added, Deleted, Unchanged
}

data class PatchLine(
  val type: PatchLineType,
  val lineNumber: Int,
  val line: String,
  val compareText: String = line.trim(),
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PatchLine

    return compareText == other.compareText
  }

  override fun hashCode(): Int {
    return compareText.hashCode()
  }
}

object DiffUtil {
  // ... (previous code remains unchanged)

  private val log = LoggerFactory.getLogger(DiffUtil::class.java)

  /**
   * Generates a list of DiffResult representing the differences between two lists of strings.
   * This function compares the original and modified texts line by line and categorizes each line as added, deleted, or unchanged.
   *
   * @param original The original list of strings.
   * @param modified The modified list of strings.
   * @return A list of DiffResult indicating the differences.
   */
  fun generateDiff(original: List<String>, modified: List<String>): List<PatchLine> {
    val originalLines = original.mapIndexed { i, v -> PatchLine(Unchanged, i, v.trim()) }
    val modifiedLines = modified.mapIndexed { i, v -> PatchLine(Unchanged, i, v.trim()) }
    val patchLines = mutableListOf<PatchLine>()
    var i = 0
    var j = 0

    log.debug("Starting diff generation. Original size: ${original.size}, Modified size: ${modified.size}")

    while (i < originalLines.size && j < modifiedLines.size) {
      val originalLine = originalLines[i]
      val modifiedLine = modifiedLines[j]

      log.trace("Comparing lines - Original: $originalLine, Modified: $modifiedLine")
      if (originalLine == modifiedLine) {
        patchLines.add(PatchLine(Unchanged, originalLine.lineNumber, original[i]))
        i++
        j++
      } else {
        val originalIndex = originalLines.subList(i, originalLines.size).indexOf(modifiedLine).let { if (it == -1) null else it + i }
        val modifiedIndex = modifiedLines.subList(j, modifiedLines.size).indexOf(originalLine).let { if (it == -1) null else it + j }
        log.debug("Mismatch found. Original index: $originalIndex, Modified index: $modifiedIndex")

        if (originalIndex != null && modifiedIndex != null) {
          log.debug("Both indices found. Choosing shorter path.")
          if (originalIndex - i < modifiedIndex - j) {
            while (i < originalIndex) {
              patchLines.add(PatchLine(Deleted, originalLines[i].lineNumber, original[i]))
              i++
            }
          } else {
            while (j < modifiedIndex) {
              patchLines.add(PatchLine(Added, modifiedLines[j].lineNumber, modified[j]))
              j++
            }
          }
        } else if (originalIndex != null) {
          log.debug("Original index found. Deleting lines until match.")
          while (i < originalIndex) {
            patchLines.add(PatchLine(Deleted, originalLines[i].lineNumber, original[i]))
            i++
          }
        } else if (modifiedIndex != null) {
          log.debug("Modified index found. Adding lines until match.")
          while (j < modifiedIndex) {
            patchLines.add(PatchLine(Added, modifiedLines[j].lineNumber, modified[j]))
            j++
          }
        } else {
          patchLines.add(PatchLine(Deleted, originalLines[i].lineNumber, original[i]))
          patchLines.add(PatchLine(Added, modifiedLines[j].lineNumber, modified[j]))
          i++
          j++
        }
      }
    }

    log.debug("Processing remaining lines. Original: ${originalLines.size - i}, Modified: ${modifiedLines.size - j}")
    while (i < originalLines.size) {
      patchLines.add(PatchLine(Deleted, originalLines[i].lineNumber, original[i]))
      i++
    }

    while (j < modifiedLines.size) {
      patchLines.add(PatchLine(Added, modifiedLines[j].lineNumber, modified[j]))
      j++
    }

    log.info("Diff generation completed. Total patch lines: ${patchLines.size}")

    return patchLines
  }

  /**
   * Formats the list of DiffResult into a human-readable string representation.
   * This function processes each diff result to format added, deleted, and unchanged lines appropriately,
   * including context lines and markers for easier reading.
   *
   * @param patchLines The list of DiffResult to format.
   * @param contextLines The number of context lines to include around changes.
   * @return A formatted string representing the diff.
   */
  fun formatDiff(patchLines: List<PatchLine>, contextLines: Int = 3): String {
    val formattedLines = mutableListOf<String>()
    var lastPrintedLine = -1

    log.debug("Starting diff formatting. Total lines: ${patchLines.size}, Context lines: $contextLines")

    patchLines.forEachIndexed { index, lineDiff ->
      if (lineDiff.type != Unchanged ||
        (index > 0 && patchLines[index - 1].type != Unchanged) ||
        (index < patchLines.size - 1 && patchLines[index + 1].type != Unchanged)
      ) {

        // Print context lines before the change
        val contextStart = maxOf(lastPrintedLine + 1, index - contextLines)
        for (i in contextStart until index) {
          if (i > lastPrintedLine) {
            formattedLines.add("  ${patchLines[i].line}")
            lastPrintedLine = i
          }
        }

        // Print the change
        val prefix = when (lineDiff.type) {
          Added -> "+ "
          Deleted -> "- "
          Unchanged -> "  "
        }
        formattedLines.add("$prefix${lineDiff.line}")
        lastPrintedLine = index

      }
    }

    log.info("Diff formatting completed. Total formatted lines: ${formattedLines.size}")

    val formattedDiff = formattedLines.joinToString("\n")
    log.debug("Formatted diff:\n$formattedDiff")
    return formattedDiff
  }
}