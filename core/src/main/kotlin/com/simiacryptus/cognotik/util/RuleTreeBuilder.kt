package com.simiacryptus.cognotik.util

import org.intellij.lang.annotations.Language
import java.util.*
import java.util.stream.Collectors

object RuleTreeBuilder {

    val String.escape get() = replace("$", "\\$")

    fun String.safeSubstring(from: Int, to: Int?) = when {
        to == null -> ""
        from >= to -> ""
        from < 0 -> ""
        to > length -> ""
        else -> substring(from, to)
    }

    @Language("kotlin")
    fun getRuleExpression(
        toMatch: Set<String>,
        doNotMatch: SortedSet<String>,
        result: Boolean
    ): String = if (doNotMatch.size < toMatch.size) {
        getRuleExpression(doNotMatch, toMatch.toSortedSet(), !result)
    } else """
      when {
        ${
        getRules(toMatch.toSet(), doNotMatch.toSortedSet(), result).lineSequence()
            .map {
                when {
                    it.isBlank() -> {
                        when {
                            it.length < "  ".length -> "  "
                            else -> it
                        }
                    }

                    else -> "  " + it
                }
            }
            .joinToString("\n")
    }
        else -> ${!result}
      }

      """.trimIndent().trim()

    private fun getRules(
        toMatch: Set<String>,
        doNotMatch: SortedSet<String>,
        result: Boolean
    ): String {
        if (doNotMatch.isEmpty()) return "true -> $result\n"
        val sb: StringBuilder = StringBuilder()
        val remainingItems = toMatch.toMutableSet()
        fun String.bestPrefix(): String {
            val pfx = allowedPrefixes(setOf(this), doNotMatch).firstOrNull() ?: this
            require(pfx.isNotBlank())

            return pfx
        }
        while (remainingItems.isNotEmpty()) {

            val bestNextPrefix = bestPrefix(remainingItems.toSortedSet(), doNotMatch)




            when {

























                bestNextPrefix == null -> break
                else -> {
                    val matchedItems = remainingItems.filter { it.startsWith(bestNextPrefix) }.toSet()
                    val matchedBlacklist = doNotMatch.filter { it.startsWith(bestNextPrefix) }
                    when {
                        matchedBlacklist.isEmpty() -> sb.append("""path.startsWith("${bestNextPrefix.bestPrefix().escape}") -> $result""" + "\n")
                        matchedItems.isEmpty() -> sb.append("""path.startsWith("${bestNextPrefix.bestPrefix().escape}") -> ${!result}""" + "\n")
                        (matchedItems + matchedBlacklist).map { it.bestPrefix() }.distinct().size < 3 -> break
                        else -> {
                            val subRules = getRuleExpression(
                                matchedItems.map { it.removePrefix(bestNextPrefix) }.toSet(),
                                matchedBlacklist.map { it.removePrefix(bestNextPrefix) }.toSortedSet(),
                                result
                            )
                            sb.append(
                                """
                path.startsWith("${bestNextPrefix.escape}") -> {
                  val path = path.substring(${bestNextPrefix.length})
                  ${
                                    subRules.lineSequence()
                                        .map {
                                            when {
                                                it.isBlank() -> {
                                                    when {
                                                        it.length < "  ".length -> "  "
                                                        else -> it
                                                    }
                                                }

                                                else -> "  " + it
                                            }
                                        }
                                        .joinToString("\n")
                                }
                }
                """.trimIndent() + "\n"
                            )
                        }
                    }
                    remainingItems.removeAll(matchedItems)
                }
            }
        }
        remainingItems.map { it.bestPrefix() }.toSortedSet().forEach {
            require(doNotMatch.none { prefix -> prefix.startsWith(it) })
            sb.append("""path.startsWith("${it.escape}") -> $result""" + "\n")
        }
        return sb.toString()
    }

    private fun bestPrefix(
        positiveSet: SortedSet<String>,
        negativeSet: SortedSet<String>
    ) = allowedPrefixes(positiveSet, negativeSet)
        .parallelStream()
        .flatMap { prefixExpand(listOf(it)).stream() }
        .filter { it.isNotBlank() }
        .map { prefix ->
            val goodCnt = positiveSet.subSet(prefix, prefix + "\uFFFF").size
            val badCnt = negativeSet.subSet(prefix, prefix + "\uFFFF").size
            if (badCnt == 0) return@map prefix to (goodCnt - 1).toDouble() * prefix.length

            val totalCnt = goodCnt + badCnt
            val goodFactor = goodCnt.toDouble() / totalCnt
            val badFactor = badCnt.toDouble() / totalCnt
            val entropy = goodFactor * Math.log(goodFactor) + badFactor * Math.log(badFactor)
            prefix to entropy
        }.reduce({ a, b -> if (a.second >= b.second) a else b }).orElse(null)?.first

    fun prefixExpand(allowedPrefixes: Collection<String>) =
        allowedPrefixes.filter { allowedPrefixes.none { prefix -> prefix != it && prefix.startsWith(it) } }
            .flatMap { prefixExpand(it) }.toSet()

    private fun prefixExpand(it: String) = (1..it.length).map { i -> it.substring(0, i) }

    fun allowedPrefixes(
        items: Collection<String>,
        doNotMatch: SortedSet<String>
    ) = items.toList().parallelStream().map { item ->
        val list = listOf(
            item.safeSubstring(
                0,
                longestCommonPrefix(doNotMatch.tailSet(item).firstOrNull(), item)?.length?.let { it + 1 }),
            item.safeSubstring(
                0,
                longestCommonPrefix(doNotMatch.headSet(item).lastOrNull(), item)?.length?.let { it + 1 }),
        )
        list.maxByOrNull { it.length } ?: list.firstOrNull()
    }.distinct().collect(Collectors.toSet()).filterNotNull().filter { it.isNotBlank() }.toSortedSet()

    fun longestCommonPrefix(a: String?, b: String?): String? {
        if (a == null || b == null) return null
        var i = 0
        while (i < a.length && i < b.length && a[i] == b[i]) i++
        return a.substring(0, i)
    }

}

