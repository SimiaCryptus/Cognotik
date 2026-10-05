/**
 * Site-wide Theme Manager
 *
 * Manages two independent settings:
 *   1. Mode    — 'auto' | 'light' | 'dark'
 *                localStorage 'cognotik-theme', URL ?theme=
 *   2. Palette — a theme id from the manifest in /themes.js (window.CognotikThemes)
 *                localStorage 'cognotik-palette', URL ?palette=
 *
 * The pair (palette, mode) is resolved through the manifest's
 * resolveAttribute() into the value of the data-theme attribute on <html>
 * (e.g. 'light', 'dark', 'sepia', 'solarizedDark', 'nord'). For the default
 * palette in 'auto' mode the attribute is removed so the CSS
 * prefers-color-scheme media query takes over.
 *
 * The requested mode is also exposed as data-mode on <html>, and the
 * palette id as data-palette.
 *
 * URL parameters (when valid) override and are persisted to localStorage.
 */
(function (global) {
    'use strict';

    var STORAGE_KEY = 'cognotik-theme';
    var PALETTE_STORAGE_KEY = 'cognotik-palette';
    var URL_PARAM = 'theme';
    var PALETTE_URL_PARAM = 'palette';
    var LINK_ID = 'theme-stylesheet';
    var FALLBACK_MODES = ['auto', 'light', 'dark'];

    function getManifest() {
        return global.CognotikThemes || null;
    }

    var manifest = getManifest();
    if (!manifest) {
        console.warn('[ThemeManager] themes.js manifest not loaded; palette switching disabled.');
    }

    var VALID_THEMES = manifest ? manifest.modeIds() : FALLBACK_MODES.slice();
    var DEFAULT_THEME = (manifest && manifest.defaultMode) || 'auto';

    var modeListeners = [];
    var paletteListeners = [];

    // ----- helpers -----

    function notify(list, value) {
        list.forEach(function (fn) {
            try { fn(value); } catch (e) { console.warn('[ThemeManager] listener error', e); }
        });
    }

    function readStorage(key) {
        try { return localStorage.getItem(key); } catch (e) { return null; }
    }

    function writeStorage(key, value) {
        try { localStorage.setItem(key, value); } catch (e) { /* ignore */ }
    }

    function readUrlParam(name) {
        try { return new URLSearchParams(window.location.search).get(name); } catch (e) { return null; }
    }

    function isValidMode(value) {
        return !!value && VALID_THEMES.indexOf(value) !== -1;
    }

    function isValidPalette(value) {
        var m = getManifest();
        return !!value && !!m && m.has(value);
    }

    function getDarkQuery() {
        try {
            return window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;
        } catch (e) {
            return null;
        }
    }

    function systemPrefersDark() {
        var q = getDarkQuery();
        return !!(q && q.matches);
    }

    // ----- mode (auto / light / dark) -----

    function getUrlTheme() {
        var v = readUrlParam(URL_PARAM);
        return isValidMode(v) ? v : null;
    }

    function getStoredTheme() {
        var v = readStorage(STORAGE_KEY);
        return isValidMode(v) ? v : null;
    }

    function getCurrentTheme() {
        // Priority: URL param > stored > default
        return getUrlTheme() || getStoredTheme() || DEFAULT_THEME;
    }

    /**
     * Compute the data-theme attribute value for a palette/mode pair.
     * Returns null when the attribute should be removed.
     */
    function resolveAttribute(palette, mode) {
        var m = getManifest();
        if (!m) return mode === 'auto' ? null : mode;
        // The default palette in auto mode defers to the CSS media query
        // (pass undefined); other palettes need an explicit system preference.
        var prefersDark = (mode === 'auto' && palette !== m.defaultTheme)
            ? systemPrefersDark()
            : undefined;
        return m.resolveAttribute(palette, mode, prefersDark);
    }

    function applyTheme(theme) {
        var root = document.documentElement;
        if (!isValidMode(theme)) theme = DEFAULT_THEME;
        var attr = resolveAttribute(getCurrentPalette(), theme);
        if (attr) {
            root.setAttribute('data-theme', attr);
        } else {
            root.removeAttribute('data-theme');
        }
        // Expose the effective light/dark scheme so CSS can target every dark
        // palette (nord, dracula, sepiaDark, …), not only data-theme="dark".
        var m = getManifest();
        var isDark;
        if (attr) {
            isDark = (m && typeof m.isDarkVariant === 'function')
                ? m.isDarkVariant(attr)
                : attr === 'dark';
        } else {
            // Attribute removed => CSS media query decides; mirror it here.
            isDark = systemPrefersDark();
        }
        root.setAttribute('data-scheme', isDark ? 'dark' : 'light');
        try { root.style.colorScheme = isDark ? 'dark' : 'light'; } catch (e) { /* ignore */ }
        root.setAttribute('data-mode', theme);
        notify(modeListeners, theme);
    }

    function setTheme(theme) {
        if (!isValidMode(theme)) {
            console.warn('[ThemeManager] Invalid theme:', theme);
            return;
        }
        writeStorage(STORAGE_KEY, theme);
        applyTheme(theme);
    }

    // ----- palette (manifest entry) -----

    function getUrlPalette() {
        var v = readUrlParam(PALETTE_URL_PARAM);
        return isValidPalette(v) ? v : null;
    }

    function getStoredPalette() {
        var v = readStorage(PALETTE_STORAGE_KEY);
        return isValidPalette(v) ? v : null;
    }

    function getCurrentPalette() {
        var m = getManifest();
        return getUrlPalette() || getStoredPalette() || (m ? m.defaultTheme : null);
    }

    function ensureStylesheet(href) {
        var resolved;
        try {
            resolved = new URL(href, document.baseURI).href;
        } catch (e) {
            resolved = href;
        }
        var link = document.getElementById(LINK_ID);
        if (link) {
            if (link.href !== resolved) link.href = resolved;
            return;
        }
        link = document.createElement('link');
        link.id = LINK_ID;
        link.rel = 'stylesheet';
        link.href = resolved;
        var head = document.head || document.documentElement;
        var first = head.querySelector('link[rel="stylesheet"]');
        if (first) {
            head.insertBefore(link, first);
        } else {
            head.appendChild(link);
        }
    }

    function applyPalette(id) {
        var m = getManifest();
        if (!m) return;
        var theme = m.get(id) || m.get(m.defaultTheme);
        if (!theme) return;
        ensureStylesheet(theme.stylesheet);
        document.documentElement.setAttribute('data-palette', theme.id);
        notify(paletteListeners, theme.id);
    }

    function setPalette(id) {
        if (!isValidPalette(id)) {
            console.warn('[ThemeManager] Invalid palette:', id);
            return;
        }
        writeStorage(PALETTE_STORAGE_KEY, id);
        applyPalette(id);
        // Re-resolve data-theme for the new palette.
        applyTheme(getCurrentTheme());
    }

    // ----- init / binding -----

    function watchSystemPreference() {
        var q = getDarkQuery();
        if (!q) return;
        var handler = function () {
            if (getCurrentTheme() === 'auto') applyTheme('auto');
        };
        if (typeof q.addEventListener === 'function') {
            q.addEventListener('change', handler);
        } else if (typeof q.addListener === 'function') {
            q.addListener(handler);
        }
    }

    function init() {
        var urlPalette = getUrlPalette();
        if (urlPalette) writeStorage(PALETTE_STORAGE_KEY, urlPalette);
        applyPalette(getCurrentPalette());

        var urlTheme = getUrlTheme();
        if (urlTheme) writeStorage(STORAGE_KEY, urlTheme);
        applyTheme(getCurrentTheme());

        watchSystemPreference();
    }

    function populate(selectEl, items) {
        if (selectEl.options.length > 0) return;
        items.forEach(function (item) {
            var opt = document.createElement('option');
            opt.value = item.id;
            opt.textContent = item.label || item.name || item.id;
            if (item.description) opt.title = item.description;
            selectEl.appendChild(opt);
        });
    }

    function bindSelector(selectEl) {
        if (!selectEl) return;
        var m = getManifest();
        populate(selectEl, m ? m.modes : FALLBACK_MODES.map(function (id) { return { id: id }; }));
        selectEl.value = getCurrentTheme();
        selectEl.addEventListener('change', function () {
            setTheme(selectEl.value);
        });
        onChange(function (theme) {
            if (selectEl.value !== theme) selectEl.value = theme;
        });

        // Single-variant palettes (e.g. Nord) ignore the mode, so disable the
        // mode selector while one is active.
        var defaultTitle = selectEl.getAttribute('title') || 'Select theme';
        var syncDisabled = function () {
            var t = m ? m.get(getCurrentPalette()) : null;
            var single = !!(t && t.modes.length === 1);
            selectEl.disabled = single;
            selectEl.title = single
                ? t.name + ' only has a ' + t.modes[0] + ' variant'
                : defaultTitle;
        };
        onPaletteChange(syncDisabled);
        syncDisabled();
    }

    function bindPaletteSelector(selectEl) {
        var m = getManifest();
        if (!selectEl || !m) return;
        populate(selectEl, m.themes);
        selectEl.value = getCurrentPalette();
        selectEl.addEventListener('change', function () {
            setPalette(selectEl.value);
        });
        onPaletteChange(function (id) {
            if (selectEl.value !== id) selectEl.value = id;
        });
    }

    function onChange(fn) {
        if (typeof fn === 'function') modeListeners.push(fn);
    }

    function onPaletteChange(fn) {
        if (typeof fn === 'function') paletteListeners.push(fn);
    }

    global.ThemeManager = {
        init: init,
        // mode
        getCurrentTheme: getCurrentTheme,
        setTheme: setTheme,
        bindSelector: bindSelector,
        onChange: onChange,
        // palette
        getCurrentPalette: getCurrentPalette,
        setPalette: setPalette,
        bindPaletteSelector: bindPaletteSelector,
        onPaletteChange: onPaletteChange,
        getManifest: getManifest,
        // resolution
        resolveAttribute: resolveAttribute,
        // constants
        STORAGE_KEY: STORAGE_KEY,
        PALETTE_STORAGE_KEY: PALETTE_STORAGE_KEY,
        URL_PARAM: URL_PARAM,
        PALETTE_URL_PARAM: PALETTE_URL_PARAM,
        VALID_THEMES: VALID_THEMES
    };

    // Apply immediately to minimise flash of unstyled/incorrect theme.
    init();
})(window);