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