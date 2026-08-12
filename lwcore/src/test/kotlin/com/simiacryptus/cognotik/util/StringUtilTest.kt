package com.simiacryptus.cognotik.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class StringUtilTest {

  @Nested
  inner class StripPrefix {
    @Test
    fun `removes matching prefix`() {
      assertEquals("World", StringUtil.stripPrefix("HelloWorld", "Hello").toString())
    }

    @Test
    fun `returns original when prefix does not match`() {
      assertEquals("HelloWorld", StringUtil.stripPrefix("HelloWorld", "Goodbye").toString())
    }

    @Test
    fun `empty prefix returns original`() {
      assertEquals("HelloWorld", StringUtil.stripPrefix("HelloWorld", "").toString())
    }

    @Test
    fun `prefix equal to text yields empty string`() {
      assertEquals("", StringUtil.stripPrefix("abc", "abc").toString())
    }
  }

  @Nested
  inner class StripSuffix {
    @Test
    fun `removes matching suffix`() {
      assertEquals("Hello", StringUtil.stripSuffix("HelloWorld", "World"))
    }

    @Test
    fun `returns original when suffix does not match`() {
      assertEquals("HelloWorld", StringUtil.stripSuffix("HelloWorld", "Universe"))
    }

    @Test
    fun `empty suffix returns original`() {
      assertEquals("HelloWorld", StringUtil.stripSuffix("HelloWorld", ""))
    }
  }

  @Nested
  inner class TrimPrefixSuffix {
    @Test
    fun `trimPrefix removes leading whitespace`() {
      assertEquals("abc", StringUtil.trimPrefix("   abc").toString())
    }

    @Test
    fun `trimPrefix is a no-op without leading whitespace`() {
      assertEquals("abc   ", StringUtil.trimPrefix("abc   ").toString())
    }

    @Test
    fun `trimSuffix removes trailing whitespace`() {
      assertEquals("abc", StringUtil.trimSuffix("abc   "))
    }

    @Test
    fun `trimSuffix is a no-op without trailing whitespace`() {
      assertEquals("   abc", StringUtil.trimSuffix("   abc"))
    }

    @Test
    fun `trimPrefix handles tabs and newlines`() {
      assertEquals("abc", StringUtil.trimPrefix("\t\n abc").toString())
    }
  }

  @Nested
  inner class WhitespacePrefix {
    @Test
    fun `returns shortest non-empty whitespace prefix`() {
      assertEquals("  ", StringUtil.getWhitespacePrefix("  a", "    b").toString())
    }

    @Test
    fun `ignores lines with no whitespace prefix`() {
      assertEquals("  ", StringUtil.getWhitespacePrefix("a", "  b").toString())
    }

    @Test
    fun `returns empty string when no lines have whitespace`() {
      assertEquals("", StringUtil.getWhitespacePrefix("a", "b").toString())
    }

    @Test
    fun `returns empty string for no input`() {
      assertEquals("", StringUtil.getWhitespacePrefix().toString())
    }
  }

  @Nested
  inner class WhitespaceSuffix {
    @Test
    fun `returns longest whitespace suffix`() {
      assertEquals("    ", StringUtil.getWhitespaceSuffix("a  ", "b    "))
    }

    @Test
    fun `returns empty string when no trailing whitespace`() {
      assertEquals("", StringUtil.getWhitespaceSuffix("a", "b"))
    }

    @Test
    fun `returns empty string for no input`() {
      assertEquals("", StringUtil.getWhitespaceSuffix())
    }
  }

  @Nested
  inner class ToStringConversion {
    @Test
    fun `converts int array to char sequence`() {
      val ints = intArrayOf('a'.code, 'b'.code, 'c'.code)
      assertEquals("abc", StringUtil.toString(ints).toString())
    }

    @Test
    fun `empty array yields empty string`() {
      assertEquals("", StringUtil.toString(intArrayOf()).toString())
    }
  }

  @Nested
  inner class Trim {
    @Test
    fun `returns list unchanged when under max`() {
      val items = listOf<CharSequence>("a", "b", "c")
      assertEquals(items, StringUtil.trim(items, 10, false))
    }

    @Test
    fun `reduces list to max size`() {
      val items = (1..20).map { it.toString() as CharSequence }
      val trimmed = StringUtil.trim(items, 5, false)
      assertEquals(5, trimmed.size)
      assertTrue(items.containsAll(trimmed))
    }

    @Test
    fun `preserves head when requested`() {
      val items = (1..20).map { it.toString() as CharSequence }
      repeat(10) {
        val trimmed = StringUtil.trim(items, 3, true)
        assertEquals(3, trimmed.size)
        assertEquals("1", trimmed.first().toString())
      }
    }

    @Test
    fun `does not mutate the input list`() {
      val items = (1..20).map { it.toString() as CharSequence }
      StringUtil.trim(items, 2, false)
      assertEquals(20, items.size)
    }
  }
}