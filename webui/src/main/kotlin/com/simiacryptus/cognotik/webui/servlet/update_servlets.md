---
specifies: *Servlet.kt
---

Integrate `/modules/theme.js` as documented below

/**
* Site-wide Theme Manager
*
* Manages theme (light/dark/auto) across all pages.
* - Persists selection in localStorage under 'cognotik-theme'
* - Overridable via URL parameter ?theme=light|dark|auto
* - URL param value (if present and valid) is persisted to localStorage
* - Applies theme by setting data-theme attribute on <html>
*
* Usage:
*   <script src="/modules/theme.js"></script>
*   ThemeManager.init();
*   // Optionally bind a <select> element:
*   ThemeManager.bindSelector(document.getElementById('theme-selector'));
*/