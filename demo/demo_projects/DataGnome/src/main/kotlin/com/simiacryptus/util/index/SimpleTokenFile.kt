package com.simiacryptus.util.index

import com.simiacryptus.util.files.XBytes
import java.io.File

class SimpleTokenFile(file: File) : TokenFile(file) {

    override val tokenIndices by lazy { (0 until tokenCount.asLong).map { XBytes(it) } }
    override val tokenCount: XTokens = run {
        val bytePosition = fileLength
        require(bytePosition > 0) { "Data file empty: $bytePosition" }
        require(bytePosition < Int.MAX_VALUE) { "Data file too large: $bytePosition" }
        XTokens(bytePosition.asLong)
    }

    override fun charToTokenIndex(position: XChars) = XTokens(position.asLong)

    override fun tokenToCharIndex(position: XTokens) = XChars(position.asLong)

    override fun charIterator(position: XChars): () -> CharIterator = {
        object : CharIterator() {
            val buffer = ByteArray(1)
            var current = position
            override fun hasNext() = true
            override fun nextChar(): Char {
                read(XBytes(current.asLong), buffer)
                current = (current + 1) % fileLength.asLong
                return buffer[0].toInt().toChar()
            }
        }
    }

}

