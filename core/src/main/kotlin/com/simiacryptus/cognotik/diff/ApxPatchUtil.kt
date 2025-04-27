package com.simiacryptus.cognotik.diff

import org.apache.commons.text.similarity.LevenshteinDistance

object ApxPatchUtil {

    fun patch(source: String, patch: String): String {
        val sourceLines = source.lines()
        val patchLines = patch.lines()

        val result = mutableListOf<String>()

        var sourceIndex = 0

        for (patchLine in patchLines.map { it.trim() }) {
            when {

                patchLine.startsWith("---") || patchLine.startsWith("+++") -> continue

                patchLine.startsWith("@@") -> continue

                patchLine.startsWith("-") -> {
                    sourceIndex = onDelete(patchLine, sourceIndex, sourceLines, result)
                }

                patchLine.startsWith("+") -> {
                    result.add(patchLine.substring(1))
                }

                patchLine.matches(Regex("\\d+:.*")) -> {
                    sourceIndex = onContextLine(patchLine.substringAfter(":"), sourceIndex, sourceLines, result)
                }

                else -> {
                    sourceIndex = onContextLine(patchLine, sourceIndex, sourceLines, result)
                }
            }
        }

        while (sourceIndex < sourceLines.size) {
            result.add(sourceLines[sourceIndex])
            sourceIndex++
        }

        return result.joinToString("\n")
    }

    private fun onDelete(
        patchLine: String,
        sourceIndex: Int,
        sourceLines: List<String>,
        result: MutableList<String>
    ): Int {
        var sourceIndex1 = sourceIndex
        val delLine = patchLine.substring(1)
        val sourceIndexSearch = lookAheadFor(sourceIndex1, sourceLines, delLine)
        if (sourceIndexSearch > 0 && sourceIndexSearch + 1 < sourceLines.size) {
            val contextChunk = sourceLines.subList(sourceIndex1, sourceIndexSearch)
            result.addAll(contextChunk)
            sourceIndex1 = sourceIndexSearch + 1
        } else {
            println("Deletion line not found in source file: $delLine")

        }
        return sourceIndex1
    }

    private fun onContextLine(
        patchLine: String,
        sourceIndex: Int,
        sourceLines: List<String>,
        result: MutableList<String>
    ): Int {
        var sourceIndex1 = sourceIndex
        val sourceIndexSearch = lookAheadFor(sourceIndex1, sourceLines, patchLine)
        if (sourceIndexSearch > 0 && sourceIndexSearch + 1 < sourceLines.size) {
            val contextChunk = sourceLines.subList(sourceIndex1, sourceIndexSearch + 1)
            result.addAll(contextChunk)
            sourceIndex1 = sourceIndexSearch + 1
        } else {
            println("Context line not found in source file: $patchLine")

        }
        return sourceIndex1
    }

    private fun lookAheadFor(
        sourceIndex: Int,
        sourceLines: List<String>,
        patchLine: String
    ): Int {
        var sourceIndexSearch = sourceIndex
        while (sourceIndexSearch < sourceLines.size) {
            if (lineMatches(patchLine, sourceLines[sourceIndexSearch++])) return sourceIndexSearch - 1
        }
        return -1
    }

    private fun lineMatches(
        a: String,
        b: String,
        factor: Double = 0.1,
    ): Boolean {
        val threshold = (Math.max(a.trim().length, b.trim().length) * factor).toInt()
        val levenshteinDistance = LevenshteinDistance(threshold + 1)
        val dist = levenshteinDistance.apply(a.trim(), b.trim())
        return if (dist >= 0) {
            dist <= threshold
        } else {
            false
        }
    }
}

