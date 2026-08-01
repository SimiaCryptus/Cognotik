/**
 * Runtime configuration for the shared `/lib/app/` modules.
 *
 * Because these modules moved from `<app>/utils/<module>.js` to the host-absolute
 * path `/lib/app/<module>.js`, they can no longer assume that the page that loaded
 * them lives at the server root. Anything that talks to an *absolute* server
 * endpoint (`/docops`, `/proxy/...`, `/apiProviders/...`) must be resolved through
 * `serverUrl()` so that a deployment behind a path prefix keeps working.
 *
 * Resolution order (first non-empty wins):
 *   1. Values passed to `configure({...})` at runtime.
 *   2. `window.COGNOTIK_CONFIG` (set by the loading HTML page, before the module script).
 *   3. `<meta name="cognotik-server-base" content="...">` (and friends).
 *   4. `<html data-server-base="...">`.
 *   5. Built-in defaults (empty prefix == same host root).
 */

const DEFAULTS = {
    /** Prefix for absolute server endpoints. '' means "host root". */
    serverBase: '',
    /** Optional explicit overrides — normally derived from the URL instead. */
    appId: null,
    sessionId: null,
    basePath: null,
    /** Optional explicit endpoint for the session list used by menu.js. */
    sessionsEndpoint: null,
    /** Optional template for the IDE view, e.g. '{appRoot}/ui/?session={sessionId}#/' */
    ideUrlTemplate: '{appRoot}/ui/?session={sessionId}#/'
};

let overrides = {};
let cached = null;

function readMeta(name) {
    const el = document.querySelector(`meta[name="${name}"]`);
    return el ? (el.getAttribute('content') || '') : '';
}

function readDataset(name) {
    const root = document.documentElement;
    return (root && root.dataset && root.dataset[name]) || '';
}

function fromDocument() {
    const global = (typeof window !== 'undefined' && window.COGNOTIK_CONFIG) || {};
    return {
        serverBase: global.serverBase
            || readMeta('cognotik-server-base')
            || readDataset('serverBase')
            || '',
        appId: global.appId || readMeta('cognotik-app-id') || null,
        sessionId: global.sessionId || readMeta('cognotik-session-id') || null,
        basePath: global.basePath || readMeta('cognotik-base-path') || null,
        sessionsEndpoint: global.sessionsEndpoint || readMeta('cognotik-sessions-endpoint') || null,
        ideUrlTemplate: global.ideUrlTemplate || readMeta('cognotik-ide-url-template') || null
    };
}

function stripTrailingSlash(s) {
    return (s || '').replace(/\/+$/, '');
}

/**
 * Get the effective configuration object (cached until `configure()` is called).
 * @returns {Object}
 */
export function getConfig() {
    if (!cached) {
        const doc = fromDocument();
        const merged = {...DEFAULTS};
        for (const key of Object.keys(DEFAULTS)) {
            if (overrides[key] !== undefined && overrides[key] !== null && overrides[key] !== '') {
                merged[key] = overrides[key];
            } else if (doc[key]) {
                merged[key] = doc[key];
            }
        }
        merged.serverBase = stripTrailingSlash(merged.serverBase);
        cached = merged;
    }
    return cached;
}

/**
 * Override configuration at runtime. Safe to call before or after other modules load.
 * @param {Object} next - Partial configuration
 * @returns {Object} The new effective configuration
 */
export function configure(next = {}) {
    overrides = {...overrides, ...next};
    cached = null;
    return getConfig();
}

/**
 * Resolve an absolute server path against the configured server base.
 * Absolute URLs (http/https/protocol-relative) are returned unchanged.
 * @param {string} path - e.g. '/docops' or '/proxy/usage'
 * @returns {string}
 */
export function serverUrl(path = '') {
    const base = getConfig().serverBase;
    if (!path) return base || '/';
    if (/^([a-z][a-z0-9+.-]*:)?\/\//i.test(path)) return path;
    if (!base) return path;
    return base + (path.startsWith('/') ? path : '/' + path);
}

export const ConfigUtils = {
    getConfig,
    configure,
    serverUrl
};