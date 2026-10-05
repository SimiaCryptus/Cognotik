/**
 * Cognotik Theme Manifest
 *
 * Declares every colour theme ("palette") available to the site, plus the
 * display modes (auto / light / dark) each palette can render in.
 *
 * All palettes live in a single stylesheet (themes.css) which defines the
 * CSS custom properties listed in `tokens` for:
 *   - :root                                   (Point-CAD light defaults)
 *   - @media (prefers-color-scheme: dark)     (Point-CAD system dark)
 *   - [data-theme="<variant>"]                (explicit user choice)
 *
 * Each palette maps the modes it supports to a `data-theme` attribute value
 * via `variants`. Every registered palette currently ships both a light and
 * a dark variant (full palette x mode cross product). Single-mode palettes
 * are still supported: they render their sole variant regardless of the
 * requested / system mode.
 *
 * style.css consumes these tokens (directly or via aliases such as
 * --color-primary -> --color-brand), so adding a theme only requires a new
 * [data-theme="..."] block in themes.css and a new entry in THEMES below.
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
      * data-theme values that render a dark scheme. Legacy CSS that keys on
      * html[data-theme="dark"] misses every non-default dark palette; consumers
      * (modules/theme.js) should also set html[data-scheme="light|dark"] from
      * isDarkVariant() and legacy selectors should target that instead.
      */
     var DARK_VARIANTS = ['dark', 'sepiaDark', 'solarizedDark', 'nord', 'dracula', 'highContrast'];


    /**
     * Registered palettes. The first entry is used if defaultTheme is missing.
     *
     * variants: mode id -> value of the data-theme attribute in themes.css.
     * modes:    derived from variants (kept explicit for consumers).
     */
    var THEMES = [
        {
            id: 'point-cad',
            name: 'Default',
            description: 'Default Cognotik palette — 18 colours, light and dark variants.',
            stylesheet: STYLESHEET,
            modes: ['light', 'dark'],
            variants: {light: 'light', dark: 'dark'},
            preview: {
                light: {
                    canvas: 'oklch(97.248% 0.00454 258.32)',
                    surface: 'oklch(100% 0 89.88)',
                    brand: 'oklch(65.309% 0.1347 242.69)',
                    accent: 'oklch(52.606% 0.17049 314.65)'
                },
                dark: {
                    canvas: 'oklch(20.098% 0.02108 267.58)',
                    surface: 'oklch(25.347% 0.02351 270.32)',
                    brand: 'oklch(65.309% 0.1347 242.69)',
                    accent: 'oklch(52.606% 0.17049 314.65)'
                }
            }
        },
        {
            id: 'sepia',
            name: 'Sepia',
            description: 'Warm paper tones for comfortable long reading sessions, light and dark variants.',
            stylesheet: STYLESHEET,
            modes: ['light', 'dark'],
            variants: {light: 'sepia', dark: 'sepiaDark'},
            preview: {
                light: {
                    canvas: 'oklch(94.404% 0.02781 88.75)',
                    surface: 'oklch(97.375% 0.01672 88)',
                    brand: 'oklch(52.648% 0.11512 44.6)',
                    accent: 'oklch(52.606% 0.17049 314.65)'
                },
                dark: {
                    canvas: 'oklch(26.7% 0.018 75)',
                    surface: 'oklch(29.7% 0.022 75)',
                    brand: 'oklch(69% 0.11 52)',
                    accent: 'oklch(65% 0.12 315)'
                }
            }
        },
        {
            id: 'solarized',
            name: 'Solarized',
            description: 'Ethan Schoonover\'s precision palette, light and dark variants.',
            stylesheet: STYLESHEET,
            modes: ['light', 'dark'],
            variants: {light: 'solarizedLight', dark: 'solarizedDark'},
            preview: {
                light: {
                    canvas: 'oklch(97.353% 0.02605 90.1)',
                    surface: 'oklch(93.061% 0.02603 92.4)',
                    brand: 'oklch(61.488% 0.13939 244.93)',
                    accent: 'oklch(58.232% 0.12614 279.1)'
                },
                dark: {
                    canvas: 'oklch(26.734% 0.04861 219.82)',
                    surface: 'oklch(30.921% 0.05176 219.65)',
                    brand: 'oklch(61.488% 0.13939 244.93)',
                    accent: 'oklch(58.232% 0.12614 279.1)'
                }
            }
        },
        {
            id: 'nord',
            name: 'Nord',
            description: 'Arctic, north-bluish palette, light (Snow Storm) and dark (Polar Night) variants.',
            stylesheet: STYLESHEET,
            modes: ['light', 'dark'],
            variants: {light: 'nordLight', dark: 'nord'},
            preview: {
                light: {
                    canvas: 'oklch(95.1% 0.007 264.5)',
                    surface: 'oklch(93.3% 0.0098 261)',
                    brand: 'oklch(59.4% 0.077 254)',
                    accent: 'oklch(69.207% 0.0625 332.66)'
                },
                dark: {
                    canvas: 'oklch(32.437% 0.02294 264.18)',
                    surface: 'oklch(37.921% 0.02897 266.47)',
                    brand: 'oklch(77.464% 0.06225 217.47)',
                    accent: 'oklch(69.207% 0.0625 332.66)'
                }
            }
        },
        {
            id: 'dracula',
            name: 'Dracula',
            description: 'Vivid purple-accented palette, light (Alucard) and dark variants.',
            stylesheet: STYLESHEET,
            modes: ['light', 'dark'],
            variants: {light: 'draculaLight', dark: 'dracula'},
            preview: {
                light: {
                    canvas: 'oklch(98.9% 0.019 95)',
                    surface: 'oklch(99.5% 0.009 95)',
                    brand: 'oklch(50.5% 0.19 286)',
                    accent: 'oklch(48% 0.18 7)'
                },
                dark: {
                    canvas: 'oklch(28.823% 0.0221 277.51)',
                    surface: 'oklch(34.02% 0.02663 276.05)',
                    brand: 'oklch(74.202% 0.14855 301.88)',
                    accent: 'oklch(75.461% 0.18307 346.81)'
                }
            }
        },
        {
            id: 'high-contrast',
            name: 'High Contrast',
            description: 'Maximum-contrast palette for accessibility, light and dark variants.',
            stylesheet: STYLESHEET,
            modes: ['light', 'dark'],
            variants: {light: 'highContrastLight', dark: 'highContrast'},
            preview: {
                light: {
                    canvas: 'oklch(100% 0 0)',
                    surface: 'oklch(97% 0 0)',
                    brand: 'oklch(47% 0.19 260)',
                    accent: 'oklch(45% 0.24 305)'
                },
                dark: {
                    canvas: 'oklch(0% 0 0)',
                    surface: 'oklch(19.125% 0 89.88)',
                    brand: 'oklch(77.616% 0.13396 234.95)',
                    accent: 'oklch(75.295% 0.17287 310.51)'
                }
            }
        }
    ];

    /** Sanity-check entries so mismatches are caught during development. */
    THEMES.forEach(function (t) {
        var variantModes = Object.keys(t.variants || {});
        if (!variantModes.length && global.console) {
            console.warn('[CognotikThemes] theme "' + t.id + '" declares no variants');
        }
        t.modes.forEach(function (m) {
            if (variantModes.indexOf(m) === -1 && global.console) {
                console.warn('[CognotikThemes] theme "' + t.id + '" lists mode "' + m + '" without a variant');
            }
        });
        variantModes.forEach(function (m) {
            if (t.modes.indexOf(m) === -1 && global.console) {
                console.warn('[CognotikThemes] theme "' + t.id + '" has variant "' + m + '" not listed in modes');
            }
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
    deepFreeze(THEMES);
     deepFreeze(DARK_VARIANTS);

    var manifest = {
        version: 2,
        defaultTheme: 'point-cad',
        defaultMode: 'auto',
        tokens: TOKENS,
        modes: MODES,
        themes: THEMES,
         darkVariants: DARK_VARIANTS,
         /** @returns {boolean} whether a data-theme value is a dark scheme */
         isDarkVariant: function (variant) {
             return DARK_VARIANTS.indexOf(variant) !== -1;
         },

        /** @returns {object|null} theme entry by id */
        get: function (id) {
            for (var i = 0; i < THEMES.length; i++) {
                if (THEMES[i].id === id) return THEMES[i];
            }
            return null;
        },

        /** @returns {boolean} whether a theme id is registered */
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
         * @param {string}  themeId           registered theme id
         * @param {string}  mode              'auto' | 'light' | 'dark'
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

            // 'auto' (or unknown): the default palette can defer to CSS media queries.
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