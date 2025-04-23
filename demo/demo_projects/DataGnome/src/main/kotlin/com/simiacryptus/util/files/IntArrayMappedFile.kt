package com.simiacryptus.util.files

import java.io.File
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

class IntArrayMappedFile(private val file: File) {

    val length: XElements
        get() {
            val length = file.length()
            require(length > 0) { "Data file empty: $length" }
            require(length < Int.MAX_VALUE) { "Data file too large: $length" }
            return XElements(length / 4)
        }

    private val channel by lazy { FileChannel.open(file.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE) }
    private var _mappedByteBuffer: MappedByteBuffer? = null
    private var mappedByteBuffer: MappedByteBuffer
        get() {
            if (_mappedByteBuffer == null) {
                _mappedByteBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, file.length())
            }
            return _mappedByteBuffer!!
        }
        set(value) {
            _mappedByteBuffer = value
        }

    fun get(pos: XElements): Int {
        return mappedByteBuffer.getInt((pos.asLong * 4).toInt())
    }

    fun set(pos: XElements, value: Int) {
        mappedByteBuffer.putInt((pos.asLong * 4).toInt(), value)
    }

    fun flush() {
        mappedByteBuffer.force()
    }

    fun close() {
        channel.close()
    }

    fun append(value: Int) {
        val newLength = (length.asLong + 1) * 4
        channel.truncate(newLength)
        _mappedByteBuffer = null // Invalidate the current buffer
        mappedByteBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, newLength)
        mappedByteBuffer.putInt((length.asLong * 4 - 4).toInt(), value)
    }

    fun allocate(newLength: XElements) {
        require(newLength.asLong > length.asLong) { "New length must be greater than current length" }
        val newByteLength = newLength.asLong * 4
        channel.truncate(newByteLength)
        mappedByteBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, newByteLength)
    }

    fun fill(value: Int) {
        for (i in 0 until length.asLong) {
            set(XElements(i), value)
        }
    }

    companion object {

    }
}