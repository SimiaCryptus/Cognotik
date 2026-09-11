---
specifies: ../latex-notes.md
related:
  - ../paper.pdf
  - ../paper.tex
  - ../build.log.md
  - ../content.md
task_type: DOCUMENT_CRITIQUE
---

* Visually inspect the rendered `paper.pdf`, page by page
* Look for: overfull/underfull boxes, orphaned headings, broken tables, oversized or
  misplaced figures, bad page breaks, missing captions, inconsistent typography,
  unresolved references (`??`), and anything the article contains but the PDF omits
* Cross-check `build.log.md` for warnings that are visible in the output
* Rewrite **only** the `## OCR review` section of `latex-notes.md`
  - reproduce the `## Author notes` section **verbatim**, ahead of your section
  - if `## Author notes` is missing, emit an empty one
* Format your section as a prioritized checklist of concrete, actionable fixes:
  `- [p3] figure 2 overflows the margin — wrap in \begin{figure}[H] and set width=0.8\linewidth`
* Prefix each item with the page number it was observed on
* Say `- nothing to fix` when the rendering is clean — do not invent work