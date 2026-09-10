/**
 * store.js — versioned, autosaving persistence envelope.
 *
 * Everything the roadmap adds (drafts + revisions, annotations, revision
 * queues) lives in a single `state.json` document with a `schemaVersion`
 * and a migration hook.
 */

import {readFile, writeFile} from '/app/fileIO.js';
import {SCHEMA_VERSION, nowIso} from './core.js';
import {pruneRevisions} from './drafts.js';

export const STATE_FILE = 'state.json';
const REVISION_RETENTION = 40;

const emptyState = () => ({
    schemaVersion: SCHEMA_VERSION,
    updatedAt: nowIso(),
    drafts: {},       // draftId -> Draft
    annotations: {},  // draftId -> Annotation[]
    queues: {}        // draftId -> RevisionQueue
});

/** Forward migrations live here; each step upgrades exactly one version. */
const MIGRATIONS = {
    // 0: state => ({...state, schemaVersion: 1})
};

export function migrate(raw) {
    if (!raw || typeof raw !== 'object') return emptyState();
    let state = {...emptyState(), ...raw};
    while (state.schemaVersion < SCHEMA_VERSION && MIGRATIONS[state.schemaVersion]) {
        state = MIGRATIONS[state.schemaVersion](state);
    }
    state.schemaVersion = SCHEMA_VERSION;
    for (const key of ['drafts', 'annotations', 'queues']) {
        if (!state[key] || typeof state[key] !== 'object') state[key] = {};
    }
    return state;
}

export function createStore(basePath, {saveDelayMs = 700} = {}) {
    let state = emptyState();
    let timer = null;
    let pending = null;
    const listeners = new Set();

    const notify = () => {
        for (const listener of [...listeners]) {
            try {
                listener(state);
            } catch (err) {
                console.warn('[store] listener failed', err);
            }
        }
    };

    const persist = async () => {
        const snapshot = {...state, updatedAt: nowIso()};
        for (const [id, draft] of Object.entries(snapshot.drafts)) {
            snapshot.drafts[id] = pruneRevisions(draft, REVISION_RETENTION);
        }
        await writeFile(basePath, STATE_FILE, JSON.stringify(snapshot, null, 2));
    };

    const schedule = () => {
        clearTimeout(timer);
        timer = setTimeout(() => {
            pending = persist().catch(err => console.warn('[store] save failed', err));
        }, saveDelayMs);
    };

    return {
        get: () => state,

        async load() {
            try {
                const text = await readFile(basePath, STATE_FILE);
                if (text?.trim()) state = migrate(JSON.parse(text));
            } catch (err) {
                console.warn('[store] load failed, starting empty:', err);
                state = emptyState();
            }
            notify();
            return state;
        },

        /** Mutate the live state; schedules a save and notifies subscribers. */
        update(mutator) {
            const result = mutator(state);
            if (result && typeof result === 'object') state = result;
            state.updatedAt = nowIso();
            schedule();
            notify();
            return state;
        },

        replace(next) {
            state = migrate(next);
            schedule();
            notify();
            return state;
        },

        async flush() {
            clearTimeout(timer);
            await persist();
            await pending;
        },

        subscribe(listener) {
            listeners.add(listener);
            listener(state);
            return () => listeners.delete(listener);
        },

        /** One JSON bundle: drafts + revisions + annotations + items. */
        exportBundle() {
            return JSON.stringify({
                kind: 'philosophical-calculator-bundle',
                schemaVersion: SCHEMA_VERSION,
                exportedAt: nowIso(),
                state
            }, null, 2);
        },

        importBundle(text) {
            const parsed = JSON.parse(text);
            const next = parsed?.state ?? parsed;
            if (!next || typeof next !== 'object') throw new Error('Not a valid bundle');
            return this.replace(next);
        }
    };
}