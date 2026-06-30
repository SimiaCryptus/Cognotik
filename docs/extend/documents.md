---
documents: ../core/src/main/kotlin/com/simiacryptus/cognotik/docs/*
specifies: ../site/cognotik.com/doc_formats.html
---

# Document Reading Module

This module provides a unified interface for extracting text and rendering images from a wide variety of document
formats. It leverages several industry-standard libraries (Apache POI, PDFBox, Jsoup, etc.) to provide robust document
processing capabilities.

## Features

- **Unified API**: Access different document types through a common `DocumentReader` interface.
- **Format Support**: Extensive support for office documents, PDFs, emails, and web formats.
- **Pagination**: Support for page-based text extraction for large documents.
- **Rendering**: Ability to render document pages (specifically PDFs) into images.
- **Recursive Processing**: The Email reader (`EmlReader`) automatically processes attachments using the appropriate
  readers.
- **Smart Text Splitting**: `TextReader` and `HTMLReader` include logic to split large files into logical "pages" for
  processing.

## Supported Formats

| Format              | Extension        | Reader Class | Features                     |
|:--------------------|:-----------------|:-------------|:-----------------------------|
| PDF                 | `.pdf`           | `PDFReader`  | Text, Pagination, Rendering  |
| Word                | `.docx`          | `DocxReader` | Text (paragraphs and tables) |
| Word (Legacy)       | `.doc`           | `DocReader`  | Text                         |
| Excel               | `.xlsx`          | `XlsxReader` | Text (Sheet-aware)           |
| Excel (Legacy)      | `.xls`           | `XlsReader`  | Text (Sheet-aware)           |
| PowerPoint          | `.pptx`          | `PptxReader` | Text (Slide-aware, Notes)    |
| PowerPoint (Legacy) | `.ppt`           | `PptReader`  | Text (Slide-aware)           |
| OpenDocument        | `.odt`           | `OdtReader`  | Text                         |
| Rich Text           | `.rtf`           | `RtfReader`  | Text                         |
| HTML                | `.html`, `.htm`  | `HTMLReader` | Text, Pagination             |
| Email               | `.eml`           | `EmlReader`  | Text, Headers, Attachments   |
| Plain Text          | `.txt` (default) | `TextReader` | Text, Pagination             |

## Core Interfaces

### `DocumentReader`

The base interface for all readers. It extends `AutoCloseable` for proper resource management.

```kotlin
interface DocumentReader : AutoCloseable {
  fun getText(): String
}
```

### `PaginatedDocumentReader`

Extends `DocumentReader` for formats that support or simulate pagination.

```kotlin
interface PaginatedDocumentReader : DocumentReader {
  fun getPageCount(): Int
  fun getText(startPage: Int, endPage: Int): String
}
```

### `RenderableDocumentReader`

Extends `DocumentReader` for formats that can be rendered as images.

```kotlin
interface RenderableDocumentReader : DocumentReader {
  fun getPageCount(): Int
  fun renderImage(pageIndex: Int, dpi: Float): BufferedImage
}
```

## Usage

### Getting a Reader

The easiest way to obtain a reader is via the `File` extension function:

```kotlin
import com.simiacryptus.cognotik.docs.getDocumentReader
import com.simiacryptus.cognotik.docs.isDocumentFile
import java.io.File
val file = File("document.pdf")
if (file.isDocumentFile()) {
  file.getDocumentReader().use { reader ->
    val text = reader.getText()
    println(text)
  }
}
```

### Checking File Support

Use the `isDocumentFile()` extension function to check if a file is supported:

```kotlin
val file = File("example.docx")
if (file.isDocumentFile()) {
  // File format is supported
}
```

### Handling Paginated Documents

```kotlin
val reader = file.getDocumentReader()
if (reader is PaginatedDocumentReader) {
  val pageCount = reader.getPageCount()
  println("Document has $pageCount pages")
  // Get text from first page only
  val firstPageText = reader.getText(0, 1)
}
```

### Rendering PDF Pages

```kotlin
val reader = file.getDocumentReader()
if (reader is RenderableDocumentReader) {
  val pageCount = reader.getPageCount()
  for (i in 0 until pageCount) {
    val image = reader.renderImage(i, 150f) // 150 DPI
    // Process the BufferedImage
  }
}
```

### Configuration

The `Settings` data class allows you to configure behavior for certain readers (like `HTMLReader` and `TextReader`):

```kotlin
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

Example usage with `TextReader`:

```kotlin
val settings = Settings(addLineNumbers = true)
val reader = TextReader(file)
reader.configure(settings)
val textWithLineNumbers = reader.getText()
```

## Reader Details

### PDFReader

Implements both `PaginatedDocumentReader` and `RenderableDocumentReader`. Uses Apache PDFBox for text extraction and
image rendering.

- Supports page-range text extraction
- Renders pages to `BufferedImage` at configurable DPI
- Automatically registers ImageIO service providers for proper image format support

### DocxReader

Extracts text from Microsoft Word `.docx` files using Apache POI.

- Extracts text from paragraphs
- Extracts text from tables (tab-separated cells)

### DocReader

Extracts text from legacy Microsoft Word `.doc` files using Apache POI's HWPF library.

### XlsxReader / XlsReader

Extracts text from Excel spreadsheets.

- Processes all sheets in the workbook
- Handles STRING, NUMERIC, BOOLEAN, and FORMULA cell types
- Tab-separated cell values within rows

### PptxReader / PptReader

Extracts text from PowerPoint presentations.

- Extracts slide titles
- Extracts text from all text shapes
- `PptxReader` also extracts speaker notes

### HTMLReader

Parses HTML files using Jsoup.

- Implements `PaginatedDocumentReader` with smart page splitting
- Configurable line numbering via `Settings`
- Splits large documents into ~16KB pages at paragraph boundaries

### TextReader

Reads plain text files with pagination support.

- Implements `PaginatedDocumentReader`
- Uses entropy-based splitting algorithm to find optimal page breaks
- Prefers splitting at empty lines
- Configurable line numbering via `Settings`

### EmlReader

Parses email files using Jakarta Mail.

- Extracts email headers (From, To, CC, Subject, Date)
- Processes message body (text/plain and text/html)
- Recursively processes attachments using appropriate document readers
- Automatically cleans up temporary files on close

### OdtReader

Reads OpenDocument Text files using ODF Toolkit.

### RtfReader

Reads Rich Text Format files using Java's built-in RTF support (`RTFEditorKit`).

## Implementation Details

### Dependencies

- **Apache POI**: Microsoft Office formats (`.doc`, `.docx`, `.xls`, `.xlsx`, `.ppt`, `.pptx`)
- **Apache PDFBox**: PDF processing and rendering
- **Jsoup**: HTML parsing and text extraction
- **Jakarta Mail**: Email (`.eml`) file parsing
- **ODF Toolkit**: OpenDocument text files (`.odt`)
- **Java Swing**: RTF file support (built-in)

### Resource Management

All readers implement `AutoCloseable`. Always use `use` blocks or try-with-resources to ensure proper cleanup:

```kotlin
file.getDocumentReader().use { reader ->
  // Work with reader
} // Automatically closed
```

### Thread Safety

The `PDFReader` temporarily sets the thread's context class loader during image rendering to ensure proper ImageIO
service provider discovery. This is handled internally and transparent to users.
