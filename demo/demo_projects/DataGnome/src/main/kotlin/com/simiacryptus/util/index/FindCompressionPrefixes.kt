package com.simiacryptus.util.index

import com.simiacryptus.util.files.XElements
import com.simiacryptus.util.files.elements
import com.simiacryptus.util.files.until
import java.util.*

fun FileIndexer.findCompressionPrefixes(threshold: Int, count: Int): Array<Pair<String, Int>> {
    val returnMap = TreeMap<String, Int>()
    val map = TreeMap<String, TreeSet<XElements>>()
    for (elementIndex in (0.elements until index.length)) {
        val lastPtrIdx = if (elementIndex <= 0) null else index.get(elementIndex - 1).tokens
        val currentIdx = index.get(elementIndex).tokens
        val nextPtrIdx = if (elementIndex >= index.length - 1) null else index.get(elementIndex + 1).tokens
        val lastPtr = lastPtrIdx?.run { data.tokenIterator(this) }
        val nextPtr = nextPtrIdx?.run { data.tokenIterator(this) }
        val currentPtr = data.tokenIterator(currentIdx)
        val commonPrefixA = FileIndexer.commonPrefix(lastPtr?.invoke(), currentPtr())
        val commonPrefixB = FileIndexer.commonPrefix(currentPtr(), nextPtr?.invoke())
        val longestCommonPrefix = if (commonPrefixA.length > commonPrefixB.length) commonPrefixA else commonPrefixB
        map.keys.filter { !longestCommonPrefix.startsWith(it) }.toTypedArray().forEach { newPrefix ->
            val size = map.remove(newPrefix)!!.size
            val fitness = prefixFitness(newPrefix, size)
            if (fitness > threshold) {
                returnMap[newPrefix] = size
            }
            map.remove(newPrefix)
        }
        longestCommonPrefix.indices.forEach { j ->
            val substring = longestCommonPrefix.substring(0, j)
            map.getOrPut(substring) { TreeSet<XElements>() }.add(elementIndex)
        }
    }
    map.keys.toTypedArray().forEach {
        val size = map.remove(it)!!.size
        val fitness = prefixFitness(it, size)
        if (fitness > threshold) {
            returnMap[it] = size
        }
    }
    return collect(returnMap, count).toList().sortedBy { -prefixFitness(it.first, it.second) }.toTypedArray()
}

private fun prefixFitness(string: String, count: Int): Int {
    val length = string.encodeToByteArray().size
    return (count * length) - (count * 4) - length
}

private fun collect(map: TreeMap<String, Int>, count: Int): Map<String, Int> {

    val returnMap = TreeMap<String, Int>()
    while (map.isNotEmpty() && returnMap.size < count) {
        val best = map.entries.maxByOrNull { prefixFitness(it.key, it.value) }!!
        returnMap[best.key] = best.value
        map.keys.filter { best.key.startsWith(it) || it.startsWith(best.key) }
            .toTypedArray().forEach { newPrefix -> map.remove(newPrefix) }
    }
    return returnMap
}