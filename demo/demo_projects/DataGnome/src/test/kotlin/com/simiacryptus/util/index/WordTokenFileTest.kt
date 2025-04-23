package com.simiacryptus.util.index

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.charset.Charset

class WordTokenFileTest {

    private lateinit var wordTokenFile: WordTokenFile
    private val testFilePath = "src/test/resources/testFile.txt"
    private val testFileContent = "Hello world! This is a test file."
    private val emptyFilePath = "src/test/resources/emptyFile.txt"
    private val specialCharFilePath = "src/test/resources/specialCharFile.txt"

    @BeforeEach
    fun setUp() {
        // Create a test file with sample content
        val testFile = File(testFilePath)
        testFile.writeText(testFileContent, Charset.forName("UTF-8"))

        // Initialize WordTokenFile with the test file
        wordTokenFile = WordTokenFile(testFile)
    }

    @AfterEach
    fun tearDown() {
        // Delete test files after each test
        File(testFilePath).delete()
        File(emptyFilePath).delete()
        File(specialCharFilePath).delete()
    }

    @Test
    fun testRead() {
        val tokenIndex = wordTokenFile.tokenIndices.first()
        val token = wordTokenFile.read(tokenIndex)
        assertEquals("Hello", token)
    }

    @Test
    fun testCharToTokenIndex() {
        val charPosition = XChars(6) // Position after "Hello "
        val tokenIndex = wordTokenFile.charToTokenIndex(charPosition)
        assertEquals(XTokens(2), tokenIndex)
    }

    @Test
    fun testReadString() {
        val tokenIndex = XTokens(1) // Token index for "world!"
        val result = wordTokenFile.readString(tokenIndex, XChars(5), XChars(0))
        assertEquals("world", result)
    }

    @Test
    fun testTokenToCharIndex() {
        val tokenIndex = XTokens(1) // Token index for "world!"
        val charPosition = wordTokenFile.tokenToCharIndex(tokenIndex)
        assertEquals(XChars(6), charPosition)
    }

    @Test
    fun testTokenIterator() {
        val iterator = wordTokenFile.tokenIterator(XTokens(0))()
        val tokens = iterator.asSequence().take(5).toList()
        assertEquals(listOf("Hello", "world!", "This", "is", "a"), tokens)
    }

    @Test
    fun testEmptyFile() {
        val emptyFile = File(emptyFilePath)
        emptyFile.writeText("", Charset.forName("UTF-8"))

        val emptyWordTokenFile = WordTokenFile(emptyFile)
        assertEquals(XTokens(0), emptyWordTokenFile.tokenCount)
    }

    @Test
    fun testSpecialCharacters() {
        val specialCharContent = "Hello, world! こんにちは世界"
        val specialCharFile = File(specialCharFilePath)
        specialCharFile.writeText(specialCharContent, Charset.forName("UTF-8"))

        val specialCharWordTokenFile = WordTokenFile(specialCharFile)
        val iterator = specialCharWordTokenFile.tokenIterator(XTokens(0))()
        val tokens = iterator.asSequence().take(4).toList()
        assertEquals(listOf("Hello,", "world!", "こんにちは", "世界"), tokens)
    }
}