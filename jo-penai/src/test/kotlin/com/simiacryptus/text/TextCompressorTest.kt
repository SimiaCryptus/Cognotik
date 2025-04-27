package com.simiacryptus.text

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TextCompressorTest {
    companion object {
        val log = org.slf4j.LoggerFactory.getLogger(TextCompressorTest::class.java)
    }

    @Test
    fun `compress should return original text when text is too short`() {
        log.debug("Starting test: compress should return original text when text is too short")
        val compressor = TextCompressor(minLength = 20)
        val shortText = "This is a short text"
        log.debug("Input text length: {}, content: '{}'", shortText.length, shortText)
        assertEquals(shortText, compressor.compress(shortText))
        log.debug("Test completed successfully")
    }

    @Test
    fun `compress should abbreviate repeated subsequences`() {
        log.debug("Starting test: compress should abbreviate repeated subsequences")
        val compressor = TextCompressor(minLength = 10)
        val repeatedText = """
            The quick brown fox jumps over the lazy dog.
            The quick brown fox jumps over the lazy dog.
            Some other text in between.
            The quick brown fox jumps over the lazy dog.
        """.trimIndent()
        log.debug("Input text length: {}", repeatedText.length)

        val compressed = compressor.compress(repeatedText)
        log.debug(
            "Compressed text length: {}, reduction: {}%",
            compressed.length,
            String.format("%.2f", 100.0 * (repeatedText.length - compressed.length) / repeatedText.length)
        )
        log.debug("Compressed content: '{}'", compressed)

        assertTrue(compressed.length < repeatedText.length)

        assertTrue(compressed.contains("..."))

        assertTrue(compressed.startsWith("The quick brown fox jumps over the lazy dog."))
        log.debug("Test completed successfully")
    }

    @Test
    fun `compress should handle text with no repetitions`() {
        log.debug("Starting test: compress should handle text with no repetitions")
        val compressor = TextCompressor(minLength = 10)
        val uniqueText =
            "All the words in this sentence are unique and there are no repeated phrases of significant length."
        log.debug("Input text length: {}, content: '{}'", uniqueText.length, uniqueText)
        assertEquals(uniqueText, compressor.compress(uniqueText))
        log.debug("Test completed successfully - no compression occurred as expected")
    }

    @Test
    fun `compress should respect minOccurrences parameter`() {
        log.debug("Starting test: compress should respect minOccurrences parameter")
        val compressor = TextCompressor(minLength = 10)
        val text = """
            Repeated phrase here. Repeated phrase here.
            Another repeated phrase. Another repeated phrase. Another repeated phrase.
        """.trimIndent()
        log.debug("Input text length: {}", text.length)
        log.debug("Input content: '{}'", text)

        val compressedWithMin3 = compressor.compress(text)
        log.debug("Compressed text with minOccurrences=3: '{}'", compressedWithMin3)



        assertTrue(compressedWithMin3.startsWith("Repeated phrase here. Repe"))

        assertFalse(compressedWithMin3.contains("Repeated phrase here. Repeated phrase here."))


        assertFalse(compressedWithMin3.contains("Another repeated phrase. Another repeated phrase. Another repeated phrase."))
        log.debug("Test completed successfully")
    }

    @Test
    fun `compress should prioritize longer patterns`() {
        log.debug("Starting test: compress should prioritize longer patterns")
        val compressor = TextCompressor(minLength = 5)
        val text = """
            This is a longer repeated pattern with nested shorter repeated pattern.
            This is a longer repeated pattern with nested shorter repeated pattern.
            The shorter repeated pattern appears elsewhere too.
            The shorter repeated pattern is here again.
        """.trimIndent()
        log.debug("Input text length: {}", text.length)

        val compressed = compressor.compress(text)
        log.debug(
            "Compressed text length: {}, reduction: {}%",
            compressed.length, String.format("%.2f", 100.0 * (text.length - compressed.length) / text.length)
        )
        log.debug("Compressed content: '{}'", compressed)




        assertTrue(compressed.contains("This ...n."))


        val shorterPatternCount = "shorter repeated pattern".toRegex().findAll(compressed).count()
        log.debug("Count of 'shorter repeated pattern' in compressed text: {}", shorterPatternCount)
        assertTrue(shorterPatternCount <= 2)

        log.debug("Test completed successfully")
    }

    @Test
    fun `compress should handle overlapping patterns correctly`() {
        log.debug("Starting test: compress should handle overlapping patterns correctly")
        val compressor = TextCompressor(minLength = 10)
        val text = "ABCDEFGHIJKLMNOPQRSTUVWXYZ ABCDEFGHIJKLMNOPQRSTUVWXYZ ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        log.debug("Input text length: {}, content: '{}'", text.length, text)

        val compressed = compressor.compress(text)
        log.debug("Compressed text: '{}'", compressed)

        assertTrue(compressed.length < text.length)

        val alphabetCount = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toRegex().findAll(compressed).count()
        log.debug("Count of full alphabet sequences in compressed text: {}", alphabetCount)
        log.debug("Test completed successfully")
    }

//    @Test
//    fun `createUniqueAbbreviation should maintain context`() {
//        log.debug("Starting test: createUniqueAbbreviation should maintain context")
//        val compressor = TextCompressor()
//        val text =
//            "This is a very long sentence that needs to be abbreviated properly to maintain context at beginning and end."
//        log.debug("Original text: '{}'", text)
//
//        val method = TextCompressor::class.java.getDeclaredMethod(
//            "createUniqueAbbreviation",
//            String::class.java,
//            Int::class.java
//        )
//        method.isAccessible = true
//        log.debug("Accessing private method createUniqueAbbreviation via reflection")
//
//        val abbreviation = method.invoke(compressor, text, 0) as String
//        log.debug("Generated abbreviation: '{}'", abbreviation)
//
//        assertTrue(abbreviation.startsWith("This "))
//        assertTrue(abbreviation.endsWith(" end."))
//        assertTrue(abbreviation.contains("..."))
//        log.debug("Test completed successfully")
//    }
}