package com.simiacryptus.cognotik.webui.servlet.util

import com.simiacryptus.cognotik.fileserver.util.RangeUtil
import com.simiacryptus.cognotik.fileserver.handler.FsErrorCode
import com.simiacryptus.cognotik.fileserver.handler.FsException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RangeUtilTest {

  @Test
  fun `parse returns null when no header is present`() {
    assertNull(RangeUtil.parse(null, 1000L, "read", "/f"))
    assertNull(RangeUtil.parse("", 1000L, "read", "/f"))
  }

  @Test
  fun `parse rejects unsupported units`() {
    val ex = assertThrows(FsException::class.java) {
      RangeUtil.parse("items=0-10", 1000L, "read", "/f")
    }
    assertEquals(FsErrorCode.ERANGE, ex.code)
  }

  @Test
  fun `parse handles a normal explicit range`() {
    val range = RangeUtil.parse("bytes=0-99", 1000L, "read", "/f")!!
    assertEquals(0L, range.start)
    assertEquals(99L, range.endInclusive)
    assertEquals(100L, range.length)
  }

  @Test
  fun `parse handles a suffix range`() {
    val range = RangeUtil.parse("bytes=-500", 1000L, "read", "/f")!!
    assertEquals(500L, range.start)
    assertEquals(999L, range.endInclusive)
    assertEquals(500L, range.length)
  }

  @Test
  fun `parse handles an open ended range`() {
    val range = RangeUtil.parse("bytes=500-", 1000L, "read", "/f")!!
    assertEquals(500L, range.start)
    assertEquals(999L, range.endInclusive)
  }

  @Test
  fun `parse clamps an end beyond the file size`() {
    val range = RangeUtil.parse("bytes=0-5000", 1000L, "read", "/f")!!
    assertEquals(0L, range.start)
    assertEquals(999L, range.endInclusive)
  }

  @Test
  fun `parse rejects range for an empty file`() {
    val ex = assertThrows(FsException::class.java) {
      RangeUtil.parse("bytes=0-10", 0L, "read", "/f")
    }
    assertEquals(FsErrorCode.ERANGE, ex.code)
  }

  @Test
  fun `parse rejects a start beyond the file size`() {
    val ex = assertThrows(FsException::class.java) {
      RangeUtil.parse("bytes=1000-1050", 1000L, "read", "/f")
    }
    assertEquals(FsErrorCode.ERANGE, ex.code)
  }

  @Test
  fun `parse rejects malformed ranges`() {
    assertThrows(FsException::class.java) { RangeUtil.parse("bytes=abc-100", 1000L, "read", "/f") }
    assertThrows(FsException::class.java) { RangeUtil.parse("bytes=100", 1000L, "read", "/f") }
  }

  @Test
  fun `parse rejects an inverted range`() {
    val ex = assertThrows(FsException::class.java) {
      RangeUtil.parse("bytes=100-50", 1000L, "read", "/f")
    }
    assertEquals(FsErrorCode.ERANGE, ex.code)
  }

  @Test
  fun `contentRangeStart returns null when header is absent`() {
    assertNull(RangeUtil.contentRangeStart(null, "write", "/f"))
  }

  @Test
  fun `contentRangeStart extracts the starting offset`() {
    assertEquals(100L, RangeUtil.contentRangeStart("bytes 100-199/1000", "write", "/f"))
  }

  @Test
  fun `contentRangeStart rejects malformed header`() {
    val ex = assertThrows(FsException::class.java) {
      RangeUtil.contentRangeStart("malformed", "write", "/f")
    }
    assertEquals(FsErrorCode.EINVAL, ex.code)
  }
}