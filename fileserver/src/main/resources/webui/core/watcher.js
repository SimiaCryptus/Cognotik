import {bus} from './bus.js';
import {store} from './store.js';
import {caps} from './capabilities.js';
import {dirname} from './paths.js';
import config from '../config.js';

let source = null;
let pending = new Set();
let flushTimer = null;
let backoff = 1000;

function setState(state, reason = null) {
    store.set({watcher: {...store.get().watcher, state, reason}});
    bus.emit('watcher:state', {state, reason});
}

function flush() {
    flushTimer = null;
    const dirs = Array.from(pending);
    pending = new Set();
    if (dirs.length) bus.emit('fs:changed', {dirs});
}

function queue(path) {
    pending.add(dirname(path));
    if (!flushTimer) flushTimer = setTimeout(flush, config.watchCoalesceMs);
}

export const watcher = {
    start(path = '/', {recursive = true} = {}) {
        if (caps.watch !== 'sse' || !config.features.watch) {
            setState('off', caps.watch === 'none' ? 'ENOSYS' : caps.watch);
            return;
        }
        this.stop();
        const url = new URL(caps.__base || '', location.href);
        void url; // base is embedded in the client URL below
        const href = watcher.urlFor(path, recursive);
        try {
            source = new EventSource(href, {withCredentials: false});
        } catch (e) {
            setState('off', 'ENETWORK');
            return;
        }
        source.addEventListener('ready', () => {
            backoff = 1000;
            setState('live');
        });
        source.addEventListener('change', (event) => {
            store.set({watcher: {...store.get().watcher, lastEventAt: Date.now(), state: 'live'}});
            let payload = null;
            try {
                payload = JSON.parse(event.data);
            } catch (e) {
                return;
            }
            queue(payload.path);
            bus.emit('fs:event', payload);
        });
        source.addEventListener('overflow', () => {
            bus.emit('fs:overflow', {});
            setState('live', 'overflow');
        });
        source.onerror = () => {
            source?.close();
            source = null;
            setState('off', 'reconnecting');
            const delay = Math.min(backoff, 30000) * (0.75 + Math.random() * 0.5);
            backoff = Math.min(backoff * 2, 30000);
            setTimeout(() => watcher.start(path, {recursive}), delay);
        };
    },
    /** Overridden at boot with the concrete FS API base. */
    urlFor(path, recursive) {
        return `.fsapi/v1/watch?path=${encodeURIComponent(path)}&recursive=${recursive}`;
    },
    stop() {
        if (source) {
            source.close();
            source = null;
        }
        setState('off');
    },
};
