package com.simiacryptus.util

import org.slf4j.LoggerFactory

import java.nio.charset.Charset
import java.util.*
import java.util.regex.Pattern
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.math.abs

object StringUtil {
    private val log = LoggerFactory.getLogger(StringUtil::class.java)

    @JvmStatic
    fun stripPrefix(text: CharSequence, prefix: CharSequence): CharSequence {
        log.debug("stripPrefix called with text of length: {}, prefix of length: {}", text.length, prefix.length)
        val startsWith = text.toString().startsWith(prefix.toString())
        return if (startsWith) {
            text.toString().substring(prefix.length)
        } else {
            text.toString()
        }
    }

    @JvmStatic
    fun trimPrefix(text: CharSequence): CharSequence {
        log.debug("trimPrefix called with text of length: {}", text.length)
        val prefix = getWhitespacePrefix(text)
        return stripPrefix(text, prefix)
    }

    @JvmStatic
    fun trimSuffix(text: CharSequence): String {
        log.debug("trimSuffix called with text of length: {}", text.length)
        val suffix = getWhitespaceSuffix(text)
        return stripSuffix(text, suffix)
    }

    @JvmStatic
    fun stripSuffix(text: CharSequence, suffix: CharSequence): String {
        log.debug("stripSuffix called with text of length: {}, suffix of length: {}", text.length, suffix.length)
        val endsWith = text.toString().endsWith(suffix.toString())
        return if (endsWith) {
            text.toString().substring(0, text.length - suffix.length)
        } else {
            text.toString()
        }
    }

    @JvmStatic
    fun toString(ints: IntArray): CharSequence {
        log.debug("toString called with int array of size: {}", ints.size)
        val chars = CharArray(ints.size)
        for (i in ints.indices) {
            chars[i] = ints[i].toChar()
        }
        return String(chars)
    }

    @JvmStatic
    fun getWhitespacePrefix(vararg lines: CharSequence): CharSequence {
        log.debug("getWhitespacePrefix called with {} lines", lines.size)
        return Arrays.stream(lines)
            .map { l: CharSequence ->
                toString(
                    l.chars().takeWhile { codePoint: Int ->
                        Character.isWhitespace(
                            codePoint
                        )
                    }.toArray()
                )
            }
            .filter { x: CharSequence -> x.isNotEmpty() }
            .min(Comparator.comparing { obj: CharSequence -> obj.length }).orElse("")
    }

    @JvmStatic
    fun getWhitespaceSuffix(vararg lines: CharSequence): String {
        log.debug("getWhitespaceSuffix called with {} lines", lines.size)
        return reverse(
            Arrays.stream(lines)
            .map { obj: CharSequence? -> reverse(obj!!) }
            .map { l: CharSequence ->
                toString(
                    l.chars().takeWhile { codePoint: Int ->
                        Character.isWhitespace(
                            codePoint
                        )
                    }.toArray()
                )
            }
            .max(Comparator.comparing { obj: CharSequence -> obj.length }).orElse("")
        ).toString()
    }

    @JvmStatic
    private fun reverse(l: CharSequence): CharSequence {
        log.debug("reverse called with CharSequence of length: {}", l.length)
        return StringBuffer(l).reverse().toString()
    }

    @JvmStatic
    fun trim(items: List<CharSequence>, max: Int, preserveHead: Boolean): List<CharSequence> {
        log.debug("trim called with {} items, max: {}, preserveHead: {}", items.size, max, preserveHead)
        var items = items
        items = ArrayList(items)
        val random = Random()
        while (items.size > max) {
            val index = random.nextInt(items.size)
            if (preserveHead && index == 0) continue
            items.removeAt(index)
        }
        return items
    }

    @JvmStatic
    fun restrictCharacterSet(text: String, charset: Charset): String {
        log.debug("restrictCharacterSet called with text of length: {}, charset: {}", text.length, charset)
        val encoder = charset.newEncoder()
        val sb = StringBuilder()
        text.toCharArray().filter(encoder::canEncode).forEach(sb::append)
        return sb.toString()
    }

}