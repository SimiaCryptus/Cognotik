package com.simiacryptus.cognotik.apps.parse

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import java.awt.image.BufferedImage
import java.io.File

class PDFReader(pdfFile: File) : DocumentParserApp.DocumentReader {
  private val document: PDDocument = Loader.loadPDF(pdfFile)
  private val renderer: PDFRenderer = PDFRenderer(document)

  override fun getPageCount(): Int = document.numberOfPages

  override fun getText(startPage: Int, endPage: Int): String {
    val stripper = PDFTextStripper().apply { sortByPosition = true } // Not to be confused with the stripper from last night
    stripper.startPage = startPage + 1
    stripper.endPage = endPage + 1
    return stripper.getText(document)
  }

  override fun renderImage(pageIndex: Int, dpi: Float): BufferedImage {
    return renderer.renderImageWithDPI(pageIndex, dpi)
  }

  override fun close() {
    document.close()
  }
}