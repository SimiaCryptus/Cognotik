# OCR Import

AI-powered OCR (Optical Character Recognition) tool for extracting text from images and scanned documents.

## Overview

OCR Import uses AI vision models to convert images and scanned PDFs into structured Markdown text. It provides a simple
drag-and-drop interface for uploading documents and produces clean, editable text output.

## Features

- **Multi-format support** — PNG, JPG, JPEG, GIF, BMP, TIFF, WEBP, and PDF files
- **AI-powered extraction** — Uses configurable smart, fast, and image models for optimal results
- **Drag & drop upload** — Simple file upload with drag-and-drop or file browser
- **Batch processing** — Upload and process multiple files at once
- **Live progress monitoring** — Real-time status updates and processing logs
- **Markdown output** — Extracted text is formatted as clean Markdown
- **Raw/rendered toggle** — View results as rendered Markdown or raw text
- **In-browser editing** — Edit OCR results directly before saving
- **Copy & download** — Copy results to clipboard or download as `.md` file
- **Git integration** — Save and commit results with a custom commit message
- **Token usage tracking** — Monitor AI token consumption and estimated costs

## Workflow

1. **Configure Models** — Select the AI models to use for OCR processing (smart, fast, and image models)
2. **Upload Documents** — Drag and drop or browse for images/PDFs to process
3. **Run OCR** — Execute the AI-powered OCR pipeline on uploaded files
4. **Review Results** — View, edit, and verify the extracted text
5. **Save & Export** — Download results or commit them to the repository

## Supported File Formats

| Format | Extensions      |
|--------|-----------------|
| PNG    | `.png`          |
| JPEG   | `.jpg`, `.jpeg` |
| GIF    | `.gif`          |
| BMP    | `.bmp`          |
| TIFF   | `.tiff`         |
| WebP   | `.webp`         |
| PDF    | `.pdf`          |

## File Structure

```
ocr-import/
├── app.html          # Main application UI
├── app.js            # Application logic
├── style.css         # Styles
├── README.md         # This file
├── ops/
│   └── run_ocr.md    # OCR operation specification
├── input/            # Uploaded files (created at runtime)
└── output.md         # OCR results (created at runtime)
```

## Usage Notes

- For best results with scanned documents, use high-resolution images (300 DPI or higher)
- The smart model is used as the primary OCR engine; the fast model handles lighter tasks
- The image model is optional and used for image-specific processing
- Model selections are persisted in local storage across sessions
- If a previous OCR task is still running when the page loads, monitoring will automatically resume