package com.simiacryptus.util.files

import java.io.File
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

class SequenceFile(private val file: File) {

    private val channel by lazy { FileChannel.open(file.toPath(), StandardOpenOption.READ) }
    private var mappedByteBuffer1: MappedByteBuffer? = null
    private val mappedByteBuffer: MappedByteBuffer
        get() {
            if (null == mappedByteBuffer1) {
                mappedByteBuffer1 = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
            }
            return mappedByteBuffer1!!
        }
    private val bufferedOutputStream by lazy { file.outputStream().buffered() }

    private var read = false
    private var write = false
    private var pos = XElements(0L)

    fun append(str: ByteArray): XElements {
        mappedByteBuffer1 = null
        bufferedOutputStream.write(str.size.toBytes())
        bufferedOutputStream.write(str)
        bufferedOutputStream.flush()
        write = true
        val prev = pos
        pos += 1
        return prev
    }

    fun get(pos: XElements): ByteArray? {
        read = true
        var curPos = 0
        var curIdx = XElements(0)
        val mappedByteBuffer = mappedByteBuffer
        val capacity = mappedByteBuffer.capacity()
        if (pos.toInt() >= getSize()) return null
        while (curIdx < pos) {
            if (curPos >= capacity) return null
            val length = if (curPos + 4 <= capacity) mappedByteBuffer.getInt(curPos) else return null
            if (curPos + length + 4 > capacity) return null
            curPos += length + 4
            curIdx += 1
        }
        val length = mappedByteBuffer.getInt(curPos)
        if (curPos + 4 + length > capacity) return null
        curPos += 4
        val result = ByteArray(length)
        if (curPos + length > capacity) return null
        mappedByteBuffer.get(curPos, result)
        return result
    }

    fun read(): Array<ByteArray> {
        val result = mutableListOf<ByteArray>()
        var curPos = 0
        val mappedByteBuffer = mappedByteBuffer
        val capacity = mappedByteBuffer.capacity()
        while (curPos < capacity) {
            val length = mappedByteBuffer.getInt(curPos)
            curPos += 4
            if (curPos + length > capacity) {
                throw IllegalStateException()
            }
            val str = ByteArray(length)
            mappedByteBuffer.get(curPos, str)
            result.add(str)
            curPos += length
        }
        return result.toTypedArray()
    }

    fun getAllIndices(): List<XElements> {
        val indices = mutableListOf<XElements>()
        var curPos = 0
        var curIdx = XElements(0)
        val mappedByteBuffer = mappedByteBuffer
        val capacity = mappedByteBuffer.capacity()
        while (curPos < capacity) {
            val length = mappedByteBuffer.getInt(curPos)
            curPos += length + 4
            indices.add(curIdx)
            curIdx += 1
        }
        return indices
    }

    fun getSize(): Int {
        return pos.toInt()
    }

    fun getIndexed(index: Int): ByteArray? {
        return get(XElements(index.toLong()))
    }

    fun readIndexed(indices: List<Int>): List<ByteArray?> {
        return indices.map { getIndexed(it) }
    }

    fun close() {
        if (write) {
            bufferedOutputStream.close()
        }
        if (read) {
            channel.close()
        }
    }

}