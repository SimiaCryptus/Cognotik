---
task_type: AutoFix
folder: ../
specifies: ../build.log.md
---

Run `build.sh`

* The script compiles `paper.tex` into `paper.pdf`
* Capture the full engine output into `build.log.md`
* If compilation fails, fix `paper.tex` (missing packages, unescaped characters,
  broken environments, missing image files) and re-run until it succeeds
* Never remove content to make the build pass — repair the markup instead