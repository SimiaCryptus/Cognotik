package com.simiacryptus.cognotik.webui.servlet.util

import com.simiacryptus.cognotik.fileserver.util.FsPath
import com.simiacryptus.cognotik.fileserver.handler.FsErrorCode
import com.simiacryptus.cognotik.fileserver.handler.FsException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FsPathTest {

  @TempDir
  lateinit var root: File

  @Test
  fun `normalize strips leading slash and collapses dot segments`() {
    assertEquals("a/b/c", FsPath.normalize("/a/b/c", "stat"))
    assertEquals("a/b", FsPath.normalize("a/./b", "stat"))
    assertEquals("", FsPath.normalize("/", "stat"))
    assertEquals("", FsPath.normalize("", "stat"))
  }

  @Test
  fun `normalize resolves parent references lexically`() {
    assertEquals("b", FsPath.normalize("a/../b", "stat"))
    assertEquals("a/c", FsPath.normalize("a/b/../c", "stat"))
  }

  @Test
  fun `normalize rejects escaping the root`() {
    val ex = assertThrows(FsException::class.java) {
      FsPath.normalize("../escape", "stat")
    }
    assertEquals(FsErrorCode.EINVAL, ex.code)
  }

  @Test
  fun `normalize rejects the reserved fsapi segment`() {
    val ex = assertThrows(FsException::class.java) {
      FsPath.normalize("a/.fsapi/b", "stat")
    }
    assertEquals(FsErrorCode.EINVAL, ex.code)
  }

  @Test
  fun `normalize rejects NUL bytes`() {
    val ex = assertThrows(FsException::class.java) {
      FsPath.normalize("a/\u0000b", "stat")
    }
    assertEquals(FsErrorCode.EINVAL, ex.code)
  }

  @Test
  fun `normalize rejects backslash colon and tilde`() {
    assertThrows(FsException::class.java) { FsPath.normalize("a\\b", "stat") }
    assertThrows(FsException::class.java) { FsPath.normalize("a:b", "stat") }
    assertThrows(FsException::class.java) { FsPath.normalize("a~b", "stat") }
  }

  @Test
  fun `normalize rejects trailing dot or space segments`() {
    assertThrows(FsException::class.java) { FsPath.normalize("a/b.", "stat") }
    assertThrows(FsException::class.java) { FsPath.normalize("a/b ", "stat") }
  }

  @Test
  fun `resolve produces a virtual path and a contained file`() {
    val target = FsPath.resolve(root, "sub/dir/file.txt", "stat")
    assertEquals("/sub/dir/file.txt", target.virtual)
    assertTrue(target.file.path.startsWith(root.canonicalFile.path))
  }

  @Test
  fun `resolve of empty path returns the root itself`() {
    val target = FsPath.resolve(root, "", "stat")
    assertEquals("/", target.virtual)
    assertEquals(root.canonicalFile, target.file)
  }

  @Test
  fun `virtualPath returns slash for the root itself`() {
    assertEquals("/", FsPath.virtualPath(root, root))
  }

  @Test
  fun `virtualPath returns relative path for a contained file`() {
    val file = File(root, "a/b.txt")
    assertEquals("/a/b.txt", FsPath.virtualPath(root, file))
  }

  @Test
  fun `join combines base and relative segments`() {
    assertEquals("/", FsPath.join("/", ""))
    assertEquals("/a/b", FsPath.join("/a", "b"))
    assertEquals("/a/b", FsPath.join("/a/", "b"))
  }
}