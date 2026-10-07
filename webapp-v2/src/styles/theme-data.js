/**
 * Theme data — the JS mirror of `styles/theme-data.css`.
 *
 * Contract:
 *   - `styles/theme-data.css` declares the 14 `--color-*` tokens once per theme under
 *     `[data-theme="<id>"]` (plus `:root` / `prefers-color-scheme` fallbacks for the
 *     pre-JS paint). It is the ONLY place colour values live.
 *   - This module declares WHICH themes exist and their metadata. It deliberately does
 *     not duplicate the colour values — read them back with `readToken()`/`readPalette()`
 *     when JS needs a concrete colour (e.g. canvas/SVG renderers).
 *   - `styles/base.css` derives every tint/glow/gradient from these tokens with
 *     `color-mix()`, so a theme never declares more than these 14 colours.
 *
 * Adding a theme:
 *   1. add a `[data-theme="<id>"]` block with all 14 tokens to theme-data.css
 *   2. register it in THEMES below (`dark` drives color-scheme + mermaid variant)
 */

/** Token names; each maps to the custom property `--color-<name>`. */
export const COLOR_TOKENS = Object.freeze([
    'canvas',
    'canvas-deep',
    'surface-1',
    'surface-2',
    'code-bg',
    'text',
    'text-muted',
    'brand',
    'brand-contrast',
    'secondary',
    'accent',
    'accent-contrast',
    'danger',
    'success'
]);

/** Registry, in cycle order. `dark: true` drives `color-scheme` and mermaid's theme. */
export const THEMES = Object.freeze({
    nexus: Object.freeze({id: 'nexus', label: 'Nexus', dark: true}),
    synthwave: Object.freeze({id: 'synthwave', label: 'Synthwave', dark: true}),
    matrix: Object.freeze({id: 'matrix', label: 'Matrix', dark: true}),
    dark: Object.freeze({id: 'dark', label: 'Slate', dark: true}),
    light: Object.freeze({id: 'light', label: 'Photon', dark: false})
});

export const DEFAULT_THEME = 'nexus';
/** What 'auto' resolves to on each side of `prefers-color-scheme`. */
export const DARK_FALLBACK = 'nexus';
export const LIGHT_FALLBACK = 'light';

/** `'brand'` -> `'--color-brand'` */
export function cssVar(token) {
    return `--color-${token}`;
}

/** Current computed value of a token (follows the active `data-theme`). */
export function readToken(token, element = document.documentElement) {
    try {
        return getComputedStyle(element).getPropertyValue(cssVar(token)).trim();
    } catch {
        return '';
    }
}

/** `{canvas: 'oklch(…)', brand: 'oklch(…)', …}` for the active theme. */
export function readPalette(element = document.documentElement) {
    const out = {};
    for (const token of COLOR_TOKENS) out[token] = readToken(token, element);
    return out;
}