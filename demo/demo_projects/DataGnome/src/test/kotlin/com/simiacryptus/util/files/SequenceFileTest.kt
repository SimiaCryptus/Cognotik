package com.simiacryptus.util.files

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

internal class SequenceFileTest {

    private lateinit var sequenceFile: SequenceFile
    private lateinit var testFile: File

    @BeforeEach
    fun setUp() {
        testFile = Files.createTempFile(null, null).toFile()
        sequenceFile = SequenceFile(testFile)
    }

    @AfterEach
    fun tearDown() {
        sequenceFile.close()
        testFile.delete()
    }

    @Test
    fun `append and get should retrieve the same data`() {
        val data = "Hello, World!".toByteArray()
        val index = sequenceFile.append(data)
        assertArrayEquals(data, sequenceFile.get(index))
    }

    @Test
    fun `read should retrieve all appended data`() {
        val data1 = "Hello".toByteArray()
        val data2 = "World".toByteArray()
        sequenceFile.append(data1)
        sequenceFile.append(data2)
        val readData = sequenceFile.read()
        assertArrayEquals(data1, readData[0])
        assertArrayEquals(data2, readData[1])
    }

    @Test
    fun `get with invalid index should return null`() {
        assertNull(sequenceFile.get(XElements(999)))
    }

    @Test
    fun `append should increase the position`() {
        val initialPos = sequenceFile.append("First".toByteArray())
        val nextPos = sequenceFile.append("Second".toByteArray())
        assertTrue(nextPos > initialPos)
    }

    @Test
    fun `read on empty file should return empty array`() {
        assertArrayEquals(emptyArray<ByteArray>(), sequenceFile.read())
    }

    @Test
    fun `close should not throw exception`() {
        assertDoesNotThrow { sequenceFile.close() }
    }

    @Test
    fun `getAllIndices should return all indices`() {
        val data1 = "Hello".toByteArray()
        val data2 = "World".toByteArray()
        val index1 = sequenceFile.append(data1)
        val index2 = sequenceFile.append(data2)
        val indices = sequenceFile.getAllIndices()
        assertEquals(2, indices.size)
        assertEquals(index1, indices[0])
        assertEquals(index2, indices[1])
    }

    @Test
    fun `getSize should return correct size`() {
        assertEquals(0, sequenceFile.getSize())
        sequenceFile.append("Hello".toByteArray())
        assertEquals(1, sequenceFile.getSize())
        sequenceFile.append("World".toByteArray())
        assertEquals(2, sequenceFile.getSize())
    }

    @Test
    fun `getIndexed should retrieve the correct data`() {
        val data1 = "Hello".toByteArray()
        val data2 = "World".toByteArray()
        sequenceFile.append(data1)
        sequenceFile.append(data2)
        assertArrayEquals(data1, sequenceFile.getIndexed(0))
        assertArrayEquals(data2, sequenceFile.getIndexed(1))
    }

    @Test
    fun `readIndexed should retrieve the correct data`() {
        val data1 = "Hello".toByteArray()
        val data2 = "World".toByteArray()
        sequenceFile.append(data1)
        sequenceFile.append(data2)
        val readData = sequenceFile.readIndexed(listOf(0, 1))
        assertArrayEquals(data1, readData[0])
        assertArrayEquals(data2, readData[1])
    }

    @Test
    fun `readIndexed with invalid indices should return null for those indices`() {
        val data1 = "Hello".toByteArray()
        sequenceFile.append(data1)
        sequenceFile.append("World".toByteArray())
        val readData = sequenceFile.readIndexed(listOf(0, 1, 2))
        assertArrayEquals(data1, readData[0])
        assertNull(readData[2])
    }
}
