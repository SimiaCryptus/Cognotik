# Comic Serial Generator

    Turns a single article / story idea into an ongoing comic book series, then compiles
    every episode into one self-contained HTML comicbook.

    ## Files

    ```
    comic-serial/
    ├── app.html   # entry point (Idea / Pipeline / Series / Models tabs)
    ├── app.js     # ES module, imports everything shared from /lib/app/
    ├── style.css  # tokens + all styling
    ├── ops/       # DocOp definitions
    └── README.md
    ```

    ## Pipeline

    | Step | Op                    | Input(s)                       | Output           |
    |------|-----------------------|--------------------------------|------------------|
    | 1    | `ops/comic_op.md`     | `idea.md`                      | `comic_1.md`     |
    | 2+   | `ops/sequel_op.md`    | `idea.md`, `comic_<n-1>.md`    | `comic_<n>.md`   |
    | 3    | `ops/html_book_op.md` | all `comic_*.md` / `comic_*.html` | `comicbook.html` |

    * **Idea** — `idea.md` auto-saves 800 ms after typing stops, and on explicit Save.
    * **Pipeline** — step 1, sequels, batch generation (first comic + N sequels + book).
    * **Series** — accordion of every episode, rendered HTML preferred over markdown.
    * **Models** — optional per-op overrides for smart / fast / image models.

    Episode count is derived from the filesystem (`comic_<n>.md` or `comic_<n>.html`),
    never from local state. Badges are restored from `docops.status.json` on load.

    ## Conformance

    Usage, Sessions, Git and Download UI are provided by the shared menubar
    (`initMenu()`); this app only renders **inline** per-step session links via
    `updateSessionLinks(target, info, getProxyUrl, containerId)`.

    | 3-file | Modern JS | Menubar | No dup. chrome | Viewport | Mobile |
    |:------:|:---------:|:-------:|:--------------:|:--------:|:------:|
    |   ✅   |    ✅     |   ✅    |       ✅       |    ✅    |   ✅   |

    No outstanding items.