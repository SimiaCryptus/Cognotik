import {API_URL, IS_DEV} from './env.js';
import {createLogger} from '../util/logger.js';
import {bus, Events} from '../core/bus.js';

/** reverse-spec §12 — appInfo fetch, defaults and effects. */

const log = createLogger('AppConfig');

export const DEFAULT_APP_CONFIG = Object.freeze({
    applicationName: 'Chat App',
    inputCnt: 0,
    stickyInput: true,
    loadImages: true,
    showMenubar: true
});

let current = {...DEFAULT_APP_CONFIG};
let inflight = null;

export function getAppConfig() {
    return current;
}

/** `REACT_APP_API_URL` replacement, normalised to a trailing slash. */
export function baseUrl() {
    const raw = API_URL || `${window.location.origin}${window.location.pathname}`;
    return raw.endsWith('/') ? raw : `${raw}/`;
}

/** Memoised by the in-flight promise: exactly one request per session. */
export function fetchAppInfo(sessionId) {
    if (inflight) return inflight;

    const url = `${baseUrl()}appInfo?session=${encodeURIComponent(sessionId)}`;
    inflight = fetch(url, {headers: {Accept: 'application/json'}})
        .then((response) => {
            if (!response.ok) throw new Error(`appInfo HTTP ${response.status}`);
            const contentType = response.headers.get('content-type') || '';
            if (!contentType.includes('application/json') && !contentType.includes('text/json')) {
                throw new Error(`appInfo returned unexpected content-type: ${contentType}`);
            }
            return response.json();
        })
        .then((payload) => {
            current = {...DEFAULT_APP_CONFIG, ...(payload || {})};
            log.info('Loaded appInfo', current);
            bus.emit(Events.CONFIG_LOADED, current);
            return current;
        })
        .catch((err) => {
            log.warn('appInfo unavailable — using defaults', {url, error: err.message});
            current = {...DEFAULT_APP_CONFIG};
            bus.emit(Events.CONFIG_LOADED, current);
            return current;
        });

    return inflight;
}

/** §12 effects that are not owned by another module. */
export function applyAppConfig(config = current) {
    if (config.applicationName) document.title = config.applicationName;
    applyMenubarConfig(config.showMenubar !== false);
    document.body.classList.toggle('sticky-input', config.stickyInput !== false);
    document.body.classList.toggle('no-images', config.loadImages === false);
}

/** Legacy DOM contract retained verbatim (§12, §19.2.5). */
export function applyMenubarConfig(show) {
    const toolbar = document.getElementById('toolbar');
    const namebar = document.getElementById('namebar');
    const mainInput = document.getElementById('main-input');
    const session = document.getElementById('session');

    if (show) {
        if (toolbar) toolbar.style.display = '';
        if (namebar) namebar.style.display = '';
        return;
    }
    if (toolbar) toolbar.style.display = 'none';
    if (namebar) namebar.style.display = 'none';
    if (mainInput) mainInput.style.top = '0';
    if (session) {
        session.style.top = '0';
        session.style.width = '100%';
        session.style.position = 'absolute';
    }
}

/**
 * Transport config is derived from `location` in production and is not
 * user-overridable; development may persist an override (§12).
 */
export function loadDevWebSocketConfig() {
    if (!IS_DEV) return null;
    try {
        const raw = localStorage.getItem('websocketConfig');
        return raw ? JSON.parse(raw) : null;
    } catch (err) {
        log.warn('Invalid websocketConfig in localStorage', err);
        return null;
    }
}

export function saveDevWebSocketConfig(config) {
    if (!IS_DEV) return;
    try {
        localStorage.setItem('websocketConfig', JSON.stringify(config));
    } catch (err) {
        log.warn('Could not persist websocketConfig', err);
    }
}