import {Component} from './base.js';
import {h, clear} from '../core/dom.js';
import {store} from '../core/store.js';
import {bus} from '../core/bus.js';
import {tabs} from './tabs/TabModel.js';
import {allStatusItems} from '../core/registry.js';
import {executeCommand} from '../core/commands.js';
import {caps} from '../core/capabilities.js';

export class StatusBar extends Component {
    render() {
        this.pathCell = h('span', {class: 'fs-status__cell'});
        this.cursorCell = h('span', {class: 'fs-status__cell'});
        this.selectionCell = h('span', {class: 'fs-status__cell'});
        this.langCell = h('span', {class: 'fs-status__cell'});
        this.watchCell = h('button', {
            type: 'button', class: 'fs-status__cell',
            onclick: () => executeCommand('tree.refresh'),
        });
        this.taskCell = h('span', {class: 'fs-status__cell'});
        this.el = h('footer', {class: 'fs-status', role: 'contentinfo'}, [
            h('span', {class: 'sr-only', text: 'Status'}),
            this.pathCell, this.cursorCell, this.selectionCell, this.langCell,
            h('span', {style: {flex: '1'}}), this.taskCell, this.watchCell,
        ]);
        for (const item of allStatusItems()) {
            const el = item.create?.();
            if (el) this.el.appendChild(el);
        }
        return this.el;
    }

    mounted() {
        this.track(tabs.onChange(() => this.syncTab()));
        this.track(bus.on('editor:cursor', (info) => this.syncCursor(info)));
        this.track(store.subscribe(() => {
            this.syncWatcher();
            this.syncTasks();
        }));
        this.syncTab();
        this.syncWatcher();
    }

    syncTab() {
        const tab = tabs.activeTab();
        clear(this.pathCell);
        clear(this.langCell);
        if (!tab) {
            this.pathCell.textContent = 'No file open';
            this.cursorCell.textContent = '';
            return;
        }
        this.pathCell.textContent = tab.path + (tab.dirty ? ' •' : '');
        const flags = [];
        if (tab.readOnly || caps.readOnly) flags.push('read-only');
        if (tab.editorKind) flags.push(tab.editorKind);
        this.langCell.textContent = flags.join(' · ');
    }

    syncCursor({line, column, selectionCount} = {}) {
        this.cursorCell.textContent = line ? `Ln ${line}, Col ${column}` : '';
        this.selectionCell.textContent = selectionCount ? `${selectionCount} selected` : '';
    }

    syncWatcher() {
        const {state, reason} = store.get().watcher;
        const label = state === 'live' ? 'live' : state === 'polling' ? 'polling' : `off${reason ? ` (${reason})` : ''}`;
        this.watchCell.textContent = `Watch: ${label}`;
        this.watchCell.title = 'Refresh the tree';
    }

    syncTasks() {
        const tasks = store.get().tasks;
        clear(this.taskCell);
        if (!tasks.length) return;
        const task = tasks[tasks.length - 1];
        this.taskCell.append(
            h('span', {role: 'progressbar', 'aria-label': task.label, 'aria-valuetext': task.message || 'working'}),
            h('span', {text: `${task.label}${task.message ? `: ${task.message}` : '…'}`}),
            task.cancel ? h('button', {type: 'button', text: 'Cancel', onclick: () => task.cancel()}) : null,
        );
    }
}
