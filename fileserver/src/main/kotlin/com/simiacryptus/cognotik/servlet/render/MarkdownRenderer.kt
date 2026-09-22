package com.simiacryptus.cognotik.webui.servlet.render

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import com.vladsch.flexmark.ext.tables.TablesExtension.create
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.parser.Parser.EXTENSIONS
import com.vladsch.flexmark.util.data.MutableDataSet
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File

object MarkdownRenderer {
  private val log = LoggerFactory.getLogger(MarkdownRenderer::class.java)
  fun renderAsHtml(markdownContent: String, title: String): String {
    val options = MutableDataSet()
    options.set(EXTENSIONS, listOf(create()))
    val html = HtmlRenderer.builder(options).build().render(Parser.builder(options).build().parse(markdownContent))
    return """
<!DOCTYPE html>
<html lang="en" data-theme="auto">
<head>
<meta charset="UTF-8"></meta>
<meta name="viewport" content="width=device-width, initial-scale=1.0"></meta>
<title>$title</title>
<style>
:root {
   --bg-page: #ffffff;
   --text-primary: #1c1e21;
   --code-bg: #f4f4f4;
   --code-text: #1c1e21;
}
html[data-theme="dark"] {
   --bg-page: #1a1d21;
   --text-primary: #e4e6eb;
   --code-bg: #2d3035;
   --code-text: #e4e6eb;
}
@media (prefers-color-scheme: dark) {
   html[data-theme="auto"] {
     --bg-page: #1a1d21;
     --text-primary: #e4e6eb;
     --code-bg: #2d3035;
     --code-text: #e4e6eb;
   }
}
body { font-family: Arial, sans-serif; margin: 40px; background-color: var(--bg-page); color: var(--text-primary); }
pre { background-color: var(--code-bg); color: var(--code-text); padding: 10px; border-radius: 4px; }
code { background-color: var(--code-bg); color: var(--code-text); padding: 2px 4px; border-radius: 2px; }
</style>
<script src="/modules/theme.js"></script>
<script>
   if (typeof ThemeManager !== 'undefined') {
     ThemeManager.init();
   }
</script>
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