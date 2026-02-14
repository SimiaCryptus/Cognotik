package com.simiacryptus.cognotik.ui.patch

sealed class ResponseSegment {
  data class Markdown(val content: String) : ResponseSegment()
  data class NewFileBlock(
    val filename: String,
    val language: String,
    val code: String,
    val originalRange: IntRange
  ) : ResponseSegment()

  data class DiffBlock(
    val filename: String,
    val diff: String,
    val originalRange: IntRange
  ) : ResponseSegment()
}