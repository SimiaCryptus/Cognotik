package com.simiacryptus.util.files

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files

internal class IntArrayAppendFileTest {

    @Test
    fun testAppendAndLength() {
        val tempFile = Files.createTempFile(null, null).toFile()
        tempFile.deleteOnExit()

        val intArrayAppendFile = IntArrayAppendFile(tempFile)
        assertEquals(XElements(0), intArrayAppendFile.length, "File length should be 0 before any append operations")

        intArrayAppendFile.append(42)
        assertEquals(XElements(1), intArrayAppendFile.length, "File length should be 1 after appending one element")

        intArrayAppendFile.append(-1)
        assertEquals(XElements(2), intArrayAppendFile.length, "File length should be 2 after appending two elements")

        intArrayAppendFile.close()
    }

    @Test
    fun testClose() {
        val tempFile = Files.createTempFile(null, null).toFile()
        tempFile.deleteOnExit()

        val intArrayAppendFile = IntArrayAppendFile(tempFile)
        intArrayAppendFile.close()

        assertThrows(IllegalStateException::class.java) {
            intArrayAppendFile.append(42)
        }
    }

    @Test
    fun testRead() {
        val tempFile = Files.createTempFile(null, null).toFile()
        tempFile.deleteOnExit()

        val intArrayAppendFile = IntArrayAppendFile(tempFile)
        intArrayAppendFile.append(42)
        intArrayAppendFile.append(-1)
        intArrayAppendFile.append(123456)

        assertEquals(42, intArrayAppendFile.read(0), "First element should be 42")
        assertEquals(-1, intArrayAppendFile.read(1), "Second element should be -1")
        assertEquals(123456, intArrayAppendFile.read(2), "Third element should be 123456")

        assertThrows(IndexOutOfBoundsException::class.java) {
            intArrayAppendFile.read(3)
        }

        intArrayAppendFile.close()
    }
}