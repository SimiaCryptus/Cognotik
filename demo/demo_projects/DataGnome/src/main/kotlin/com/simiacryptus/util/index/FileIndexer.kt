package com.simiacryptus.util.index

import com.simiacryptus.util.files.LongArrayMappedFile
import com.simiacryptus.util.files.XElements
import com.simiacryptus.util.files.elements
import com.simiacryptus.util.files.until
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*


class FileIndexer(
    val data: TokenFile,
    val index: LongArrayMappedFile,
) {

    init {
        require(index.length.asLong == data.tokenCount.asLong) {
            "Index length ${index.length} does not match token count ${data.tokenCount}"
        }
    }

    val tokenSet: Set<String> by lazy {
        data.tokenIterator(0.tokens).invoke().asSequence().take(data.tokenCount.asInt).toSet()
    }

    fun buildIndex(n: XChars = XChars(2)) {
        populateIndex(
            n = n,
            skip = n,
            spans = Companion.toSpans(
                0.elements, populateByScan(
                    n = n,
                    skip = 0.chars,
                    from = 0.elements,
                    to = index.length,
                    indices = 0.tokens until data.tokenCount
                )
            )
        )
    }

    private fun countNGrams(
        n: XChars,
        skip: XChars = 0.chars,
        indices: Iterable<XTokens>
    ): TreeMap<CharSequence, Int> {
        val map = TreeMap<CharSequence, Int>()
        for (position in indices) {
            val key = data.readString(position = position, n = n, skip = skip)
            map[key] = map.getOrDefault(key, 0) + 1
        }
        return map
    }

    private fun populateIndex(
        n: XChars,
        skip: XChars,
        spans: Array<Pair<XElements, XElements>>
    ) {
        spans.forEach { (start, end) ->
            val count = end - start
            if (count > 1) {
                var indices = (start until end).map { index.get(it) }.toLongArray()
                if (count >= 1000) {
                    // Sort and recurse for large blocks
                    val nextMap = populateByScan(
                        n = n, skip = skip, from = start, to = end,
                        indices = indices.asIterable().map { it.tokens })
                    if (nextMap.size > 1) {
                        populateIndex(n = n, skip = skip + n, spans = toSpans(start, nextMap))
                        return@forEach
                    }
                }
                // Sort directly for small blocks
                indices = indices.sortedBy {
                    data.readString(position = it.tokens, n = n, skip = skip)
                }.toLongArray()
                for (i in indices.indices) {
                    index.set(start + i, indices[i])
                }
            }
        }
    }

    private fun populateByScan(
        n: XChars,
        skip: XChars,
        from: XElements,
        to: XElements,
        indices: Iterable<XTokens>,
    ): TreeMap<CharSequence, Int> {
        val nGramCounts = countNGrams(n, skip, indices)
        require(nGramCounts.values.sum() == (to - from).asLong.toInt())
        val nGramPositions = accumulatePositions(nGramCounts)
        for (tokenPosition in indices) {
            val key = data.readString(tokenPosition, n, skip)
            val position = nGramPositions[key]!!
            require(position >= 0)
            require(position < (to - from))
            index.set(from + position, tokenPosition.asLong)
            nGramPositions[key] = position + 1
        }
        return nGramCounts
    }


    fun close() {
        index.close()
        data.close()
    }

    companion object {
        private val log = LoggerFactory.getLogger(FileIndexer::class.java)

        fun accumulatePositions(nGramCounts: TreeMap<CharSequence, Int>): TreeMap<CharSequence, XElements> {
            val nGramPositions = TreeMap<CharSequence, XElements>()
            var position = 0.elements
            for ((nGram, count) in nGramCounts) {
                nGramPositions[nGram] = position
                position += count
            }
            return nGramPositions
        }

        fun commonPrefix(a: Iterator<String>?, b: Iterator<String>?): String {
            a ?: return ""
            b ?: return ""
            val buffer = StringBuilder()
            var loopCnt = 0
            while (a.hasNext() && b.hasNext()) {
                if (loopCnt++ > 10000) {
                    throw IllegalStateException()
                }
                val next = a.next()
                val next2 = b.next()
                if (next != next2) break
                buffer.append(next)
            }
            return buffer.toString()
        }

        private fun toSpans(
            from: XElements,
            parent: TreeMap<CharSequence, Int>
        ): Array<Pair<XElements, XElements>> {
            var position = from
            return parent.map { (_, count) ->
                val start = position
                val end = start + count
                position = end
                start to end
            }.toTypedArray<Pair<XElements, XElements>>()
        }

    }
}

fun FileIndexer(dataFile: File, indexFile: File = File(dataFile.parentFile, "${dataFile.name}.index")) =
    FileIndexer(CharsetTokenFile(dataFile), indexFile)

fun FileIndexer(
    data: TokenFile,
    indexFile: File = File(data.file.parentFile, "${data.file.name}.index")
) = FileIndexer(data, LongArrayMappedFile(indexFile).apply {
    //data.tokenCount.asLong.elements
})