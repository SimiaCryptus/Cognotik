# Resume Customizer

An AI-powered resume tailoring application that customizes your resume for specific job opportunities using a multi-step pipeline.

## Overview

The Resume Customizer takes your master resume and tailors it to match a specific job description and company culture, then generates polished PDF documents in multiple formats.

## Features

- **AI-Powered Customization** – Analyzes job descriptions and tailors resume content accordingly
- **Company Research Integration** – Aligns language and emphasis with company values and tech stack
- **Multiple Output Formats** – Generates both Standard (two-column) and Simple (single-column) LaTeX/PDF layouts
- **Schema-Validated Output** – Ensures generated resume JSON conforms to a strict TypeScript schema
- **Interactive Web UI** – Browser-based interface for managing the full pipeline

## Pipeline Steps

1. **Analyze Job Requirements** – Extracts key skills, qualifications, and requirements from the job description
2. **Research Company** – Analyzes company culture, values, and technology stack
3. **Customize Resume** – Tailors resume content to match the job and company
4. **Generate Documents** – Renders LaTeX templates and compiles PDFs via `build.sh`

## File Structure

```
resume-customizer/
├── app.html                  # Web UI
├── app.js                    # Frontend logic
├── style.css                 # Styles
├── resume-general.json       # Master resume (source of truth)
├── resume-custom.json        # Generated tailored resume (output)
├── job_description.md        # Target job description (input)
├── company-info.md           # Company research notes (input)
├── simple.tex                # Generated simple-format LaTeX (output)
├── standard.tex              # Generated standard-format LaTeX (output)
├── build.sh                  # LaTeX compilation script
├── build.log.md              # Build output log
└── ops/
    ├── customize_resume.md   # Op: generate tailored resume JSON
    ├── render_simple.md      # Op: render simple LaTeX template
    ├── render_standard.md    # Op: render standard LaTeX template
    ├── render_tex.md         # Op: compile LaTeX to PDF
    ├── resume-schema.ts      # TypeScript schema for resume JSON
    ├── simple.tex.erb        # ERB template for simple format
    └── standard.tex.erb      # ERB template for standard format
```

## Usage

1. Open the web UI (`app.html`) in your browser
2. Paste the **job description** into the Job Description field
3. Add any **company research** (values, tech stack, culture notes)
4. Select your preferred **AI models** for smart and fast tasks
5. Click **Run Full Pipeline** to generate your customized resume
6. Download the resulting PDF in Standard or Simple format

## Resume Schema

The generated `resume-custom.json` conforms to the `Resume` TypeScript interface defined in `ops/resume-schema.ts`. Key sections include:

- `personal` – Contact info, name, title, social links
- `summary` – Executive summary (supports Markdown)
- `coreCompetencies` – Key skills list
- `experience` – Work history with highlights and technologies
- `skills` – Categorized technical skills
- `projects` – Open source or notable projects
- `education` – Academic background
- `publications` – Papers, articles, or other publications
- `metadata` – Version, target role, keywords

## Templates

Two LaTeX ERB templates are provided:

- **`simple.tex.erb`** – Clean single-column layout using the Libertine font
- **`standard.tex.erb`** – Professional two-column layout

Both templates support Markdown-formatted strings for rich text rendering in resume content.