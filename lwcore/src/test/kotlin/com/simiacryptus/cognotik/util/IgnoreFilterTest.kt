package com.simiacryptus.cognotik.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class IgnoreFilterTest {

  @TempDir
  lateinit var tempDir: File

  @BeforeEach
  fun setUp() {
    IgnoreFileUtil.clearCache()
    File(tempDir, ".git").mkdirs()
  }

  private fun write(name: String, content: String = "x"): File =
    File(tempDir, name).apply { parentFile.mkdirs(); writeText(content) }

  @Test
  fun `dot file filter`() {
    assertTrue(IgnoreFilter.DOT_FILE.matches(write(".env")))
    assertFalse(IgnoreFilter.DOT_FILE.matches(write("env")))
  }

  @Test
  fun `node modules filter`() {
    assertTrue(IgnoreFilter.NODE_MODULES.matches(File(tempDir, "node_modules")))
    assertFalse(IgnoreFilter.NODE_MODULES.matches(File(tempDir, "modules")))
  }

  @Test
  fun `ignore file name filters`() {
    assertTrue(IgnoreFilter.GITIGNORE_FILE.matches(File(tempDir, ".gitignore")))
    assertTrue(IgnoreFilter.LLMIGNORE_FILE.matches(File(tempDir, ".llmignore")))
    IgnoreFilter.IGNORE_FILE_NAMES.forEach {
      assertTrue(IgnoreFilter.IGNORE_FILE.matches(File(tempDir, it)), it)
    }
    assertFalse(IgnoreFilter.IGNORE_FILE.matches(File(tempDir, "ignore")))
  }

  @Test
  fun `missing and directory filters`() {
    assertTrue(IgnoreFilter.MISSING.matches(File(tempDir, "nope")))
    assertFalse(IgnoreFilter.MISSING.matches(write("yes.txt")))
    assertTrue(IgnoreFilter.DIRECTORY.matches(File(tempDir, "d").apply { mkdirs() }))
    assertFalse(IgnoreFilter.DIRECTORY.matches(write("f.txt")))
  }

  @Test
  fun `oversize filter is false for small files`() {
    assertFalse(IgnoreFilter.OVERSIZE.matches(write("small.txt")))
    assertEquals(100_000_000L, IgnoreFilter.MAX_FILE_SIZE)
  }

  @Test
  fun `binary extension filter`() {
    assertTrue(IgnoreFilter.BINARY_EXTENSION.matches(write("a.PNG")))
    assertTrue(IgnoreFilter.BINARY_EXTENSION.matches(write("a.jar")))
    assertFalse(IgnoreFilter.BINARY_EXTENSION.matches(write("a.kt")))
  }

  @Test
  fun `binary content filter`() {
    val binary = File(tempDir, "blob.txt").apply { writeBytes(ByteArray(1000) { if (it % 2 == 0) 0 else 65 }) }
    assertTrue(IgnoreFilter.BINARY_CONTENT.matches(binary))
    assertFalse(IgnoreFilter.BINARY_CONTENT.matches(write("text.txt", "Hello, World! ".repeat(20))))
  }

  @Test
  fun `gitignore backed filter`() {
    write(".gitignore", "*.log")
    assertTrue(IgnoreFilter.GITIGNORE.matches(write("a.log")))
    assertFalse(IgnoreFilter.GITIGNORE.matches(write("a.txt")))
  }

  @Test
  fun `invoke operators mirror matches`() {
    val dot = write(".env")
    assertTrue(IgnoreFilter.DOT_FILE(dot))
    assertTrue(IgnoreFilter.DOT_FILE(dot.toPath()))
  }

  @Test
  fun `matchesAny and matchesNone`() {
    val f = write("a.png")
    assertTrue(IgnoreFilter.matchesAny(f, IgnoreFilter.DOT_FILE, IgnoreFilter.BINARY_EXTENSION))
    assertFalse(IgnoreFilter.matchesNone(f, IgnoreFilter.DOT_FILE, IgnoreFilter.BINARY_EXTENSION))
    assertTrue(IgnoreFilter.matchesNone(f, IgnoreFilter.DOT_FILE))
    assertFalse(IgnoreFilter.matchesAny(f.toPath(), IgnoreFilter.DOT_FILE))
  }

  @Test
  fun `matchesAny with no filters is false`() {
    assertFalse(IgnoreFilter.matchesAny(write("x.txt")))
    assertTrue(IgnoreFilter.matchesNone(write("x.txt")))
  }

  @Test
  fun `predicate and accepting are inverses`() {
    val dot = write(".env")
    val plain = write("env.txt")
    val reject = IgnoreFilter.predicate(IgnoreFilter.DOT_FILE)
    val accept = IgnoreFilter.accepting(IgnoreFilter.DOT_FILE)
    assertTrue(reject(dot))
    assertFalse(accept(dot))
    assertFalse(reject(plain))
    assertTrue(accept(plain))
  }

  @Test
  fun `predefined selections have expected contents`() {
    assertTrue(IgnoreFilter.LLM.contains(IgnoreFilter.LLMIGNORE))
    assertTrue(IgnoreFilter.GIT.contains(IgnoreFilter.GITIGNORE))
    assertTrue(IgnoreFilter.DEFAULT.contains(IgnoreFilter.GITIGNORE))
    assertTrue(IgnoreFilter.RECURSIVE_LISTING.contains(IgnoreFilter.DOT_FILE))
    assertTrue(IgnoreFilter.BINARY.contains(IgnoreFilter.BINARY_CONTENT))
    assertTrue(IgnoreFilter.TEXT_SELECTION.contains(IgnoreFilter.OVERSIZE))
  }

  @Test
  fun `binary extensions set covers common types`() {
    listOf("zip", "png", "pdf", "mp4", "ttf", "class", "wasm").forEach {
      assertTrue(it in IgnoreFilter.BINARY_EXTENSIONS, it)
    }
    assertFalse("kt" in IgnoreFilter.BINARY_EXTENSIONS)
  }
}