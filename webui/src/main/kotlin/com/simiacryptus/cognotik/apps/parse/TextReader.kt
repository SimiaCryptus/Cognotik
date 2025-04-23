package com.simiacryptus.cognotik.apps.parse

import java.awt.image.BufferedImage
import java.io.File

class TextReader(private val textFile: File) : DocumentParserApp.DocumentReader {
  private val pages: List<String> = splitIntoPages(textFile.readLines().joinToString("\n"))
  private lateinit var settings: DocumentParserApp.Settings
  fun configure(settings: DocumentParserApp.Settings) {
    this.settings = settings
  }

  override fun getPageCount(): Int = pages.size

  override fun getText(startPage: Int, endPage: Int): String {
    val text = pages.subList(startPage, endPage.coerceAtMost(pages.size)).joinToString("\n")
    return if (settings.addLineNumbers) {
      text.lines().mapIndexed { index, line ->
        "${(index + 1).toString().padStart(6)}: $line"
      }.joinToString("\n")
    } else text
  }

  override fun renderImage(pageIndex: Int, dpi: Float): BufferedImage {
    throw UnsupportedOperationException("Text files do not support image rendering")
  }

  override fun close() {
    // No resources to close for text files
  }

  private fun splitIntoPages(text: String, maxChars: Int = 16000): List<String> {
    if (text.length <= maxChars) return listOf(text)
    val lines = text.split("\n")
    if (lines.size <= 1) return listOf(text)
    val splitFitnesses = lines.indices.map { i ->
      val leftSize = lines.subList(0, i).map { it.length }.sum()
      val rightSize = lines.subList(i, lines.size).map { it.length }.sum()
      if (leftSize <= 0) return@map i to Double.MAX_VALUE
      if (rightSize <= 0) return@map i to Double.MAX_VALUE
      var fitness = -((leftSize.toDouble() / text.length) * Math.log1p(rightSize.toDouble() / text.length) +
          (rightSize.toDouble() / text.length) * Math.log1p(leftSize.toDouble() / text.length))
      if (lines[i].isEmpty()) fitness *= 2
      i to fitness
    }.toTypedArray().toMutableList()

    val bestSplitIndex = splitFitnesses.minByOrNull { it.second }?.first ?: lines.size / 2
    val leftText = lines.subList(0, bestSplitIndex).joinToString("\n")
    val rightText = lines.subList(bestSplitIndex, lines.size).joinToString("\n")
    return splitIntoPages(leftText, maxChars) + splitIntoPages(rightText, maxChars)
  }
}