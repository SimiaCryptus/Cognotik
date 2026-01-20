package com.simiacryptus.cognotik.docs

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import java.awt.image.BufferedImage
import java.io.File
import java.util.*
import javax.imageio.spi.IIORegistry
import javax.imageio.spi.ImageReaderSpi
import javax.imageio.spi.ImageWriterSpi

class PDFReader(pdfFile: File) : PaginatedDocumentReader, RenderableDocumentReader {
    private val document: PDDocument = PDDocument.load(pdfFile)
    private val renderer: PDFRenderer = PDFRenderer(document)

    companion object {
        init {
            val registry = IIORegistry.getDefaultInstance()
            val loader = PDFReader::class.java.classLoader
            ServiceLoader.load(ImageReaderSpi::class.java, loader).forEach {
                registry.registerServiceProvider(it)
            }
            ServiceLoader.load(ImageWriterSpi::class.java, loader).forEach {
                registry.registerServiceProvider(it)
            }
        }
    }

    override fun getText(): String {
        val stripper = PDFTextStripper().apply { sortByPosition = true }
        return stripper.getText(document)
    }

    override fun getPageCount(): Int = document.numberOfPages

    override fun getText(startPage: Int, endPage: Int): String {
        val stripper =
            PDFTextStripper().apply { sortByPosition = true }

        stripper.startPage = startPage + 1
        stripper.endPage = endPage + 1
        return stripper.getText(document)
    }

    override fun renderImage(pageIndex: Int, dpi: Float): BufferedImage {
        val currentThread = Thread.currentThread()
        val originalClassLoader = currentThread.contextClassLoader
        try {
            currentThread.contextClassLoader = this::class.java.classLoader
            return renderer.renderImageWithDPI(pageIndex, dpi)
        } finally {
            currentThread.contextClassLoader = originalClassLoader
        }
    }

    override fun close() {
        document.close()
    }
}