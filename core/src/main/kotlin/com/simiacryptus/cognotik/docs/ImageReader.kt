package com.simiacryptus.cognotik.docs

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class ImageReader(private val imageFile: File) : RenderableDocumentReader, PaginatedDocumentReader {
  private var image: BufferedImage? = null

  private fun loadImage(): BufferedImage {
    return image ?: ImageIO.read(imageFile).also { image = it }
      ?: throw IllegalArgumentException("Unable to read image file: ${imageFile.name}")
  }

  override fun getText(): String = ""

  override fun getPageCount(): Int = 1
  override fun getText(startPage: Int, endPage: Int): String {
    require(startPage == 0 && endPage == 1) { "Image files only have a single page (index 0), requested: $startPage to $endPage" }
    return ""
  }

  override fun renderImage(pageIndex: Int, dpi: Float): BufferedImage {
    require(pageIndex == 0) { "Image files only have a single page (index 0), requested: $pageIndex" }
    val src = loadImage()
    if (dpi == 72f) return src
    val scale = dpi / 72f
    val width = (src.width * scale).toInt()
    val height = (src.height * scale).toInt()
    val scaled = BufferedImage(width, height, src.type.takeIf { it != 0 } ?: BufferedImage.TYPE_INT_ARGB)
    val g = scaled.createGraphics()
    try {
      g.drawImage(src, 0, 0, width, height, null)
    } finally {
      g.dispose()
    }
    return scaled
  }

  override fun close() {
    image?.flush()
    image = null
  }
}