package com.simiacryptus.util.index

import com.simiacryptus.util.files.XBytes
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

abstract class BaseTokenFileTest {

    abstract fun createTokenFile(file: File): TokenFile

    @Test
    open fun testRead() {
        val file = createTempFile()
        // Write some test data to the file
        file.writeText("Test data for reading")

        // Read data from the token file
        val buffer = ByteArray(file.length().toInt())
        val tokenFile = createTokenFile(file)
        tokenFile.read(XBytes(0), buffer)

        // Assert that the data read is correct
        assertEquals("Test data for reading", String(buffer))
    }

    @Test
    open fun testReadString() {
        val file = createTempFile()
        // Write some test data to the file
        val text = "Test data for reading"
        file.writeText(text)

        // Read data from the token file
        val tokenFile = createTokenFile(file)

        // Assert that the data read is correct
        assertEquals(
            text, tokenFile.readString(
                position = XTokens(0),
                n = XChars(text.length.toLong()),
                skip = XChars(0)
            )
        )
    }


    @Test
    open fun testCharIterator() {
        val file = createTempFile()
        // Write some test data to the file
        val text = "Test data for reading"
        file.writeText(text)

        // Read data from the token file
        val tokenFile = createTokenFile(file)

        val array1 = tokenFile.charIterator(XChars(0)).invoke()
            .asSequence().take(text.length).toList().toTypedArray()
        assertEquals(text, array1.joinToString(""))

        val array2 = tokenFile.charIterator(XChars(0)).invoke()
            .asSequence().take(text.length * 2).toList().toTypedArray()
        assertEquals(text + text, array2.joinToString(""))
    }

    @Test
    open fun testTokenIterator() {
        val file = createTempFile()
        // Write some test data to the file
        val text = "Test data for reading"
        file.writeText(text)
        val tokenFile = createTokenFile(file)

        val tokenLength = tokenLength(text)
        val array1 = tokenFile.tokenIterator(XTokens(0)).invoke()
            .asSequence().take(tokenLength).toList().toTypedArray()
        assertEquals(text, array1.joinToString(""))

        val array2 = tokenFile.tokenIterator(XTokens(0)).invoke()
            .asSequence().take(tokenLength * 2).toList().toTypedArray()
        assertEquals(text + text, array2.joinToString(""))

    }

    open fun tokenLength(text: String): Int = text.length

    @Test
    open fun testWriteCompressed() {
        val file = createTempFile()
        // Define a simple codec for the test
        val codec = listOf("a", "b", "c", " ")

        // Write some test data to the file
        file.writeText("a b c")

        val tokenFile = createTokenFile(file)
        // Compress the data in the token file
        val (compressedFile, dictionaryFile) = tokenFile.writeCompressed(codec)

        // Assert that the compressed file and dictionary file are created
        assertTrue(compressedFile.exists())
        assertTrue(dictionaryFile.exists())

        // Assert that the compressed file is not empty
        assertNotEquals(0, compressedFile.length())
    }

    private fun createTempFile(): File {
        return File.createTempFile("test", ".tmp").apply {
            deleteOnExit()
        }
    }
}

class WordTokenFileTest2 : BaseTokenFileTest() {
    override fun createTokenFile(file: File) = WordTokenFile(file)
    override fun tokenLength(text: String) = text.split(" ").size * 2 - 1
}

class SimpleTokenFileTest : BaseTokenFileTest() {
    override fun createTokenFile(file: File) = SimpleTokenFile(file)
}

class CharsetTokenFileTest : BaseTokenFileTest() {
    @Test
    override fun testCharIterator() = super.testCharIterator()

    override fun createTokenFile(file: File) = CharsetTokenFile(file)
}

//class CompressedTokenFileTest : BaseTokenFileTest() {
//    override fun createTokenFile(file: File) = CompressedTokenFile(file)
//}


