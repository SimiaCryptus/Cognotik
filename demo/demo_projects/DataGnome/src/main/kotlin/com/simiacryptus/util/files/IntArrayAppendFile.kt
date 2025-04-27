package com.simiacryptus.util.files

import java.io.File
import java.io.RandomAccessFile

class IntArrayAppendFile(val file: File) {

    private var isClosed: Boolean = false
    var length: XElements = run {
        val length = file.length()

        require(length < Int.MAX_VALUE) { "Data file too large: $length" }
        XElements(length / 4)
    }
        private set

    private val bufferedOutputStream by lazy { file.outputStream().buffered() }
    fun append(value: Int) {
        if (isClosed) throw IllegalStateException("File is closed")
        val toBytes = value.toBytes()
        bufferedOutputStream.write(toBytes)
        bufferedOutputStream.flush()
        length = length + 1
    }

    fun read(index: Int): Int {
        if (index < 0 || index >= length.asLong) throw IndexOutOfBoundsException("Index: $index, Length: ${length.asLong}")
        val randomAccessFile = RandomAccessFile(file, "r")
        randomAccessFile.seek(index * 4L)
        val value = randomAccessFile.readInt()
        randomAccessFile.close()
        return value
    }

    fun close() {
        isClosed = true
        bufferedOutputStream.close()
    }

    companion object {
    }
}