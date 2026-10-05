# Cognotik Theme System

This guide is for developers who are adapting a page or component of the Cognotik web UI. It covers:

1. How to wire a page up to the theme system, so the user's saved theme is restored from `localStorage` on load.
2. How to write CSS that follows the active theme.
3. How to add a theme selector, either through the shared Menubar or as a custom control.
4. How to add a new palette.

---

## 1. Concepts

The theme system stores two independent user settings.

| Setting     | Values                                                          | localStorage key   | URL override |
|-------------|-----------------------------------------------------------------|--------------------|--------------|
| **Mode**    | `auto`, `light`, `dark`                                         | `cognotik-theme`   | `?theme=`    |
| **Palette** | `point-cad` (Default), `sepia`, `solarized`, `nord`, `dracula`, `high-contrast` | `cognotik-palette` | `?palette=`  |

The system combines the palette and the mode into a single **variant**. Examples are `light`, `dark`, `sepia`, `sepiaDark`, `nordLight` and `highContrast`. The variant is written to `<html data-theme="…">`, and `themes.css` defines the colour tokens for each variant.

The files involved are:

| File                 | Role |
|----------------------|------|
| `/themes.css`        | Holds the colour tokens (`--color-*`) for every variant. Its `:root` block supplies the defaults, plus a `prefers-color-scheme: dark` fallback. |
| `/themes.js`         | The manifest. It registers the palettes, modes and tokens, and resolves variants. It exposes `window.CognotikThemes`. |
| `/modules/theme.js`  | The runtime manager. It reads and writes `localStorage`, applies attributes to `<html>`, and binds selectors. It exposes `window.ThemeManager`. |
| `/modules/menubar.js`| An optional shared top bar. Its "Appearance" popup contains the palette, theme and layout selectors. |
| `/style.css`         | The shared component styles. They consume the tokens and also define aliases such as `--color-primary`. |

### Attributes set on `<html>`

`ThemeManager` keeps the following attributes up to date. Your CSS and JS may read any of them.

| Attribute / property  | Example          | Meaning |
|-----------------------|------------------|---------|
| `data-theme`          | `solarizedDark`  | The resolved variant. It is **absent** when the Default palette is in `auto` mode, so the CSS media query decides. |
| `data-scheme`         | `light` / `dark` | The effective light or dark scheme. It is always present. **Use this for dark-mode tweaks.** |
| `data-mode`           | `auto`           | The mode the user requested. |
| `data-palette`        | `nord`           | The active palette id. |
| `style.colorScheme`   | `dark`           | Makes native controls (scrollbars, form fields) match the scheme. |

---

## 2. Enabling theming on a page

### 2.1 Minimal setup

Add the following to the page's `<head>`. Use **absolute paths** so the page works from any sub-directory.

```html
<head>
    <meta name="color-scheme" content="light dark">

    <!-- 1. Theme tokens. The id is required: ThemeManager manages this <link>. -->
    <link href="/themes.css" id="theme-stylesheet" rel="stylesheet">
    <!-- 2. Shared styles (optional), then your page styles. -->
    <link href="/style.css" rel="stylesheet">
    <link href="my-page.css" rel="stylesheet">

    <!-- 3. Manifest, then manager. The order matters. -->
    <script src="/themes.js"></script>
    <script src="/modules/theme.js"></script>
</head>
```

You do not need to call anything else. `modules/theme.js` calls `ThemeManager.init()` as soon as it loads. On load it:

1. Reads `?palette=` and `?theme=` from the URL. Valid values are persisted to `localStorage`.
2. Otherwise reads `cognotik-palette` and `cognotik-theme` from `localStorage`.
3. Otherwise falls back to the manifest defaults: palette `point-cad`, mode `auto`.
4. Applies `data-theme`, `data-scheme`, `data-mode` and `data-palette` to `<html>`.
5. Watches the OS `prefers-color-scheme` setting, and re-applies the theme when the mode is `auto`.

#### Rules

- **Load `themes.js` before `modules/theme.js`.** If the manifest is missing, ThemeManager logs a warning. It still handles light and dark, but palette switching is disabled.
- **Load the scripts in `<head>`, not at the end of `<body>`.** This avoids a flash of the wrong theme. The manager only touches `<html>` and `<head>`, so it is safe to run before the body exists. (`index.html` currently loads them at the end of the body. New pages should prefer `<head>`.)
- **Keep `id="theme-stylesheet"` on the themes `<link>`.** ThemeManager looks the link up by this id. If the link is missing, ThemeManager creates one and inserts it before the first stylesheet.

### 2.2 Iframe pages and secondary pages

Each document has its own `<html>` element. Every page that should be themed therefore needs the setup above. This includes pages shown in iframes, such as `/usage/`, `/apiKeys/` and `about.html`.

`localStorage` is shared per origin. A page opened in an iframe therefore picks up the current theme when it loads.

To follow changes **live** while the page stays open (for example, the user switches palette in the parent window), add a storage listener:

```html
<script>
    window.addEventListener('storage', function (e) {
        if (!window.ThemeManager || !e.newValue) return;
        if (e.key === ThemeManager.PALETTE_STORAGE_KEY) ThemeManager.setPalette(e.newValue);
        if (e.key === ThemeManager.STORAGE_KEY) ThemeManager.setTheme(e.newValue);
    });
</script>
```

Note that URL parameters take priority over `localStorage`. A page loaded with `?palette=nord` stays on Nord until the URL changes.

### 2.3 Behaviour without JavaScript

Without JS, `themes.css` still applies the Default palette. The `:root` block supplies light colours, and the `prefers-color-scheme: dark` block supplies dark colours. `style.css` also contains a zero-specificity `:where(:root)` fallback that applies if `themes.css` fails to load.

---

## 3. Writing theme-aware CSS

### 3.1 Use the tokens and never hard-code colours

Every variant defines these 18 custom properties. The canonical list is `CognotikThemes.tokens`.

| Token                                          | Use for |
|------------------------------------------------|---------|
| `--color-canvas`                               | The page background. |
| `--color-surface`, `--color-surface-alt`       | Cards, panels, modals, inputs, and alternate or nested surfaces. |
| `--color-border`, `--color-border-strong`      | Subtle dividers, and input or control borders. |
| `--color-text`, `--color-text-muted`, `--color-heading` | Body copy, secondary text, and headings. |
| `--color-brand`, `--color-brand-hover`         | Primary buttons, active states, and focus. |
| `--color-link`                                 | Links. |
| `--color-accent`                               | Secondary highlight, such as badges. |
| `--color-secondary`, `--color-secondary-hover` | Neutral or secondary buttons. |
| `--color-success`, `--color-warning`           | Status colours. |
| `--color-danger`, `--color-danger-hover`       | Status colours and destructive actions. |

`style.css` also defines these aliases. Either name works.

```css
--color-primary       -> --color-brand
--color-primary-hover -> --color-brand-hover
--color-bg            -> --color-canvas
--color-muted         -> --color-text-muted
```

An example component:

```css
.my-panel {
    background: var(--color-surface);
    color: var(--color-text);
    border: 1px solid var(--color-border);
}
.my-panel h3 { color: var(--color-heading); }
.my-panel .hint { color: var(--color-text-muted); }
.my-panel button.primary { background: var(--color-brand); color: #fff; }
.my-panel button.primary:hover { background: var(--color-brand-hover); }
```

For translucent variants, derive the colour from a token instead of writing an `rgba()` literal:

```css
.overlay { background: color-mix(in srgb, var(--color-surface) 90%, transparent); }
.focus   { box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-brand) 30%, transparent); }
```

Inline styles and JS-generated styles should also use tokens, with a fallback value:

```js
el.style.background = 'var(--color-surface, #fff)';
el.style.borderColor = 'var(--color-border, #ddd)';
```

### 3.2 Dark-mode tweaks: key on `data-scheme`

Sometimes a token is not enough, for example for shadow strength or image filters. In that case, target `html[data-scheme="dark"]`:

```css
html[data-scheme="dark"] .my-panel {
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.4);
}
html[data-scheme="dark"] .my-panel img.diagram {
    filter: invert(0.9) hue-rotate(180deg);
}
```

Avoid the following selectors for this purpose:

- **`[data-theme="dark"]`** only matches the Default dark variant. It misses `nord`, `dracula`, `sepiaDark`, `solarizedDark` and `highContrast`.
- **`@media (prefers-color-scheme: dark)`** follows the OS setting, not the user's choice. It fires even when the user has explicitly picked Light, and it does not fire when the user picks Dark on a light OS. Legacy blocks of this kind remain in `style.css`, but you should not add new ones.

To check the scheme in JS:

```js
var isDark = document.documentElement.getAttribute('data-scheme') === 'dark';
```

---

## 4. Adding a theme selector

### 4.1 Option A: use the shared Menubar (recommended)

The Menubar renders an "🎨 Appearance" button. Its popup contains the palette, theme (mode) and layout selectors, already bound to ThemeManager.

```html
<div id="menubar-container"></div>

<!-- After themes.js and modules/theme.js -->
<script src="/modules/menubar.js"></script>
<script>
    document.addEventListener('DOMContentLoaded', function () {
        Menubar.render('#menubar-container', {
            title: 'Cognotik',
            showThemeSelector: true,     // auto / light / dark
            showPaletteSelector: true,   // requires the themes.js manifest
            showLayoutSelector: false,   // only for app-grid style pages
            // appearanceMenu: false,    // render the selectors inline instead of in a popup
            buttons: [
                {id: 'my-btn', icon: '⚙️', label: 'Settings', onClick: function () { /* … */ }}
            ]
        });
    });
</script>
```

The Menubar options that relate to theming are:

| Option                | Default        | Effect |
|-----------------------|----------------|--------|
| `showThemeSelector`   | `true`         | Shows the mode selector (`#theme-selector`). |
| `showPaletteSelector` | `true`         | Shows the palette selector (`#palette-selector`). It is hidden automatically if the manifest is missing. |
| `appearanceMenu`      | `true`         | Groups the selectors in the popup. Set it to `false` to render them inline in the top bar. |
| `appearanceLabel`     | `Appearance`   | The button text. |
| `appearanceIcon`      | `🎨`           | The button icon. |
| `appearanceTitle`     | `Look & Feel`  | The popup heading and tooltip. |

### 4.2 Option B: build your own selector

If your UI has its own settings panel, create plain `<select>` elements and let ThemeManager bind them:

```html
<label>Palette <select id="my-palette" class="theme-selector"></select></label>
<label>Theme   <select id="my-mode"    class="theme-selector"></select></label>

<script>
    document.addEventListener('DOMContentLoaded', function () {
        ThemeManager.bindPaletteSelector(document.getElementById('my-palette'));
        ThemeManager.bindSelector(document.getElementById('my-mode'));
    });
</script>
```

The binding functions do the following:

- **Populate empty selects.** Palettes are taken from `CognotikThemes.themes`, and modes from `CognotikThemes.modes`. If the `<select>` already has `<option>`s, they are kept, so you can supply custom labels. The option values must still be the palette ids or mode ids.
- **Set the current value.**
- **Handle changes.** On `change`, they call `setPalette` or `setTheme`, which persists the value to `localStorage` and applies it.
- **Stay in sync** when the theme changes from elsewhere, for example from another selector or from code.
- **Disable single-variant palettes.** `bindSelector` disables the mode select while a single-variant palette is active. All current palettes have both variants, so this does not happen today.

Call each binding **once per element**. Every call adds new listeners. The `theme-selector` class gives the select the shared styling from `style.css`.

### 4.3 Option C: fully custom UI

Use this for swatches, toggle buttons and similar controls. Drive ThemeManager directly:

```js
// Read
ThemeManager.getCurrentPalette();   // 'nord'
ThemeManager.getCurrentTheme();     // 'auto' | 'light' | 'dark'

// Write: persists to localStorage and applies immediately
ThemeManager.setPalette('solarized');
ThemeManager.setTheme('dark');

// React to changes made anywhere on the page
ThemeManager.onPaletteChange(function (paletteId) { /* … */ });
ThemeManager.onChange(function (mode) { /* … */ });
```

Each manifest entry includes `preview` colours, which are useful for drawing swatches:

```js
var manifest = ThemeManager.getManifest();          // === window.CognotikThemes
manifest.themes.forEach(function (t) {
    // t.id, t.name, t.description, t.modes, t.variants
    // t.preview.light / t.preview.dark -> {canvas, surface, brand, accent}
    var swatch = document.createElement('button');
    swatch.title = t.description;
    swatch.textContent = t.name;
    swatch.style.background = t.preview.light.canvas;
    swatch.style.borderColor = t.preview.light.brand;
    swatch.onclick = function () { ThemeManager.setPalette(t.id); };
    container.appendChild(swatch);
});
```

### ThemeManager API reference

| Member                         | Description |
|--------------------------------|-------------|
| `init()`                       | Applies the stored or URL theme. It runs automatically on load, so you do not normally call it. |
| `getCurrentTheme()`            | Returns the current mode (URL > storage > default). |
| `setTheme(mode)`               | Validates, persists and applies a mode. |
| `bindSelector(select)`         | Binds a `<select>` to the mode. |
| `onChange(fn)`                 | Registers a mode change listener. |
| `getCurrentPalette()`          | Returns the current palette id (URL > storage > default). |
| `setPalette(id)`               | Validates, persists and applies a palette, then re-resolves the mode. |
| `bindPaletteSelector(select)`  | Binds a `<select>` to the palette. |
| `onPaletteChange(fn)`          | Registers a palette change listener. |
| `getManifest()`                | Returns `window.CognotikThemes`, or `null`. |
| `resolveAttribute(palette, mode)` | Returns the `data-theme` value a pair would produce, or `null`. |
| `STORAGE_KEY`, `PALETTE_STORAGE_KEY` | `'cognotik-theme'`, `'cognotik-palette'` |
| `URL_PARAM`, `PALETTE_URL_PARAM`     | `'theme'`, `'palette'` |
| `VALID_THEMES`                 | `['auto', 'light', 'dark']` |

### Linking to a page with a specific theme

Append the URL parameters. They are applied and persisted:

```
/index.html?palette=dracula&theme=dark
```

---

## 5. Adding a new palette

1. **Add the CSS.** In `themes.css`, add one `[data-theme="<variant>"]` block per variant, for example `ocean` and `oceanDark`. Each block must define **all 18 tokens**.
2. **Register the palette.** In `themes.js`, add an entry to `THEMES`:
   ```js
   {
       id: 'ocean',
       name: 'Ocean',
       description: 'Cool blues, light and dark variants.',
       stylesheet: STYLESHEET,
       modes: ['light', 'dark'],
       variants: {light: 'ocean', dark: 'oceanDark'},
       preview: {
           light: {canvas: '…', surface: '…', brand: '…', accent: '…'},
           dark:  {canvas: '…', surface: '…', brand: '…', accent: '…'}
       }
   }
   ```
3. **Mark the dark variant.** Add every dark variant id to `DARK_VARIANTS` in `themes.js`. If you skip this step, `data-scheme` will report `light`, and dark-mode tweaks will not apply.
4. **Check the console.** It warns if `modes` and `variants` do not match.

No other code changes are needed. The selectors populate themselves from the manifest.

---

## 6. Checklist for adapting a UI component

- [ ] `/themes.css` (with `id="theme-stylesheet"`), `/themes.js` and `/modules/theme.js` are included in `<head>`, in that order.
- [ ] Colours use `var(--color-*)` tokens. There are no hard-coded `#fff`, `#333` or `rgba(52,152,219,…)` values.
- [ ] Dark-only tweaks use `html[data-scheme="dark"]`, not `prefers-color-scheme` or `[data-theme="dark"]`.
- [ ] The theme selector comes either from `Menubar.render({...showThemeSelector, showPaletteSelector})` or from `ThemeManager.bind*Selector()` on your own `<select>`.
- [ ] Iframe pages include the theme scripts. Long-lived iframes add the `storage` listener.
- [ ] The page has been checked in at least one light palette, one dark palette, and High Contrast.