package com.simiacryptus.cognotik.docs

import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import java.io.File
import java.io.FileInputStream

class DocReader(docFile: File) : DocumentReader {
  private val document: HWPFDocument = HWPFDocument(FileInputStream(docFile))
  private val extractor: WordExtractor = WordExtractor(document)
  override fun getText(): String {
    return extractor.text
  }

  override fun close() {
    extractor.close()
    document.close()
  }
}