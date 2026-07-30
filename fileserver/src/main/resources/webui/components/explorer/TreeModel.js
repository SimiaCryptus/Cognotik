import {join, basename, compareNames, isAncestor, dirname} from '../../core/paths.js';

/** Pure, DOM-free tree model keyed by virtual path. Unit-testable. */
export class TreeModel {
    constructor({fs}) {
        this.fs = fs;
        this.nodes = new Map();
        this.expanded = new Set(['/']);
        this.selection = [];
        this.focus = null;
        this.query = '';
        this.listeners = new Set();
        this.nodes.set('/', {path: '/', name: '/', type: 'dir', level: 0, state: 'collapsed', childPaths: null});
    }

    onChange(fn) {
        this.listeners.add(fn);
        return () => this.listeners.delete(fn);
    }

     /**
      * `hint` lets a view repaint surgically. 'selection' means only the
      * selected/focused rows changed: rebuilding the row elements there would
      * (a) reset the scroll position and (b) break double click, because the
      * second click would land on a freshly created element (#3).
      */
     emit(hint) {
        this.listeners.forEach((fn) => {
            try {
                 fn(this, hint);
            } catch (e) {
                console.error(e);
            }
        });
    }

    node(path) {
        return this.nodes.get(path);
    }

    isLoaded(path) {
        return !!this.nodes.get(path)?.childPaths;
    }

    async load(path, {force = false} = {}) {
        const node = this.nodes.get(path);
        if (!node || node.type !== 'dir') return;
        if (node.childPaths && !force) return;
        node.state = 'loading';
        this.emit();
        try {
            const result = await this.fs.readdir(path, {stat: true, depth: 1});
            const children = (result.entries || []).map((entry) => ({
                path: join(path, entry.name),
                name: entry.name,
                type: entry.type === 'dir' ? 'dir' : 'file',
                level: node.level + 1,
                state: entry.type === 'dir' ? 'collapsed' : 'leaf',
                childPaths: null,
                size: entry.size,
                mtimeMs: entry.mtimeMs,
                readOnly: !!entry.readOnly,
                mimeType: entry.mimeType,
            }));
            /* Folders first, then a natural (numeric-aware) comparator. */
            children.sort((a, b) => (a.type === b.type ? compareNames(a.name, b.name) : a.type === 'dir' ? -1 : 1));
            const keep = new Set(children.map((c) => c.path));
            for (const existing of node.childPaths || []) {
                if (!keep.has(existing)) this.remove(existing, {silent: true});
            }
            for (const child of children) {
                const previous = this.nodes.get(child.path);
                if (previous) {
                    Object.assign(previous, child, {
                        childPaths: previous.childPaths,
                        state: this.expanded.has(child.path) ? 'expanded' : child.state,
                    });
                } else {
                    this.nodes.set(child.path, child);
                }
            }
            node.childPaths = children.map((c) => c.path);
            node.truncated = !!result.truncated;
            node.error = null;
            node.state = this.expanded.has(path) ? 'expanded' : 'collapsed';
        } catch (error) {
            node.state = 'error';
            node.error = error;
            throw error;
        } finally {
            this.emit();
        }
    }

    async expand(path) {
        const node = this.nodes.get(path);
        if (!node || node.type !== 'dir') return;
        this.expanded.add(path);
        node.state = 'expanded';
        await this.load(path);
        this.emit();
    }

    collapse(path) {
        this.expanded.delete(path);
        const node = this.nodes.get(path);
        if (node) node.state = 'collapsed';
        this.emit();
    }

    async toggle(path) {
        if (this.expanded.has(path)) this.collapse(path);
        else await this.expand(path);
    }

    collapseAll() {
        this.expanded = new Set(['/']);
        for (const node of this.nodes.values()) if (node.type === 'dir' && node.path !== '/') node.state = 'collapsed';
        this.emit();
    }

    async refresh(path) {
        if (!this.isLoaded(path)) return;
        await this.load(path, {force: true});
    }

    invalidateAll() {
        for (const node of this.nodes.values()) if (node.path !== '/') node.childPaths = null;
    }

    remove(path, {silent = false} = {}) {
        for (const key of Array.from(this.nodes.keys())) {
            if (key === path || isAncestor(path, key)) {
                this.nodes.delete(key);
                this.expanded.delete(key);
            }
        }
        const parent = this.nodes.get(dirname(path));
        if (parent?.childPaths) parent.childPaths = parent.childPaths.filter((p) => p !== path);
        this.selection = this.selection.filter((p) => p !== path && !isAncestor(path, p));
        if (!silent) this.emit();
    }

    /** Reveal: loads every ancestor and expands it. */
    async reveal(path) {
        const parts = path.split('/').filter(Boolean);
        let current = '/';
        await this.expand('/');
        for (let i = 0; i < parts.length - 1; i++) {
            current = join(current, parts[i]);
            await this.expand(current);
        }
        this.focus = path;
        this.emit();
    }

    setQuery(query) {
        this.query = query || '';
        this.emit();
    }

    matches(node) {
        if (!this.query) return true;
        return node.name.toLowerCase().includes(this.query.toLowerCase());
    }

    /** Flattened list of visible rows (root itself is not rendered). */
    rows() {
        const out = [];
        const walk = (path) => {
            const parent = this.nodes.get(path);
            if (!parent?.childPaths) return;
            for (const childPath of parent.childPaths) {
                const child = this.nodes.get(childPath);
                if (!child) continue;
                const visible = this.matches(child);
                if (visible) out.push(child);
                if (child.type === 'dir' && (this.expanded.has(childPath) || (this.query && this.isLoaded(childPath)))) {
                    walk(childPath);
                }
            }
        };
        walk('/');
        return out;
    }

    select(paths, {focus} = {}) {
        this.selection = Array.from(new Set(paths));
        if (focus) this.focus = focus;
         this.emit('selection');
    }

    /** collapseDescendants: a selected folder swallows its selected children. */
    selectedResources({collapseDescendants = true} = {}) {
        let paths = this.selection.slice();
        if (collapseDescendants) {
            paths = paths.filter((path) => !paths.some((other) => other !== path && isAncestor(other, path)));
        }
        return paths
            .map((path) => this.nodes.get(path))
            .filter(Boolean)
            .map((node) => ({
                path: node.path, name: node.name || basename(node.path), type: node.type,
                size: node.size, mtimeMs: node.mtimeMs, readOnly: node.readOnly, mimeType: node.mimeType,
            }));
    }
}