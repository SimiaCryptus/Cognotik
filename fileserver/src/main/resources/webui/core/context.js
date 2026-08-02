import {caps} from './capabilities.js';
import {fs} from './fsclient.js';
import {ui} from './ui.js';
import {t} from './i18n.js';
import {commonAncestor} from './paths.js';

const sources = new Map();      // origin -> () => partial context
const providers = [];           // (key, ctx) => value | undefined
let activeOrigin = 'explorer';

export function registerContextSource(origin, fn) {
    sources.set(origin, fn);
    return () => sources.delete(origin);
}

/** JetBrains-style DataProvider chain for extensible data keys. */
export function registerDataProvider(fn) {
    providers.push(fn);
    return () => {
        const index = providers.indexOf(fn);
        if (index >= 0) providers.splice(index, 1);
    };
}

export function setActiveOrigin(origin) {
    if (sources.has(origin)) activeOrigin = origin;
}

export function getActiveOrigin() {
    return activeOrigin;
}

/**
 * Snapshots the context at invocation time: a running action never re-reads
 * the live selection, so a watch refresh mid-run cannot retarget it.
 */
export function buildContext(options = {}) {
    const origin = options.origin || activeOrigin;
    let base = {};
    try {
        base = sources.get(origin)?.() || {};
    } catch (e) {
        console.warn('context source failed', e);
    }
    /* The active editor is global state: menus, the palette and keybindings all
       need it regardless of which surface produced the invocation. */
    let editorBase = base;
    if (origin !== 'editor' && !base.activeTab) {
        try {
            editorBase = sources.get('editor')?.() || {};
        } catch (e) {
            editorBase = {};
        }
    }


    const resources = (options.resources || base.resources || []).slice(0, caps.limits.maxContextResources);
    const truncated = (options.resources || base.resources || []).length > resources.length;
    const paths = resources.map((r) => r.path);
    const editorSelection = options.editorSelection !== undefined
        ? options.editorSelection
        : (base.editorSelection || editorBase.editorSelection || null);

    const ctx = {
        origin,
        anchor: options.anchor || null,
        resources,
        paths,
        files: resources.filter((r) => r.type === 'file'),
        dirs: resources.filter((r) => r.type === 'dir'),
        commonAncestor: commonAncestor(paths),
        truncated,
        count: (options.resources || base.resources || []).length,
        activeTab: base.activeTab || editorBase.activeTab || null,
        editor: base.editor || editorBase.editor || null,
        editorSelection,
        selection: paths,
        caps,
        /* Only a read-only *file* makes the context read-only. A folder we may
           not write to can still contain files whitelisted in `.writeable`, so
           the parent's mode must never pre-emptively disable an operation —
           the server answers EACCES when it really means it. */
        readOnly: caps.readOnly || resources.some((r) => r.type === 'file' && r.readOnly),
        fs,
        ui,
        t,
        signal: options.signal || null,
        progress: options.progress || (() => {
        }),
        get(key, fallback) {
            if (key in this) return this[key];
            for (let i = providers.length - 1; i >= 0; i--) {
                const value = providers[i](key, this);
                if (value !== undefined) return value;
            }
            return fallback;
        },
    };
    return ctx;
}