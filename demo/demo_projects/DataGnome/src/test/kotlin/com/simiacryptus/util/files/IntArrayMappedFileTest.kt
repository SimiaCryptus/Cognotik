package com.simiacryptus.util.files

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

internal class IntArrayMappedFileTest {

    @Test
    fun testLength() {
        val tempFile = createTempDataFile(intArrayOf(1, 2, 3, 4))
        val mappedFile = IntArrayMappedFile(tempFile)
        assertEquals(4, mappedFile.length.asLong)
    }

    @Test
    fun testGet() {
        val tempFile = createTempDataFile(intArrayOf(10, 20, 30, 40))
        val mappedFile = IntArrayMappedFile(tempFile)
        assertEquals(10, mappedFile.get(XElements(0)))
        assertEquals(20, mappedFile.get(XElements(1)))
        assertEquals(30, mappedFile.get(XElements(2)))
        assertEquals(40, mappedFile.get(XElements(3)))
    }

    @Test
    fun testSet() {
        val tempFile = createTempDataFile(intArrayOf(10, 20, 30, 40))
        val mappedFile = IntArrayMappedFile(tempFile)
        mappedFile.set(XElements(0), 100)
        mappedFile.set(XElements(1), 200)
        mappedFile.set(XElements(2), 300)
        mappedFile.set(XElements(3), 400)
        assertEquals(100, mappedFile.get(XElements(0)))
        assertEquals(200, mappedFile.get(XElements(1)))
        assertEquals(300, mappedFile.get(XElements(2)))
        assertEquals(400, mappedFile.get(XElements(3)))
    }

    @Test
    fun testFlush() {
        val tempFile = createTempDataFile(intArrayOf(10, 20, 30, 40))
        val mappedFile = IntArrayMappedFile(tempFile)
        mappedFile.set(XElements(0), 100)
        mappedFile.flush()
        assertEquals(100, mappedFile.get(XElements(0)))
    }

    @Test
    fun testClose() {
        val tempFile = createTempDataFile(intArrayOf(1, 2, 3, 4))
        val mappedFile = IntArrayMappedFile(tempFile)
        assertDoesNotThrow { mappedFile.close() }
    }

    @Test
    fun testAppend() {
        val tempFile = createTempDataFile(intArrayOf(1, 2, 3, 4))
        val mappedFile = IntArrayMappedFile(tempFile)
        mappedFile.append(5)
        assertEquals(5, mappedFile.length.asLong)
        assertEquals(5, mappedFile.get(XElements(4)))
    }

    @Test
    fun testAllocate() {
        val tempFile = createTempDataFile(intArrayOf(1, 2, 3, 4))
        val mappedFile = IntArrayMappedFile(tempFile)
        mappedFile.allocate(XElements(6))
        assertEquals(6, mappedFile.length.asLong)
        mappedFile.set(XElements(4), 50)
        mappedFile.set(XElements(5), 60)
        assertEquals(50, mappedFile.get(XElements(4)))
        assertEquals(60, mappedFile.get(XElements(5)))
    }

    @Test
    fun testFill() {
        val tempFile = createTempDataFile(intArrayOf(1, 2, 3, 4))
        val mappedFile = IntArrayMappedFile(tempFile)
        mappedFile.fill(99)
        for (i in 0 until mappedFile.length.asLong) {
            assertEquals(99, mappedFile.get(XElements(i)))
        }
    }

    private fun createTempDataFile(data: IntArray): File {
        val tempFile = Files.createTempFile(null, null).toFile()
        tempFile.deleteOnExit()
        RandomAccessFile(tempFile, "rw").use { raf ->
            data.forEach { value ->
                raf.writeInt(value)
            }
        }
        return tempFile
    }
}