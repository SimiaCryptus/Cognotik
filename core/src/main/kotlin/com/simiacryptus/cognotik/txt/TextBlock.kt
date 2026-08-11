package com.simiacryptus.cognotik.txt

interface TextBlock {
  companion object {
    val TAB_REPLACEMENT: CharSequence = "  "
    const val DELIMITER: String = "\n"
  }

  fun rawString(): Array<out CharSequence>

  fun withIndent(indent: CharSequence): TextBlock

}