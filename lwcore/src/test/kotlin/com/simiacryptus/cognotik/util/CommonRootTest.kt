package com.simiacryptus.cognotik.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class CommonRootTest {

  @TempDir
  lateinit var tempDir: File

  private fun dir(name: String): Path = File(tempDir, name).apply { mkdirs() }.toPath()
  private fun file(name: String): Path =
    File(tempDir, name).apply { parentFile.mkdirs(); writeText("x") }.toPath()

  @Test
  fun `empty array fails`() {
    assertThrows(IllegalStateException::class.java) {
      emptyArray<Path>().commonRoot()
    }
  }

  @Test
  fun `single file resolves to its parent`() {
    val f = file("a/b.txt")
    assertEquals(f.parent, arrayOf(f).commonRoot())
  }

  @Test
  fun `single directory resolves to itself`() {
    val d = dir("a")
    assertEquals(d, arrayOf(d).commonRoot())
  }

  @Test
  fun `nested path collapses to the ancestor`() {
    val parent = dir("a")
    val child = dir("a/b")
    assertEquals(parent, arrayOf(parent, child).commonRoot())
    assertEquals(parent, arrayOf(child, parent).commonRoot())
  }

  @Test
  fun `siblings collapse to their shared parent`() {
    val a = dir("root/a")
    val b = dir("root/b")
    val expected = File(tempDir, "root").toPath()
    assertEquals(expected.toString(), arrayOf(a, b).commonRoot().toString())
  }

  @Test
  fun `three paths collapse to the shared root`() {
    val a = dir("root/a/x")
    val b = dir("root/b")
    val c = dir("root/c/y/z")
    val expected = File(tempDir, "root").toPath()
    assertEquals(expected.toString(), arrayOf(a, b, c).commonRoot().toString())
  }

  @Test
  fun `identical paths collapse to themselves`() {
    val a = dir("root/a")
    assertEquals(a, arrayOf(a, a).commonRoot())
  }
}