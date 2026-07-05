package com.simiacryptus.cognotik.docs

import org.odftoolkit.odfdom.doc.OdfTextDocument
import java.io.File

class OdtReader(odtFile: File) : DocumentReader {
  private val document: OdfTextDocument = OdfTextDocument.loadDocument(odtFile)
  override fun getText(): String {
    return document.contentRoot.textContent ?: ""
  }

  override fun close() {
    document.close()
  }
}