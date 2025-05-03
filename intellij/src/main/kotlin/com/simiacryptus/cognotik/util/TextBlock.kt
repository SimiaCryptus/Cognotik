package com.simiacryptus.cognotik.util

interface TextBlock {
    companion object {
        val TAB_REPLACEMENT: CharSequence = "  "
        const val DELIMITER: String = "\n"
    }

    fun rawString(): Array<out CharSequence>

    val textBlock: CharSequence
        get() = rawString().joinToString(DELIMITER)

    fun withIndent(indent: CharSequence): TextBlock

}