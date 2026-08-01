import {h, clear} from '../../core/dom.js';
import {fs} from '../../core/fsclient.js';
import {ui} from '../../core/ui.js';
import {bus} from '../../core/bus.js';
import {announce} from '../../core/a11y.js';
import {copyText} from '../../core/clipboard.js';
import {basename, dirname, join} from '../../core/paths.js';
import {publicUrl} from '../../core/urls.js';
import {parseDocOpsStatus, sessionUrl} from '../../core/sessions.js';
import config from '../../config.js';

/**
 * A `docops.status.json` rendered as what it actually is: a directory of live
 * agent sessions.
 *
 * Reached through a *virtual* tab whose stat carries `previewKind: 'docops-status'`
 * (see `ui.openPreview`), so the raw JSON still opens in Monaco for editing. Each
 * row links to its target document (an ordinary editor tab) and to its session,
 * which opens as a hosted chat tab rather than a pop-up.
 */
const FILTERS = [
    {id: 'all', label: 'All'},
    {id: 'running', label: 'Running'},
    {id: 'completed', label: 'Completed'},
];
/** Above this, opening "all" is a decision rather than a click. */
const BULK_CONFIRM = 8;

function formatTime(value) {
    if (!value) return null;
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString();
}

export class DocOpsStatusView {
    static id = 'docops-status';

    static canOpen(stat) {
        return stat?.previewKind === 'docops-status';
    }

    constructor(ctx) {
        this.ctx = ctx;
        this.path = ctx.tab.stat.sourcePath || ctx.tab.path;
        this.tasks = [];
        this.lastUpdated = null;
        this.filter = 'all';
        this.timer = null;

        this.summary = h('p', {class: 'fs-docops__summary', role: 'status'});
        this.list = h('div', {class: 'fs-docops__list', role: 'list'});
        this.body = h('div', {
            class: 'fs-docops', tabindex: '0',
            'aria-label': `Sessions described by ${basename(this.path)}`,
        }, [this.summary, this.list]);

        this.filterSelect = h('select', {
            'aria-label': 'Filter tasks',
            onchange: (event) => {
                this.filter = event.target.value;
                this.paint();
            },
        }, FILTERS.map((filter) => h('option', {value: filter.id, text: filter.label})));

        this.status = h('span', {class: 'fs-preview__status', role: 'status'});
        this.el = h('div', {class: 'fs-preview'}, [
            h('div', {class: 'fs-preview__toolbar', role: 'toolbar', 'aria-label': 'DocOps sessions'}, [
                h('span', {text: basename(this.path)}),
                this.filterSelect,
                h('span', {style: {flex: '1'}}),
                this.status,
                h('button', {
                    type: 'button', text: '⧉ Open all',
                    title: 'Open every listed session in its own tab',
                    onclick: () => this.openAll(),
                }),
                h('button', {type: 'button', text: '⟳ Reload', onclick: () => this.reload()}),
                h('button', {
                    type: 'button', text: '✎ Edit JSON',
                    onclick: () => ui.openPath(this.path, {pinned: true}),
                }),
                h('a', {href: publicUrl(this.path), target: '_blank', rel: 'noopener', text: '↗ Raw'}),
            ]),
            this.body,
        ]);

        /* Status documents are written while the agents run. */
        this.offEvent = bus.on('fs:event', (event) => {
            if (event?.path === this.path) this.reload();
        });
    }

    async load() {
        await this.reload();
    }

    async reload() {
        clearTimeout(this.timer);
        this.timer = null;
        this.status.textContent = 'Loading…';
        let text = '';
        try {
            text = (await fs.readText(this.path)).text ?? '';
        } catch (error) {
            this.fail(`Could not read ${basename(this.path)}: ${error?.message || error}`);
            return;
        }
        let parsed;
        try {
            parsed = parseDocOpsStatus(text);
        } catch (error) {
            this.fail(`${basename(this.path)} is not a status document (${error.message})`);
            return;
        }
        this.lastUpdated = parsed.lastUpdated;
        this.tasks = parsed.tasks;
        /* One probe resolves the URL template for the whole document. */
        await Promise.all(this.tasks.map(async (task) => {
            task.url = task.sessionId ? await sessionUrl(task.sessionId) : null;
        }));
        this.status.textContent = '';
        this.paint();
        this.schedule();
    }

    fail(message) {
        this.status.textContent = '';
        this.summary.textContent = '';
        clear(this.list);
        this.list.appendChild(h('p', {class: 'fs-docops__empty', text: message}));
    }

    /** Poll only while something is still running (watch events cover the rest). */
    schedule() {
        const interval = config.sessions?.pollMs ?? 0;
        if (!interval || !this.tasks.some((task) => task.running)) return;
        this.timer = setTimeout(() => this.reload(), interval);
    }

    matches(task) {
        if (this.filter === 'running') return task.running;
        if (this.filter === 'completed') return task.status === 'COMPLETED' || task.status === 'DONE';
        return true;
    }

    paint() {
        clear(this.list);
        const running = this.tasks.filter((task) => task.running).length;
        this.summary.textContent = [
            `${this.tasks.length} task(s)`,
            running ? `${running} running` : null,
            this.lastUpdated ? `updated ${formatTime(this.lastUpdated)}` : null,
        ].filter(Boolean).join(' · ');
        const visible = this.tasks.filter((task) => this.matches(task));
        if (!visible.length) {
            this.list.appendChild(h('p', {
                class: 'fs-docops__empty',
                text: this.tasks.length ? 'No task matches this filter' : 'No tasks recorded yet',
            }));
            return;
        }
        for (const task of visible) this.list.appendChild(this.row(task));
    }

    row(task) {
        const target = this.targetPath(task);
        const meta = [
            task.sessionId,
            task.startedAt ? `started ${formatTime(task.startedAt)}` : null,
            task.completedAt ? `completed ${formatTime(task.completedAt)}` : null,
            task.message,
        ].filter(Boolean).join(' · ');
        return h('div', {class: 'fs-docops__row', role: 'listitem', dataset: {status: task.status}}, [
            h('span', {
                class: 'fs-docops__status', 'data-status': task.status,
                text: task.status.toLowerCase().replace(/_/g, ' '),
            }),
            h('div', {class: 'fs-docops__main'}, [
                h('button', {
                    type: 'button', class: 'fs-docops__target', title: `Open ${target}`,
                    text: task.target, onclick: () => this.openTarget(target),
                }),
                h('p', {class: 'fs-docops__meta', title: meta, text: meta || '—'}),
            ]),
            h('div', {class: 'fs-docops__actions'}, task.url ? [
                h('button', {
                    type: 'button', class: 'fs-docops__open', text: 'Open session',
                    onclick: () => this.openSession(task),
                }),
                h('button', {
                    type: 'button', text: '⧉', title: 'Copy the session link',
                    'aria-label': `Copy the link to session ${task.sessionId}`,
                    onclick: () => this.copyLink(task),
                }),
                h('a', {
                    href: task.url, target: '_blank', rel: 'noopener', text: '↗',
                    title: 'Open in a new browser tab',
                    'aria-label': `Open session ${task.sessionId} in a new browser tab`,
                }),
            ] : [h('span', {class: 'fs-preview__status', text: 'no session'})]),
        ]);
    }

    /** Targets are recorded relative to the folder holding the status document. */
    targetPath(task) {
        const target = String(task.target || '').trim();
        return target.startsWith('/') ? target : join(dirname(this.path), target);
    }

    async openTarget(path) {
        try {
            await ui.openPath(path, {pinned: true});
        } catch (error) {
            ui.toast({severity: 'warn', message: `${basename(path)} is not in this workspace`});
        }
    }

    async openSession(task) {
        const url = task.url || (task.sessionId ? await sessionUrl(task.sessionId) : null);
        if (!url) return;
        /* Hosted as a tab: a pop-up blocker cannot lose it (see ChatSession). */
        if (typeof ui.openChat === 'function') {
            await ui.openChat({url, name: basename(task.target) || task.sessionId});
            announce(`Opened session ${task.sessionId}`);
            return;
        }
        window.open(url, '_blank', 'noopener');
    }

    async openAll() {
        const targets = this.tasks.filter((task) => this.matches(task) && task.url);
        if (!targets.length) {
            ui.toast({severity: 'info', message: 'No session to open'});
            return;
        }
        if (targets.length > BULK_CONFIRM) {
            const ok = await ui.confirm({
                title: `Open ${targets.length} sessions?`,
                body: 'Each session opens in its own tab.',
                confirmLabel: 'Open all',
            });
            if (!ok) return;
        }
        for (const task of targets) await this.openSession(task);
    }

    async copyLink(task) {
        const ok = await copyText(task.url);
        ui.toast({
            severity: ok ? 'info' : 'error',
            message: ok ? 'Session link copied' : 'Could not access the clipboard',
        });
    }

    focus() {
        this.body.focus();
    }

    dispose() {
        clearTimeout(this.timer);
        this.offEvent?.();
    }
}