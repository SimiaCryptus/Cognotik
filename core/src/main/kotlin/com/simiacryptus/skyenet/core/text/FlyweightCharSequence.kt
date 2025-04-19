package com.simiacryptus.skyenet.core.text

/**
 * A flyweight CharSequence that references a sub‐sequence of a backing String without copying.
 *
 * @param source the backing String
 * @param offset the start index (inclusive) within source
 * @param size   the number of characters in this sequence
 */
class FlyweightCharSequence(
  private val source: String,
  private val offset: Int = 0,
  private val size: Int = source.length - offset
) : CharSequence {
  init {
    require(offset >= 0 && size >= 0 && offset + size <= source.length) {
      "Invalid offset ($offset) or size ($size) for source length ${source.length}"
    }
  }
  
  override val length: Int
    get() = size
  
  override fun get(index: Int): Char {
    if (index < 0 || index >= size) {
      throw IndexOutOfBoundsException("Index: $index, Length: $size")
    }
    return source[offset + index]
  }
  
  override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
    if (startIndex < 0 || endIndex < startIndex || endIndex > size) {
      throw IndexOutOfBoundsException(
        "startIndex: $startIndex, endIndex: $endIndex, Length: $size"
      )
    }
    return FlyweightCharSequence(source, offset + startIndex, endIndex - startIndex)
  }
  
  override fun toString(): String =
    source.substring(offset, offset + size)
}