import {fs} from '../../core/fsclient.js';
import {bus} from '../../core/bus.js';
import {basename} from '../../core/paths.js';

export class TabModel {
    constructor() {
        this.order = [];
        this.byId = new Map();
        this.active = null;
        this.preview = null;
        this.seq = 0;
        this.listeners = new Set();
    }

    onChange(fn) {
        this.listeners.add(fn);
        return () => this.listeners.delete(fn);
    }

    emit() {
        this.listeners.forEach((fn) => {
            try {
                fn(this);
            } catch (e) {
                console.error(e);
            }
        });
    }

    list() {
        return this.order.map((id) => this.byId.get(id)).filter(Boolean);
    }

    activeTab() {
        return this.active ? this.byId.get(this.active) : null;
    }

    byPath(path) {
        return this.list().find((tab) => tab.path === path);
    }

    dirtyTabs() {
        return this.list().filter((tab) => tab.dirty);
    }

    /** Single click opens a preview tab; Enter/edit pins it. */
    async open(path, {preview = false, pinned = !preview, line, col, stat, virtual} = {}) {
        let tab = this.byPath(path);
        if (!tab) {
            const info = stat || (virtual ? {path, type: 'file', size: 0, readOnly: true} : await fs.stat(path));
            if (preview && this.preview && this.byId.has(this.preview) && !this.byId.get(this.preview).dirty) {
                this.close(this.preview, {silent: true});
            }
            tab = {
                id: `t${++this.seq}`,
                path,
                name: basename(path),
                stat: info,
                etag: info.etag || null,
                mtimeMs: info.mtimeMs || 0,
                size: info.size || 0,
                mimeType: info.mimeType || 'application/octet-stream',
                readOnly: !!info.readOnly,
                dirty: false,
                pinned,
                preview,
                virtual: !!virtual,
                content: virtual ? info.content : undefined,
                languageId: info.languageId,
                line, col,
            };
            this.byId.set(tab.id, tab);
            this.order.push(tab.id);
            if (preview) this.preview = tab.id;
            bus.emit('tab:opened', tab);
        } else if (pinned) {
            tab.pinned = true;
            tab.preview = false;
            if (this.preview === tab.id) this.preview = null;
        }
        if (line) {
            tab.line = line;
            tab.col = col;
        }
        this.activate(tab.id);
        return tab;
    }

    activate(id) {
        if (!this.byId.has(id)) return;
        this.active = id;
        this.emit();
        bus.emit('tab:activated', this.byId.get(id));
    }

    close(id, {silent = false} = {}) {
        const index = this.order.indexOf(id);
        if (index < 0) return;
        const tab = this.byId.get(id);
        this.order.splice(index, 1);
        this.byId.delete(id);
        if (this.preview === id) this.preview = null;
        if (this.active === id) {
            const next = this.order[Math.min(index, this.order.length - 1)] || null;
            this.active = next;
            if (next) bus.emit('tab:activated', this.byId.get(next));
        }
        if (!silent) {
            this.emit();
            bus.emit('tab:closed', tab);
        }
    }

    setDirty(id, dirty) {
        const tab = this.byId.get(id);
        if (!tab || tab.dirty === dirty) return;
        tab.dirty = dirty;
        if (dirty) {
            tab.pinned = true;
            tab.preview = false;
            if (this.preview === id) this.preview = null;
        }
        this.emit();
        bus.emit('tab:dirty', tab);
    }

    move(delta) {
        if (!this.order.length) return;
        const index = this.order.indexOf(this.active);
        const next = (index + delta + this.order.length) % this.order.length;
        this.activate(this.order[next]);
    }
}

export const tabs = new TabModel();
