---
specifies: ../site/cognotik.com/*.html
related: ../site/cognotik.com/assets/styles/main.css
---

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

## 12. CSS Classes Reference

### Layout Classes

#### Container Classes

| Class | Description | Usage |
|:------|:------------|:------|
| `.page-container` | Main content wrapper with max-width and padding | Wrap main page content |
| `.task-container` | Task-specific container with min-height | Task/mode pages |
| `.header-container` | Flexbox container for header elements | Inside `<header>` |
| `.content-grid` | CSS Grid layout for content sections | Multi-column layouts |
| `.sidebar-layout` | Two-column layout with sidebar | Documentation pages |

```css
/* Container Classes */
.page-container {
    max-width: 1200px;
    margin: 0 auto;
    padding: var(--space-8) var(--space-4);
}

.task-container {
    max-width: var(--container-max-width);
    margin: 0 auto;
    padding: var(--space-8) var(--space-4);
    min-height: calc(100vh - var(--header-height));
}

.content-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
    gap: var(--space-6);
}

.sidebar-layout {
    display: grid;
    grid-template-columns: 280px 1fr;
    gap: var(--space-8);
}

@media (max-width: 768px) {
    .sidebar-layout {
        grid-template-columns: 1fr;
    }
}
```

#### Flexbox Utilities

| Class | Description |
|:------|:------------|
| `.flex` | `display: flex` |
| `.flex-col` | `flex-direction: column` |
| `.flex-wrap` | `flex-wrap: wrap` |
| `.items-center` | `align-items: center` |
| `.items-start` | `align-items: flex-start` |
| `.items-end` | `align-items: flex-end` |
| `.justify-center` | `justify-content: center` |
| `.justify-between` | `justify-content: space-between` |
| `.justify-end` | `justify-content: flex-end` |
| `.gap-1` through `.gap-8` | Gap spacing using scale |

```css
/* Flexbox Utilities */
.flex { display: flex; }
.flex-col { flex-direction: column; }
.flex-wrap { flex-wrap: wrap; }
.items-center { align-items: center; }
.items-start { align-items: flex-start; }
.items-end { align-items: flex-end; }
.justify-center { justify-content: center; }
.justify-between { justify-content: space-between; }
.justify-end { justify-content: flex-end; }

.gap-1 { gap: var(--space-1); }
.gap-2 { gap: var(--space-2); }
.gap-3 { gap: var(--space-3); }
.gap-4 { gap: var(--space-4); }
.gap-6 { gap: var(--space-6); }
.gap-8 { gap: var(--space-8); }
```

---

### Typography Classes

#### Heading Classes

| Class | Size | Font | Usage |
|:------|:-----|:-----|:------|
| `.text-display` | 3rem | Cinzel | Hero headlines |
| `.text-title` | 2.25rem | Cinzel | Page titles |
| `.text-heading` | 1.875rem | Cinzel | Section headings |
| `.text-subheading` | 1.5rem | Cinzel | Subsection headings |
| `.text-body-lg` | 1.125rem | Inter | Lead paragraphs |
| `.text-body` | 1rem | Inter | Body text |
| `.text-body-sm` | 0.875rem | Inter | Secondary text |
| `.text-caption` | 0.75rem | Inter | Captions, labels |
| `.text-mono` | 0.9rem | JetBrains Mono | Code, technical |

```css
/* Typography Classes */
.text-display {
    font-family: var(--font-display);
    font-size: 3rem;
    font-weight: 700;
    line-height: 1.1;
    letter-spacing: -0.02em;
}

.text-title {
    font-family: var(--font-display);
    font-size: var(--text-4xl);
    font-weight: 600;
    line-height: 1.2;
}

.text-heading {
    font-family: var(--font-display);
    font-size: var(--text-3xl);
    font-weight: 600;
    line-height: 1.3;
}

.text-subheading {
    font-family: var(--font-display);
    font-size: var(--text-2xl);
    font-weight: 600;
    line-height: 1.3;
}

.text-body-lg {
    font-size: var(--text-lg);
    line-height: 1.7;
}

.text-body {
    font-size: var(--text-base);
    line-height: 1.6;
}

.text-body-sm {
    font-size: var(--text-sm);
    line-height: 1.5;
}

.text-caption {
    font-size: var(--text-xs);
    line-height: 1.4;
    text-transform: uppercase;
    letter-spacing: 0.05em;
}

.text-mono {
    font-family: var(--font-mono);
    font-size: 0.9em;
}
```

#### Text Color Classes

| Class | Color Variable | Usage |
|:------|:---------------|:------|
| `.text-primary` | `--text-primary` | Primary content |
| `.text-secondary` | `--text-secondary` | Secondary content |
| `.text-tertiary` | `--text-tertiary` | Muted content |
| `.text-accent` | `--accent-primary` | Links, highlights |
| `.text-success` | `--accent-secondary` | Success states |
| `.text-warning` | `--accent-warning` | Warning states |
| `.text-danger` | `--accent-danger` | Error states |

```css
/* Text Color Classes */
.text-primary { color: var(--text-primary); }
.text-secondary { color: var(--text-secondary); }
.text-tertiary { color: var(--text-tertiary); }
.text-accent { color: var(--accent-primary); }
.text-success { color: var(--accent-secondary); }
.text-warning { color: var(--accent-warning); }
.text-danger { color: var(--accent-danger); }
```

#### Text Alignment & Decoration

| Class | Property |
|:------|:---------|
| `.text-left` | `text-align: left` |
| `.text-center` | `text-align: center` |
| `.text-right` | `text-align: right` |
| `.font-normal` | `font-weight: 400` |
| `.font-medium` | `font-weight: 500` |
| `.font-semibold` | `font-weight: 600` |
| `.font-bold` | `font-weight: 700` |
| `.uppercase` | `text-transform: uppercase` |
| `.capitalize` | `text-transform: capitalize` |
| `.no-wrap` | `white-space: nowrap` |
| `.truncate` | Ellipsis overflow |

```css
/* Text Utilities */
.text-left { text-align: left; }
.text-center { text-align: center; }
.text-right { text-align: right; }

.font-normal { font-weight: 400; }
.font-medium { font-weight: 500; }
.font-semibold { font-weight: 600; }
.font-bold { font-weight: 700; }

.uppercase { text-transform: uppercase; }
.capitalize { text-transform: capitalize; }
.no-wrap { white-space: nowrap; }

.truncate {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}
```

---

### Spacing Classes

#### Margin Classes

| Pattern | Description | Example |
|:--------|:------------|:--------|
| `.m-{size}` | All sides | `.m-4` |
| `.mx-{size}` | Horizontal (left + right) | `.mx-auto` |
| `.my-{size}` | Vertical (top + bottom) | `.my-8` |
| `.mt-{size}` | Top only | `.mt-4` |
| `.mr-{size}` | Right only | `.mr-2` |
| `.mb-{size}` | Bottom only | `.mb-6` |
| `.ml-{size}` | Left only | `.ml-4` |

**Size Scale:** `0`, `1`, `2`, `3`, `4`, `6`, `8`, `12`, `16`, `auto`

```css
/* Margin Classes */
.m-0 { margin: 0; }
.m-1 { margin: var(--space-1); }
.m-2 { margin: var(--space-2); }
.m-3 { margin: var(--space-3); }
.m-4 { margin: var(--space-4); }
.m-6 { margin: var(--space-6); }
.m-8 { margin: var(--space-8); }

.mx-auto { margin-left: auto; margin-right: auto; }
.mx-4 { margin-left: var(--space-4); margin-right: var(--space-4); }

.my-4 { margin-top: var(--space-4); margin-bottom: var(--space-4); }
.my-8 { margin-top: var(--space-8); margin-bottom: var(--space-8); }

.mt-0 { margin-top: 0; }
.mt-4 { margin-top: var(--space-4); }
.mt-8 { margin-top: var(--space-8); }

.mb-0 { margin-bottom: 0; }
.mb-4 { margin-bottom: var(--space-4); }
.mb-8 { margin-bottom: var(--space-8); }

.ml-auto { margin-left: auto; }
.mr-auto { margin-right: auto; }
```

#### Padding Classes

| Pattern | Description | Example |
|:--------|:------------|:--------|
| `.p-{size}` | All sides | `.p-4` |
| `.px-{size}` | Horizontal | `.px-6` |
| `.py-{size}` | Vertical | `.py-8` |
| `.pt-{size}` | Top only | `.pt-4` |
| `.pr-{size}` | Right only | `.pr-2` |
| `.pb-{size}` | Bottom only | `.pb-6` |
| `.pl-{size}` | Left only | `.pl-4` |

```css
/* Padding Classes */
.p-0 { padding: 0; }
.p-1 { padding: var(--space-1); }
.p-2 { padding: var(--space-2); }
.p-3 { padding: var(--space-3); }
.p-4 { padding: var(--space-4); }
.p-6 { padding: var(--space-6); }
.p-8 { padding: var(--space-8); }

.px-4 { padding-left: var(--space-4); padding-right: var(--space-4); }
.px-6 { padding-left: var(--space-6); padding-right: var(--space-6); }

.py-4 { padding-top: var(--space-4); padding-bottom: var(--space-4); }
.py-8 { padding-top: var(--space-8); padding-bottom: var(--space-8); }

.pt-4 { padding-top: var(--space-4); }
.pb-4 { padding-bottom: var(--space-4); }
.pl-4 { padding-left: var(--space-4); }
.pr-4 { padding-right: var(--space-4); }
```

---

### Component Classes

#### Button Classes

| Class | Description | Visual |
|:------|:------------|:-------|
| `.btn` | Base button styles | Required base class |
| `.btn-primary` | Primary action button | Blue background |
| `.btn-secondary` | Secondary action | Outlined style |
| `.btn-ghost` | Minimal button | Transparent background |
| `.btn-danger` | Destructive action | Red styling |
| `.btn-sm` | Small size | Reduced padding |
| `.btn-lg` | Large size | Increased padding |
| `.btn-icon` | Icon-only button | Square, centered |
| `.btn-block` | Full width | `width: 100%` |

```css
/* Button Base */
.btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: var(--space-2);
    padding: var(--space-2) var(--space-4);
    font-family: var(--font-body);
    font-size: var(--text-sm);
    font-weight: 500;
    line-height: 1.5;
    text-decoration: none;
    border: 1px solid transparent;
    border-radius: var(--radius-md);
    cursor: pointer;
    transition: all var(--transition-fast);
}

.btn:disabled {
    opacity: 0.5;
    cursor: not-allowed;
}

/* Button Variants */
.btn-primary {
    background: var(--accent-primary);
    color: var(--bg-app);
    border-color: var(--accent-primary);
}

.btn-primary:hover:not(:disabled) {
    background: #79b8ff;
    border-color: #79b8ff;
}

.btn-secondary {
    background: transparent;
    color: var(--text-primary);
    border-color: var(--border-default);
}

.btn-secondary:hover:not(:disabled) {
    background: var(--bg-hover);
    border-color: var(--border-emphasis);
}

.btn-ghost {
    background: transparent;
    color: var(--text-secondary);
    border-color: transparent;
}

.btn-ghost:hover:not(:disabled) {
    background: var(--bg-hover);
    color: var(--text-primary);
}

.btn-danger {
    background: var(--accent-danger);
    color: white;
    border-color: var(--accent-danger);
}

.btn-danger:hover:not(:disabled) {
    background: #ff6b6b;
    border-color: #ff6b6b;
}

/* Button Sizes */
.btn-sm {
    padding: var(--space-1) var(--space-3);
    font-size: var(--text-xs);
}

.btn-lg {
    padding: var(--space-3) var(--space-6);
    font-size: var(--text-base);
}

.btn-icon {
    padding: var(--space-2);
    aspect-ratio: 1;
}

.btn-block {
    width: 100%;
}
```

#### Card Classes

| Class | Description |
|:------|:------------|
| `.card` | Base card container |
| `.card-header` | Card header section |
| `.card-body` | Card content area |
| `.card-footer` | Card footer section |
| `.card-hover` | Adds hover effect |
| `.card-bordered` | Adds visible border |
| `.card-elevated` | Adds shadow elevation |

```css
/* Card Classes */
.card {
    background: var(--bg-panel);
    border-radius: var(--radius-lg);
    overflow: hidden;
}

.card-bordered {
    border: 1px solid var(--border-subtle);
}

.card-elevated {
    box-shadow: var(--shadow-md);
}

.card-hover {
    transition: transform var(--transition-fast), box-shadow var(--transition-fast);
}

.card-hover:hover {
    transform: translateY(-2px);
    box-shadow: var(--shadow-lg);
}

.card-header {
    padding: var(--space-4);
    border-bottom: 1px solid var(--border-subtle);
    background: var(--bg-elevated);
}

.card-body {
    padding: var(--space-4);
}

.card-footer {
    padding: var(--space-4);
    border-top: 1px solid var(--border-subtle);
    background: var(--bg-elevated);
}
```

#### Panel Classes

| Class | Description |
|:------|:------------|
| `.panel` | Base panel container |
| `.panel-header` | Panel header with icon/title |
| `.panel-body` | Panel content area |
| `.panel-footer` | Panel footer section |
| `.panel-collapsible` | Collapsible panel |
| `.panel-collapsed` | Collapsed state |

```css
/* Panel Classes */
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

.panel-icon {
    font-size: 1.2em;
}

.panel-title {
    font-weight: 500;
    color: var(--text-primary);
}

.panel-body {
    padding: var(--space-4);
}

.panel-footer {
    padding: var(--space-3) var(--space-4);
    border-top: 1px solid var(--border-subtle);
    background: var(--bg-elevated);
}

/* Collapsible Panel */
.panel-collapsible .panel-header {
    cursor: pointer;
    user-select: none;
}

.panel-collapsible .panel-header::after {
    content: '▼';
    margin-left: auto;
    font-size: 0.75em;
    transition: transform var(--transition-fast);
}

.panel-collapsed .panel-header::after {
    transform: rotate(-90deg);
}

.panel-collapsed .panel-body {
    display: none;
}
```

#### Form Classes

| Class | Description |
|:------|:------------|
| `.form-group` | Form field wrapper |
| `.form-label` | Field label |
| `.form-input` | Text input styling |
| `.form-select` | Select dropdown |
| `.form-textarea` | Textarea styling |
| `.form-checkbox` | Checkbox wrapper |
| `.form-radio` | Radio button wrapper |
| `.form-hint` | Help text below field |
| `.form-error` | Error message |
| `.input-error` | Error state for inputs |

```css
/* Form Classes */
.form-group {
    margin-bottom: var(--space-4);
}

.form-label {
    display: block;
    margin-bottom: var(--space-2);
    font-size: var(--text-sm);
    font-weight: 500;
    color: var(--text-primary);
}

.form-input,
.form-select,
.form-textarea {
    display: block;
    width: 100%;
    padding: var(--space-2) var(--space-3);
    font-family: var(--font-body);
    font-size: var(--text-base);
    line-height: 1.5;
    color: var(--text-primary);
    background: var(--bg-elevated);
    border: 1px solid var(--border-default);
    border-radius: var(--radius-md);
    transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
}

.form-input:focus,
.form-select:focus,
.form-textarea:focus {
    outline: none;
    border-color: var(--accent-primary);
    box-shadow: 0 0 0 3px rgba(88, 166, 255, 0.15);
}

.form-input::placeholder {
    color: var(--text-tertiary);
}

.form-textarea {
    min-height: 120px;
    resize: vertical;
}

.form-hint {
    margin-top: var(--space-1);
    font-size: var(--text-sm);
    color: var(--text-tertiary);
}

.form-error {
    margin-top: var(--space-1);
    font-size: var(--text-sm);
    color: var(--accent-danger);
}

.input-error {
    border-color: var(--accent-danger);
}

.input-error:focus {
    box-shadow: 0 0 0 3px rgba(248, 81, 73, 0.15);
}

/* Checkbox & Radio */
.form-checkbox,
.form-radio {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    cursor: pointer;
}

.form-checkbox input,
.form-radio input {
    width: 18px;
    height: 18px;
    accent-color: var(--accent-primary);
}
```

---

### Code Block Classes

| Class | Description |
|:------|:------------|
| `.code-block` | Code block container |
| `.code-header` | Header with language/copy button |
| `.code-lang` | Language label |
| `.copy-btn` | Copy to clipboard button |
| `.code-inline` | Inline code styling |
| `.code-highlight` | Highlighted line |

```css
/* Code Block Classes */
.code-block {
    background: var(--bg-elevated);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-lg);
    overflow: hidden;
    margin-bottom: var(--space-4);
}

.code-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: var(--space-2) var(--space-4);
    background: var(--bg-panel);
    border-bottom: 1px solid var(--border-subtle);
}

.code-lang {
    font-family: var(--font-mono);
    font-size: var(--text-xs);
    color: var(--text-tertiary);
    text-transform: uppercase;
    letter-spacing: 0.05em;
}

.copy-btn {
    padding: var(--space-1) var(--space-2);
    font-size: var(--text-xs);
    color: var(--text-secondary);
    background: transparent;
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-sm);
    cursor: pointer;
    transition: all var(--transition-fast);
}

.copy-btn:hover {
    color: var(--text-primary);
    background: var(--bg-hover);
    border-color: var(--border-default);
}

.copy-btn.copied {
    color: var(--accent-secondary);
    border-color: var(--accent-secondary);
}

.code-block pre {
    margin: 0;
    padding: var(--space-4);
    overflow-x: auto;
    background: transparent;
    border: none;
    border-radius: 0;
}

.code-block code {
    font-family: var(--font-mono);
    font-size: var(--text-sm);
    line-height: 1.6;
    background: transparent;
    padding: 0;
}

.code-inline {
    font-family: var(--font-mono);
    font-size: 0.9em;
    padding: 0.15em 0.4em;
    background: var(--bg-elevated);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-sm);
    color: var(--accent-primary);
}

.code-highlight {
    background: rgba(88, 166, 255, 0.1);
    display: block;
    margin: 0 calc(var(--space-4) * -1);
    padding: 0 var(--space-4);
    border-left: 3px solid var(--accent-primary);
}
```

---

### Tab Classes

| Class | Description |
|:------|:------------|
| `.tabs` | Tab container |
| `.tabs-nav` | Tab navigation wrapper |
| `.tab-btn` | Individual tab button |
| `.tab-btn.active` | Active tab state |
| `.tabs-content` | Content panels wrapper |
| `.tab-panel` | Individual content panel |
| `.tab-panel.active` | Visible panel |

```css
/* Tab Classes */
.tabs {
    background: var(--bg-panel);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-lg);
    overflow: hidden;
}

.tabs-nav {
    display: flex;
    background: var(--bg-elevated);
    border-bottom: 1px solid var(--border-subtle);
    overflow-x: auto;
}

.tab-btn {
    flex: 0 0 auto;
    padding: var(--space-3) var(--space-4);
    font-family: var(--font-body);
    font-size: var(--text-sm);
    font-weight: 500;
    color: var(--text-secondary);
    background: transparent;
    border: none;
    border-bottom: 2px solid transparent;
    cursor: pointer;
    transition: all var(--transition-fast);
    white-space: nowrap;
}

.tab-btn:hover {
    color: var(--text-primary);
    background: var(--bg-hover);
}

.tab-btn.active {
    color: var(--accent-primary);
    border-bottom-color: var(--accent-primary);
}

.tabs-content {
    padding: var(--space-4);
}

.tab-panel {
    display: none;
}

.tab-panel.active {
    display: block;
    animation: fadeIn var(--transition-fast);
}

@keyframes fadeIn {
    from { opacity: 0; }
    to { opacity: 1; }
}
```

---

### Badge & Tag Classes

| Class | Description |
|:------|:------------|
| `.badge` | Base badge styling |
| `.badge-primary` | Primary/info badge |
| `.badge-success` | Success/stable badge |
| `.badge-warning` | Warning/beta badge |
| `.badge-danger` | Danger/error badge |
| `.badge-purple` | Purple accent badge |
| `.tag` | Removable tag |
| `.tag-group` | Group of tags |

```css
/* Badge Classes */
.badge {
    display: inline-flex;
    align-items: center;
    gap: var(--space-1);
    padding: var(--space-1) var(--space-2);
    font-size: var(--text-xs);
    font-weight: 500;
    line-height: 1;
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

.badge-purple {
    background: rgba(163, 113, 247, 0.15);
    color: var(--accent-purple);
}

/* Tag Classes */
.tag {
    display: inline-flex;
    align-items: center;
    gap: var(--space-1);
    padding: var(--space-1) var(--space-2);
    font-size: var(--text-sm);
    background: var(--bg-elevated);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-md);
    color: var(--text-secondary);
}

.tag-remove {
    display: inline-flex;
    padding: 2px;
    margin-left: var(--space-1);
    color: var(--text-tertiary);
    cursor: pointer;
    border-radius: 50%;
    transition: all var(--transition-fast);
}

.tag-remove:hover {
    color: var(--accent-danger);
    background: rgba(248, 81, 73, 0.15);
}

.tag-group {
    display: flex;
    flex-wrap: wrap;
    gap: var(--space-2);
}
```

---

### Utility Classes

#### Display Classes

| Class | Property |
|:------|:---------|
| `.hidden` | `display: none` |
| `.block` | `display: block` |
| `.inline` | `display: inline` |
| `.inline-block` | `display: inline-block` |
| `.flex` | `display: flex` |
| `.inline-flex` | `display: inline-flex` |
| `.grid` | `display: grid` |

#### Visibility Classes

| Class | Description |
|:------|:------------|
| `.visible` | `visibility: visible` |
| `.invisible` | `visibility: hidden` |
| `.sr-only` | Screen reader only |

```css
/* Display Utilities */
.hidden { display: none !important; }
.block { display: block; }
.inline { display: inline; }
.inline-block { display: inline-block; }
.flex { display: flex; }
.inline-flex { display: inline-flex; }
.grid { display: grid; }

/* Visibility Utilities */
.visible { visibility: visible; }
.invisible { visibility: hidden; }

.sr-only {
    position: absolute;
    width: 1px;
    height: 1px;
    padding: 0;
    margin: -1px;
    overflow: hidden;
    clip: rect(0, 0, 0, 0);
    white-space: nowrap;
    border: 0;
}
```

#### Position Classes

| Class | Property |
|:------|:---------|
| `.relative` | `position: relative` |
| `.absolute` | `position: absolute` |
| `.fixed` | `position: fixed` |
| `.sticky` | `position: sticky` |
| `.inset-0` | All sides 0 |
| `.top-0` | `top: 0` |
| `.right-0` | `right: 0` |
| `.bottom-0` | `bottom: 0` |
| `.left-0` | `left: 0` |

```css
/* Position Utilities */
.relative { position: relative; }
.absolute { position: absolute; }
.fixed { position: fixed; }
.sticky { position: sticky; top: 0; }

.inset-0 { top: 0; right: 0; bottom: 0; left: 0; }
.top-0 { top: 0; }
.right-0 { right: 0; }
.bottom-0 { bottom: 0; }
.left-0 { left: 0; }
```

#### Border Classes

| Class | Description |
|:------|:------------|
| `.border` | Default border |
| `.border-0` | No border |
| `.border-t` | Top border only |
| `.border-r` | Right border only |
| `.border-b` | Bottom border only |
| `.border-l` | Left border only |
| `.rounded` | Default radius |
| `.rounded-sm` | Small radius |
| `.rounded-lg` | Large radius |
| `.rounded-full` | Fully rounded |
| `.rounded-none` | No radius |

```css
/* Border Utilities */
.border { border: 1px solid var(--border-default); }
.border-0 { border: none; }
.border-t { border-top: 1px solid var(--border-default); }
.border-r { border-right: 1px solid var(--border-default); }
.border-b { border-bottom: 1px solid var(--border-default); }
.border-l { border-left: 1px solid var(--border-default); }

.border-subtle { border-color: var(--border-subtle); }
.border-emphasis { border-color: var(--border-emphasis); }
.border-accent { border-color: var(--accent-primary); }

.rounded { border-radius: var(--radius-md); }
.rounded-sm { border-radius: var(--radius-sm); }
.rounded-lg { border-radius: var(--radius-lg); }
.rounded-xl { border-radius: var(--radius-xl); }
.rounded-full { border-radius: 9999px; }
.rounded-none { border-radius: 0; }
```

#### Background Classes

| Class | Color |
|:------|:------|
| `.bg-app` | `--bg-app` |
| `.bg-panel` | `--bg-panel` |
| `.bg-elevated` | `--bg-elevated` |
| `.bg-hover` | `--bg-hover` |
| `.bg-accent` | `--accent-primary` |
| `.bg-success` | `--accent-secondary` |
| `.bg-warning` | `--accent-warning` |
| `.bg-danger` | `--accent-danger` |

```css
/* Background Utilities */
.bg-app { background-color: var(--bg-app); }
.bg-panel { background-color: var(--bg-panel); }
.bg-elevated { background-color: var(--bg-elevated); }
.bg-hover { background-color: var(--bg-hover); }
.bg-transparent { background-color: transparent; }

.bg-accent { background-color: var(--accent-primary); }
.bg-success { background-color: var(--accent-secondary); }
.bg-warning { background-color: var(--accent-warning); }
.bg-danger { background-color: var(--accent-danger); }
```

#### Shadow Classes

| Class | Shadow |
|:------|:-------|
| `.shadow-none` | No shadow |
| `.shadow-sm` | Small shadow |
| `.shadow` | Default shadow |
| `.shadow-md` | Medium shadow |
| `.shadow-lg` | Large shadow |

```css
/* Shadow Utilities */
.shadow-none { box-shadow: none; }
.shadow-sm { box-shadow: var(--shadow-sm); }
.shadow { box-shadow: var(--shadow-md); }
.shadow-md { box-shadow: var(--shadow-md); }
.shadow-lg { box-shadow: var(--shadow-lg); }
```

#### Overflow Classes

| Class | Property |
|:------|:---------|
| `.overflow-auto` | `overflow: auto` |
| `.overflow-hidden` | `overflow: hidden` |
| `.overflow-visible` | `overflow: visible` |
| `.overflow-scroll` | `overflow: scroll` |
| `.overflow-x-auto` | `overflow-x: auto` |
| `.overflow-y-auto` | `overflow-y: auto` |

```css
/* Overflow Utilities */
.overflow-auto { overflow: auto; }
.overflow-hidden { overflow: hidden; }
.overflow-visible { overflow: visible; }
.overflow-scroll { overflow: scroll; }
.overflow-x-auto { overflow-x: auto; }
.overflow-y-auto { overflow-y: auto; }
```

---

### Responsive Utilities

#### Breakpoints

| Breakpoint | Min Width | Prefix |
|:-----------|:----------|:-------|
| Mobile | 0px | (default) |
| Tablet | 640px | `sm:` |
| Laptop | 768px | `md:` |
| Desktop | 1024px | `lg:` |
| Wide | 1280px | `xl:` |

```css
/* Responsive Display */
@media (max-width: 767px) {
    .md\:hidden { display: none !important; }
}

@media (min-width: 768px) {
    .md\:block { display: block; }
    .md\:flex { display: flex; }
    .md\:grid { display: grid; }
    .hidden-md { display: none !important; }
}

@media (min-width: 1024px) {
    .lg\:block { display: block; }
    .lg\:flex { display: flex; }
    .lg\:grid { display: grid; }
    .hidden-lg { display: none !important; }
}

/* Responsive Grid */
@media (min-width: 768px) {
    .md\:grid-cols-2 { grid-template-columns: repeat(2, 1fr); }
    .md\:grid-cols-3 { grid-template-columns: repeat(3, 1fr); }
}

@media (min-width: 1024px) {
    .lg\:grid-cols-3 { grid-template-columns: repeat(3, 1fr); }
    .lg\:grid-cols-4 { grid-template-columns: repeat(4, 1fr); }
}
```

---

### Animation Classes

| Class | Description |
|:------|:------------|
| `.animate-fade-in` | Fade in animation |
| `.animate-slide-up` | Slide up animation |
| `.animate-slide-down` | Slide down animation |
| `.animate-spin` | Continuous rotation |
| `.animate-pulse` | Pulsing opacity |
| `.transition` | Default transition |
| `.transition-fast` | Fast transition |
| `.transition-slow` | Slow transition |

```css
/* Animation Classes */
@keyframes fadeIn {
    from { opacity: 0; }
    to { opacity: 1; }
}

@keyframes slideUp {
    from { opacity: 0; transform: translateY(10px); }
    to { opacity: 1; transform: translateY(0); }
}

@keyframes slideDown {
    from { opacity: 0; transform: translateY(-10px); }
    to { opacity: 1; transform: translateY(0); }
}

@keyframes spin {
    from { transform: rotate(0deg); }
    to { transform: rotate(360deg); }
}

@keyframes pulse {
    0%, 100% { opacity: 1; }
    50% { opacity: 0.5; }
}

.animate-fade-in { animation: fadeIn var(--transition-base); }
.animate-slide-up { animation: slideUp var(--transition-base); }
.animate-slide-down { animation: slideDown var(--transition-base); }
.animate-spin { animation: spin 1s linear infinite; }
.animate-pulse { animation: pulse 2s ease-in-out infinite; }

.transition { transition: all var(--transition-base); }
.transition-fast { transition: all var(--transition-fast); }
.transition-slow { transition: all var(--transition-slow); }
.transition-none { transition: none; }
```

---

### Hero Section Classes

| Class | Description |
|:------|:------------|
| `.hero` | Hero section container |
| `.hero-content` | Hero content wrapper |
| `.hero-title` | Main hero headline |
| `.hero-subtitle` | Hero description text |
| `.hero-actions` | CTA button group |
| `.hero-gradient` | Gradient background variant |

```css
/* Hero Section Classes */
.hero {
    padding: var(--space-16) var(--space-4);
    text-align: center;
    background: var(--bg-app);
}

.hero-gradient {
    background: radial-gradient(
        ellipse at top center,
        rgba(88, 166, 255, 0.15) 0%,
        transparent 50%
    ), var(--bg-app);
}

.hero-content {
    max-width: 800px;
    margin: 0 auto;
}

.hero-title {
    font-family: var(--font-display);
    font-size: clamp(2.5rem, 5vw, 4rem);
    font-weight: 700;
    line-height: 1.1;
    margin-bottom: var(--space-4);
    background: linear-gradient(
        135deg,
        var(--text-primary) 0%,
        var(--accent-primary) 100%
    );
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
}

.hero-subtitle {
    font-size: var(--text-xl);
    color: var(--text-secondary);
    line-height: 1.6;
    margin-bottom: var(--space-8);
    max-width: 600px;
    margin-left: auto;
    margin-right: auto;
}

.hero-actions {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: var(--space-4);
}
```

---

### Feature Grid Classes

| Class | Description |
|:------|:------------|
| `.features` | Features section container |
| `.features-grid` | Grid layout for feature cards |
| `.feature-card` | Individual feature card |
| `.feature-icon` | Feature icon container |
| `.feature-title` | Feature card title |
| `.feature-description` | Feature card description |

```css
/* Feature Grid Classes */
.features {
    padding: var(--space-16) var(--space-4);
    max-width: 1200px;
    margin: 0 auto;
}

.features-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
    gap: var(--space-6);
}

.feature-card {
    padding: var(--space-6);
    background: var(--bg-panel);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-lg);
    text-align: center;
    transition: all var(--transition-fast);
}

.feature-card:hover {
    border-color: var(--border-default);
    transform: translateY(-2px);
}

.feature-icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 48px;
    height: 48px;
    margin-bottom: var(--space-4);
    font-size: 1.5rem;
    color: var(--accent-primary);
    background: rgba(88, 166, 255, 0.1);
    border-radius: var(--radius-md);
}

.feature-title {
    font-size: var(--text-xl);
    font-weight: 600;
    margin-bottom: var(--space-2);
    color: var(--text-primary);
}

.feature-description {
    font-size: var(--text-sm);
    color: var(--text-secondary);
    line-height: 1.6;
}
```

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