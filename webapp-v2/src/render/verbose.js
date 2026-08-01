import {debounce} from '../util/debounce.js';
import {createLogger} from '../util/logger.js';
import {bus, Events} from '../core/bus.js';

/** reverse-spec §15 — verbose-mode gating. */

const log = createLogger('Verbose');
const STORAGE_KEY = 'verboseMode';
const WRAPPER_CLASS = 'verbose-wrapper';
const VISIBLE_CLASS = 'verbose-visible';

let enabled = readPersisted();

function readPersisted() {
    try {
        return localStorage.getItem(STORAGE_KEY) === 'true';
    } catch {
        return false;
    }
}

function persist(value) {
    try {
        localStorage.setItem(STORAGE_KEY, value ? 'true' : 'false');
    } catch (err) {
        log.warn('Could not persist verbose flag', err);
    }
}

export function isVerbose() {
    return enabled;
}

/**
 * Wrap server-emitted `[class*="verbose"]` nodes exactly once at render time.
 * Toggling then only flips classes — the DOM is never re-wrapped (§15).
 */
export function applyVerboseWrappers(root) {
    if (!root) return;
    const candidates = root.querySelectorAll('[class*="verbose"]');
    candidates.forEach((node) => {
        if (node.classList.contains(WRAPPER_CLASS)) return;
        if (node.parentElement?.classList.contains(WRAPPER_CLASS)) return;
        if (!node.parentNode) return;
        const wrapper = document.createElement('span');
        wrapper.className = WRAPPER_CLASS + (enabled ? ` ${VISIBLE_CLASS}` : '');
        node.parentNode.insertBefore(wrapper, node);
        wrapper.appendChild(node);
    });
}

export function syncVerboseDom(root = document) {
    document.body.classList.toggle('verbose-mode', enabled);
    root.querySelectorAll(`.${WRAPPER_CLASS}`).forEach((wrapper) => {
        wrapper.classList.toggle(VISIBLE_CLASS, enabled);
    });
}

export function setVerbose(value) {
    if (enabled === value) return;
    enabled = value;
    persist(enabled);
    syncVerboseDom(document);
    log.info('Verbose mode', {enabled});
    bus.emit(Events.VERBOSE_CHANGED, enabled);
}

/** Debounced at 250ms per §15. */
export const toggleVerbose = debounce(() => setVerbose(!enabled), 250);

/** Ctrl/Cmd + Shift + V (§15). */
export function installVerboseShortcut(target = document) {
    const handler = (event) => {
        if (!(event.ctrlKey || event.metaKey) || !event.shiftKey) return;
        if (String(event.key).toLowerCase() !== 'v') return;
        event.preventDefault();
        toggleVerbose();
    };
    target.addEventListener('keydown', handler);
    return () => target.removeEventListener('keydown', handler);
}