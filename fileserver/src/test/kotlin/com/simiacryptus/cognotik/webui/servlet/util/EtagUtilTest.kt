package com.simiacryptus.cognotik.webui.servlet.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class EtagUtilTest {

  @TempDir
  lateinit var tempDir: File

  @Test
  fun `weakEtag encodes size and mtime as hex`() {
    val file = File(tempDir, "sample.txt")
    file.writeText("hello world")
    val etag = EtagUtil.weakEtag(file)
    val expected = "W/\"${file.length().toString(16)}-${file.lastModified().toString(16)}\""
    assertEquals(expected, etag)
    assertTrue(etag.startsWith("W/\""))
  }

  @Test
  fun `matches returns false for null or blank header`() {
    assertFalse(EtagUtil.matches(null, "W/\"abc-def\""))
    assertFalse(EtagUtil.matches("", "W/\"abc-def\""))
    assertFalse(EtagUtil.matches("   ", "W/\"abc-def\""))
  }

  @Test
  fun `matches wildcard always matches`() {
    assertTrue(EtagUtil.matches("*", "W/\"abc-def\""))
    assertTrue(EtagUtil.matches(" * ", "anything"))
  }

  @Test
  fun `matches exact weak etag`() {
    val etag = "W/\"1a-2b\""
    assertTrue(EtagUtil.matches(etag, etag))
  }

  @Test
  fun `matches ignores weak prefix and quotes`() {
    assertTrue(EtagUtil.matches("\"1a-2b\"", "W/\"1a-2b\""))
    assertTrue(EtagUtil.matches("W/\"1a-2b\"", "\"1a-2b\""))
  }

  @Test
  fun `matches supports comma separated list`() {
    val header = "\"xxx-yyy\", W/\"1a-2b\", \"zzz-www\""
    assertTrue(EtagUtil.matches(header, "W/\"1a-2b\""))
  }

  @Test
  fun `matches returns false when no entry matches`() {
    val header = "\"xxx-yyy\", \"zzz-www\""
    assertFalse(EtagUtil.matches(header, "W/\"1a-2b\""))
  }
}