---
specifies: ../page.html
related:
  - ../content.md
  - ../instruct.md
task_type: WriteHtml
---

* Render `content.md` as a standalone, publish-ready web page
* Keep it self-contained: inline CSS (and JS, if any); no external build step,
  no framework CDN
* Mobile-first and responsive, with a comfortable reading measure on desktop
* Accessible: semantic landmarks (`header` / `main` / `nav` / `footer`), correct
  heading order, alt text on every image, sufficient colour contrast, visible focus styles
* Preserve every inline illustration reference from `content.md`, placed beside the
  passage it illustrates
* Convert markdown tables and fenced code faithfully (`table`, `pre` / `code`)
* `content.md` is the canonical prose — do not invent new content
* Emit only the HTML document — no commentary, no markdown fences