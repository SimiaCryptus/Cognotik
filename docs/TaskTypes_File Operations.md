# File Operations

## IterativeImageDecomposition

Recursively analyze an image to find details, text, or specific objects.

Performs a deep-dive analysis of an image by:
<ul>
    <li>Identifying regions of interest based on a query</li>
    <li>Recursively cropping and re-analyzing those regions</li>
    <li>Stitching results into a hierarchical dataset</li>
</ul>
Useful for OCR on complex forms, crowd analysis, or finding small details.

#### Planner Prompt Segment

```text
IterativeImageDecomposition - Recursively analyzes images for fine details
* Use for: OCR on complex documents, finding small objects (Waldo), or crowd analysis.
* Inputs: Image file, query (what to look for).
* Outputs: A hierarchical JSON report and an annotated image.
* Mechanism: Recursively crops and re-prompts the vision model on regions of interest.
```

#### Default Execution Configuration

```json
{
  "task_type" : "IterativeImageDecomposition",
  "files" : null,
  "segmentation_query" : "Describe the contents of this image in detail",
  "analysis_query" : "Describe the contents of this image in detail",
  "dpi" : 150.0,
  "max_depth" : 2,
  "min_region_size" : 100,
  "output_file" : "analysis_result.json",
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "IterativeImageDecomposition",
  "task_description" : "Describe the contents of this image in detail",
  "related_files" : null,
  "extractContent" : false
}
```

#### Default Type Configuration

```json
{
  "task_type" : "IterativeImageDecomposition",
  "name" : "IterativeImageDecomposition",
  "model" : null
}
```

---

## OCRTask

Convert documents (PDF, Images) to Markdown text

Uses Vision models to extract text and formatting from documents.
<ul>
<li>Supports PDF and Image files</li>
<li>Converts to Markdown format</li>
<li>Preserves layout and structure where possible</li>
<li>Optionally extracts figures and metadata</li>
</ul>

#### Planner Prompt Segment

```text
OCR - Convert documents (PDF, Images) to Markdown text.
* Extracts text from images and PDFs using Vision models.
* Preserves formatting as Markdown.
* Optionally extracts figures as images and metadata/form fields.
* Saves output to a .md file with the same name.
```

#### Default Execution Configuration

```json
{
  "task_type" : "OCRTask",
  "files" : null,
  "dpi" : 150.0,
  "extract_figures" : false,
  "extract_metadata" : false,
  "extract_text" : false,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "OCRTask",
  "related_files" : null,
  "extractContent" : false
}
```

#### Default Type Configuration

```json
{
  "task_type" : "OCRTask",
  "name" : "OCRTask",
  "model" : null
}
```

---

