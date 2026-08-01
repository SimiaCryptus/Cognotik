import {createLogger} from '../util/logger.js';

/** reverse-spec §13 — single source of truth for archive mode (fixes §20.7). */

const log = createLogger('Archive');

export const isArchive =
    document.documentElement.hasAttribute('data-archive') ||
    window.location.pathname.includes('/archive/');

/** Hydrates from `#archived-messages`; parse failure logs and yields no messages. */
export function loadArchivedMessages() {
    if (!isArchive) return [];
    const node = document.getElementById('archived-messages');
    if (!node) {
        log.warn('Archive mode with no #archived-messages payload');
        return [];
    }
    try {
        const parsed = JSON.parse(node.textContent || '[]');
        if (!Array.isArray(parsed)) {
            log.error('Archived payload is not an array');
            return [];
        }
        log.info('Hydrated archived messages', {count: parsed.length});
        return parsed;
    } catch (err) {
        log.error('Failed to parse archived messages', err);
        return [];
    }
}

export function applyArchiveBodyClass() {
    if (isArchive) document.body.classList.add('archive-mode');
}