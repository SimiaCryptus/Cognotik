package com.simiacryptus.cognotik.webui.servlet.render

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil.markdownToHtml
import jakarta.servlet.http.HttpServletResponse
import java.io.ByteArrayOutputStream
import java.io.File

object MarkdownRenderer {
  private val log = LoggerFactory.getLogger(MarkdownRenderer::class.java)
  fun renderAsHtml(markdownContent: String, title: String): String {
    val html = markdownContent.markdownToHtml()
    return """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8"></meta>
<meta name="viewport" content="width=device-width, initial-scale=1.0"></meta>
<title>$title</title>
<style>
body { font-family: Arial, sans-serif; margin: 40px; }
pre { background-color: #f4f4f4; padding: 10px; border-radius: 4px; }
code { background-color: #f4f4f4; padding: 2px 4px; border-radius: 2px; }
</style>
</head>
<body>
$html
</body>
</html>
"""
  }

  fun renderAsPdf(html: String, baseUri: String): ByteArray {
    val outputStream = ByteArrayOutputStream()
    PdfRendererBuilder()
      .withHtmlContent(html, baseUri)
      .toStream(outputStream)
      .run()
    return outputStream.toByteArray()
  }

  fun renderMarkdown(mdFile: File, resp: HttpServletResponse, asPdf: Boolean) {
    try {
      val markdownContent = mdFile.readText()
      val fullHtml = renderAsHtml(markdownContent, mdFile.name)
      if (asPdf) {
        val baseUri = mdFile.parentFile.toURI().toString()
        val byteArray = renderAsPdf(fullHtml, baseUri)
        resp.contentType = "application/pdf"
        resp.status = HttpServletResponse.SC_OK
        resp.outputStream.write(byteArray)
      } else {
        resp.contentType = "text/html"
        resp.characterEncoding = "UTF-8"
        resp.status = HttpServletResponse.SC_OK
        resp.writer.write(fullHtml)
      }
    } catch (e: Exception) {
      log.error("Error rendering markdown file: ${mdFile.absolutePath}", e)
      resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      resp.writer.write("Error rendering markdown: ${e.message}")
    }
  }
}