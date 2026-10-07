/**
 * theme.js — bridge between the shared `/app/` modules and the central
 * Cognotik theme system (`/themes.css`, `/themes.js`, `/modules/theme.js`).
 *
 * Pages should ideally include the theme assets in <head> (avoids a flash of
 * the wrong theme). When they don't, `ensureTheme()` loads them on demand in
 * the required order: stylesheet, manifest, then ThemeManager.
 *
 * Usage:
 *   import { ensureTheme, isDarkScheme } from '/app/theme.js';
 *   const tm = await ensureTheme();     // ThemeManager or null
 */
import {serverUrl} from './config.js';

/** Default locations of the central theme assets (host-absolute, prefixed by serverUrl). */
export const THEME_DEFAULTS = {
    stylesheetHref: '/themes.css',
    manifestSrc: '/themes.js',
    managerSrc: '/modules/theme.js'
};

const STYLESHEET_ID = 'theme-stylesheet';
const scriptPromises = {};
let themePromise = null;

/** @returns {Object|null} window.ThemeManager when loaded */
export function getThemeManager() {
    return (typeof window !== 'undefined' && window.ThemeManager) || null;
}

/** @returns {Object|null} window.CognotikThemes manifest when loaded */
export function getThemeManifest() {
    const tm = getThemeManager();
    const fromManager = tm && typeof tm.getManifest === 'function' ? tm.getManifest() : null;
    return fromManager || (typeof window !== 'undefined' && window.CognotikThemes) || null;
}

/**
 * Ensure `<link id="theme-stylesheet">` exists, inserted before any other stylesheet.
 * @param {string} [href]
 * @returns {HTMLLinkElement}
 */
export function ensureThemeStylesheet(href = THEME_DEFAULTS.stylesheetHref) {
    let link = document.getElementById(STYLESHEET_ID);
    if (link) return link;
    link = document.createElement('link');
    link.id = STYLESHEET_ID;
    link.rel = 'stylesheet';
    link.href = serverUrl(href);
    const head = document.head;
    const firstSheet = head.querySelector('link[rel="stylesheet"], style');
    head.insertBefore(link, firstSheet || head.firstChild);
    return link;
}

function loadScript(src) {
    const url = serverUrl(src);
    if (scriptPromises[url]) return scriptPromises[url];
    scriptPromises[url] = new Promise(resolve => {
        const s = document.createElement('script');
        s.src = url;
        s.async = false;
        s.onload = () => resolve(true);
        s.onerror = () => {
            console.warn('Could not load theme script:', url);
            resolve(false);
        };
        document.head.appendChild(s);
    });
    return scriptPromises[url];
}

/**
 * Make sure the central theme system is available. Never rejects.
 * @param {Object} [options]
 * @param {boolean} [options.autoLoad=true] - Load missing assets automatically
 * @param {string} [options.stylesheetHref='/themes.css']
 * @param {string} [options.manifestSrc='/themes.js']
 * @param {string} [options.managerSrc='/modules/theme.js']
 * @returns {Promise<Object|null>} ThemeManager, or null if unavailable
 */
export function ensureTheme(options = {}) {
    const opts = {...THEME_DEFAULTS, autoLoad: true, ...options};
    const existing = getThemeManager();
    if (existing) return Promise.resolve(existing);
    if (!opts.autoLoad) return Promise.resolve(null);
    if (!themePromise) {
        themePromise = (async () => {
            try {
                ensureThemeStylesheet(opts.stylesheetHref);
                // Order matters: the manifest must be present before the manager initialises.
                if (!window.CognotikThemes) await loadScript(opts.manifestSrc);
                if (!getThemeManager()) await loadScript(opts.managerSrc);
                const tm = getThemeManager();
                if (tm && typeof tm.init === 'function'
                    && !document.documentElement.hasAttribute('data-scheme')) {
                    tm.init();
                }
                return tm;
            } catch (e) {
                console.warn('Theme system unavailable:', e);
                return null;
            }
        })();
    }
    return themePromise;
}

let syncRefs = 0;

function onStorage(e) {
    const tm = getThemeManager();
    if (!tm || !e.newValue) return;
    if (e.key === tm.PALETTE_STORAGE_KEY && typeof tm.setPalette === 'function') tm.setPalette(e.newValue);
    else if (e.key === tm.STORAGE_KEY && typeof tm.setTheme === 'function') tm.setTheme(e.newValue);
}

/**
 * Follow palette/mode changes made in other windows or iframes (same origin).
 * A single listener is shared by all callers.
 * @returns {Function} unsubscribe
 */
export function syncThemeAcrossWindows() {
    if (syncRefs++ === 0) window.addEventListener('storage', onStorage);
    let active = true;
    return () => {
        if (!active) return;
        active = false;
        if (--syncRefs === 0) window.removeEventListener('storage', onStorage);
    };
}

/** @returns {boolean} true when the effective scheme is dark */
export function isDarkScheme() {
    return document.documentElement.getAttribute('data-scheme') === 'dark';
}

/**
 * Human-readable description of the active theme, e.g. `Nord · auto`.
 * @returns {string} '' when the theme system is not loaded
 */
export function describeTheme() {
    const tm = getThemeManager();
    if (!tm) return '';
    const paletteId = typeof tm.getCurrentPalette === 'function' ? tm.getCurrentPalette() : '';
    const mode = typeof tm.getCurrentTheme === 'function' ? tm.getCurrentTheme() : '';
    const manifest = getThemeManifest();
    const palette = manifest && Array.isArray(manifest.themes)
        ? manifest.themes.find(t => t.id === paletteId) : null;
    return [palette ? palette.name : paletteId, mode].filter(Boolean).join(' \u00b7 ');
}

export const ThemeUtils = {
    THEME_DEFAULTS,
    getThemeManager,
    getThemeManifest,
    ensureThemeStylesheet,
    ensureTheme,
    syncThemeAcrossWindows,
    isDarkScheme,
    describeTheme
};