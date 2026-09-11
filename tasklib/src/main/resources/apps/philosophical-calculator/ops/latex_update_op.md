---
specifies: ../paper.tex
related:
  - ../latex-notes.md
  - ../content.md
  - ../instruct.md
  - ../build.log.md
---

* Revise `paper.tex` in place
* `latex-notes.md` is the authority for this pass:
  - apply every item under `## Author notes` first
  - then apply every item under `## OCR review`
  - ignore items already satisfied by the current source
* Fix any error or warning reported in `build.log.md`
* `content.md` remains the canonical source of the prose — do not invent new content,
  only re-typeset, re-order or re-balance what is already there unless a note asks for more
* Keep the document compilable: `article` class with
  `\usepackage{graphicx,hyperref,booktabs,geometry}`, `figure`/`table` floats,
  `verbatim` for code, and `& % $ # _ { } ~ ^ \` escaped outside verbatim
* Preserve existing `\label`/`\ref` pairs and illustration paths
* Emit only the `.tex` source — no commentary, no markdown fences