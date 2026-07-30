import {createLogger} from '../util/logger.js';

/** reverse-spec §3.4 / §3.5 — frame decode/encode. */

const log = createLogger('Protocol');

/** Content is treated as HTML when it matches this (§3.4). */
export const HTML_PATTERN = /<[a-z][\s\S]*>/i;

/**
 * Inbound data frame: `<id>,<version>,<content>`.
 * Split on the FIRST TWO commas only — content may contain commas, newlines and raw HTML.
 * Returns null for malformed frames (caller logs + drops).
 */
export function decodeDataFrame(raw) {
    if (typeof raw !== 'string') return null;

    const firstComma = raw.indexOf(',');
    if (firstComma < 0) return null;
    const secondComma = raw.indexOf(',', firstComma + 1);
    if (secondComma < 0) return null;

    const id = raw.slice(0, firstComma);
    const versionRaw = raw.slice(firstComma + 1, secondComma);
    const content = raw.slice(secondComma + 1);

    if (!id || !versionRaw) return null;

    let version = parseInt(versionRaw, 10);
    if (Number.isNaN(version)) {
        log.debug('Non-numeric version; substituting now()', {id, versionRaw});
        version = Date.now();
    }

    const isHtml = HTML_PATTERN.test(content);
    return {
        id,
        version,
        content,
        isHtml,
        rawHtml: isHtml ? content : null,
        timestamp: Date.now()
    };
}

/**
 * Control frames are JSON. A frame that fails JSON.parse is NOT an error: it is a
 * data frame. Detection must therefore be try/parse/dispatch/else-fall-through (§3.3).
 */
export function parseControlFrame(raw) {
    if (typeof raw !== 'string') return null;
    const trimmed = raw.trimStart();
    if (!trimmed.startsWith('{')) return null;
    try {
        const parsed = JSON.parse(trimmed);
        if (parsed && typeof parsed === 'object' && typeof parsed.type === 'string') return parsed;
    } catch {
        /* data frame */
    }
    return null;
}

export const CONTROL_TYPES = Object.freeze({PING: 'ping', PONG: 'pong', CONNECT: 'connect'});

export function encodePing() {
    return JSON.stringify({type: CONTROL_TYPES.PING, timestamp: Date.now()});
}

export function encodePong() {
    return JSON.stringify({type: CONTROL_TYPES.PONG, timestamp: Date.now()});
}

/** `!<messageId>,<action>` — actions are never whitelisted (§3.5). */
export function encodeAction(messageId, action) {
    return `!${messageId},${action}`;
}

/** `!<messageId>,userTxt,<encodeURIComponent(text)>` */
export function encodeUserText(messageId, text) {
    return `!${messageId},userTxt,${encodeURIComponent(text)}`;
}