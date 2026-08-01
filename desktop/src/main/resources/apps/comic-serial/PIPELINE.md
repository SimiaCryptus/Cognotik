# Pipeline

This document describes the transformation pipeline defined by the operations
in the `ops/` directory. Each operation declares, via front-matter, the file
pattern(s) it consumes and the file(s) it produces.

## Stages

### 1. Comic Generation (`ops/comic_op.md`)

* **Transforms:** `../idea.md` -> `../comic_1.md`
* **Task type:** `ComicBookGeneration`
* **Description:** Generates a comic representation of the source article
  (`idea.md`), producing the first comic in the series, `comic_1.md`.

### 2. Comic Sequel Generation (`ops/sequel_op.md`)

* **Transforms:** `../comic_(\d+)\.md` -> `../comic_$1+1.md`
* **Related:** `../idea.md`
* **Task type:** `ComicBookGeneration`
* **Description:** Given an existing comic `comic_N.md`, generates the next
  comic in the sequence, `comic_(N+1).md`, using the original article
  (`idea.md`) as additional context. This operation is recursive and can be
  applied repeatedly to extend the comic series indefinitely.

### 3. HTML Book Generation (`ops/html_book_op.md`)

* **Transforms:** `../comic_(\d+)\.comic\.json` -> `../comicbook.html`
* **Description:** Generates a self-contained HTML presentation of the
  comic book from the structured `comic_N.comic.json` data. All images are
  referenced (not embedded), captions are displayed as muted text below
  each image, and the page is styled to be visually appealing.

## Flow Summary

```
idea.md
  --(comic_op)--> comic_1.md
                     --(sequel_op)--> comic_2.md
                                          --(sequel_op)--> comic_3.md
                                                               --(sequel_op)--> ...

comic_N.comic.json --(html_book_op)--> comicbook.html
```

## Notes

* The `sequel_op` uses a regex-capture (`$1+1`) to compute the next index in
  the comic sequence, allowing the pipeline to chain indefinitely.
* The `html_book_op` consumes the JSON representation (`*.comic.json`) of a
  comic stage, rather than the markdown source, and produces the final
  presentation artifact (`comicbook.html`).
* All paths in the transforms are relative to the `ops/` directory.