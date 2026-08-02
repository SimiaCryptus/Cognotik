# SEO Companion Page Generator

`generate-seo-pages.js` produces a static, crawler-friendly companion
site that mirrors the interactive labs and essays.

Unlike the main site (which renders Markdown in the browser with
`marked.js`), these pages render every README to HTML **at build time**,
so search engines see fully-formed content with no JavaScript required.

## What it generates

For each lab / experiment / essay (from `experiments.json`,
`essays.json`, falling back to `labs.json`):

- `companion/<slug>/index.html` — README-based landing page with full
  `<head>` SEO metadata (title, description, canonical, OpenGraph,
  Twitter cards) and `TechArticle` / `Article` + `BreadcrumbList`
  JSON-LD structured data.
- `companion/<slug>/video.html` — a dedicated video landing page with
  `VideoObject` structured data (only when the item declares a `video`).

Plus:

- `companion/index.html` — a hub linking to every generated page.
- `companion/sitemap.xml` — a sitemap of all generated URLs.

## Usage

```bash
npm install            # installs marked (build-time only)
npm run build:seo      # writes to ./companion
```

Options:

```bash
node scripts/generate-seo-pages.js \
  --out companion \
  --base https://math.cognotik.com \
  --site-name "Mathematical Explorations" \
  --author "SimiaCryptus"
```

| Flag          | Default                     | Description                      |
| ------------- | --------------------------- | -------------------------------- |
| `--out`       | `companion`                 | Output folder (relative to root) |
| `--base`      | `https://math.cognotik.com` | Canonical base URL               |
| `--root`      | `process.cwd()`             | Project root                     |
| `--site-name` | `Mathematical Explorations` | Used in titles / OG / JSON-LD    |
| `--author`    | `SimiaCryptus`              | `author` meta + JSON-LD author   |

## Notes

- Relative image / link URLs inside READMEs are rewritten so they still
  resolve from each generated page back to the lab's source folder.
- `mermaid` code blocks are preserved and rendered client-side via a
  CDN module, loaded **only** on pages that contain a diagram.
- The generated pages have **no runtime build dependencies** — just
  HTML, inline CSS, and (optionally) MathJax/Mermaid from CDNs.
- To enable LaTeX rendering on companion pages, you can add MathJax to
  `extraHead` the same way Mermaid is handled.
