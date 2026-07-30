import {createLogger} from '../util/logger.js';
    import {bus, Events} from '../core/bus.js';

    /**
     * Theme support (groundwork for reverse-spec §19.1).
     *
     * A theme is NOTHING but a set of `--theme-*` custom properties declared under
     * `html[data-theme="<id>"]` in `styles/themes.css`. Adding a theme is therefore:
     *   1. add the CSS block in styles/themes.css
     *   2. register it in THEMES below
     * No other module changes — nothing here hard-codes a colour.
     */

    const log = createLogger('Theme');

    const STORAGE_KEY = 'theme';
    const ATTRIBUTE = 'data-theme';

    /** `?theme=<id>` (also honoured in a hash query: `#session?theme=dark`). */
    export const QUERY_PARAM = 'theme';
    /** Pseudo-theme: follow the OS `prefers-color-scheme`. */
    export const AUTO_THEME = 'auto';
     export const DEFAULT_THEME = 'nexus';
     /** What 'auto' resolves to on each side of `prefers-color-scheme`. */
     export const DARK_FALLBACK = 'nexus';
     export const LIGHT_FALLBACK = 'light';

    /** Registry. `dark: true` drives `color-scheme` and the mermaid/Prism variants. */
    export const THEMES = Object.freeze({
         nexus: Object.freeze({id: 'nexus', label: 'Nexus', dark: true}),
         synthwave: Object.freeze({id: 'synthwave', label: 'Synthwave', dark: true}),
         matrix: Object.freeze({id: 'matrix', label: 'Matrix', dark: true}),
         dark: Object.freeze({id: 'dark', label: 'Slate', dark: true}),
         light: Object.freeze({id: 'light', label: 'Photon', dark: false})
    });

    export function listThemes() {
        return Object.values(THEMES);
    }

    export function isKnownTheme(id) {
        return Object.prototype.hasOwnProperty.call(THEMES, normalize(id));
    }

    function normalize(id) {
        return String(id ?? '').trim().toLowerCase();
    }

    function prefersDark() {
        try {
            return !!window.matchMedia?.('(prefers-color-scheme: dark)')?.matches;
        } catch {
            return false;
        }
    }

    /** Maps a preference ('auto' | '<id>') to a concrete registered theme, or null. */
    function concrete(preference) {
        const id = normalize(preference);
        if (!id) return null;
         if (id === AUTO_THEME) return prefersDark() ? DARK_FALLBACK : LIGHT_FALLBACK;
        return isKnownTheme(id) ? id : null;
    }
     /** Next theme in registry order — powers the HUD cycle control. */
     export function cycleTheme() {
         const ids = Object.keys(THEMES);
         const next = ids[(ids.indexOf(current) + 1) % ids.length];
         return setTheme(next);
     }

    /** The preference as asked for ('auto' is preserved so the OS keeps being tracked). */
    let requested = DEFAULT_THEME;
    /** The concrete theme currently on the document. */
    let current = DEFAULT_THEME;
    let mediaListenerInstalled = false;

    export function getTheme() {
        return current;
    }

    export function getRequestedTheme() {
        return requested;
    }

    export function isDarkTheme() {
        return !!THEMES[current]?.dark;
    }

    function readPersisted() {
        try {
            return localStorage.getItem(STORAGE_KEY);
        } catch {
            return null;
        }
    }

    function persist(preference) {
        try {
            localStorage.setItem(STORAGE_KEY, preference);
        } catch (err) {
            log.warn('Could not persist theme preference', err);
        }
    }

    /**
     * Resolution order (first usable hit wins):
     *   1. explicit preference passed by the embedder
     *   2. `?theme=<id>` (search string, then hash query)
     *   3. persisted preference (localStorage)
     *   4. 'auto' — follow `prefers-color-scheme`
     */
    export function resolveThemePreference(explicit, loc = window.location) {
        if (explicit) {
            if (concrete(explicit)) return normalize(explicit);
            log.warn('Unknown theme requested by embedder; ignoring', {
                theme: explicit,
                known: Object.keys(THEMES)
            });
        }

        const fromQuery =
            new URLSearchParams(loc.search || '').get(QUERY_PARAM) ||
            new URLSearchParams(String(loc.hash || '').split('?')[1] || '').get(QUERY_PARAM);
        if (fromQuery) {
            if (concrete(fromQuery)) {
                log.debug('Theme from query param', {theme: normalize(fromQuery)});
                return normalize(fromQuery);
            }
            log.warn('Unknown theme in query param; falling back', {
                theme: fromQuery,
                known: [...Object.keys(THEMES), AUTO_THEME]
            });
        }

        const stored = readPersisted();
        if (stored && concrete(stored)) return normalize(stored);

        return AUTO_THEME;
    }

    /** Writes the attribute + mirrors the state for CSS/JS consumers. */
    function paint(themeId) {
        const previous = current;
        current = themeId;

        const root = document.documentElement;
        root.setAttribute(ATTRIBUTE, themeId);
        // Native form controls / scrollbars must follow the theme too.
        root.style.colorScheme = THEMES[themeId]?.dark ? 'dark' : 'light';
        document.body?.classList.toggle('theme-dark', !!THEMES[themeId]?.dark);

        if (previous !== themeId) {
            log.info('Theme applied', {theme: themeId, requested});
            bus.emit(Events.THEME_CHANGED, {theme: themeId, requested});
        }
        return themeId;
    }

    function watchSystemTheme() {
        if (mediaListenerInstalled) return;
        let query;
        try {
            query = window.matchMedia?.('(prefers-color-scheme: dark)');
        } catch {
            return;
        }
        if (!query) return;
        const handler = () => {
            if (requested !== AUTO_THEME) return; // an explicit choice beats the OS
            paint(concrete(AUTO_THEME) || DEFAULT_THEME);
        };
        if (typeof query.addEventListener === 'function') query.addEventListener('change', handler);
        else if (typeof query.addListener === 'function') query.addListener(handler);
        mediaListenerInstalled = true;
    }

    /**
     * Sets (and by default persists) the preference. Accepts any registered id or 'auto'.
     * Exposed on `window.__cognotik.setTheme` for console/embedder use.
     */
    export function setTheme(preference, {persist: shouldPersist = true} = {}) {
        const resolved = concrete(preference);
        if (!resolved) {
            log.warn('Ignoring unknown theme', {theme: preference, known: Object.keys(THEMES)});
            return current;
        }
        requested = normalize(preference);
        if (shouldPersist) persist(requested);
        return paint(resolved);
    }

    /** Convenience for a toggle control: light <-> dark. */
    export function toggleTheme() {
        return setTheme(isDarkTheme() ? 'light' : 'dark');
    }

    /**
     * Boot step 0 (before any content is mounted, so nothing flashes).
     * A theme arriving via `?theme=` is remembered so in-app navigation keeps it.
     */
    export function installTheme({theme, location = window.location, persist: shouldPersist = true} = {}) {
        requested = resolveThemePreference(theme, location);
        if (shouldPersist) persist(requested);
        watchSystemTheme();
        return paint(concrete(requested) || DEFAULT_THEME);
    }