package com.simiacryptus.cognotik.input

import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.hslf.usermodel.HSLFTextShape
import java.io.File
import java.io.FileInputStream

class PptReader(pptFile: File) : DocumentReader {
    private val slideShow: HSLFSlideShow = HSLFSlideShow(FileInputStream(pptFile))

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
                if (shape is HSLFTextShape) {
                    val shapeText = shape.text
                    if (!shapeText.isNullOrBlank()) {
                        text.append(shapeText)
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