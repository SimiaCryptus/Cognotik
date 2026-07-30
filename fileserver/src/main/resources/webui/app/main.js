import config from '../config.js';
import {h, clear} from '../core/dom.js';
import {store} from '../core/store.js';
import {bus} from '../core/bus.js';
import {persist} from '../core/persist.js';
import {initA11y, announce} from '../core/a11y.js';
import {initFsClient, fs} from '../core/fsclient.js';
import {caps, initCapabilities} from '../core/capabilities.js';
import {initKeymap, rebuildKeymap} from '../core/keymap.js';
import {watcher} from '../core/watcher.js';
import {allCommands, isVisible, isEnabled, executeCommand} from '../core/commands.js';
import {initToasts} from '../components/overlays/Toasts.js';
import {initModal} from '../components/overlays/Modal.js';
import {initQuickPick} from '../components/overlays/QuickPick.js';
import {AppShell} from '../components/AppShell.js';
import {registerContributions} from './contributions.js';
import {tabs} from '../components/tabs/TabModel.js';

/** Exposed so contributions.js can build palette items without a cycle. */
window.__fsCommands = {allCommands, isVisible, isEnabled};

const root = document.getElementById('fs-root');

async function probe(base) {
    if (!base) return false;
    try {
        const response = await fetch(`${base.replace(/\/$/, '')}/meta`, {credentials: 'same-origin'});
        if (!response.ok) return false;
        const meta = await response.json();
        return typeof meta?.apiVersion === 'number';
    } catch (e) {
        return false;
    }
}

function candidates() {
    const out = [];
    const meta = document.querySelector('meta[name="fs-api-base"]')?.content;
    const query = new URLSearchParams(location.search).get('api');
    const session = new URLSearchParams(location.search).get('session');
    const stored = persist.global('apiBase');
    /* Derived from the servlet layout: /ui/ and /fileIndex/<session>/ are siblings. */
    const prefix = location.pathname.replace(/\/ui(\/.*)?$/, '');
    if (query) out.push(query);
    if (session) out.push(`${prefix}/fileIndex/${session}/.fsapi/v1`);
    if (meta) out.push(meta);
    if (stored) out.push(stored);
    /* Derived from the CLI layout: /ui/ -> /files/root/.fsapi/v1 */
    out.push(`${prefix}/files/root/.fsapi/v1`);
    out.push(`${prefix}/.fsapi/v1`);
    return out.map((value) => value.replace(/\/$/, '')).filter((value, index, list) => list.indexOf(value) === index);
}

async function resolveBase() {
    for (const candidate of candidates()) {
        // eslint-disable-next-line no-await-in-loop
        if (await probe(candidate)) return candidate;
    }
    return null;
}

function fatal(message, {code, retry} = {}) {
    clear(root);
    root.removeAttribute('aria-busy');
    const input = h('input', {type: 'text', id: 'fs-api-input', placeholder: '/files/root/.fsapi/v1'});
    root.appendChild(h('div', {class: 'fs-binary', role: 'alert'}, [
        h('h1', {text: 'Cannot reach the file service'}),
        h('p', {text: message}),
        code ? h('p', {text: `Error code: ${code}`}) : null,
        h('div', {class: 'fs-field'}, [
            h('label', {htmlFor: 'fs-api-input', text: 'FS API base URL'}), input,
        ]),
        h('div', {}, [
            h('button', {
                type: 'button', text: 'Retry',
                onclick: () => {
                    if (input.value.trim()) persist.global('apiBase', input.value.trim().replace(/\/$/, ''));
                    (retry || (() => location.reload()))();
                },
            }),
            h('a', {href: '../files/root/', text: 'Use the classic file browser'}),
        ]),
    ]));
}

async function boot() {
    initA11y(document.body);
    initToasts(document.body);
    initModal();
    initQuickPick();

    const base = await resolveBase();
    if (!base) {
        fatal('No FS API endpoint answered /meta. Enter the base URL below, or pass ?api=…', {code: 'ENETWORK'});
        return;
    }
    persist.global('apiBase', base);
    persist.init(base);
    store.set({base, classicBase: base.replace(/\/\.fsapi\/v1$/, '')});

    initFsClient(base);
    watcher.urlFor = (path, recursive) => fs.watchUrl(path, {recursive});

    try {
        await initCapabilities(fs);
    } catch (error) {
        fatal('The server answered /meta with an error.', {code: error?.code});
        return;
    }

    /* GET /actions: verify assumptions and ingest server-declared descriptors. */
    fs.actions().then((payload) => {
        if (payload?.actions?.length) bus.emit('actions:descriptors', payload);
    }).catch(() => { /* purely additive; ignore */
    });

    if (typeof window.ThemeManager !== 'undefined') {
        try {
            window.ThemeManager.init();
        } catch (e) { /* optional */
        }
    }
    const theme = persist.get('theme', 'auto');
    document.documentElement.setAttribute('data-theme', theme);
    document.documentElement.setAttribute('data-accent', persist.get('accent', 'indigo'));
    document.documentElement.setAttribute('data-density', persist.get('layout', {})?.density || 'comfortable');

    clear(root);
    root.removeAttribute('aria-busy');
    const shell = new AppShell();
    shell.el = shell.render();
    root.replaceWith(shell.el);
    shell.mounted();
    registerContributions({shell});
    rebuildKeymap();
    initKeymap(document);

    await restoreSession();
    applyHash();
    window.addEventListener('hashchange', applyHash);
    window.addEventListener('beforeunload', (event) => {
        if (!tabs.dirtyTabs().length) return;
        event.preventDefault();
        event.returnValue = '';
    });

    if (caps.watch === 'sse') watcher.start('/', {recursive: true});
    else store.set({watcher: {state: 'off', reason: caps.watch}});

    announce('Workspace ready');
}

async function restoreSession() {
    const saved = persist.get('tabs', []) || [];
    const missing = [];
    for (const entry of saved) {
        try {
            // eslint-disable-next-line no-await-in-loop
            await tabs.open(entry.path, {pinned: entry.pinned !== false, line: entry.line, col: entry.col});
        } catch (e) {
            missing.push(entry.path);
        }
    }
    if (missing.length) {
        (await import('../core/ui.js')).ui.toast({
            severity: 'info',
            message: `${missing.length} previously open file(s) are gone`,
        });
    }
    const active = persist.get('active');
    const activeTab = active && tabs.byPath(active);
    if (activeTab) tabs.activate(activeTab.id);

    tabs.onChange(() => {
        persist.patch({
            tabs: tabs.list().filter((tab) => !tab.virtual).map((tab) => ({
                path: tab.path, pinned: tab.pinned, line: tab.line, col: tab.col,
            })),
            active: tabs.activeTab()?.path || null,
        });
    });
}

/** Deep links: #/src/main.kt:120:8 (file), #/src/ (reveal folder). */
function applyHash() {
    const raw = decodeURIComponent(location.hash.replace(/^#/, ''));
    if (!raw || raw === '/') return;
    const match = /^(.*?)(?::(\d+))?(?::(\d+))?$/.exec(raw);
    const path = match?.[1] || raw;
    const line = match?.[2] ? Number(match[2]) : undefined;
    const col = match?.[3] ? Number(match[3]) : undefined;
    if (path.endsWith('/')) {
        bus.emit('explorer:reveal', path.replace(/\/$/, '') || '/');
        return;
    }
    import('../core/ui.js').then(({ui}) => ui.openPath(path, {pinned: true, line, col}));
}

bus.on('tab:activated', (tab) => {
    if (!tab || tab.virtual) return;
    const hash = `#${tab.path}`;
    if (location.hash !== hash) history.replaceState(null, '', hash);
});

/** Task plumbing (status bar + cancellation) for actions and uploads. */
(async () => {
    const {ui} = await import('../core/ui.js');
    let seq = 0;
    ui.task = ({label, cancel} = {}) => {
        const id = `task${++seq}`;
        const task = {id, label: label || 'Working', message: '', cancel};
        store.set({tasks: [...store.get().tasks, task]});
        return {
            progress(message) {
                task.message = message;
                store.set({tasks: [...store.get().tasks]});
            },
            done() {
                store.set({tasks: store.get().tasks.filter((t) => t.id !== id)});
            },
        };
    };
    void executeCommand;
})();

boot().catch((error) => {
    console.error(error);
    fatal(error?.message || 'Unexpected start-up failure', {code: error?.code});
});