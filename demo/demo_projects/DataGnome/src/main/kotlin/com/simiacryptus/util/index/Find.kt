package com.simiacryptus.util.index

import com.simiacryptus.util.files.XElements
import com.simiacryptus.util.files.elements
import com.simiacryptus.util.files.until

fun FileIndexer.find(sequence: CharSequence): Array<XTokens> {
    var start = 0.elements
    var end = index.length
    while (start < end) {
        val mid = XElements((start + end).asLong / 2)
        val midVal = data.readString(XTokens(index.get(mid)), XChars(sequence.length.toLong()))
        when {
            midVal < sequence -> start = mid + 1
            midVal > sequence -> end = mid
            else -> {

                var i = mid
                var buffer: String
                while (i > 0) {
                    buffer = data.readString(XTokens(index.get(i - 1)), XChars(sequence.length.toLong()))
                    if (buffer != sequence) break
                    i -= 1
                }

                var j = mid
                while (j < index.length) {
                    buffer = data.readString(XTokens(index.get(j + 1)), XChars(sequence.length.toLong()))
                    if (buffer != sequence) break
                    j += 1
                }
                return (i until (j + 1)).map { index.get(it) }.sorted().map { XTokens(it) }.toTypedArray()
            }
        }
    }
    return emptyArray()
}

private operator fun CharSequence.compareTo(sequence: CharSequence): Int {
    var i = 0
    while (i < length && i < sequence.length) {
        val next = get(i)
        val next2 = sequence[i]
        if (next < next2) return -1
        if (next > next2) return 1
        i++
    }
    if (length > sequence.length) return 1

    if (sequence.length > length) return -1

    return 0
}
