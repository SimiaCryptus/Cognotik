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
(function(global) {
    'use strict';

    const STORAGE_KEY = 'cognotik-theme';
    const URL_PARAM = 'theme';
    const VALID_THEMES = ['auto', 'light', 'dark'];
    const DEFAULT_THEME = 'auto';

    const listeners = [];

    function getUrlTheme() {
        try {
            const params = new URLSearchParams(window.location.search);
            const value = params.get(URL_PARAM);
            if (value && VALID_THEMES.indexOf(value) !== -1) {
                return value;
            }
        } catch (e) {
            // ignore
        }
        return null;
    }

    function getStoredTheme() {
        try {
            const value = localStorage.getItem(STORAGE_KEY);
            if (value && VALID_THEMES.indexOf(value) !== -1) {
                return value;
            }
        } catch (e) {
            // ignore
        }
        return null;
    }

    function storeTheme(theme) {
        try {
            localStorage.setItem(STORAGE_KEY, theme);
        } catch (e) {
            // ignore
        }
    }

    function applyTheme(theme) {
        const root = document.documentElement;
        if (theme === 'auto') {
            root.removeAttribute('data-theme');
        } else {
            root.setAttribute('data-theme', theme);
        }
        listeners.forEach(function(fn) {
            try { fn(theme); } catch (e) { console.warn('[ThemeManager] listener error', e); }
        });
    }

    function getCurrentTheme() {
        // Priority: URL param > stored > default
        return getUrlTheme() || getStoredTheme() || DEFAULT_THEME;
    }

    function setTheme(theme) {
        if (VALID_THEMES.indexOf(theme) === -1) {
            console.warn('[ThemeManager] Invalid theme:', theme);
            return;
        }
        storeTheme(theme);
        applyTheme(theme);
    }

    function init() {
        const urlTheme = getUrlTheme();
        if (urlTheme) {
            // URL param overrides and persists
            storeTheme(urlTheme);
            applyTheme(urlTheme);
        } else {
            applyTheme(getStoredTheme() || DEFAULT_THEME);
        }
    }

    function bindSelector(selectEl) {
        if (!selectEl) return;
        selectEl.value = getCurrentTheme();
        selectEl.addEventListener('change', function() {
            setTheme(selectEl.value);
        });
        // Keep selector in sync if theme is changed elsewhere
        onChange(function(theme) {
            if (selectEl.value !== theme) selectEl.value = theme;
        });
    }

    function onChange(fn) {
        if (typeof fn === 'function') listeners.push(fn);
    }

    const ThemeManager = {
        init: init,
        getCurrentTheme: getCurrentTheme,
        setTheme: setTheme,
        bindSelector: bindSelector,
        onChange: onChange,
        STORAGE_KEY: STORAGE_KEY,
        URL_PARAM: URL_PARAM,
        VALID_THEMES: VALID_THEMES
    };

    global.ThemeManager = ThemeManager;

    // Auto-initialize as early as possible to avoid FOUC
    if (document.readyState === 'loading') {
        // Apply immediately (don't wait for DOMContentLoaded)
        init();
    } else {
        init();
    }
})(window);