/**
 * Cognotik Theme Manifest (v3)
 *
 * Declares every colour theme ("palette") available to the site, plus the
 * display modes (auto / light / dark) each palette can render in.
 *
 * IMPORTANT: themes.css is generated output from the theme calculator and is
 * NOT edited by hand. It defines a fixed set of [data-theme="<variant>"]
 * blocks. This manifest only *curates* those variants: it names them after
 * what they actually look like and pairs them into day/night palettes.
 *
 * Variants available in themes.css:
 *   light, dark, sepia, sepiaDark, solarizedLight, solarizedDark,
 *   nord, nordLight, dracula, draculaLight, highContrast, highContrastLight
 *
 * The VARIANTS table below mirrors the key colours of each block exactly, so
 * previews always match what the stylesheet renders. If the calculator is
 * re-run, update VARIANTS to match.
 *
 * Note: several variants share the same semantic colours (danger / success /
 * warning, and often accent), so palettes are differentiated mainly by
 * canvas, surface, brand and text.
 *
 * Must be loaded BEFORE modules/theme.js, which reads `window.CognotikThemes`.
 */
(function (global) {
    'use strict';

    /** CSS custom properties every theme stylesheet must define. */
    var TOKENS = [
        '--color-canvas',
        '--color-surface',
        '--color-surface-alt',
        '--color-border',
        '--color-border-strong',
        '--color-text',
        '--color-text-muted',
        '--color-heading',
        '--color-brand',
        '--color-brand-hover',
        '--color-link',
        '--color-accent',
        '--color-secondary',
        '--color-secondary-hover',
        '--color-success',
        '--color-warning',
        '--color-danger',
        '--color-danger-hover'
    ];

    /** Display modes. 'auto' follows the operating system preference. */
    var MODES = [
        {id: 'auto', label: 'Auto (system)'},
        {id: 'light', label: 'Light'},
        {id: 'dark', label: 'Dark'}
    ];

    var STYLESHEET = '/themes.css';

    /**
     * Every [data-theme] block in themes.css, with its scheme and key colours
     * copied verbatim from the stylesheet.
     */
    var VARIANTS = {
        light: {
            scheme: 'light', look: 'Cloud white with sky-blue highlights',
            canvas: 'oklch(97.248% 0.00454 258.32)', surface: 'oklch(99.98% 0 258.32)',
            brand: 'oklch(65.309% 0.1347 242.69)', accent: 'oklch(52.606% 0.17049 314.65)',
            text: 'oklch(32.105% 0 154.99)'
        },
        dark: {
            scheme: 'dark', look: 'Midnight navy with sky-blue highlights',
            canvas: 'oklch(20.098% 0.02108 267.58)', surface: 'oklch(25.26% 0.01853 267.58)',
            brand: 'oklch(65.309% 0.1347 242.69)', accent: 'oklch(52.606% 0.17049 314.65)',
            text: 'oklch(70.182% 0.05448 177.24)'
        },
        sepia: {
            scheme: 'light', look: 'Cream parchment with rust-brown ink',
            canvas: 'oklch(94.404% 0.02781 88.75)', surface: 'oklch(97.077% 0.02131 88.75)',
            brand: 'oklch(52.648% 0.11512 44.6)', accent: 'oklch(52.606% 0.17049 314.65)',
            text: 'oklch(29.905% 0.02 231.46)'
        },
        sepiaDark: {
            scheme: 'dark', look: 'Espresso brown with glowing amber',
            canvas: 'oklch(26.473% 0.02199 78.8)', surface: 'oklch(30.271% 0.0246 78.8)',
            brand: 'oklch(69.736% 0.11124 52.08)', accent: 'oklch(66.545% 0.12032 315.44)',
            text: 'oklch(89.888% 0.02 134.91)'
        },
        solarizedLight: {
            scheme: 'light', look: 'Sun-bleached sand with ocean blue',
            canvas: 'oklch(97.353% 0.02605 90.1)', surface: 'oklch(93.187% 0.08713 90.1)',
            brand: 'oklch(61.488% 0.13939 244.93)', accent: 'oklch(58.232% 0.12614 279.1)',
            text: 'oklch(40.268% 0.02 220.57)'
        },
        solarizedDark: {
            scheme: 'dark', look: 'Deep sea teal with ocean blue',
            canvas: 'oklch(26.734% 0.04861 219.82)', surface: 'oklch(30.909% 0.05173 219.82)',
            brand: 'oklch(61.488% 0.13939 244.93)', accent: 'oklch(58.232% 0.12614 279.1)',
            text: 'oklch(85.459% 0.02 218.8)'
        },
        nord: {
            scheme: 'dark', look: 'Slate-blue dusk with glacier-ice cyan',
            canvas: 'oklch(32.437% 0.02294 264.18)', surface: 'oklch(37.519% 0.02246 264.18)',
            brand: 'oklch(77.464% 0.06225 217.47)', accent: 'oklch(69.207% 0.0625 332.66)',
            text: 'oklch(92.65% 0.01 269.19)'
        },
        nordLight: {
            scheme: 'light', look: 'Frosted pewter with muted steel blue',
            canvas: 'oklch(95.128% 0.00745 260.73)', surface: 'oklch(93.197% 0.00979 260.73)',
            brand: 'oklch(59.436% 0.07725 254.03)', accent: 'oklch(69.207% 0.0625 332.66)',
            text: 'oklch(32.469% 0.015 214.56)'
        },
        dracula: {
            scheme: 'dark', look: 'Plum night with lavender and neon pink',
            canvas: 'oklch(28.823% 0.0221 277.51)', surface: 'oklch(33.708% 0.02392 277.51)',
            brand: 'oklch(74.202% 0.14855 301.88)', accent: 'oklch(75.461% 0.18307 346.81)',
            text: 'oklch(97.374% 0.01 119.62)'
        },
        draculaLight: {
            scheme: 'light', look: 'Ivory with indigo and crimson',
            canvas: 'oklch(98.688% 0.0214 95.28)', surface: 'oklch(99.351% 0.01062 95.28)',
            brand: 'oklch(50.908% 0.1878 287.15)', accent: 'oklch(46.825% 0.17651 4.96)',
            text: 'oklch(0% 0.01 5.26)'
        },
        highContrast: {
            scheme: 'dark', look: 'Pure black with electric cyan',
            canvas: 'oklch(0% 0 0)', surface: 'oklch(15.957% 0 360)',
            brand: 'oklch(77.616% 0.13396 234.95)', accent: 'oklch(75.295% 0.17287 310.51)',
            text: 'oklch(100% 0 110.53)'
        },
        highContrastLight: {
            scheme: 'light', look: 'Pure white with cobalt ink',
            canvas: 'oklch(100% 0 89.88)', surface: 'oklch(97.023% 0 89.88)',
            brand: 'oklch(47.264% 0.19848 260.52)', accent: 'oklch(46.286% 0.24024 305.13)',
            text: 'oklch(0% 0 0)'
        }
    };

    /**
     * data-theme values that render a dark scheme (derived from VARIANTS).
     * Consumers (modules/theme.js) should set html[data-scheme="light|dark"]
     * from isDarkVariant(); legacy selectors should target that instead of
     * html[data-theme="dark"].
     */
    var DARK_VARIANTS = Object.keys(VARIANTS).filter(function (v) {
        return VARIANTS[v].scheme === 'dark';
    });

    /** Build a preview swatch set straight from the stylesheet values. */
    function swatches(variant) {
        var v = VARIANTS[variant];
        if (!v) return null;
        return {canvas: v.canvas, surface: v.surface, brand: v.brand, accent: v.accent, text: v.text};
    }

    /**
     * Palette definitions, ordered cool -> warm -> vivid -> stark.
     *
     * id:       stable identifier (persisted in localStorage)
     * aliases:  legacy ids that resolve to this palette (pre-v3 preferences)
     * family:   loose grouping for UIs ('cool' | 'warm' | 'vivid' | 'stark')
     * variants: mode id -> data-theme attribute value in themes.css
     */
    var DEFS = [
        {
            id: 'harbor',
            aliases: ['point-cad'],
            name: 'Harbor',
            family: 'cool',
            description: 'Crisp cloud-white by day, deep midnight navy by night — sky-blue signals with a violet accent. The default.',
            variants: {light: 'light', dark: 'dark'}
        },
        {
            id: 'fjord',
            aliases: ['nord'],
            name: 'Fjord',
            family: 'cool',
            description: 'Frosted pewter by day, slate-blue dusk by night — quiet steel and glacier-ice highlights with a touch of heather.',
            variants: {light: 'nordLight', dark: 'nord'}
        },
        {
            id: 'abyss',
            aliases: [],
            name: 'Abyss',
            family: 'cool',
            description: 'Clean cloud-white by day that sinks into deep sea-teal water by night.',
            variants: {light: 'light', dark: 'solarizedDark'}
        },
        {
            id: 'lagoon',
            aliases: ['solarized'],
            name: 'Lagoon',
            family: 'warm',
            description: 'Sun-bleached sand by day, deep teal lagoon by night — ocean-blue links in both.',
            variants: {light: 'solarizedLight', dark: 'solarizedDark'}
        },
        {
            id: 'dune',
            aliases: [],
            name: 'Dune',
            family: 'warm',
            description: 'Golden desert sand by day, warm campfire brown with amber embers by night.',
            variants: {light: 'solarizedLight', dark: 'sepiaDark'}
        },
        {
            id: 'ember',
            aliases: ['sepia'],
            name: 'Ember',
            family: 'warm',
            description: 'Cream parchment with rust-brown ink by day, espresso and glowing amber by night. Easy on the eyes for long reads.',
            variants: {light: 'sepia', dark: 'sepiaDark'}
        },
        {
            id: 'twilight',
            aliases: [],
            name: 'Twilight',
            family: 'vivid',
            description: 'An overcast silver morning that fades into a lavender-and-pink violet evening.',
            variants: {light: 'nordLight', dark: 'dracula'}
        },
        {
            id: 'orchid',
            aliases: ['dracula'],
            name: 'Orchid',
            family: 'vivid',
            description: 'Ivory petals with indigo and crimson by day, plum night with lavender and neon pink after dark.',
            variants: {light: 'draculaLight', dark: 'dracula'}
        },
        {
            id: 'stark',
            aliases: ['high-contrast'],
            name: 'Stark',
            family: 'stark',
            description: 'Maximum contrast for accessibility: pure white with cobalt ink, or pure black with electric cyan.',
            variants: {light: 'highContrastLight', dark: 'highContrast'}
        }
    ];

    var THEMES = DEFS.map(function (d) {
        var modes = Object.keys(d.variants);
        var preview = {};
        modes.forEach(function (m) {
            preview[m] = swatches(d.variants[m]);
        });
        return {
            id: d.id,
            aliases: d.aliases || [],
            name: d.name,
            family: d.family,
            description: d.description,
            stylesheet: STYLESHEET,
            modes: modes,
            variants: d.variants,
            preview: preview
        };
    });

    /** Sanity-check entries so mismatches are caught during development. */
    var seenIds = {};
    THEMES.forEach(function (t) {
        var warn = function (msg) {
            if (global.console) console.warn('[CognotikThemes] theme "' + t.id + '" ' + msg);
        };
        var variantModes = Object.keys(t.variants || {});
        if (!variantModes.length) warn('declares no variants');
        t.modes.forEach(function (m) {
            if (variantModes.indexOf(m) === -1) warn('lists mode "' + m + '" without a variant');
        });
        variantModes.forEach(function (m) {
            var v = t.variants[m];
            if (t.modes.indexOf(m) === -1) warn('has variant "' + m + '" not listed in modes');
            if (!VARIANTS[v]) {
                warn('references data-theme "' + v + '" which themes.css does not define');
            } else if ((m === 'light' || m === 'dark') && VARIANTS[v].scheme !== m) {
                warn('maps ' + m + ' mode to "' + v + '", a ' + VARIANTS[v].scheme + ' variant');
            }
        });
        [t.id].concat(t.aliases).forEach(function (id) {
            if (seenIds[id]) warn('id/alias "' + id + '" is already used by "' + seenIds[id] + '"');
            seenIds[id] = t.id;
        });
    });

    function deepFreeze(obj) {
        if (obj && typeof obj === 'object' && !Object.isFrozen(obj)) {
            Object.keys(obj).forEach(function (k) {
                deepFreeze(obj[k]);
            });
            Object.freeze(obj);
        }
        return obj;
    }

    deepFreeze(TOKENS);
    deepFreeze(MODES);
    deepFreeze(VARIANTS);
    deepFreeze(THEMES);
    deepFreeze(DARK_VARIANTS);

    var manifest = {
        version: 3,
        defaultTheme: 'harbor',
        defaultMode: 'auto',
        tokens: TOKENS,
        modes: MODES,
        themes: THEMES,
        variants: VARIANTS,
        darkVariants: DARK_VARIANTS,

        /** @returns {boolean} whether a data-theme value is a dark scheme */
        isDarkVariant: function (variant) {
            return DARK_VARIANTS.indexOf(variant) !== -1;
        },

        /** @returns {object|null} key colours / description of a data-theme value */
        variantInfo: function (variant) {
            return VARIANTS[variant] || null;
        },

        /** @returns {object|null} theme entry by id or legacy alias */
        get: function (id) {
            var i;
            for (i = 0; i < THEMES.length; i++) {
                if (THEMES[i].id === id) return THEMES[i];
            }
            for (i = 0; i < THEMES.length; i++) {
                if (THEMES[i].aliases.indexOf(id) !== -1) return THEMES[i];
            }
            return null;
        },

        /**
         * Map a (possibly legacy) id to the current palette id.
         * @returns {string|null}
         */
        canonicalId: function (id) {
            var t = manifest.get(id);
            return t ? t.id : null;
        },

        /** @returns {boolean} whether a theme id (or legacy alias) is registered */
        has: function (id) {
            return manifest.get(id) !== null;
        },

        /** @returns {string[]} registered theme ids */
        themeIds: function () {
            return THEMES.map(function (t) {
                return t.id;
            });
        },

        /** @returns {string[]} valid mode ids (auto, light, dark) */
        modeIds: function () {
            return MODES.map(function (m) {
                return m.id;
            });
        },

        /** @returns {boolean} whether a theme supports a mode ('auto' is always supported) */
        supportsMode: function (themeId, mode) {
            if (mode === 'auto') return true;
            var t = manifest.get(themeId);
            return !!(t && t.modes.indexOf(mode) !== -1);
        },

        /**
         * Resolve the value to place in the `data-theme` attribute.
         *
         * @param {string}  themeId             registered theme id (or legacy alias)
         * @param {string}  mode                'auto' | 'light' | 'dark'
         * @param {boolean} [systemPrefersDark] OS preference, used for 'auto'
         * @returns {string|null} attribute value, or null to remove the
         *          attribute (default palette in auto mode, letting the
         *          prefers-color-scheme media query take over)
         */
        resolveAttribute: function (themeId, mode, systemPrefersDark) {
            var t = manifest.get(themeId) || manifest.get(manifest.defaultTheme);
            if (!t) return null;
            var v = t.variants;

            // Single-mode palettes always render their only variant.
            if (t.modes.length === 1) return v[t.modes[0]];

            if (mode === 'light' || mode === 'dark') {
                return v[mode] || v[t.modes[0]];
            }

            // 'auto' (or unknown): the default palette can defer to CSS media queries
            // (themes.css :root + prefers-color-scheme match its light/dark variants).
            if (t.id === manifest.defaultTheme && typeof systemPrefersDark !== 'boolean') {
                return null;
            }
            var want = systemPrefersDark ? 'dark' : 'light';
            return v[want] || v[t.modes[0]];
        }
    };

    if (!manifest.has(manifest.defaultTheme) && THEMES.length) {
        manifest.defaultTheme = THEMES[0].id;
    }

    global.CognotikThemes = Object.freeze(manifest);
})(window);