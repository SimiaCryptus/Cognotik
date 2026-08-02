# Vendored web libraries

These files are **generated** – do not edit them by hand.

```sh
cd src/main/resources/web/lib
node download.js            # fetch missing files
node download.js --force    # refresh to the latest patch releases
node download.js --check    # verify checksums against manifest.json
```

`manifest.json` records the resolved version, size, SHA-256 and the SRI (`sha384-…`) hash of every file, so upgrades are
reviewable in a diff.

## Layout

```
lib/
  mermaid.min.js
  marked.min.js
  purify.min.js
  mathjax/tex-mml-chtml.js
  prism/prism.min.js
  prism/prism-tomorrow.min.css
  prism/components/prism-<lang>.min.js
  prism/plugins/prism-<plugin>.min.(js|css)
  manifest.json
```

## Including them

`node download.js --tags` prints a ready-to-paste snippet, e.g.

```html

<link rel="stylesheet" href="/lib/prism/prism-tomorrow.min.css">
<script src="/lib/prism/prism.min.js" defer></script>
<script src="/lib/marked.min.js" defer></script>
<script src="/lib/purify.min.js" defer></script>
<script src="/lib/mermaid.min.js" defer></script>
<script src="/lib/mathjax/tex-mml-chtml.js" defer></script>
```

## MathJax fonts

`tex-mml-chtml.js` lazily loads its CHTML web fonts. Either download them locally:

```sh
node download.js --fonts
```

…or point MathJax at the CDN before the script tag:

```html

<script>
    window.MathJax = {chtml: {fontURL: 'https://cdn.jsdelivr.net/npm/mathjax@3/es5/output/chtml/fonts/woff-v2'}};
</script>
```

## Upgrading a major version

Edit the `VERSIONS` map (and `PRISM_LANGUAGES` / `PRISM_PLUGINS` if needed) at the top of `download.js`, then run
`node download.js --force`.

```