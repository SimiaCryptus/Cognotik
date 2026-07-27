import {Component} from './../base.js';
import {h} from '../../core/dom.js';
import {fs} from '../../core/fsclient.js';
import {bus} from '../../core/bus.js';
import {caps} from '../../core/capabilities.js';
import {ui} from '../../core/ui.js';
import {announce} from '../../core/a11y.js';
import {raise, FsError} from '../../core/errors.js';
import {registerContextSource} from '../../core/context.js';
import {tabs} from '../tabs/TabModel.js';
import {pickEditor, readTextFor} from './EditorRegistry.js';
import {basename} from '../../core/paths.js';

/** Hosts one role="tabpanel" per tab, owns editor lifecycle and saving. */
export class EditorArea extends Component {
    render() {
        this.panels = new Map();
        this.empty = h('div', {class: 'fs-editor__empty'}, [
            h('p', {text: 'No file open.'}),
            h('p', {}, [h('kbd', {text: 'Ctrl/Cmd+P'}), ' to open a file, ',
                h('kbd', {text: 'Ctrl/Cmd+Shift+P'}), ' for the command palette.']),
        ]);
        this.el = h('div', {class: 'fs-editor', id: 'fs-editor'}, [this.empty]);
        return this.el;
    }

    mounted() {
        this.track(tabs.onChange(() => this.sync()));
        this.track(bus.on('tab:closed', (tab) => this.disposePanel(tab?.id)));
        this.track(bus.on('fs:event', (event) => this.reconcile(event)));
        this.track(registerContextSource('editor', () => {
            const tab = tabs.activeTab();
            const entry = tab ? this.panels.get(tab.id) : null;
            return {
                resources: tab && !tab.virtual
                    ? [{
                        path: tab.path,
                        name: tab.name,
                        type: 'file',
                        size: tab.size,
                        readOnly: tab.readOnly,
                        mimeType: tab.mimeType
                    }]
                    : [],
                activeTab: tab,
                editor: entry?.editor ? {kind: entry.editorId, ...entry.editor} : null,
                editorSelection: entry?.editor?.getSelection?.() || null,
            };
        }));
        ui.openPath = (path, options) => tabs.open(path, options);
        ui.openVirtualDocument = ({title, languageId, content}) => tabs.open(`fs-virtual:/${title}`, {
            virtual: true, pinned: true,
            stat: {
                path: `fs-virtual:/${title}`,
                type: 'file',
                size: content?.length || 0,
                readOnly: true,
                languageId,
                content,
                mimeType: 'text/plain'
            },
        });
        this.sync();
    }

    async sync() {
        const active = tabs.activeTab();
        this.empty.hidden = !!active;
        for (const [id, entry] of this.panels) {
            const isActive = active?.id === id;
            entry.panel.dataset.active = String(isActive);
        }
        if (!active) return;
        if (!this.panels.has(active.id)) await this.createPanel(active);
        const entry = this.panels.get(active.id);
        entry.panel.dataset.active = 'true';
        entry.editor?.focus?.();
    }

    async createPanel(tab) {
        const panel = h('div', {
            class: 'fs-editor__panel', role: 'tabpanel', id: `panel-${tab.id}`,
            'aria-labelledby': `tab-${tab.id}`, tabindex: '0', dataset: {active: 'true'},
        });
        this.el.appendChild(panel);
        const entry = {panel, editor: null, editorId: null, banner: null};
        this.panels.set(tab.id, entry);

        const readOnly = tab.readOnly || caps.readOnly
            || (tab.size > (caps.limits.maxFileSize || Infinity));
        const exclude = [];
        let descriptor = pickEditor(tab.stat, {exclude});
        while (descriptor) {
            try {
                const editor = descriptor.create({
                    tab,
                    host: panel,
                    readOnly,
                    fs,
                    onDirty: (dirty) => tabs.setDirty(tab.id, dirty),
                    onCursor: (info) => bus.emit('editor:cursor', info),
                });
                panel.appendChild(editor.el);
                await editor.load?.();
                entry.editor = editor;
                entry.editorId = descriptor.id;
                tab.editorKind = descriptor.id;
                announce(`Opened ${tab.name}, ${tab.size} bytes${readOnly ? ', read-only' : ''}`);
                return entry;
            } catch (error) {
                console.warn(`editor ${descriptor.id} failed`, error);
                exclude.push(descriptor.id);
                panel.textContent = '';
                if (error instanceof FsError && error.code !== 'ENETWORK' && error.code === 'EFBIG') {
                    raise(error, {operation: 'open', path: tab.path});
                    break;
                }
                descriptor = pickEditor(tab.stat, {exclude});
            }
        }
        if (!entry.editor) {
            panel.appendChild(h('div', {class: 'fs-editor__empty', text: `Cannot display ${tab.name}.`}));
        }
        return entry;
    }

    disposePanel(id) {
        const entry = this.panels.get(id);
        if (!entry) return;
        try {
            entry.editor?.dispose?.();
        } catch (e) {
            console.warn(e);
        }
        entry.panel.remove();
        this.panels.delete(id);
    }

    /** §11 — clean tabs reload silently, dirty tabs get a non-modal banner. */
    async reconcile(event) {
        const tab = tabs.byPath(event?.path);
        if (!tab || tab.virtual) return;
        const entry = this.panels.get(tab.id);
        if (!entry?.editor) return;
        let stat;
        try {
            stat = await fs.stat(tab.path);
        } catch (error) {
            if (error.code === 'ENOENT') {
                ui.toast({severity: 'info', message: `${tab.name} no longer exists`});
                tabs.close(tab.id);
            }
            return;
        }
        if (stat.etag === tab.etag) return;
        if (!tab.dirty) {
            const {text, etag, mtimeMs} = await readTextFor(tab).catch(() => ({}));
            if (text != null && entry.editor.setValue) {
                entry.editor.setValue(text, {preserveViewState: true});
                tab.etag = etag;
                tab.mtimeMs = mtimeMs;
                tabs.setDirty(tab.id, false);
                announce(`${tab.name} reloaded from disk`);
            }
            return;
        }
        if (entry.banner) return;
        entry.banner = h('div', {class: 'fs-editor__banner', role: 'status'}, [
            h('span', {text: `${tab.name} changed on disk.`}),
            h('button', {
                type: 'button', text: 'Reload', onclick: async () => {
                    const {text, etag} = await readTextFor(tab);
                    entry.editor.setValue(text);
                    tab.etag = etag;
                    tabs.setDirty(tab.id, false);
                    entry.banner.remove();
                    entry.banner = null;
                }
            }),
            h('button', {
                type: 'button', text: 'Keep mine', onclick: () => {
                    entry.banner.remove();
                    entry.banner = null;
                }
            }),
        ]);
        entry.panel.prepend(entry.banner);
    }

    editorFor(tabId) {
        return this.panels.get(tabId)?.editor || null;
    }

    /** §10.2 — optimistic concurrency with If-Match. */
    async save(tab) {
        const entry = this.panels.get(tab.id);
        if (!entry?.editor?.getValue) {
            ui.toast({severity: 'info', message: `${tab.name} cannot be saved`});
            return false;
        }
        if (tab.virtual) {
            ui.toast({severity: 'info', message: 'Use "Save As…" for generated documents'});
            return false;
        }
        const bytes = new TextEncoder().encode(entry.editor.getValue());
        try {
            const result = await fs.writeFile(tab.path, bytes, {ifMatch: tab.etag || undefined});
            tab.etag = result.etag;
            tab.mtimeMs = result.mtimeMs;
            tab.size = result.size;
            tabs.setDirty(tab.id, false);
            announce(`Saved ${basename(tab.path)}`);
            return true;
        } catch (error) {
            if (error.code === 'EBUSY') return this.resolveConflict(tab, bytes);
            raise(error, {operation: 'save', path: tab.path});
            return false;
        }
    }

    async resolveConflict(tab, bytes) {
        const choice = await ui.quickPick({
            title: `${tab.name} changed on disk`,
            items: [
                {id: 'overwrite', label: 'Overwrite', description: 'Discard the version on disk'},
                {id: 'reload', label: 'Reload', description: 'Discard your changes'},
                {id: 'cancel', label: 'Cancel'},
            ],
        });
        if (choice?.id === 'overwrite') {
            const result = await fs.writeFile(tab.path, bytes);
            tab.etag = result.etag;
            tabs.setDirty(tab.id, false);
            announce(`Saved ${tab.name}`);
            return true;
        }
        if (choice?.id === 'reload') {
            const {text, etag} = await readTextFor(tab);
            this.panels.get(tab.id)?.editor?.setValue(text);
            tab.etag = etag;
            tabs.setDirty(tab.id, false);
        }
        return false;
    }
}
