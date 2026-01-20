# File Modification Task Transcript


## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
None

### File Context
# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/docs/DocReader.kt

```
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
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/docs/DocumentReader.kt

```
package com.simiacryptus.cognotik.docs

import java.awt.image.BufferedImage
import java.io.File

interface DocumentReader : AutoCloseable {
    fun getText(): String
}

interface PaginatedDocumentReader : DocumentReader {
    fun getPageCount(): Int
    fun getText(startPage: Int, endPage: Int): String

    override fun getText(): String = getText(0, getPageCount())
}

interface RenderableDocumentReader : DocumentReader {
    fun getPageCount(): Int
    fun renderImage(pageIndex: Int, dpi: Float): BufferedImage
}

fun File.getDocumentReader(): DocumentReader = when {
    this.name.endsWith(".pdf", ignoreCase = true) -> PDFReader(this)
    this.name.endsWith(".docx", ignoreCase = true) -> DocxReader(this)
    this.name.endsWith(".doc", ignoreCase = true) -> DocReader(this)
    this.name.endsWith(".xlsx", ignoreCase = true) -> XlsxReader(this)
    this.name.endsWith(".xls", ignoreCase = true) -> XlsReader(this)
    this.name.endsWith(".pptx", ignoreCase = true) -> PptxReader(this)
    this.name.endsWith(".ppt", ignoreCase = true) -> PptReader(this)
    this.name.endsWith(".odt", ignoreCase = true) -> OdtReader(this)
    this.name.endsWith(".rtf", ignoreCase = true) -> RtfReader(this)
    this.name.endsWith(".html", ignoreCase = true) -> HTMLReader(this)
    this.name.endsWith(".htm", ignoreCase = true) -> HTMLReader(this)
    this.name.endsWith(".eml", ignoreCase = true) -> EmlReader(this)
    else -> TextReader(this)
}

fun File.isDocumentFile(): Boolean {
    val supportedExtensions = listOf(
        ".pdf", ".docx", ".doc", ".xlsx", ".xls", ".pptx", ".ppt",
        ".odt", ".rtf", ".html", ".htm", ".eml", ".txt"
    )
    return supportedExtensions.any { this.name.endsWith(it, ignoreCase = true) }
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/docs/DocxReader.kt

```
package com.simiacryptus.cognotik.docs

import org.apache.poi.xwpf.usermodel.XWPFDocument

import java.io.File
import java.io.FileInputStream

class DocxReader(docxFile: File) : DocumentReader {
    private val document: XWPFDocument = XWPFDocument(FileInputStream(docxFile))

    override fun getText(): String {
        val text = StringBuilder()

        // Extract text from paragraphs
        document.paragraphs.forEach { paragraph ->
            text.append(paragraph.text)
            text.append("\n")
        }

        // Extract text from tables
        document.tables.forEach { table ->
            table.rows.forEach { row ->
                row.tableCells.forEach { cell ->
                    text.append(cell.text)
                    text.append("\t")
                }
                text.append("\n")
            }
        }

        return text.toString()
    }

    override fun close() {
        document.close()
    }
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/docs/EmlReader.kt

```
package com.simiacryptus.cognotik.docs

import jakarta.mail.BodyPart
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.*
import kotlin.io.path.createTempFile

class EmlReader(file: File) : DocumentReader {
    private var message: MimeMessage? = null
    private val tempFiles = mutableListOf<File>()

    init {
        FileInputStream(file).use { inputStream ->
            val props = Properties()
            val session = Session.getDefaultInstance(props, null)
            message = MimeMessage(session, inputStream)
        }
    }

    override fun getText(): String {
        val message = this.message ?: return ""
        val result = StringBuilder()
        // Add email headers
        result.appendLine("From: ${message.from?.joinToString(", ") ?: ""}")
        result.appendLine("To: ${message.getRecipients(Message.RecipientType.TO)?.joinToString(", ") ?: ""}")
        result.appendLine("CC: ${message.getRecipients(Message.RecipientType.CC)?.joinToString(", ") ?: ""}")
        result.appendLine("Subject: ${message.subject ?: ""}")
        result.appendLine("Date: ${message.sentDate ?: ""}")
        result.appendLine()
        result.appendLine("--- Message Body ---")
        result.appendLine()
        // Process message content
        processContent(message.content, result)
        return result.toString()
    }

    private fun processContent(content: Any?, result: StringBuilder) {
        when (content) {
            is String -> {
                result.appendLine(content)
            }

            is MimeMultipart -> {
                processMultipart(content, result)
            }

            is InputStream -> {
                result.appendLine(content.bufferedReader().use { it.readText() })
            }
        }
    }

    private fun processMultipart(multipart: MimeMultipart, result: StringBuilder) {
        for (i in 0 until multipart.count) {
            val bodyPart = multipart.getBodyPart(i)
            processPart(bodyPart, result)
        }
    }

    private fun processPart(part: BodyPart, result: StringBuilder) {
        val disposition = part.disposition
        val contentType = part.contentType.lowercase()
        when {
            disposition?.lowercase()?.contains("attachment") == true ||
                    disposition?.lowercase()?.contains("inline") == true -> {
                processAttachment(part, result)
            }

            contentType.contains("text/plain") || contentType.contains("text/html") -> {
                processContent(part.content, result)
            }

            contentType.contains("multipart") -> {
                processContent(part.content, result)
            }

            else -> {
                // Try to process as attachment if it has a filename
                if (part.fileName != null) {
                    processAttachment(part, result)
                }
            }
        }
    }

    private fun processAttachment(part: BodyPart, result: StringBuilder) {
        val fileName = part.fileName ?: "attachment"
        result.appendLine()
        result.appendLine("--- Attachment: $fileName ---")
        try {
            // Create a temporary file for the attachment
            val tempFile = createTempFile(
                prefix = "eml_attachment_",
                suffix = "_$fileName"
            ).toFile()
            tempFiles.add(tempFile)
            // Save attachment to temp file
            part.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            // Use the file extension to determine the appropriate reader
            val attachmentReader = tempFile.getDocumentReader()
            attachmentReader.use {
                val attachmentText = it.getText()
                result.appendLine(attachmentText)
            }
        } catch (e: Exception) {
            result.appendLine("Error reading attachment: ${e.message}")
        }
        result.appendLine("--- End Attachment: $fileName ---")
        result.appendLine()
    }

    override fun close() {
        // Clean up temporary files
        tempFiles.forEach { file ->
            try {
                file.delete()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
        tempFiles.clear()
    }
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/docs/HTMLReader.kt

```
package com.simiacryptus.cognotik.docs

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File

class HTMLReader(htmlFile: File) : PaginatedDocumentReader {
    private val document: Document = Jsoup.parse(htmlFile, "UTF-8")
    private val fullText: String = document.body().text()
    private val pages: List<String> by lazy { splitIntoPages(fullText) }
    private var settings: Settings? = null

    fun configure(settings: Settings) {
        this.settings = settings
    }

    override fun getText(): String {
        return if (settings?.addLineNumbers == true) {
            fullText.lines().mapIndexed { index, line ->
                "${(index + 1).toString().padStart(6)}: $line"
            }.joinToString("\n")
        } else fullText
    }


    override fun getPageCount(): Int = pages.size

    override fun getText(startPage: Int, endPage: Int): String {
        val text = pages.subList(startPage, endPage.coerceAtMost(pages.size)).joinToString("\n")
        return if (settings?.addLineNumbers == true) {
            text.lines().mapIndexed { index, line ->
                "${(index + 1).toString().padStart(6)}: $line"
            }.joinToString("\n")
        } else text
    }


    override fun close() {

    }

    private fun splitIntoPages(text: String, maxChars: Int = 16000): List<String> {
        if (text.length <= maxChars) return listOf(text)

        val paragraphs = text.split(Regex("\\n\\s*\\n"))

        val pages = mutableListOf<String>()
        var currentPage = StringBuilder()

        for (paragraph in paragraphs) {
            if (currentPage.length + paragraph.length > maxChars) {
                if (currentPage.isNotEmpty()) {
                    pages.add(currentPage.toString())
                    currentPage = StringBuilder()
                }

                if (paragraph.length > maxChars) {
                    val words = paragraph.split(" ")
                    var currentChunk = StringBuilder()

                    for (word in words) {
                        if (currentChunk.length + word.length > maxChars) {
                            pages.add(currentChunk.toString())
                            currentChunk = StringBuilder()
                        }
                        if (currentChunk.isNotEmpty()) currentChunk.append(" ")
                        currentChunk.append(word)
                    }
                    if (currentChunk.isNotEmpty()) {
                        currentPage.append(currentChunk)
                    }
                } else {
                    currentPage.append(paragraph)
                }
            } else {
                if (currentPage.isNotEmpty()) currentPage.append("\n\n")
                currentPage.append(paragraph)
            }
        }

        if (currentPage.isNotEmpty()) {
            pages.add(currentPage.toString())
        }

        return pages
    }
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/docs/OdtReader.kt

```
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
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/docs/PDFReader.kt

```
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
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/docs/PptReader.kt

```
package com.simiacryptus.cognotik.docs

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
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/docs/PptxReader.kt

```
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
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/docs/RtfReader.kt

```
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
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/docs/Settings.kt

```
package com.simiacryptus.cognotik.docs

data class Settings(
    val dpi: Float = 120f,
    val maxPages: Int = Int.MAX_VALUE,
    val outputFormat: String = "PNG",
    val fileInputs: List<String>? = null,
    val showImages: Boolean = true,
    val pagesPerBatch: Int = 1,
    val saveImageFiles: Boolean = false,
    val saveTextFiles: Boolean = false,
    val saveFinalJson: Boolean = true,
    val fastMode: Boolean = true,
    val addLineNumbers: Boolean = false
)
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/docs/TextReader.kt

```
package com.simiacryptus.cognotik.docs

import java.io.File
import java.lang.Math.log1p
import kotlin.math.ln1p

class TextReader(textFile: File) : PaginatedDocumentReader {
    private val fullText: String = textFile.readLines().joinToString("\n")
    private val pages: List<String> by lazy { splitIntoPages(fullText) }
    private var settings: Settings? = null

    fun configure(settings: Settings) {
        this.settings = settings
    }

    override fun getText(): String {
        return if (settings?.addLineNumbers == true) {
            fullText.lines().mapIndexed { index, line ->
                "${(index + 1).toString().padStart(6)}: $line"
            }.joinToString("\n")
        } else fullText
    }


    override fun getPageCount(): Int = pages.size

    override fun getText(startPage: Int, endPage: Int): String {
        val text = pages.subList(startPage, endPage.coerceAtMost(pages.size)).joinToString("\n")
        return if (settings?.addLineNumbers == true) {
            text.lines().mapIndexed { index, line ->
                "${(index + 1).toString().padStart(6)}: $line"
            }.joinToString("\n")
        } else text
    }


    override fun close() {

    }

    private fun splitIntoPages(text: String, maxChars: Int = 16000): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val lines = text.split("\n")
        if (lines.size <= 1) return listOf(text)
        val splitFitnesses = lines.indices.map { i ->
            val leftSize = lines.subList(0, i).map { it.length }.sum()
            val rightSize = lines.subList(i, lines.size).map { it.length }.sum()
            if (leftSize <= 0) return@map i to Double.MAX_VALUE
            if (rightSize <= 0) return@map i to Double.MAX_VALUE
            var fitness = -((leftSize.toDouble() / text.length) * ln1p(rightSize.toDouble() / text.length) +
                    (rightSize.toDouble() / text.length) * log1p(leftSize.toDouble() / text.length))
            if (lines[i].isEmpty()) fitness *= 2
            i to fitness
        }.toTypedArray().toMutableList()

        val bestSplitIndex = (splitFitnesses.minByOrNull { it.second }?.first ?: lines.size) / 2
        val leftText = lines.subList(0, bestSplitIndex).joinToString("\n")
        val rightText = lines.subList(bestSplitIndex, lines.size).joinToString("\n")
        return splitIntoPages(leftText, maxChars) + splitIntoPages(rightText, maxChars)
    }
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/docs/XlsReader.kt

```
package com.simiacryptus.cognotik.docs

import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.CellType
import java.io.File
import java.io.FileInputStream

class XlsReader(xlsFile: File) : DocumentReader {
    private val workbook: HSSFWorkbook = HSSFWorkbook(FileInputStream(xlsFile))
    override fun getText(): String {
        val text = StringBuilder()
        for (sheetIndex in 0 until workbook.numberOfSheets) {
            val sheet = workbook.getSheetAt(sheetIndex)
            text.append("Sheet: ${sheet.sheetName}\n")
            text.append("=".repeat(50))
            text.append("\n")
            sheet.forEach { row ->
                row.forEach { cell ->
                    val cellValue = when (cell.cellType) {
                        CellType.STRING -> cell.stringCellValue
                        CellType.NUMERIC -> cell.numericCellValue.toString()
                        CellType.BOOLEAN -> cell.booleanCellValue.toString()
                        CellType.FORMULA -> try {
                            cell.stringCellValue
                        } catch (e: Exception) {
                            cell.numericCellValue.toString()
                        }

                        else -> ""
                    }
                    text.append(cellValue)
                    text.append("\t")
                }
                text.append("\n")
            }
            text.append("\n")
        }
        return text.toString()
    }

    override fun close() {
        workbook.close()
    }
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/docs/XlsxReader.kt

```
package com.simiacryptus.cognotik.docs

import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream

class XlsxReader(xlsxFile: File) : DocumentReader {
    private val workbook: XSSFWorkbook = XSSFWorkbook(FileInputStream(xlsxFile))
    override fun getText(): String {
        val text = StringBuilder()
        for (sheetIndex in 0 until workbook.numberOfSheets) {
            val sheet = workbook.getSheetAt(sheetIndex)
            text.append("Sheet: ${sheet.sheetName}\n")
            text.append("=".repeat(50))
            text.append("\n")
            sheet.forEach { row ->
                row.forEach { cell ->
                    val cellValue = when (cell.cellType) {
                        CellType.STRING -> cell.stringCellValue
                        CellType.NUMERIC -> cell.numericCellValue.toString()
                        CellType.BOOLEAN -> cell.booleanCellValue.toString()
                        CellType.FORMULA -> try {
                            cell.stringCellValue
                        } catch (e: Exception) {
                            cell.numericCellValue.toString()
                        }

                        else -> ""
                    }
                    text.append(cellValue)
                    text.append("\t")
                }
                text.append("\n")
            }
            text.append("\n")
        }
        return text.toString()
    }

    override fun close() {
        workbook.close()
    }
}
```

# /home/andrew/code/Cognotik/docs/index_docs.md

```
---
transforms: ../(.+/src/main/kotlin/.+/)([^\./]+)\.kt -> ../$1/README.md
---


```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

Here is the updated README.md for the document reading module.

### core/src/main/kotlin/com/simiacryptus/cognotik/docs/README.md
```markdown
# Document Reading Module

This module provides a unified interface for extracting text and rendering images from a wide variety of document formats. It leverages several industry-standard libraries (Apache POI, PDFBox, Jsoup, etc.) to provide robust document processing capabilities.

## Features

- **Unified API**: Access different document types through a common `DocumentReader` interface.
- **Format Support**: Extensive support for office documents, PDFs, emails, and web formats.
- **Pagination**: Support for page-based text extraction for large documents.
- **Rendering**: Ability to render document pages (specifically PDFs) into images.
- **Recursive Processing**: The Email reader (`EmlReader`) automatically processes attachments using the appropriate readers.
- **Smart Text Splitting**: `TextReader` and `HTMLReader` include logic to split large files into logical "pages" for processing.

## Supported Formats

| Format | Extension | Reader Class | Features |
| :--- | :--- | :--- | :--- |
| PDF | `.pdf` | `PDFReader` | Text, Pagination, Rendering |
| Word | `.docx`, `.doc` | `DocxReader`, `DocReader` | Text |
| Excel | `.xlsx`, `.xls` | `XlsxReader`, `XlsReader` | Text (Sheet-aware) |
| PowerPoint | `.pptx`, `.ppt` | `PptxReader`, `PptReader` | Text (Slide-aware, Notes) |
| OpenDocument | `.odt` | `OdtReader` | Text |
| Rich Text | `.rtf` | `RtfReader` | Text |
| HTML | `.html`, `.htm` | `HTMLReader` | Text, Pagination |
| Email | `.eml` | `EmlReader` | Text, Headers, Attachments |
| Plain Text | `.txt` (default) | `TextReader` | Text, Pagination |

## Core Interfaces

### `DocumentReader`
The base interface for all readers. It extends `AutoCloseable`.
- `getText(): String`: Extracts the full text content of the document.

### `PaginatedDocumentReader`
Extends `DocumentReader` for formats that support or simulate pagination.
- `getPageCount(): Int`: Returns the total number of pages.
- `getText(startPage: Int, endPage: Int): String`: Extracts text from a specific range of pages.

### `RenderableDocumentReader`
Extends `DocumentReader` for formats that can be rendered as images.
- `renderImage(pageIndex: Int, dpi: Float): BufferedImage`: Renders a specific page to a `BufferedImage`.

## Usage

### Getting a Reader
The easiest way to obtain a reader is via the `File` extension function:

```kotlin
val file = File("document.pdf")
if (file.isDocumentFile()) {
    file.getDocumentReader().use { reader ->
        val text = reader.getText()
        println(text)
    }
}
```

### Handling Paginated Documents
```kotlin
val reader = file.getDocumentReader()
if (reader is PaginatedDocumentReader) {
    val pageCount = reader.getPageCount()
    val firstPageText = reader.getText(0, 1)
}
```

### Configuration
The `Settings` data class allows you to configure behavior for certain readers (like `HTMLReader` and `TextReader`), such as adding line numbers or setting rendering DPI.

```kotlin
val settings = Settings(addLineNumbers = true, dpi = 150f)
val reader = TextReader(file)
reader.configure(settings)
```

## Implementation Details

- **Apache POI**: Used for Microsoft Office formats (`.doc`, `.docx`, `.xls`, `.xlsx`, `.ppt`, `.pptx`).
- **PDFBox**: Used for PDF processing and rendering.
- **Jsoup**: Used for HTML parsing and text extraction.
- **Jakarta Mail**: Used for parsing `.eml` files.
- **ODF Toolkit**: Used for OpenDocument text files.
```

### Summary of Changes
- Created a comprehensive `README.md` for the `com.simiacryptus.cognotik.docs` package.
- Documented the primary interfaces (`DocumentReader`, `PaginatedDocumentReader`, `RenderableDocumentReader`).
- Provided a lookup table for supported file extensions and their corresponding implementation classes.
- Included code snippets for common usage patterns, including the use of extension functions and configuration settings.
- Listed the underlying third-party libraries used for different formats.
</details>

                - <a href='fileIndex/G-20260120-RPFC/core/src/main/kotlin/com/simiacryptus/cognotik/docs/README.md'>core/src/main/kotlin/com/simiacryptus/cognotik/docs/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/core/src/main/kotlin/com/simiacryptus/cognotik/docs/README.md'>core/src/main/kotlin/com/simiacryptus/cognotik/docs/README.md</a> Updated
