package com.simiacryptus.cognotik.ui.patch

import com.simiacryptus.cognotik.diff.PatchProcessor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path

class DiffInstrumentorTest {

    private lateinit var fs: InMemoryFileSystem
    private lateinit var processor: PatchProcessor
    private lateinit var renderer: DiffUIRenderer
    private lateinit var instrumentor: DiffInstrumentor
    private val root = Path.of("/root")

    @BeforeEach
    fun setup() {
        fs = InMemoryFileSystem()
        processor = mockk()
        every { processor.getInitiatorPattern() } returns "```".toRegex()
        renderer = mockk(relaxed = true)
        instrumentor = DiffInstrumentor(processor, renderer, fs, patchProcessor)
    }

    @Test
    fun `instrument auto applies valid diff`() {
        val targetFile = root.resolve("src/main/Main.kt")
        fs.writeText(targetFile, "val x = 1")
        
        every { processor.apply(any(), any(), any()) } returns mockk {
            every { isValid } returns true
            every { newCode } returns "val x = 2"
            every { errors } returns emptyList()
        }

        val handleMock = mockk<(Map<Path, String>) -> Unit>(relaxed = true)
        val shouldAutoApplyMock = mockk<(Path) -> Boolean>()
        every { shouldAutoApplyMock(any()) } returns true

        instrumentor.instrument(
            root = root,
            response = TestFixtures.STANDARD_DIFF_RESPONSE,
            handle = handleMock,
            shouldAutoApply = shouldAutoApplyMock
        )

        verify { handleMock(match { it.containsKey(Path.of("src/main/Main.kt")) }) }
        verify { renderer.renderAutoApplied(targetFile, any()) }
        assertEquals("val x = 2", fs.readText(targetFile))
    }

    @Test
    fun `instrument renders apply button when auto apply is false`() {
        val targetFile = root.resolve("src/main/Main.kt")
        fs.writeText(targetFile, "val x = 1")
        
        every { processor.apply(any(), any(), any()) } returns mockk {
            every { isValid } returns true
            every { newCode } returns "val x = 2"
            every { errors } returns emptyList()
        }

        val shouldAutoApplyMock = mockk<(Path) -> Boolean>()
        every { shouldAutoApplyMock(any()) } returns false

        instrumentor.instrument(
            root = root,
            response = TestFixtures.STANDARD_DIFF_RESPONSE,
            shouldAutoApply = shouldAutoApplyMock
        )

        verify { renderer.renderApplyDiffButton(targetFile, any(), any(), any()) }
        assertEquals("val x = 1", fs.readText(targetFile)) // Not modified yet
    }

    @Test
    fun `instrument resolves new file`() {
        val handleMock = mockk<(Map<Path, String>) -> Unit>(relaxed = true)
        val shouldAutoApplyMock = mockk<(Path) -> Boolean>()
        every { shouldAutoApplyMock(any()) } returns true

        instrumentor.instrument(
            root = root,
            response = TestFixtures.NEW_FILE_RESPONSE,
            handle = handleMock,
            shouldAutoApply = shouldAutoApplyMock
        )

        val expectedPath = root.resolve("src/main/NewFile.kt")
        assertTrue(fs.exists(expectedPath))
        verify { renderer.renderAutoApplied(expectedPath, any()) }
    }
    
    @Test
    fun `resolveWithBestEffort strategy 2 - prefix stripping`() {
        // Setup file system with a file
        val targetFile = root.resolve("src/main/Main.kt")
        fs.writeText(targetFile, "val x = 1")
        
        every { processor.apply(any(), any(), any()) } returns mockk {
            every { isValid } returns true
            every { newCode } returns "val x = 2"
            every { errors } returns emptyList()
        }

        // Provide a diff with a git 'a/' prefix
        val response = """
            # File: a/src/main/Main.kt
            ```diff
            - val x = 1
            + val x = 2
            ```
        """.trimIndent()

        instrumentor.instrument(
            root = root,
            response = response,
            shouldAutoApply = { true },
            resolver = { r, f ->
                val p = r.resolve(f)
                if (fs.exists(p)) r.relativize(p).toString().replace("\\", "/") else null
            }
        )

        // It should have stripped 'a/' and found the file
        assertEquals("val x = 2", fs.readText(targetFile))
    }
}