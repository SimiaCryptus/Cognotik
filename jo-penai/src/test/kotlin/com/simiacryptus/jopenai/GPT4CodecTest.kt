package com.simiacryptus.jopenai

import com.simiacryptus.jopenai.util.GPT4Tokenizer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class GPT4TokenizerTest {

    private val codex = GPT4Tokenizer(true)

    @Test
    fun testRange() {
        assertEquals(listOf(1, 2, 3, 4, 5), GPT4Tokenizer.range(1, 5))
    }

    @Test
    fun testOrd() {
        assertEquals(65, GPT4Tokenizer.ord("A"))
    }

    @Test
    fun testChr() {
        assertEquals("A", GPT4Tokenizer.chr(65))
    }

    @Test
    fun testTextEncoder() {
        assertArrayEquals(byteArrayOf(84, 101, 115, 116), GPT4Tokenizer.TextEncoder().encode("Test"))
    }

    @Test
    fun testTextDecoder() {
        assertEquals("Test", GPT4Tokenizer.TextDecoder().decode(byteArrayOf(84, 101, 115, 116)))
    }

    @Test
    fun testGPT4Tokenizer() {
        assertEquals(listOf(1212, 318, 257, 6208), codex.encode("This is a Test"))
    }

    @Test
    fun testGPT4TokenizerDecode() {
        assertEquals("This is a Test", codex.decode(listOf(1212, 318, 257, 6208)))
    }

    @Test
    fun testGPT4TokenizerEstimateTokenCount() {
        assertEquals(1, codex.estimateTokenCount("Test"))
    }

    @Test
    fun testGPT4TokenizerChunkText() {
        assertEquals(1, codex.chunkText("Test", 2).size)
    }

    @ParameterizedTest
    @ValueSource(strings = ["This is a Test", "Hello, World!", "Foo Bar", "This is a longer test string"])
    fun testEncodingDecodingAreComplementaryGPT4(input: String) {
        val tokenizer = GPT4Tokenizer(false)
        val encoded = tokenizer.encode(input)
        val decoded = tokenizer.decode(encoded)
        val estimateTokenCount = tokenizer.estimateTokenCount(input)
        assertEquals(input, decoded, "Encoding and decoding should be complementary operations")
        assertEquals(estimateTokenCount, encoded.size, "Encoding should produce the correct number of tokens")
    }

    @ParameterizedTest
    @ValueSource(strings = ["This is a Test", "Hello, World!", "Foo Bar", "This is a longer test string"])
    fun testEncodingDecodingAreComplementaryCodex(input: String) {
        val tokenizer = GPT4Tokenizer(true)
        val encoded = tokenizer.encode(input)
        val decoded = tokenizer.decode(encoded)
        val estimateTokenCount = tokenizer.estimateTokenCount(input)
        assertEquals(input, decoded, "Encoding and decoding should be complementary operations")
        assertEquals(estimateTokenCount, encoded.size, "Encoding should produce the correct number of tokens")
    }
}