# Cognotik Product Site Standards & Best Practices

## 1. Design Philosophy

### Core Principles

| Principle | Description |
|:----------|:------------|
| **Utility First** | Developers trust density, code snippets, and concrete data. Avoid empty spaces and abstract visuals. |
| **IDE-Native Aesthetic** | Dark mode, subtle borders, high information density. Think VS Code, Stripe Docs, or Linear. |
| **No-Scroll Goal** | Critical information (What is it? How do I configure it? What does it look like?) must be visible above the fold. |
| **Vaporware Antidote** | No abstract 3D spheres/cubes. Screenshots must be of the *actual* UI or terminal output. |

### Target Audience
- **Primary:** Developers and Software Architects
- **Secondary:** Technical Decision Makers, DevOps Engineers

---

## 2. Template Structure

### Standard HTML Skeleton

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">

    <!-- SEO Meta Tags -->
    <title>[Page Title] | Cognotik</title>
    <meta name="description" content="[Page description - 150-160 chars]">
    <meta name="keywords" content="AI coding, autonomous agents, [page-specific keywords]">

    <!-- Open Graph / Social -->
    <meta property="og:title" content="[Page Title] | Cognotik">
    <meta property="og:description" content="[Page description]">
    <meta property="og:image" content="https://cognotik.com/assets/images/og-image.png">
    <meta property="og:url" content="https://cognotik.com/[page-path]">
    <meta property="og:type" content="website">

    <!-- Twitter Card -->
    <meta name="twitter:card" content="summary_large_image">
    <meta name="twitter:title" content="[Page Title] | Cognotik">
    <meta name="twitter:description" content="[Page description]">
    <meta name="twitter:image" content="https://cognotik.com/assets/images/twitter-card.png">

    <!-- Favicon Suite -->
    <link rel="icon" type="image/svg+xml" href="/assets/icons/favicon.svg">
    <link rel="icon" type="image/png" sizes="32x32" href="/assets/icons/favicon-32x32.png">
    <link rel="icon" type="image/png" sizes="16x16" href="/assets/icons/favicon-16x16.png">
    <link rel="apple-touch-icon" sizes="180x180" href="/assets/icons/apple-touch-icon.png">
    <link rel="manifest" href="/site.webmanifest">
    <meta name="theme-color" content="#0f1115">

    <!-- Fonts: Inter for UI, JetBrains Mono for Code -->
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;600&family=JetBrains+Mono:wght@400;700&display=swap" rel="stylesheet">

    <!-- jQuery & FileTree -->
    <script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/jqueryfiletree@2.1.4/dist/jQueryFileTree.min.css">
    <script src="https://cdn.jsdelivr.net/npm/jqueryfiletree@2.1.4/dist/jQueryFileTree.min.js"></script>

    <link rel="stylesheet" href="assets/styles/main.css">
    <script type="module" src="assets/scripts/components/CognotikHeader.js"></script>

</head>
<body>
    <!-- Header Component -->
    <cognotik-header></cognotik-header>

    <!-- Main Content -->
    <main class="page-container">
        <!-- Page-specific content -->
    </main>

    <!-- Footer Component -->
    <cognotik-footer></cognotik-footer>

    <!-- Core Scripts -->
    <script type="module" src="/assets/scripts/core.js"></script>
    <script type="module" src="/assets/scripts/components/header.js"></script>
    <script type="module" src="/assets/scripts/components/footer.js"></script>

    <!-- Syntax Highlighting -->
    <script src="/assets/scripts/vendor/prism.js"></script>
</body>
</html>
```

---

## 3. Asset Organization

### Directory Structure

```
/assets/
├── icons/
│   ├── favicon.svg              # Primary favicon (SVG preferred)
│   ├── favicon-32x32.png
│   ├── favicon-16x16.png
│   ├── apple-touch-icon.png     # 180x180
│   ├── android-chrome-192x192.png
│   ├── android-chrome-512x512.png
│   └── ui/                      # UI icons (SVG sprites)
│       ├── icon-sprite.svg
│       ├── arrow-right.svg
│       ├── check.svg
│       └── ...
├── images/
│   ├── og-image.png             # 1200x630 for social sharing
│   ├── twitter-card.png         # 1200x600
│   ├── hero/                    # Hero section images
│   ├── screenshots/             # Product screenshots
│   ├── diagrams/                # Architecture diagrams
│   └── logos/
│       ├── cognotik-full.svg
│       ├── cognotik-mark.svg
│       └── cognotik-wordmark.svg
├── styles/
│   ├── core.css                 # Variables, reset, typography
│   ├── components.css           # Reusable component styles
│   ├── utilities.css            # Utility classes
│   ├── prism-cognotik.css       # Syntax highlighting theme
│   └── pages/                   # Page-specific styles
│       ├── home.css
│       ├── task-page.css
│       └── docs.css
├── scripts/
│   ├── core.js                  # Core utilities
│   ├── components/
│   │   ├── header.js
│   │   ├── footer.js
│   │   ├── tabs.js
│   │   ├── code-block.js
│   │   └── GithubFileTree.js
│   └── vendor/
│       ├── prism.js
│       └── mermaid.min.js
└── fonts/                       # Self-hosted fonts (if needed)
    └── JetBrainsMono/
```

### Favicon Requirements

| Asset | Size | Format | Purpose |
|:------|:-----|:-------|:--------|
| `favicon.svg` | Scalable | SVG | Modern browsers (preferred) |
| `favicon-32x32.png` | 32×32 | PNG | Standard favicon |
| `favicon-16x16.png` | 16×16 | PNG | Small favicon |
| `apple-touch-icon.png` | 180×180 | PNG | iOS home screen |
| `android-chrome-192x192.png` | 192×192 | PNG | Android home screen |
| `android-chrome-512x512.png` | 512×512 | PNG | Android splash screen |

### Web Manifest (`/site.webmanifest`)

```json
{
  "name": "Cognotik",
  "short_name": "Cognotik",
  "description": "Collaborative Intelligence for Software Development",
  "start_url": "/",
  "display": "standalone",
  "background_color": "#0f1115",
  "theme_color": "#58a6ff",
  "icons": [
    {
      "src": "/assets/icons/android-chrome-192x192.png",
      "sizes": "192x192",
      "type": "image/png"
    },
    {
      "src": "/assets/icons/android-chrome-512x512.png",
      "sizes": "512x512",
      "type": "image/png"
    }
  ]
}
```

---

## 4. CSS Design System

### CSS Variables (The "Industrial" Theme)

```css
:root {
    /* Background Colors */
    --bg-app: #0f1115;
    --bg-panel: #161b22;
    --bg-elevated: #1c2128;
    --bg-hover: #21262d;

    /* Border Colors */
    --border-subtle: #30363d;
    --border-default: #3d444d;
    --border-emphasis: #484f58;

    /* Accent Colors */
    --accent-primary: #58a6ff;
    --accent-secondary: #7ee787;
    --accent-warning: #d29922;
    --accent-danger: #f85149;
    --accent-purple: #a371f7;

    /* Text Colors */
    --text-primary: #e6edf3;
    --text-secondary: #8b949e;
    --text-tertiary: #6e7681;
    --text-link: #58a6ff;

    /* Typography */
    --font-display: 'Cinzel', serif;
    --font-body: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
    --font-mono: 'JetBrains Mono', 'Fira Code', monospace;

    /* Font Sizes */
    --text-xs: 0.75rem;
    --text-sm: 0.875rem;
    --text-base: 1rem;
    --text-lg: 1.125rem;
    --text-xl: 1.25rem;
    --text-2xl: 1.5rem;
    --text-3xl: 1.875rem;
    --text-4xl: 2.25rem;

    /* Spacing */
    --space-1: 0.25rem;
    --space-2: 0.5rem;
    --space-3: 0.75rem;
    --space-4: 1rem;
    --space-6: 1.5rem;
    --space-8: 2rem;
    --space-12: 3rem;
    --space-16: 4rem;

    /* Border Radius */
    --radius-sm: 4px;
    --radius-md: 6px;
    --radius-lg: 8px;
    --radius-xl: 12px;

    /* Shadows */
    --shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.3);
    --shadow-md: 0 4px 6px rgba(0, 0, 0, 0.4);
    --shadow-lg: 0 10px 15px rgba(0, 0, 0, 0.5);

    /* Transitions */
    --transition-fast: 150ms ease;
    --transition-base: 200ms ease;
    --transition-slow: 300ms ease;

    /* Z-Index Scale */
    --z-dropdown: 100;
    --z-sticky: 200;
    --z-modal: 300;
    --z-tooltip: 400;
}
```

### Base Reset & Typography

```css
/* Reset */
*, *::before, *::after {
    box-sizing: border-box;
    margin: 0;
    padding: 0;
}

html {
    font-size: 16px;
    scroll-behavior: smooth;
}

body {
    font-family: var(--font-body);
    font-size: var(--text-base);
    line-height: 1.6;
    color: var(--text-primary);
    background-color: var(--bg-app);
    -webkit-font-smoothing: antialiased;
    -moz-osx-font-smoothing: grayscale;
}

/* Typography Scale */
h1, h2, h3, h4, h5, h6 {
    font-family: var(--font-display);
    font-weight: 600;
    line-height: 1.3;
    color: var(--text-primary);
}

h1 { font-size: var(--text-4xl); }
h2 { font-size: var(--text-3xl); }
h3 { font-size: var(--text-2xl); }
h4 { font-size: var(--text-xl); }

code, pre, kbd {
    font-family: var(--font-mono);
}

a {
    color: var(--text-link);
    text-decoration: none;
    transition: color var(--transition-fast);
}

a:hover {
    text-decoration: underline;
}
```

---

## 5. Component Library

### Header Component

```html
<!-- Usage -->
<cognotik-header></cognotik-header>
```

```javascript
// /assets/scripts/components/header.js
class CognotikHeader extends HTMLElement {
    connectedCallback() {
        this.innerHTML = `
            <header class="site-header">
                <div class="header-container">
                    <a href="/" class="logo">
                        <img src="/assets/images/logos/cognotik-mark.svg" alt="Cognotik" width="32" height="32">
                        <span class="logo-text">Cognotik</span>
                    </a>
                    <nav class="main-nav">
                        <a href="/task-planning">Task Planning</a>
                        <a href="/intellij-plugin">IntelliJ Plugin</a>
                        <a href="/desktop">Desktop</a>
                        <a href="/docs">Docs</a>
                    </nav>
                    <div class="header-actions">
                        <a href="https://github.com/SimiaCryptus/Cognotik" class="btn btn-ghost">
                            <svg class="icon"><!-- GitHub icon --></svg>
                            GitHub
                        </a>
                    </div>
                </div>
            </header>
        `;
    }
}
customElements.define('cognotik-header', CognotikHeader);
```

### Panel Component

```html
<div class="panel">
    <div class="panel-header">
        <span class="panel-icon">⚙️</span>
        <span class="panel-title">Configuration</span>
    </div>
    <div class="panel-body">
        <!-- Content -->
    </div>
</div>
```

```css
.panel {
    background: var(--bg-panel);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-lg);
    overflow: hidden;
}

.panel-header {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    padding: var(--space-3) var(--space-4);
    background: var(--bg-elevated);
    border-bottom: 1px solid var(--border-subtle);
    font-family: var(--font-mono);
    font-size: var(--text-sm);
    color: var(--text-secondary);
}

.panel-body {
    padding: var(--space-4);
}
```

### Code Block Component

```html
<div class="code-block">
    <div class="code-header">
        <span class="code-lang">kotlin</span>
        <button class="copy-btn" data-copy-target="code-1">Copy</button>
    </div>
    <pre><code id="code-1" class="language-kotlin">
val config = OrchestrationConfig(
    mode = CognitiveMode.AdaptivePlanningMode,
    autoFix = true
)
    </code></pre>
</div>
```

### Tabs Component

```html
<div class="tabs" data-tabs>
    <div class="tabs-nav" role="tablist">
        <button class="tab-btn active" data-tab="docs" role="tab" aria-selected="true">
            Documentation
        </button>
        <button class="tab-btn" data-tab="config" role="tab" aria-selected="false">
            Configuration
        </button>
        <button class="tab-btn" data-tab="integration" role="tab" aria-selected="false">
            Integration
        </button>
    </div>
    <div class="tabs-content">
        <div class="tab-panel active" data-tab-panel="docs" role="tabpanel">
            <!-- Documentation content -->
        </div>
        <div class="tab-panel" data-tab-panel="config" role="tabpanel" hidden>
            <!-- Configuration content -->
        </div>
        <div class="tab-panel" data-tab-panel="integration" role="tabpanel" hidden>
            <!-- Integration content -->
        </div>
    </div>
</div>
```

### Badge Component

```html
<span class="badge badge-primary">Core</span>
<span class="badge badge-success">Stable</span>
<span class="badge badge-warning">Beta</span>
<span class="badge badge-danger">Destructive</span>
```

```css
.badge {
    display: inline-flex;
    align-items: center;
    padding: var(--space-1) var(--space-2);
    font-size: var(--text-xs);
    font-weight: 500;
    border-radius: var(--radius-sm);
    text-transform: uppercase;
    letter-spacing: 0.05em;
}

.badge-primary {
    background: rgba(88, 166, 255, 0.15);
    color: var(--accent-primary);
}

.badge-success {
    background: rgba(126, 231, 135, 0.15);
    color: var(--accent-secondary);
}

.badge-warning {
    background: rgba(210, 153, 34, 0.15);
    color: var(--accent-warning);
}

.badge-danger {
    background: rgba(248, 81, 73, 0.15);
    color: var(--accent-danger);
}
```

---

## 6. Page Types & Templates

### A. Landing Page (Home)

**Purpose:** Convert visitors, explain value proposition

**Sections:**
1. Hero with tagline and primary CTA
2. Feature grid (3-4 key capabilities)
3. "Reality Check" demo (Input → Output visualization)
4. Social proof (GitHub stars, testimonials)
5. Getting started guide
6. Footer with links

### B. Product Page (Task/Mode)

**Purpose:** Technical documentation with proof of functionality

**Sections:**
1. Compact header with breadcrumbs and badges
2. Reality Check (Config → Output split view)
3. Tabbed interface (Docs, Config, Integration)
4. Live showcase (file tree, generated artifacts)

### C. Documentation Page

**Purpose:** Reference material, API docs

**Sections:**
1. Sidebar navigation
2. Content area with anchored headings
3. On-page table of contents
4. Code examples with copy buttons

### D. Download/Install Page

**Purpose:** Get users started quickly

**Sections:**
1. Platform selector (Windows/macOS/Linux)
2. Download buttons with version info
3. Installation instructions
4. Quick start guide
5. Troubleshooting FAQ

---

## 7. Accessibility Standards

### Requirements

| Requirement | Implementation |
|:------------|:---------------|
| **Color Contrast** | Minimum 4.5:1 for body text, 3:1 for large text |
| **Keyboard Navigation** | All interactive elements focusable, visible focus states |
| **Screen Readers** | Semantic HTML, ARIA labels where needed |
| **Motion** | Respect `prefers-reduced-motion` |
| **Focus Indicators** | Visible, high-contrast focus rings |

### Focus States

```css
:focus-visible {
    outline: 2px solid var(--accent-primary);
    outline-offset: 2px;
}

/* Remove default outline when using mouse */
:focus:not(:focus-visible) {
    outline: none;
}
```

### Reduced Motion

```css
@media (prefers-reduced-motion: reduce) {
    *, *::before, *::after {
        animation-duration: 0.01ms !important;
        animation-iteration-count: 1 !important;
        transition-duration: 0.01ms !important;
    }
}
```

---

## 8. Performance Guidelines

### Image Optimization

| Format | Use Case |
|:-------|:---------|
| **SVG** | Icons, logos, diagrams |
| **WebP** | Photos, screenshots (with PNG fallback) |
| **PNG** | Screenshots requiring transparency |

### Loading Strategy

```html
<!-- Critical CSS inline in <head> -->
<style>
    /* Above-the-fold styles */
</style>

<!-- Defer non-critical CSS -->
<link rel="preload" href="/assets/styles/components.css" as="style" onload="this.onload=null;this.rel='stylesheet'">

<!-- Lazy load images below fold -->
<img src="placeholder.svg" data-src="actual-image.webp" loading="lazy" alt="Description">

<!-- Defer non-critical scripts -->
<script defer src="/assets/scripts/analytics.js"></script>
```

### Performance Budget

| Metric | Target |
|:-------|:-------|
| First Contentful Paint | < 1.5s |
| Largest Contentful Paint | < 2.5s |
| Total Blocking Time | < 200ms |
| Cumulative Layout Shift | < 0.1 |
| Total Page Weight | < 500KB (initial load) |

---

## 9. SEO Checklist

### Per-Page Requirements

- [ ] Unique `<title>` tag (50-60 characters)
- [ ] Meta description (150-160 characters)
- [ ] Canonical URL
- [ ] Open Graph tags
- [ ] Twitter Card tags
- [ ] Structured data (JSON-LD) where applicable
- [ ] Semantic heading hierarchy (single H1)
- [ ] Alt text for all images
- [ ] Internal linking to related pages

### Structured Data Example

```html
<script type="application/ld+json">
{
    "@context": "https://schema.org",
    "@type": "SoftwareApplication",
    "name": "Cognotik",
    "applicationCategory": "DeveloperApplication",
    "operatingSystem": "Windows, macOS, Linux",
    "offers": {
        "@type": "Offer",
        "price": "0",
        "priceCurrency": "USD"
    },
    "aggregateRating": {
        "@type": "AggregateRating",
        "ratingValue": "4.8",
        "ratingCount": "150"
    }
}
</script>
```

---

## 10. Quality Checklist

Before publishing any page, verify:

### Content
- [ ] No placeholder text ("Lorem ipsum")
- [ ] All links functional
- [ ] Code examples tested and working
- [ ] Screenshots current and accurate

### Technical
- [ ] Valid HTML (W3C validator)
- [ ] No console errors
- [ ] Responsive at all breakpoints (320px - 1920px)
- [ ] Print stylesheet (if applicable)

### Accessibility
- [ ] Keyboard navigation works
- [ ] Screen reader tested
- [ ] Color contrast passes
- [ ] Focus states visible

### Performance
- [ ] Images optimized
- [ ] No render-blocking resources
- [ ] Lighthouse score > 90

### SEO
- [ ] Meta tags complete
- [ ] Structured data valid
- [ ] Sitemap updated

---

## 11. Version Control & Deployment

### Branch Strategy

| Branch | Purpose |
|:-------|:--------|
| `main` | Production-ready code |
| `develop` | Integration branch |
| `feature/*` | New features |
| `hotfix/*` | Production fixes |

### Commit Message Format

```
type(scope): description

[optional body]

[optional footer]
```

**Types:** `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `chore`

**Example:**
```
feat(task-page): add live file tree showcase component

- Integrates GithubFileTree.js for browsing workspace artifacts
- Adds lazy loading for performance
- Includes fallback for API rate limits

Closes #123
```
