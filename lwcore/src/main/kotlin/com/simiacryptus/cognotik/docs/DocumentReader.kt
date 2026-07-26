package com.simiacryptus.cognotik.docs

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
  fun getPageCount(): Int
  fun renderImage(pageIndex: Int, dpi: Float): BufferedImage
}

fun File.getDocumentReader(): DocumentReader = when {
  this.name.endsWith(".pdf", ignoreCase = true) -> PDFReader(this)
  this.name.endsWith(".docx", ignoreCase = true) -> DocxReader(this)
  this.name.endsWith(".doc", ignoreCase = true) -> _root_ide_package_.com.simiacryptus.cognotik.docs.DocReader(this)
  this.name.endsWith(".xlsx", ignoreCase = true) -> XlsxReader(this)
  this.name.endsWith(".xls", ignoreCase = true) -> XlsReader(this)
  this.name.endsWith(".pptx", ignoreCase = true) -> PptxReader(this)
  this.name.endsWith(".ppt", ignoreCase = true) -> PptReader(this)
  this.name.endsWith(".odt", ignoreCase = true) -> OdtReader(this)
  this.name.endsWith(".rtf", ignoreCase = true) -> RtfReader(this)
  this.name.endsWith(".html", ignoreCase = true) -> HTMLReader(this)
  this.name.endsWith(".htm", ignoreCase = true) -> HTMLReader(this)
  this.name.endsWith(".eml", ignoreCase = true) -> EmlReader(this)
  this.name.endsWith(".jpg", ignoreCase = true) -> ImageReader(this)
  this.name.endsWith(".jpeg", ignoreCase = true) -> ImageReader(this)
  this.name.endsWith(".png", ignoreCase = true) -> ImageReader(this)
  this.name.endsWith(".gif", ignoreCase = true) -> ImageReader(this)
  this.name.endsWith(".bmp", ignoreCase = true) -> ImageReader(this)
  this.name.endsWith(".tiff", ignoreCase = true) -> ImageReader(this)
  this.name.endsWith(".tif", ignoreCase = true) -> ImageReader(this)
  this.name.endsWith(".webp", ignoreCase = true) -> ImageReader(this)
  this.name.endsWith(".svg", ignoreCase = true) -> ImageReader(this)
  else -> TextReader(this)
}

fun File.isDocumentFile(): Boolean {
  val supportedExtensions = listOf(
    ".pdf", ".docx", ".doc", ".xlsx", ".xls", ".pptx", ".ppt",
    ".odt", ".rtf", ".html", ".htm", ".eml", ".txt",
    ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".tiff", ".tif", ".webp", ".svg"
  )
  return supportedExtensions.any { this.name.endsWith(it, ignoreCase = true) }
}