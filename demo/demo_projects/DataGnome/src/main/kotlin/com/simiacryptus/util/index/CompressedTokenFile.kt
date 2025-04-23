package com.simiacryptus.util.index

import com.simiacryptus.util.files.XBytes
import com.simiacryptus.util.files.XElements
import com.simiacryptus.util.files.IntArrayMappedFile
import com.simiacryptus.util.files.SequenceFile
import java.io.File

class CompressedTokenFile(
    file: File,
    dictionaryFile: File,
) : TokenFile(file) {
    override val tokenIndices: Iterable<XBytes>
        get() = (0 until tokenCount.asLong).map {
            val tokenPosition = XTokens(it)
            XBytes(tokenPosition.asLong * 4)
        }.asIterable()
    override val tokenCount: XTokens by lazy { XTokens(file.length() / 4) }
    val dict = SequenceFile(dictionaryFile)
    val data = IntArrayMappedFile(file)
    val codec by lazy { dict.read().map { String(it) } }

    override fun tokenIterator(position: XTokens): () -> Iterator<String> = {
        object : Iterator<String> {
            var nextPos = XElements(position.asLong)
            override fun hasNext() = true
            override fun next(): String {
                val get: Int = data.get((nextPos % data.length.asLong))
                nextPos += 1
                return codec[get]
            }
        }
    }

}