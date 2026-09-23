---
specifies: ../page.html
related:
  - ../webpage-notes.md
  - ../content.md
  - ../instruct.md
---

* Revise `page.html` in place
* `webpage-notes.md` is the authority for this pass:
  - apply every item under `## Author notes` first
  - then apply every item under `## OCR review`
* Keep the page standalone: inline CSS (and JS, if any), no external build step,
  no framework CDN unless a note explicitly asks for one
* Keep it responsive and accessible: sensible landmarks, heading order, alt text,
  colour contrast, and a mobile-first layout
* `content.md` remains the canonical source of the prose — do not invent new content
  unless a note asks for it
* Preserve existing illustration references
* Emit only the HTML document — no commentary, no markdown fences