package com.simiacryptus.cognotik.ui.patch

import com.simiacryptus.cognotik.diff.PatchProcessor
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ResponseParserTest {

    private lateinit var processor: PatchProcessor
    private lateinit var parser: ResponseParser

    @BeforeEach
    fun setup() {
        processor = mockk()
        every { processor.getInitiatorPattern() } returns "```".toRegex()
        parser = ResponseParser(processor)
    }

    @Test
    fun `should return empty list for blank response`() {
        assertTrue(parser.parse("   ").isEmpty())
    }

    @Test
    fun `should return markdown segment for response without code blocks`() {
        val response = "Just some text without any code blocks."
        val segments = parser.parse(response)
        
        assertEquals(1, segments.size)
        assertTrue(segments[0] is ResponseSegment.Markdown)
        assertEquals(response, (segments[0] as ResponseSegment.Markdown).content)
    }

    @Test
    fun `should auto-close unclosed code block`() {
        val segments = parser.parse(TestFixtures.MALFORMED_RESPONSE, defaultFile = "src/main/Main.kt")
        
        assertEquals(2, segments.size) // Markdown + NewFileBlock
        assertTrue(segments[1] is ResponseSegment.NewFileBlock)
        
        val newFileBlock = segments[1] as ResponseSegment.NewFileBlock
        assertTrue(newFileBlock.code.contains("fun main()"))
    }

    @Test
    fun `should detect diff block`() {
        val segments = parser.parse(TestFixtures.STANDARD_DIFF_RESPONSE)
        
        assertEquals(2, segments.size)
        assertTrue(segments[0] is ResponseSegment.Markdown)
        assertTrue(segments[1] is ResponseSegment.DiffBlock)
        
        val diffBlock = segments[1] as ResponseSegment.DiffBlock
        assertEquals("src/main/Main.kt", diffBlock.filename)
        assertTrue(diffBlock.diff.contains("- val x = 1"))
    }

    @Test
    fun `should detect new file block`() {
        val segments = parser.parse(TestFixtures.NEW_FILE_RESPONSE)
        
        assertEquals(2, segments.size)
        assertTrue(segments[1] is ResponseSegment.NewFileBlock)
        
        val newFileBlock = segments[1] as ResponseSegment.NewFileBlock
        assertEquals("src/main/NewFile.kt", newFileBlock.filename)
        assertEquals("kotlin", newFileBlock.language)
    }

    @Test
    fun `should fallback to default file if no header is found`() {
        val response = "```diff\n- a\n+ b\n```"
        val segments = parser.parse(response, defaultFile = "Fallback.kt")
        
        assertEquals(1, segments.size)
        assertTrue(segments[0] is ResponseSegment.DiffBlock)
        assertEquals("Fallback.kt", (segments[0] as ResponseSegment.DiffBlock).filename)
    }
    @Test
    fun `should handle embedded fence with language ID`() {
        val segments = parser.parse(TestFixtures.EMBEDDED_FENCE_WITH_LANG)
        assertEquals(2, segments.size)
        assertTrue(segments[0] is ResponseSegment.Markdown)
        assertTrue(segments[1] is ResponseSegment.NewFileBlock)
        val newFileBlock = segments[1] as ResponseSegment.NewFileBlock
        assertEquals("src/main/Readme.md", newFileBlock.filename)
        assertEquals("markdown", newFileBlock.language)
        assertTrue(newFileBlock.code.contains("```javascript"))
        assertTrue(newFileBlock.code.contains("console.log(\"hello\");"))
    }
    @Test
    fun `should handle indented embedded fence`() {
        val segments = parser.parse(TestFixtures.EMBEDDED_FENCE_INDENTED)
        assertEquals(2, segments.size)
        assertTrue(segments[0] is ResponseSegment.Markdown)
        assertTrue(segments[1] is ResponseSegment.NewFileBlock)
        val newFileBlock = segments[1] as ResponseSegment.NewFileBlock
        assertEquals("src/main/Readme.md", newFileBlock.filename)
        assertEquals("markdown", newFileBlock.language)
        assertTrue(newFileBlock.code.contains("    ```"))
        assertTrue(newFileBlock.code.contains("console.log(\"hello\");"))
    }
}