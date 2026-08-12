package com.simiacryptus.cognotik.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FileSelectionUtilsTest {

  @TempDir
  lateinit var tempDir: File

  /**
   * A `.git` marker keeps the ignore-file lookup from walking all the way to the
   * filesystem root (and picking up unrelated ignore files on the build machine).
   */
  @BeforeEach
  fun setUpGitMarker() {
    File(tempDir, ".git").mkdirs()
    IgnoreFileUtil.clearCache()
  }

  private fun text(name: String, content: String = "Hello, World!"): File =
    File(tempDir, name).apply { parentFile.mkdirs(); writeText(content) }

  private fun bytes(name: String, content: ByteArray): File =
    File(tempDir, name).apply { parentFile.mkdirs(); writeBytes(content) }

  @Nested
  inner class IsTextFile {
    @Test
    fun `plain text file is text`() {
      assertTrue(FileSelectionUtils.isTextFile(text("a.txt")))
    }

    @Test
    fun `missing file is not text`() {
      assertFalse(FileSelectionUtils.isTextFile(File(tempDir, "nope.txt")))
    }

    @Test
    fun `directory is not text`() {
      val dir = File(tempDir, "sub").apply { mkdirs() }
      assertFalse(FileSelectionUtils.isTextFile(dir))
    }

    @Test
    fun `data files are always considered text`() {
      val f = bytes("blob.data", ByteArray(1000) { if (it % 2 == 0) 0 else 65 })
      assertTrue(FileSelectionUtils.isTextFile(f))
    }

    @Test
    fun `binary extension is not text`() {
      assertFalse(FileSelectionUtils.isTextFile(text("image.png", "not really a png")))
    }

    @Test
    fun `binary content is not text`() {
      val f = bytes("blob.txt", ByteArray(1000) { if (it % 2 == 0) 0 else 65 })
      assertFalse(FileSelectionUtils.isTextFile(f))
    }

    @Test
    fun `llm text file honours default filters`() {
      assertTrue(FileSelectionUtils.isLLMTextFile(text("a.txt")))
      assertFalse(FileSelectionUtils.isLLMTextFile(text("image.jpg", "x")))
    }
  }

  @Nested
  inner class IsBinaryFile {
    @Test
    fun `missing file is not binary`() {
      assertFalse(FileSelectionUtils.isBinaryFile(File(tempDir, "missing")))
    }

    @Test
    fun `directory is not binary`() {
      assertFalse(FileSelectionUtils.isBinaryFile(File(tempDir, "d").apply { mkdirs() }))
    }

    @Test
    fun `empty file is not binary`() {
      assertFalse(FileSelectionUtils.isBinaryFile(text("empty.txt", "")))
    }

    @Test
    fun `binary extension short-circuits`() {
      assertTrue(FileSelectionUtils.isBinaryFile(text("thing.zip", "just text")))
    }

    @Test
    fun `small files are assumed text`() {
      assertFalse(FileSelectionUtils.isBinaryFile(text("tiny.txt", "abc")))
    }

    @Test
    fun `long ascii content is text`() {
      assertFalse(FileSelectionUtils.isBinaryFile(text("long.txt", "Hello, World! ".repeat(20))))
    }

    @Test
    fun `utf8 content is text`() {
      assertFalse(FileSelectionUtils.isBinaryFile(text("utf8.txt", "héllo wörld ünïcode ".repeat(10))))
    }

    @Test
    fun `null heavy content is binary`() {
      assertTrue(FileSelectionUtils.isBinaryFile(bytes("blob.txt", ByteArray(1000) { if (it % 2 == 0) 0 else 65 })))
    }

    @Test
    fun `very large files are skipped`() {
      val big = bytes("big.txt", ByteArray(1024 * 1024 + 32) { 0 })
      assertFalse(FileSelectionUtils.isBinaryFile(big))
    }
  }

  @Nested
  inner class Expansion {
    @Test
    fun `expands directories and filters binaries`() {
      text("src/a.txt")
      text("src/b.png", "binary-ish")
      val expanded = FileSelectionUtils.expandFileList(File(tempDir, "src"))
      assertEquals(listOf("a.txt"), expanded.map { it.name })
    }

    @Test
    fun `missing files are dropped`() {
      val expanded = FileSelectionUtils.expandFileList(File(tempDir, "does-not-exist"))
      assertTrue(expanded.isEmpty())
    }

    @Test
    fun `plain files are passed through`() {
      val f = text("solo.txt")
      assertArrayEquals(arrayOf(f), FileSelectionUtils.expandFileList(f))
    }

    @Test
    fun `expandFiles respects explicit filters`() {
      text("x/keep.txt")
      text("x/.hiddenfile")
      val expanded = FileSelectionUtils.expandFiles(listOf(File(tempDir, "x")), false, IgnoreFilter.DOT_FILE)
      assertEquals(listOf("keep.txt"), expanded.map { it.name })
    }
  }

  @Nested
  inner class Walking {
    @Test
    fun `walkFiltered returns only files`() {
      text("w/a.txt")
      text("w/nested/b.txt")
      val result = FileSelectionUtils.walkFiltered(File(tempDir, "w"))
      assertEquals(setOf("a.txt", "b.txt"), result.map { it.name }.toSet())
    }

    @Test
    fun `walkFiltered honours extra filter`() {
      text("w/a.txt")
      text("w/b.md")
      val result = FileSelectionUtils.walkFiltered(File(tempDir, "w"), extraFilter = {
        it.isDirectory || it.name.endsWith(".txt")
      })
      assertEquals(listOf("a.txt"), result.map { it.name })
    }

    @Test
    fun `filteredWalk excludes ignored files`() {
      text(".gitignore", "*.log\n")
      text("w/keep.txt")
      text("w/skip.log")
      IgnoreFileUtil.clearCache()
      val result = FileSelectionUtils.filteredWalk(File(tempDir, "w"))
      assertEquals(listOf("keep.txt"), result.map { it.name })
    }

    @Test
    fun `listFilesRecursively skips dot files and node_modules`() {
      text("r/a.txt")
      text("r/.secret")
      text("r/node_modules/lib.js")
      val listed = with(FileSelectionUtils) { File(tempDir, "r").listFilesRecursively() }
      val names = listed.map { it.name }.toSet()
      assertTrue(names.contains("a.txt"))
      assertFalse(names.contains(".secret"))
      assertFalse(names.contains("node_modules"))
      assertFalse(names.contains("lib.js"))
    }
  }

  @Nested
  inner class Trees {
    @Test
    fun `ascii tree renders entries`() {
      text("t/a.txt")
      text("t/sub/b.txt")
      val tree = FileSelectionUtils.filteredWalkAsciiTree(File(tempDir, "t"))
      assertTrue(tree.contains("a.txt"), tree)
      assertTrue(tree.contains("sub/"), tree)
      assertTrue(tree.contains("b.txt"), tree)
      assertTrue(tree.contains("└── ") || tree.contains("├── "), tree)
    }

    @Test
    fun `ascii tree of a single file renders its name`() {
      val f = text("t/only.txt")
      val tree = FileSelectionUtils.filteredWalkAsciiTree(f)
      assertEquals("only.txt", tree.trim())
    }

    @Test
    fun `ascii tree is empty when root is filtered out`() {
      val f = text("t/only.txt")
      assertEquals("", FileSelectionUtils.filteredWalkAsciiTree(f, filter = { false }))
    }

    @Test
    fun `available files include human readable sizes`() {
      text("t/a.txt", "Hello, World!")
      val listing = FileSelectionUtils.getAvailableFiles(File(tempDir, "t").toPath())
      assertEquals(1, listing.size)
      assertTrue(listing.first().contains("a.txt (13 B)"), listing.first())
    }
  }

  @Nested
  inner class PathResolution {
    @Test
    fun `prefilter trims whitespace`() {
      assertEquals("foo.txt", FileSelectionUtils.prefilterFilename("   foo.txt  "))
    }

    @Test
    fun `prefilter takes the first token`() {
      assertEquals("foo.txt", FileSelectionUtils.prefilterFilename("foo.txt some trailing words"))
    }

    @Test
    fun `prefilter strips backticks`() {
      assertEquals("foo.txt", FileSelectionUtils.prefilterFilename("`foo.txt`"))
    }

    @Test
    fun `prefilter returns null for blank input`() {
      assertNull(FileSelectionUtils.prefilterFilename("   "))
    }

    @Test
    fun `resolve returns relative path for existing file`() {
      text("p/a.txt")
      assertEquals("p/a.txt", FileSelectionUtils.resolveToRelativePath(tempDir.toPath(), "p/a.txt"))
    }

    @Test
    fun `resolve finds file recursively by name`() {
      text("p/deeper/a.txt")
      val resolved = FileSelectionUtils.resolveToRelativePath(tempDir.toPath(), "a.txt")
      assertNotNull(resolved)
      assertTrue(resolved!!.replace('\\', '/').endsWith("p/deeper/a.txt"), resolved)
    }

    @Test
    fun `resolve returns null for unknown file`() {
      assertNull(FileSelectionUtils.resolveToRelativePath(tempDir.toPath(), "no-such-file.txt"))
    }

    @Test
    fun `resolve returns null when root is not a directory`() {
      val f = text("a.txt")
      assertNull(FileSelectionUtils.resolveToRelativePath(f.toPath(), "a.txt"))
    }

    @Test
    fun `relativizeFrom produces relative path`() {
      val f = text("p/a.txt")
      val relative = with(FileSelectionUtils) { f.absolutePath.relativizeFrom(tempDir.toPath()) }
      assertEquals("p/a.txt", relative.replace('\\', '/'))
    }
  }

  @Nested
  inner class FilterCombinators {
    @Test
    fun `matchesAny and matchesNone are complementary`() {
      val dot = text(".config")
      assertTrue(FileSelectionUtils.matchesAny(dot, IgnoreFilter.DOT_FILE))
      assertFalse(FileSelectionUtils.matchesNone(dot, IgnoreFilter.DOT_FILE))
      val plain = text("plain.txt")
      assertFalse(FileSelectionUtils.matchesAny(plain, IgnoreFilter.DOT_FILE))
      assertTrue(FileSelectionUtils.matchesNone(plain, IgnoreFilter.DOT_FILE))
    }
  }
}