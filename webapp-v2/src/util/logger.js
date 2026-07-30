import {LOG_LEVEL} from '../config/env.js';

/** reverse-spec §16 — bracketed subsystem prefixes, dev-gated debug, always-on warn/error. */

const LEVELS = {debug: 10, info: 20, warn: 30, error: 40};
const threshold = LEVELS[LOG_LEVEL] ?? LEVELS.warn;

export function createLogger(subsystem) {
    const tag = `[${subsystem}]`;
    /** Error/diagnostic counters — the only observability into tab-state bugs (§16). */
    const counters = Object.create(null);

    return {
        tag,
        debug(...args) {
            if (threshold <= LEVELS.debug) console.debug(tag, ...args);
        },
        info(...args) {
            if (threshold <= LEVELS.info) console.info(tag, ...args);
        },
        warn(...args) {
            console.warn(tag, ...args);
        },
        error(...args) {
            console.error(tag, ...args);
        },
        /** Increment a named counter and return the new value. */
        count(name, delta = 1) {
            counters[name] = (counters[name] || 0) + delta;
            return counters[name];
        },
        counters() {
            return {...counters};
        }
    };
}

/** Renders a fatal error panel instead of a blank page (§16). */
export function renderFatalError(error, mount = document.getElementById('root') || document.body) {
    const panel = document.createElement('div');
    panel.className = 'fatal-error';
    panel.setAttribute('role', 'alert');
    const message = document.createElement('div');
    message.className = 'fatal-error-message';
    message.textContent = `Something went wrong: ${error?.message || String(error)}`;
    panel.appendChild(message);
    if (threshold <= LEVELS.debug && error?.stack) {
        const pre = document.createElement('pre');
        pre.className = 'fatal-error-stack';
        pre.textContent = error.stack;
        panel.appendChild(pre);
    }
    mount.appendChild(panel);
}