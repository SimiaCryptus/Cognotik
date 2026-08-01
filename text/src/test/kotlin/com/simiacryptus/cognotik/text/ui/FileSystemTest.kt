package com.simiacryptus.cognotik.text.ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class FileSystemTest {

  @Test
  fun `InMemoryFileSystem contract`() {
    val fs = InMemoryFileSystem()
    val root = Path.of("/root")
    val file = root.resolve("test.txt")

    assertFalse(fs.exists(file))
    assertEquals("", fs.readText(file))

    fs.writeText(file, "hello")
    assertTrue(fs.exists(file))
    assertEquals("hello", fs.readText(file))

    assertEquals(root.resolve("other.txt"), fs.resolve(root, "other.txt"))
  }

  @Test
  fun `RealFileSystem contract`(@TempDir tempDir: Path) {
    val fs = RealFileSystem()
    val file = tempDir.resolve("test.txt")

    assertFalse(fs.exists(file))
    assertEquals("", fs.readText(file))

    fs.writeText(file, "hello")
    assertTrue(fs.exists(file))
    assertEquals("hello", fs.readText(file))

    assertEquals(tempDir.resolve("other.txt"), fs.resolve(tempDir, "other.txt"))
  }
}