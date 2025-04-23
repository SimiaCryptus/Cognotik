package com.simiacryptus.util.files

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

internal class LongArrayMappedFileTest {

    @Test
    fun testLength() {
        val tempFile = createTempDataFile(longArrayOf(1L, 2L, 3L, 4L))
        val mappedFile = LongArrayMappedFile(tempFile)
        assertEquals(4, mappedFile.length.asLong)
    }

    @Test
    fun testGet() {
        val tempFile = createTempDataFile(longArrayOf(10L, 20L, 30L, 40L))
        val mappedFile = LongArrayMappedFile(tempFile)
        assertEquals(10L, mappedFile.get(XElements(0)))
        assertEquals(20L, mappedFile.get(XElements(1)))
        assertEquals(30L, mappedFile.get(XElements(2)))
        assertEquals(40L, mappedFile.get(XElements(3)))
    }

    @Test
    fun testSet() {
        val tempFile = createTempDataFile(longArrayOf(10L, 20L, 30L, 40L))
        val mappedFile = LongArrayMappedFile(tempFile)
        mappedFile.set(XElements(0), 100L)
        mappedFile.set(XElements(1), 200L)
        mappedFile.set(XElements(2), 300L)
        mappedFile.set(XElements(3), 400L)
        assertEquals(100L, mappedFile.get(XElements(0)))
        assertEquals(200L, mappedFile.get(XElements(1)))
        assertEquals(300L, mappedFile.get(XElements(2)))
        assertEquals(400L, mappedFile.get(XElements(3)))
    }

    @Test
    fun testFlush() {
        val tempFile = createTempDataFile(longArrayOf(10L, 20L, 30L, 40L))
        val mappedFile = LongArrayMappedFile(tempFile)
        mappedFile.set(XElements(0), 100L)
        mappedFile.flush()
        assertEquals(100L, mappedFile.get(XElements(0)))
    }

    @Test
    fun testClose() {
        val tempFile = createTempDataFile(longArrayOf(1L, 2L, 3L, 4L))
        val mappedFile = LongArrayMappedFile(tempFile)
        assertDoesNotThrow { mappedFile.close() }
    }

    @Test
    fun testAppend() {
        val tempFile = createTempDataFile(longArrayOf(1L, 2L, 3L, 4L))
        val mappedFile = LongArrayMappedFile(tempFile)
        mappedFile.append(5L)
        assertEquals(5, mappedFile.length.asLong)
        assertEquals(5L, mappedFile.get(XElements(4)))
    }

    @Test
    fun testAllocate() {
        val tempFile = createTempDataFile(longArrayOf(1L, 2L, 3L, 4L))
        val mappedFile = LongArrayMappedFile(tempFile)
        mappedFile.allocate(XElements(6))
        assertEquals(6, mappedFile.length.asLong)
        mappedFile.set(XElements(4), 50L)
        mappedFile.set(XElements(5), 60L)
        assertEquals(50L, mappedFile.get(XElements(4)))
        assertEquals(60L, mappedFile.get(XElements(5)))
    }

    @Test
    fun testFill() {
        val tempFile = createTempDataFile(longArrayOf(1L, 2L, 3L, 4L))
        val mappedFile = LongArrayMappedFile(tempFile)
        mappedFile.fill(99L)
        for (i in 0 until mappedFile.length.asLong) {
            assertEquals(99L, mappedFile.get(XElements(i)))
        }
    }

    private fun createTempDataFile(data: LongArray): File {
        val tempFile = Files.createTempFile(null, null).toFile()
        tempFile.deleteOnExit()
        RandomAccessFile(tempFile, "rw").use { raf ->
            data.forEach { value ->
                raf.writeLong(value)
            }
        }
        return tempFile
    }
}