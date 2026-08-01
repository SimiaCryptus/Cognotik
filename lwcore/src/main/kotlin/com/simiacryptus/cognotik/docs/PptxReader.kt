package com.simiacryptus.cognotik.docs

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFTextShape
import java.io.File
import java.io.FileInputStream

class PptxReader(pptxFile: File) : DocumentReader {
  private val slideShow: XMLSlideShow = XMLSlideShow(FileInputStream(pptxFile))

  override fun getText(): String {
    val text = StringBuilder()
    slideShow.slides.forEachIndexed { index, slide ->
      text.append("Slide ${index + 1}\n")
      text.append("-".repeat(20))
      text.append("\n")
      // Extract title
      slide.title?.let {
        text.append("Title: $it\n")
      }
      // Extract text from all shapes
      slide.shapes.forEach { shape ->
        if (shape is XSLFTextShape) {
          text.append(shape.text)
          text.append("\n")
        }
      }
      // Extract notes
      slide.notes?.let { notes ->
        text.append("\nNotes:\n")
        notes.shapes.forEach { shape ->
          if (shape is XSLFTextShape) {
            text.append(shape.text)
            text.append("\n")
          }
        }
      }
      text.append("\n")
    }
    return text.toString()
  }

  override fun close() {
    slideShow.close()
  }
}