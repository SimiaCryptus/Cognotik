package com.simiacryptus.cognotik.docs

import java.io.File
import java.io.FileInputStream
import javax.swing.text.DefaultStyledDocument
import javax.swing.text.rtf.RTFEditorKit

class RtfReader(rtfFile: File) : DocumentReader {
  private val text: String

  init {
    val rtfParser = RTFEditorKit()
    val document = DefaultStyledDocument()
    FileInputStream(rtfFile).use { fis ->
      rtfParser.read(fis, document, 0)
    }
    text = document.getText(0, document.length)
  }

  override fun getText(): String = text
  override fun close() {
    // No resources to close
  }
}