# File and Content Tools

The `com.simiacryptus.cognotik.plan.tools.file` package provides a comprehensive suite of tools for interacting with the file system, analyzing documents, and generating rich media content using AI. These tasks are designed to be used within the Cognotik orchestration framework to automate complex workflows involving code, data, and visual assets.

## Core File Operations

These tasks handle fundamental file system interactions with AI-powered intelligence.

*   **[FileModificationTask](FileModificationTask.kt)**: The primary tool for code generation and refactoring. It creates or modifies files using AI-generated diffs, maintaining project standards and handling complex multi-file operations.
*   **[FileSearchTask](FileSearchTask.kt)**: Performs pattern-based searches (substring or regex) across project files, providing results with configurable context lines and organized by file.
*   **[FileAppendTask](FileAppendTask.kt)**: Allows for precise additions to the end of files without modifying existing content, ideal for logs, exports, or list updates.

## Document Analysis & Discussion

Tools for extracting knowledge and insights from existing project files and documents.

*   **[ReadDocumentsTask](ReadDocumentsTask.kt)** / **[DiscussionTask](DiscussionTask.kt)**: Deeply analyzes project files to provide technical insights, answer specific questions, or generate comprehensive reports and architectural reviews.
*   **[OCRTask](OCRTask.kt)**: Converts PDFs and images into Markdown text. It can also extract figures as separate images and capture form fields or metadata.
*   **[ImageDecompositionTask](ImageDecompositionTask.kt)**: Performs recursive, deep-dive analysis of images or document pages to find fine details, perform OCR on complex layouts, or identify specific objects.

## Data & Form Processing

Tasks focused on structured data extraction and document automation.

*   **[DataIngestTask](DataIngestTask.kt)**: An iterative tool that discovers Regex patterns to parse unstructured logs or text into structured formats like JSONL and CSV, including a search index.
*   **[PdfFormTask](PdfFormTask.kt)**: Automates the filling of PDF form templates using data extracted from the conversation context or provided configuration.

## Web & Presentation Generation

Tools for creating high-level content formats for communication and display.

*   **[WriteHtmlTask](WriteHtmlTask.kt)**: Generates complete, self-contained HTML5 documents with embedded CSS and JavaScript, supporting modern responsive design and optional AI-generated images.
*   **[GeneratePresentationTask](GeneratePresentationTask.kt)**: Creates professional Reveal.js presentations including speaker notes, custom styling, and optional AI-generated slide imagery.
*   **[IllustrateDocumentTask](IllustrateDocumentTask.kt)**: Intelligently analyzes Markdown or HTML documents to identify optimal locations for visual aids and generates/inserts contextually appropriate images.

## Image Generation & Artistic Tools

A variety of tasks for creating visual assets using generative AI models.

*   **[ImageGenerationTask](ImageGenerationTask.kt)**: Creates high-quality images from detailed text descriptions, utilizing reference files for style or context.
*   **[ImageTableTask](ImageTableTask.kt)**: Generates a grid of images based on a matrix of row and column labels, useful for style comparisons or product variations.
*   **[GenerateQRImageTask](GenerateQRImageTask.kt)**: Creates artistic, stylized QR codes that remain scannable, using AI to blend data with visual aesthetics.
*   **[GenerateSpriteSheetTask](GenerateSpriteSheetTask.kt)**: Generates game assets by creating a sprite sheet image and automatically extracting coordinate metadata into a JSON file.
*   **[ImageVariationTask](ImageVariationTask.kt)**: Decomposes an image to create "Find the Differences" style variations by applying specific visual changes to identified regions.

## Advanced High-Resolution Generation

Specialized tasks for creating ultra-high-resolution imagery through recursive refinement.

*   **[SegmentedImageGenerationTask](SegmentedImageGenerationTask.kt)**: Uses semantic segmentation to identify regions of interest and recursively upscales them to create complex, highly detailed scenes.
*   **[TiledImageGenerationTask](TiledImageGenerationTask.kt)**: Employs a recursive tiling strategy to generate images with extreme detail, suitable for large-format posters or detailed maps.

## Base Implementation

*   **[AbstractFileTask](AbstractFileTask.kt)**: The base class for file-oriented tasks, providing common utility functions for file selection, glob pattern matching, and content extraction from various file types (text, PDF, etc.).