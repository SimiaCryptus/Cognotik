package com.simiacryptus.cognotik.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class GetModuleForFileTest {

  @TempDir
  lateinit var tempDir: File

  @Test
  fun `finds the git root from a nested file`() {
    val root = File(tempDir, "project").apply { mkdirs() }
    File(root, ".git").mkdirs()
    val src = File(root, "src/main/kotlin").apply { mkdirs() }
    val file = File(src, "Main.kt").apply { writeText("fun main() {}") }
    assertEquals(root, getModuleRootForFile(file))
  }

  @Test
  fun `finds the git root from a nested directory`() {
    val root = File(tempDir, "project").apply { mkdirs() }
    File(root, ".git").mkdirs()
    val src = File(root, "src/main").apply { mkdirs() }
    assertEquals(root, getModuleRootForFile(src))
  }

  @Test
  fun `returns the git root itself when given the root`() {
    val root = File(tempDir, "project").apply { mkdirs() }
    File(root, ".git").mkdirs()
    assertEquals(root, getModuleRootForFile(root))
  }

  @Test
  fun `prefers the nearest git root`() {
    val outer = File(tempDir, "outer").apply { mkdirs() }
    File(outer, ".git").mkdirs()
    val inner = File(outer, "inner").apply { mkdirs() }
    File(inner, ".git").mkdirs()
    val file = File(inner, "a.txt").apply { writeText("x") }
    assertEquals(inner, getModuleRootForFile(file))
  }

  @Test
  fun `falls back to the directory when no git root exists`() {
    val dir = File(tempDir, "loose").apply { mkdirs() }
    val file = File(dir, "a.txt").apply { writeText("x") }
    assertEquals(dir, getModuleRootForFile(file))
  }
}