---
task_type: FileModification
transforms: ../pipeline_design\.md -> ../generated_app/README.md
related:
  - ../idea.md
  - ../analysis.md
  - ../generated_app/ops/_files.json
---

Generate a comprehensive README.md for the DocOps application.

The README should follow the conventions established by existing DocOps apps and include:

## Required Sections

### Title and Description
* App name with an appropriate emoji
* One-paragraph description of what the app does

### Features
* Bullet list of key capabilities

### How It Works
* Step-by-step description of the pipeline
* ASCII art or description of the data flow DAG
* Description of each pipeline stage and what it produces

### Getting Started
1. How to open the app
2. What input to provide
3. How to run the pipeline
4. How to review results

### Pipeline Architecture
* Table of all op files with their purpose, task type, inputs, and outputs
* Description of the file naming conventions
* Directory structure tree

### File Reference
* Table of all files in the app with descriptions

### Iterative Use (if applicable)
* How to run multiple rounds
* How to refine results

### Disclaimer
* Appropriate caveats about AI-generated content
* Domain-specific warnings if applicable

Write in clear, concise markdown. Use tables, code blocks, and diagrams where they aid understanding.