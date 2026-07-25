package com.simiacryptus.cognotik.text.ui

import com.simiacryptus.cognotik.text.patch.PatchProcessor
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path

class DiffApplyControllerTest {

  private lateinit var fs: InMemoryFileSystem
  private lateinit var processor: PatchProcessor
  private val filepath = Path.of("src/main/Test.kt")

  @BeforeEach
  fun setup() {
    fs = InMemoryFileSystem()
    fs.writeText(filepath, "original code")
    processor = mockk()
  }

  @Test
  fun `happy path apply and revert`() {
    val diff = "- original code\n+ new code"

    // Mocking the PatchResult dynamically
    every { processor.apply(any(), any(), any()) } returns mockk {
      every { isValid } returns true
      every { newCode } returns "new code"
      every { errors } returns emptyList()
    }

    val controller = DiffApplyController(filepath, diff, processor, fs)
    assertEquals(ApplyState.Pending, controller.currentState())

    val applyState = controller.apply()
    assertTrue(applyState is ApplyState.Applied)
    assertEquals("new code", fs.readText(filepath))

    val revertState = controller.revert()
    assertTrue(revertState is ApplyState.Reverted)
    assertEquals("original code", fs.readText(filepath))
  }

  @Test
  fun `invalid patch transitions to failed`() {
    val diff = "bad diff"

    every { processor.apply(any(), any(), any()) } returns mockk {
      every { isValid } returns false
      every { newCode } returns ""
      every { errors } returns listOf(mockk(relaxed = true))
    }

    val controller = DiffApplyController(filepath, diff, processor, fs)
    val state = controller.apply()

    assertTrue(state is ApplyState.Failed)
    assertEquals("original code", fs.readText(filepath)) // File not modified
  }

  @Test
  fun `blank diff transitions to failed`() {
    val controller = DiffApplyController(filepath, "   ", processor, fs)
    val state = controller.apply()

    assertTrue(state is ApplyState.Failed)
    assertTrue((state as ApplyState.Failed).error is IllegalArgumentException)
  }

  @Test
  fun `idempotency of apply`() {
    val diff = "- original code\n+ new code"

    every { processor.apply(any(), any(), any()) } returns mockk {
      every { isValid } returns true
      every { newCode } returns "new code"
      every { errors } returns emptyList()
    }

    val controller = DiffApplyController(filepath, diff, processor, fs)
    val state1 = controller.apply()
    val state2 = controller.apply()

    assertSame(state1, state2) // Should return the exact same state instance
    assertEquals("new code", fs.readText(filepath))
  }
}