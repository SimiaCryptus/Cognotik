# OCR Import Demo

A walkthrough of the AI-powered OCR import tool in the Cognotik app hub, demonstrating the full workflow from document
upload to extracted markdown output.

---

## Table of Contents

1. [Introduction](#introduction)
2. [Logging In](#logging-in)
3. [Launching the OCR Import Tool](#launching-the-ocr-import-tool)
4. [Configuring AI Models](#configuring-ai-models)
5. [Uploading Documents](#uploading-documents)
6. [Running the OCR Pipeline](#running-the-ocr-pipeline)
7. [Reviewing Extracted Text](#reviewing-extracted-text)
8. [Saving and Exporting Results](#saving-and-exporting-results)
9. [Summary](#summary)

---

## Introduction

`0:00:04`

Welcome to the OCR Import Demo. This tool uses AI-powered optical character recognition to extract text from images and
scanned documents. Let's walk through the full workflow.

---

## Logging In

`0:00:22`

We need to log in first to access the application.

> *(Login loading — time-lapsed)*

Logged in successfully.

---

## Launching the OCR Import Tool

`0:00:50`

Now let's find the OCR Import tool. This is the Cognotik app hub where all available tools are listed. Let's launch the
OCR Import application.

---

## Configuring AI Models

`0:01:13`

Here is the OCR Import interface. It has sections for:

- **ML Configuration** — select and configure AI models
- **File Upload** — add documents for processing
- **OCR Execution** — run the extraction pipeline
- **Results Review** — inspect and export extracted text

First, let's configure the AI models. We'll select:

- A **smart model** for primary OCR processing
- A **fast model** for lighter tasks
- An **image model** for image-specific analysis

The smart model is set — this will be the primary engine for text extraction.
The fast model is configured for lighter processing tasks.
The image model is ready for image-specific processing.

Models are now configured.

---

## Uploading Documents

`0:02:05`

Now let's upload some documents. The tool supports the following file formats:

- PNG, JPG, GIF, BMP, TIFF, WebP
- PDF

We can drag and drop files or use the file browser.

Files have been uploaded successfully. We can see them listed in the **Input Files** section.

---

## Running the OCR Pipeline

`0:02:31`

With files uploaded, we can now run the OCR pipeline. Let's click the **Run OCR** button to start processing.

The OCR process has started. We can monitor progress in real time through the processing log and session links.

> *(OCR processing — time-lapsed)*

The AI is analyzing the uploaded documents and extracting text content. This may take a moment depending on document
complexity.

OCR processing is complete.

---

## Reviewing Extracted Text

`0:03:11`

Let's review the extracted text. Here are the results — the extracted text is formatted as clean markdown. We can:

- Toggle between **rendered** and **raw** views
- **Copy** the text to clipboard
- **Download** the output file

Switching to raw view shows the underlying markdown source, which we can edit directly if needed.

And back to the rendered view for a nicely formatted preview of the extracted content.

---

## Saving and Exporting Results

`0:03:47`

Finally, we can save and export the results. Let's add a commit message and save the output to the repository.

> *(Saving and committing — time-lapsed)*

The results have been saved and committed. The extracted text is now stored as a markdown file in the project.

---

## Summary

`0:04:13`

And that's the complete OCR Import workflow:

1. Upload scanned documents or images
2. Configure AI models for your use case
3. Run the OCR pipeline with a single click
4. Review extracted text in rendered or raw markdown
5. Save and commit results directly to your repository

All powered by AI. Thank you for watching.

