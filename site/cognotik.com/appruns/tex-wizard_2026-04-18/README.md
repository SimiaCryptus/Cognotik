# TeX Wizard

A LaTeX document authoring and rendering pipeline.

## Overview

TeX Wizard provides a streamlined workflow for creating, rendering, and building LaTeX documents. It supports ERB-templated `.tex` files, PDF compilation, and automatic rebuild capabilities.

## Operations

### `render_tex`
Renders `doc.tex` from an ERB template (`simple.tex.erb`), incorporating content from `notes.md`.

### `update_tex`
Updates `doc.tex` based on the current `doc.pdf`, `notes.md`, and `update-notes.md`.

### `render_pdf`
Runs `build.sh` to compile `doc.tex` into a PDF. Build output is logged to `build.log.md`.

### `rebuild_tex`
Runs `build.sh` to rebuild the document. Output is logged to `rebuild.log.md`. Operates in AutoFix mode to automatically resolve build issues.

## Workflow

1. **Author** — Write or update content in `notes.md` and `update-notes.md`.
2. **Render TeX** — Generate `doc.tex` from the ERB template using `render_tex`.
3. **Build PDF** — Compile the document to PDF using `render_pdf` or `rebuild_tex`.

## Files

| File               | Description                          |
|--------------------|--------------------------------------|
| `doc.tex`          | The generated LaTeX document         |
| `doc.pdf`          | The compiled PDF output              |
| `notes.md`         | Source notes for document content    |
| `update-notes.md`  | Notes for document updates           |
| `build.sh`         | Shell script to compile LaTeX to PDF |
| `build.log.md`     | Build output log                     |
| `rebuild.log.md`   | Rebuild output log                   |
| `simple.tex.erb`   | ERB template for LaTeX generation    |