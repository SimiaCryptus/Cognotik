---
template_vars:
  CHOICE: a
transforms:
  - ../story/([^./]+){{CHOICE}}\.md -> ../story/$1{{CHOICE}}.png
  - ../story/([^./]+)\.png -> ../story/$1{{CHOICE}}.png
related:
   - ../story/image_style.md
task_type: GenerateImage
---