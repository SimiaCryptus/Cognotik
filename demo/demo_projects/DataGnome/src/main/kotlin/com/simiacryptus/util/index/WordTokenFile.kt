package com.simiacryptus.util.index

import com.simiacryptus.util.files.XBytes
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset

class WordTokenFile(
    file: File,
    charsetName: String = "UTF-8",
    val maxCharSize: Int = 8
) : TokenFile(file) {
    private val charset = Charset.forName(charsetName)
    override var tokenCount: XTokens = XTokens(-1)
    override val tokenIndices by lazy { indexArray.asIterable() }

    private val indexArray: Array<XBytes> by lazy {
        val charSeq: List<Pair<XBytes, String>> = (0 until fileLength.asLong)
            .runningFold(XBytes(0) to "") { position, _ ->
                val size = position.second.encodeToByteArray().size
                val nextPos = position.first + size
                nextPos to read(nextPos)
            }.takeWhile { it.first < fileLength }
        val list = (listOf(XBytes(-1) to null) + charSeq).zipWithNext { a, b ->
            when {
                b.second == null -> XBytes(0L)
                a.second == null -> b.first
                a.second!!.isBlank() && b.second!!.isNotBlank() -> b.first
                a.second!!.isNotBlank() && b.second!!.isBlank() -> b.first
                else -> null
            }
        }.filterNotNull()
        list.toTypedArray<XBytes>()
    }

    fun read(byteIndex: XBytes): String {
        val buffer = ByteArray(maxCharSize)
        read(byteIndex, buffer)
        return charset.decode(ByteBuffer.wrap(buffer)).toString().takeWhile { it != '\u0000' }.trim()
    }

    init {
        tokenCount = XTokens(indexArray.size.toLong())
    }

    override fun charToTokenIndex(position: XChars) = XTokens(
        StringIterator().asSequence()
            .runningFold(XChars(0)) { a, b -> a + b.length }.takeWhile { it <= position }.count().toLong()
    )

    override fun readString(position: XTokens, n: XChars, skip: XChars): String {
        val prev: XBytes = indexArray[position.asInt]
        return StringIterator(prev).asSequence().drop(skip.asInt).take(n.asInt).joinToString("").trim()
    }

    override fun tokenToCharIndex(position: XTokens) = StringIterator().asSequence()
        .runningFold(XChars(0)) { a, b -> a + b.length }.drop(position.asInt).first()

    override fun tokenIterator(position: XTokens): () -> Iterator<String> = {
        StringIterator(indexArray[position.asInt])
    }

    inner class StringIterator(
        private val from: XBytes = XBytes(0L)
    ) : Iterator<String> {
        private var nextPos =
            tokenIndices.indexOf(from)
                .apply { if (this < 0) throw IllegalArgumentException("Position $from not found") }

        override fun hasNext() = true
        override fun next(): String {
            val from = indexArray[(nextPos++ % indexArray.size)]
            val to = indexArray[(nextPos % indexArray.size)]
            val buffer = when {
                to < from -> ByteArray(((fileLength + to) - from).bytesAsInt)
                to == from -> return ""
                else -> ByteArray((to - from).bytesAsInt)
            }
            read(from, buffer)
            val string = charset.decode(ByteBuffer.wrap(buffer)).toString()
            return string.takeWhile { it != '\u0000' }.trim()
        }
    }

}