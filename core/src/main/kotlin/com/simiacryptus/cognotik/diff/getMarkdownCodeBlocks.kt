package com.simiacryptus.cognotik.diff

fun String.getMarkdownCodeBlocks(): List<Pair<String, String>> {
    val lines = lines()

    // Detect all code fence occurrences with metadata
    data class FenceInfo(
        val lineIndex: Int,
        val indentation: Int,
        val language: String,
        val isOpening: Boolean, // true = definitely opening (has language), false = closing/ambiguous
        val raw: String
    )

    val fencePattern = """^(\s*)```(.*)$""".toRegex()
    val fences = mutableListOf<FenceInfo>()
    for ((lineIndex, line) in lines.withIndex()) {
        val match = fencePattern.matchEntire(line) ?: continue
        val indentation = match.groupValues[1].length
        val suffix = match.groupValues[2].trim()
        // A fence is "opening" if it has a language keyword (non-empty suffix that isn't just whitespace)
        // A bare ``` is ambiguous - could be opening or closing
        val isOpening = suffix.isNotEmpty() && !suffix.startsWith("`")
        fences.add(
            FenceInfo(
                lineIndex = lineIndex,
                indentation = indentation,
                language = suffix,
                isOpening = isOpening,
                raw = line
            )
        )
    }

    // Now find top-level code blocks by pairing open/close fences
    // Fences indented deeper than the opening fence are treated as nested content
    val result = mutableListOf<Pair<String, String>>()
    var i = 0
    while (i < fences.size) {
        val openFence = fences[i]
        var closeIndex = -1
        var j = i + 1
        while (j < fences.size) {
            val candidate = fences[j]
            if (candidate.indentation > openFence.indentation) {
                j++
                continue
            }
            if (candidate.indentation == openFence.indentation) {
                if (candidate.isOpening) {
                    break
                } else {
                    closeIndex = j
                    break
                }
            }
            // candidate.indentation < openFence.indentation: belongs to an outer scope, stop searching
            break
            j++
        }

        if (closeIndex != -1) {
            val closeFence = fences[closeIndex]
            val language = openFence.language
            val contentLines = lines.subList(openFence.lineIndex + 1, closeFence.lineIndex)
            val code = contentLines.joinToString("\n")
            result.add(language to code)
            i = closeIndex + 1
        } else {
            // No matching close found, skip this fence
            i++
        }
    }
    return result
}

data class CodeBlockMatch(
    val lang: String,
    val code: String,
    val range: IntRange
)

fun findCodeBlockMatches(
    blocks: List<Pair<String, String>>,
    response: String
): List<CodeBlockMatch> {
    val results = mutableListOf<CodeBlockMatch>()
    var searchFrom = 0
    for ((lang, code) in blocks) {
        val needle = "```$lang\n$code\n```"
        val index = response.indexOf(needle, searchFrom)
        if (index >= 0) {
            val endIndex = index + needle.length
            results.add(CodeBlockMatch(lang, code, index until endIndex))
            searchFrom = endIndex
        }
    }
    return results
}

fun String.getMarkdownCodeBlockMatches(): List<CodeBlockMatch> {
    return findCodeBlockMatches(this.getMarkdownCodeBlocks(), this)
}