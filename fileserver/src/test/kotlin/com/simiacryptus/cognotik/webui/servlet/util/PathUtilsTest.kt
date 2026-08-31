package com.simiacryptus.cognotik.webui.servlet.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PathUtilsTest {

  @TempDir
  lateinit var root: File

  @Test
  fun `isSafePath rejects dotdot in string path`() {
    assertFalse(PathUtils.isSafePath(root, "../outside.txt"))
    assertFalse(PathUtils.isSafePath(root, "a/../../outside.txt"))
  }

  @Test
  fun `isSafePath accepts normal relative path`() {
    assertTrue(PathUtils.isSafePath(root, "a/b/c.txt"))
  }

  @Test
  fun `isSafePath with File accepts contained file`() {
    val child = File(root, "sub/file.txt")
    assertTrue(PathUtils.isSafePath(root, child))
  }

  @Test
  fun `isSafePath with File rejects file outside root`() {
    val sibling = File(root.parentFile, "outside-${root.name}/file.txt")
    assertFalse(PathUtils.isSafePath(root, sibling))
  }

  @Test
  fun `resolveSafePath resolves valid relative path`() {
    val resolved = PathUtils.resolveSafePath(root, "sub/file.txt")
    assertEquals(File(root, "sub/file.txt").path, resolved.path)
  }

  @Test
  fun `resolveSafePath throws on traversal attempt`() {
    assertThrows(IllegalArgumentException::class.java) {
      PathUtils.resolveSafePath(root, "../escape.txt")
    }
  }

  @Test
  fun `validateAndNormalize accepts path within root`() {
    val target = File(root, "nested/file.txt").toPath()
    val normalized = PathUtils.validateAndNormalize(root.toPath(), target)
    assertTrue(normalized.startsWith(root.canonicalFile.toPath().normalize()))
  }

  @Test
  fun `validateAndNormalize throws for path outside root`() {
    val outside = File(root.parentFile, "definitely-outside").toPath()
    assertThrows(SecurityException::class.java) {
      PathUtils.validateAndNormalize(root.toPath(), outside)
    }
  }

  @Test
  fun `isValidFileName rejects empty or unsafe characters`() {
    assertFalse(PathUtils.isValidFileName(""))
    assertFalse(PathUtils.isValidFileName("a/b"))
    assertFalse(PathUtils.isValidFileName("a\\b"))
    assertFalse(PathUtils.isValidFileName("a:b"))
    assertFalse(PathUtils.isValidFileName("a*b"))
    assertFalse(PathUtils.isValidFileName("a?b"))
    assertFalse(PathUtils.isValidFileName("a\"b"))
    assertFalse(PathUtils.isValidFileName("a<b"))
    assertFalse(PathUtils.isValidFileName("a>b"))
    assertFalse(PathUtils.isValidFileName("a|b"))
  }

  @Test
  fun `isValidFileName accepts normal file name`() {
    assertTrue(PathUtils.isValidFileName("valid-file_name.txt"))
  }
}