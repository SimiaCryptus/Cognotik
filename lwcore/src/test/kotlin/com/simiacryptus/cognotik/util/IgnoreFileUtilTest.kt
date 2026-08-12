package com.simiacryptus.cognotik.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class IgnoreFileUtilTest {

  @TempDir
  lateinit var tempDir: File

  @BeforeEach
  fun setUp() {
    IgnoreFileUtil.clearCache()
    File(tempDir, ".git").mkdirs()
  }

  private fun write(name: String, content: String): File =
    File(tempDir, name).apply { parentFile.mkdirs(); writeText(content) }

  @Test
  fun `compile skips comments and blank lines`() {
    val f = write(".gitignore", "# a comment\n\n*.log\nbuild/\n")
    val patterns = IgnoreFileUtil.compileIgnorePatterns(f)
    assertEquals(2, patterns.size)
  }

  @Test
  fun `star wildcard patterns match`() {
    val f = write(".gitignore", "*.log")
    val regex = IgnoreFileUtil.compileIgnorePatterns(f).single()
    assertTrue(regex.matches("foo.log"))
    assertTrue(regex.matches("nested/foo.log"))
    assertFalse(regex.matches("foo.txt"))
  }

  @Test
  fun `question mark wildcard matches a single character`() {
    val f = write(".gitignore", "a?.txt")
    val regex = IgnoreFileUtil.compileIgnorePatterns(f).single()
    assertTrue(regex.matches("ab.txt"))
    assertFalse(regex.matches("abc.txt"))
  }

  @Test
  fun `trailing slash matches directory and its contents`() {
    val f = write(".gitignore", "build/")
    val regex = IgnoreFileUtil.compileIgnorePatterns(f).single()
    assertTrue(regex.matches("build"))
    assertTrue(regex.matches("build/output.txt"))
    assertTrue(regex.matches("build\\output.txt"))
    assertFalse(regex.matches("builder"))
  }

  @Test
  fun `patterns are cached until the file changes`() {
    val f = write(".gitignore", "*.log")
    val first = IgnoreFileUtil.compileIgnorePatterns(f)
    val second = IgnoreFileUtil.compileIgnorePatterns(f)
    assertSame(first, second)
  }

  @Test
  fun `cache is invalidated on modification`() {
    val f = write(".gitignore", "*.log")
    assertEquals(1, IgnoreFileUtil.compileIgnorePatterns(f).size)
    f.writeText("*.log\n*.tmp\n")
    f.setLastModified(System.currentTimeMillis() + 5_000)
    assertEquals(2, IgnoreFileUtil.compileIgnorePatterns(f).size)
  }

  @Test
  fun `clearCache forces recompilation`() {
    val f = write(".gitignore", "*.log")
    val first = IgnoreFileUtil.compileIgnorePatterns(f)
    IgnoreFileUtil.clearCache()
    assertNotSame(first, IgnoreFileUtil.compileIgnorePatterns(f))
  }

  @Test
  fun `unreadable ignore file yields no patterns`() {
    assertTrue(IgnoreFileUtil.compileIgnorePatterns(File(tempDir, "missing-ignore")).isEmpty())
  }

  @Test
  fun `isIgnored honours gitignore patterns`() {
    write(".gitignore", "*.log")
    val ignored = write("app.log", "x")
    val kept = write("app.txt", "x")
    assertTrue(IgnoreFileUtil.isIgnored(ignored.toPath(), IgnoreFileUtil.GITIGNORE))
    assertFalse(IgnoreFileUtil.isIgnored(kept.toPath(), IgnoreFileUtil.GITIGNORE))
  }

  @Test
  fun `isIgnored walks up to the marker directory`() {
    write(".gitignore", "*.log")
    val nested = write("a/b/c/app.log", "x")
    assertTrue(IgnoreFileUtil.isIgnored(nested.toPath(), IgnoreFileUtil.GITIGNORE))
  }

  @Test
  fun `always ignored names are ignored regardless of patterns`() {
    val nodeModules = File(tempDir, "node_modules").apply { mkdirs() }
    assertTrue(IgnoreFileUtil.isIgnored(nodeModules.toPath(), IgnoreFileUtil.GITIGNORE))
    assertTrue(IgnoreFileUtil.isIgnored(nodeModules.toPath(), IgnoreFileUtil.LLMIGNORE))
  }

  @Test
  fun `git directory is always ignored for gitignore spec`() {
    val gitDir = File(tempDir, ".git")
    assertTrue(IgnoreFileUtil.isIgnored(gitDir.toPath(), IgnoreFileUtil.GITIGNORE))
  }

  @Test
  fun `llmignore uses its own file name`() {
    File(tempDir, ".llm").mkdirs()
    write(".llmignore", "*.secret")
    val f = write("keys.secret", "x")
    assertTrue(IgnoreFileUtil.isIgnored(f.toPath(), IgnoreFileUtil.LLMIGNORE))
    assertFalse(IgnoreFileUtil.isIgnored(f.toPath(), IgnoreFileUtil.GITIGNORE))
  }

  @Test
  fun `isIgnoredByAny combines specs`() {
    File(tempDir, ".llm").mkdirs()
    write(".llmignore", "*.secret")
    val f = write("keys.secret", "x")
    assertTrue(IgnoreFileUtil.isIgnoredByAny(f.toPath(), IgnoreFileUtil.GITIGNORE, IgnoreFileUtil.LLMIGNORE))
    assertFalse(IgnoreFileUtil.isIgnoredByAny(f.toPath(), IgnoreFileUtil.GITIGNORE))
  }

  @Test
  fun `isIgnored with filters delegates to IgnoreFilter`() {
    val dot = write(".dotfile", "x")
    assertTrue(IgnoreFileUtil.isIgnored(dot.toPath(), IgnoreFilter.DOT_FILE))
    assertFalse(IgnoreFileUtil.isIgnored(write("plain.txt", "x").toPath(), IgnoreFilter.DOT_FILE))
  }

  @Test
  fun `predefined specs expose expected names`() {
    assertEquals(".gitignore", IgnoreFileUtil.GITIGNORE.ignoreFileName)
    assertEquals(".git", IgnoreFileUtil.GITIGNORE.markerFileName)
    assertEquals(".llmignore", IgnoreFileUtil.LLMIGNORE.ignoreFileName)
    assertEquals(".readonly", IgnoreFileUtil.READONLY.markerFileName)
    assertEquals(".hidden", IgnoreFileUtil.HIDDEN.markerFileName)
    assertEquals(".writeable", IgnoreFileUtil.WRITEABLE.markerFileName)
  }
}