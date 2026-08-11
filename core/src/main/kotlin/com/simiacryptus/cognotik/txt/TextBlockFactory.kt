package com.simiacryptus.cognotik.txt

interface TextBlockFactory<T : TextBlock?> {
  fun fromString(text: String?): T

  fun toString(text: T): CharSequence? {
    return text.toString()
  }

  fun looksLike(text: String?): Boolean
}
