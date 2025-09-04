package com.simiacryptus.cognotik.input

import java.awt.image.BufferedImage
import java.io.File

interface DocumentReader : AutoCloseable {
    fun getText(): String
}

interface PaginatedDocumentReader : DocumentReader {
    fun getPageCount(): Int
    fun getText(startPage: Int, endPage: Int): String

    override fun getText(): String = getText(0, getPageCount())
}

interface RenderableDocumentReader : DocumentReader {
    fun renderImage(pageIndex: Int, dpi: Float): BufferedImage
}

fun File.getReader(): DocumentReader = when {
    this.name.endsWith(".pdf", ignoreCase = true) -> PDFReader(this)
    this.name.endsWith(".docx", ignoreCase = true) -> DocxReader(this)
    this.name.endsWith(".doc", ignoreCase = true) -> DocReader(this)
    this.name.endsWith(".xlsx", ignoreCase = true) -> XlsxReader(this)
    this.name.endsWith(".xls", ignoreCase = true) -> XlsReader(this)
    this.name.endsWith(".pptx", ignoreCase = true) -> PptxReader(this)
    this.name.endsWith(".ppt", ignoreCase = true) -> PptReader(this)
    this.name.endsWith(".odt", ignoreCase = true) -> OdtReader(this)
    this.name.endsWith(".rtf", ignoreCase = true) -> RtfReader(this)
    this.name.endsWith(".html", ignoreCase = true) -> HTMLReader(this)
    this.name.endsWith(".htm", ignoreCase = true) -> HTMLReader(this)
    else -> TextReader(this)
}