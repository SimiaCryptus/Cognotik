package com.simiacryptus.cognotik.diff

import org.slf4j.LoggerFactory

data class CodeBlockMatch(
    val language: String, val code: String, val range: IntRange
)

private data class FenceInfo(
    val lineIndex: Int,
    val indentation: Int,
    val language: String,
    val isOpening: Boolean, // true = definitely opening (has language), false = closing/ambiguous
    val raw: String
)

private val log = LoggerFactory.getLogger("MarkdownCodeBlockExtractor")

fun String.getMarkdownCodeBlockMatches(): List<CodeBlockMatch> {
    val lines = lines()
    val fencePattern = """^(\s*)```(.*)$""".toRegex()
    val fences = mutableListOf<FenceInfo>()
    for ((lineIndex, line) in lines.withIndex()) {
        val match = fencePattern.matchEntire(line) ?: continue
        val suffix = match.groupValues[2].trim()
        // A fence is "opening" if it has a language keyword (non-empty suffix that isn't just whitespace)
        // A bare ``` is ambiguous - could be opening or closing
        fences.add(
            FenceInfo(
                lineIndex = lineIndex,
                indentation = match.groupValues[1].length,
                language = suffix,
                isOpening = suffix.isNotEmpty() && !suffix.startsWith("`"),
                raw = line
            )
        )
    }

    // Now find top-level code blocks by pairing open/close fences
    // Fences indented deeper than the opening fence are treated as nested content
    val result = mutableListOf<Pair<Pair<FenceInfo, FenceInfo>, String>>()
    var i = 0
    while (i < fences.size) {
        val openFence = fences[i]
        var closeIndex = -1
        var j = i
        var depth = 1
        while (++j < fences.size) {
            val candidate = fences[j]
            if (candidate.indentation < openFence.indentation) {
                break
            } else if (candidate.indentation == openFence.indentation) {
                if (candidate.isOpening) {
                    depth++
                } else {
                    depth--
                    if (depth == 0) {
                        closeIndex = j
                        break
                    }
                }
            }
        }
        if (closeIndex == -1) {
            log.warn("No closing fence found for opening fence at line ${openFence.lineIndex}: '${openFence.raw}'")
            i++
            continue // No matching close found, skip this fence
        }
        val closeFence = fences[closeIndex]
        val contentLines = lines.subList(openFence.lineIndex + 1, closeFence.lineIndex)
        val code = contentLines.joinToString("\n")
        result.add((openFence to closeFence) to code)
        i = closeIndex + 1
    }

    // Convert to CodeBlockMatch format
    val results = mutableListOf<CodeBlockMatch>()
    for ((t, code) in result) {
        val (openInfo,closeInfo) = t
        results.add(CodeBlockMatch(
          language = openInfo.language,
          code = code,
          range = openInfo.lineIndex until closeInfo.lineIndex + 1
        ))
    }
    return results
}