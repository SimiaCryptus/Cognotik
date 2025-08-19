package com.simiacryptus.cognotik.input

import java.awt.image.BufferedImage
import java.io.File

interface DocumentReader : AutoCloseable {
    fun getPageCount(): Int
    fun getText(startPage: Int, endPage: Int): String
    fun renderImage(pageIndex: Int, dpi: Float): BufferedImage
}

fun File.getReader(): DocumentReader = when {
    this.name.endsWith(".pdf", ignoreCase = true) -> PDFReader(this)
    this.name.endsWith(".html", ignoreCase = true) -> HTMLReader(this)
    this.name.endsWith(".htm", ignoreCase = true) -> HTMLReader(this)
    else -> TextReader(this)
}