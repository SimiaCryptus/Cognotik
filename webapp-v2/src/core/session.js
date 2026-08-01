import {createLogger} from '../util/logger.js';

/** reverse-spec §2 — session identity. */

const log = createLogger('Session');

function yyyymmdd(date = new Date()) {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}${m}${d}`;
}

function randomBase36(length) {
    let out = '';
    while (out.length < length) {
        out += Math.random().toString(36).slice(2);
    }
    return out.slice(0, length);
}

/** U-YYYYMMDD-<6 chars base36> */
export function generateUserSessionId() {
    return `U-${yyyymmdd()}-${randomBase36(6)}`;
}

/**
 * G-YYYYMMDD-<4 chars> where the tail is a URL-munged base64 of a random int64,
 * filtered to [A-Za-z0-9]. Used by the (deferred) menubar's "new global session".
 */
export function generateGlobalSessionId() {
    const bytes = new Uint8Array(8);
    if (typeof crypto !== 'undefined' && crypto.getRandomValues) crypto.getRandomValues(bytes);
    else for (let i = 0; i < 8; i++) bytes[i] = Math.floor(Math.random() * 256);
    let raw = '';
    for (const b of bytes) raw += String.fromCharCode(b);
    const munged = btoa(raw).replace(/=/g, '').replace(/\//g, '.').replace(/\+/g, '-');
    const filtered = munged.replace(/[^A-Za-z0-9]/g, '');
    return `G-${yyyymmdd()}-${filtered.slice(0, 4)}`;
}

/**
 * Resolution order (first hit wins):
 *   1. explicit sessionId passed by the embedder
 *   2. ?session=<id>
 *   3. #<id>
 *   4. freshly generated user id
 */
export function resolveSessionId(explicit, loc = window.location) {
    if (explicit) return explicit;

    const fromQuery = new URLSearchParams(loc.search).get('session');
    if (fromQuery) {
        log.debug('Session from query param', {sessionId: fromQuery});
        return fromQuery;
    }

    const fromHash = (loc.hash || '').replace(/^#/, '');
    if (fromHash) {
        log.debug('Session from fragment', {sessionId: fromHash});
        return fromHash;
    }

    const generated = generateUserSessionId();
    log.info('Generated new session id', {sessionId: generated});
    return generated;
}