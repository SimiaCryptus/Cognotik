---
specifies: ../paper.tex
related: ../content.md
task_type: WriteLatex
---

* Convert the article into a complete, standalone LaTeX document
* Use the `article` document class with `\usepackage{graphicx,hyperref,booktabs,geometry}`
* Derive `\title`, `\author` and `\date` from the article's front matter or first heading
* Map markdown headings to `\section` / `\subsection` / `\subsubsection`
* Convert markdown tables to `tabular` inside `table` environments with `booktabs` rules
* Convert fenced code blocks to `verbatim`
* Preserve inline illustrations with `\includegraphics[width=\linewidth]{...}` wrapped in
  `figure` environments with captions
* Escape `& % $ # _ { } ~ ^ \` everywhere outside verbatim environments
* Emit only the `.tex` source — no commentary, no markdown fences