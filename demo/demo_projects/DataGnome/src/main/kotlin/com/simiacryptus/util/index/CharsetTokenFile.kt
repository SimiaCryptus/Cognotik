package com.simiacryptus.util.index

import com.simiacryptus.util.files.XBytes
import java.io.File
import java.nio.ByteBuffer

class CharsetTokenFile(
    file: File,
    charsetName: String = "UTF-8",
    val maxCharSize: Int = 8
) : TokenFile(file) {
    private val charset = java.nio.charset.Charset.forName(charsetName)
    override var tokenCount: XTokens = XTokens(-1)
    override val tokenIndices by lazy { indexArray.asIterable() }

    private val indexArray by lazy {
        (0 until fileLength.asLong).runningFold(XBytes(0L)) { position, _ ->
            val buffer = ByteArray(maxCharSize)
            read(position, buffer)
            val first = charset.decode(ByteBuffer.wrap(buffer)).first()
            val size = first.toString().encodeToByteArray().size
            (position + size)
        }.takeWhile { it < fileLength }.toTypedArray()
    }

    init {
        tokenCount = XTokens(indexArray.size.toLong())
    }

    override fun readString(position: XTokens, n: XChars, skip: XChars): String {
        require(n.asLong > 0)
        val from = tokenToCharIndex(position) + skip
        return readString(indexArray[from.asInt % indexArray.size], indexArray[(from + n).asInt % indexArray.size])
    }

    private fun readString(
        fromByte: XBytes,
        toByte: XBytes
    ): String = when {
        toByte > fileLength -> readString(fromByte, toByte - fileLength)
        toByte < 0L -> readString(fromByte, toByte + fileLength)
        toByte <= 0L && fromByte == fileLength -> ""
        toByte <= fromByte -> when {
            toByte <= 0L -> readString(fromByte, fileLength)
            fromByte == fileLength -> readString(XBytes(0), toByte)
            else -> readString(fromByte, fileLength) + readString(XBytes(0), toByte)
        }

        else -> {
            val buffer = ByteArray((toByte - fromByte).asLong.toInt())
            read(fromByte, buffer)
            val toString = charset.decode(ByteBuffer.wrap(buffer)).toString()
            require(toString.isNotEmpty())
            toString
        }
    }

    override fun charToTokenIndex(position: XChars) = XTokens(position.asLong)
    override fun tokenToCharIndex(position: XTokens) = XChars(position.asLong)

    override fun charIterator(position: XChars): () -> CharIterator {
        return {
            object : CharIterator() {
                val initPos = charToTokenIndex(position)
                val readAheadBuffer = XChars(16.coerceAtMost(indexArray.size - 1).toLong())
                val initialBuffer = readString(initPos, readAheadBuffer)
                var buffer = initialBuffer
                var nextPos = position + initialBuffer.length
                var pos = 0
                override fun hasNext() = true
                override fun nextChar(): Char {
                    val char = buffer.get(pos++)
                    if (pos >= buffer.length) {
                        buffer = readString(charToTokenIndex(nextPos), XChars(16))
                        nextPos = nextPos + buffer.length
                        pos = 0
                    }
                    return char
                }
            }
        }
    }

}