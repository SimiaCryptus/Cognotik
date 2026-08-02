import {Component} from './../base.js';
import {h, clear, on} from '../../core/dom.js';
import {announce} from '../../core/a11y.js';
import {formatBytes} from '../../core/paths.js';
import {decorate} from '../../core/registry.js';
import config from '../../config.js';

const OVERSCAN = 8;

/** Virtualised renderer implementing the ARIA tree pattern. */
export class TreeView extends Component {
    constructor(props) {
        super(props);
        this.model = props.model;
        this.rows = [];
        this.typeahead = '';
        this.typeaheadTimer = null;
         /* A touch screen has no hover and no cheap double tap. */
         this.coarse = !!window.matchMedia?.('(pointer: coarse)').matches;
    }

    render() {
        this.inner = h('div', {class: 'fs-tree__inner'});
        this.el = h('div', {
            class: 'fs-tree', role: 'tree', 'aria-label': 'Files',
            'aria-multiselectable': 'true', tabindex: '-1',
        }, [this.inner]);
        this.track(on(this.el, 'scroll', () => this.paint()));
        this.track(on(this.el, 'keydown', (e) => this.onKeydown(e)));
        this.track(on(this.el, 'click', (e) => this.onClick(e)));
        this.track(on(this.el, 'dblclick', (e) => this.onDblClick(e)));
         /* Stop the browser turning the second click into a text selection,
            which otherwise swallows the dblclick target. */
         this.track(on(this.el, 'mousedown', (e) => {
             if (e.detail > 1) e.preventDefault();
         }));
        this.track(on(this.el, 'contextmenu', (e) => this.onContextMenu(e)));
        this.track(on(this.el, 'focusin', () => this.props.onFocusIn?.()));
        this.installDnd();
        return this.el;
    }

    mounted() {
        this.rowHeight = parseFloat(getComputedStyle(this.el).getPropertyValue('--fs-row-h')) || 22;
         /* Selection-only changes are applied in place; see TreeModel.emit. */
         this.track(this.model.onChange((model, hint) => {
             if (hint === 'selection') this.refreshSelection();
             else this.update();
         }));
        this.update();
    }

    update() {
        this.rows = this.model.rows();
        this.paint();
    }

    paint() {
         /* clear() collapses the scroll height, which the browser answers by
            resetting scrollTop: expanding a folder would jump to the top (#9). */
         const scrollTop = this.el.scrollTop;
        const total = this.rows.length;
        const height = this.rowHeight || 22;
        let start = 0;
        let end = total;
        if (total > config.treeVirtualiseAfter) {
            const view = this.el.clientHeight || 400;
            start = Math.max(0, Math.floor(this.el.scrollTop / height) - OVERSCAN);
            end = Math.min(total, Math.ceil((this.el.scrollTop + view) / height) + OVERSCAN);
        }
        clear(this.inner);
        if (start > 0) this.inner.appendChild(h('div', {role: 'presentation', style: {height: `${start * height}px`}}));
        for (let i = start; i < end; i++) this.inner.appendChild(this.renderRow(this.rows[i], i, total));
        if (end < total) this.inner.appendChild(h('div', {
            role: 'presentation',
            style: {height: `${(total - end) * height}px`}
        }));
        if (!total) {
            this.inner.appendChild(h('p', {
                class: 'fs-explorer__status',
                text: this.model.query ? 'No matches' : 'Empty folder'
            }));
        }
         /* Rows are taller on a coarse pointer than --fs-row-h suggests, and the
            virtualiser's spacers must agree with reality. */
         const first = this.inner.querySelector('.fs-tree__row');
         if (first) {
             const measured = first.getBoundingClientRect().height;
             if (measured > 0) this.rowHeight = measured;
         }
         if (this.el.scrollTop !== scrollTop) this.el.scrollTop = scrollTop;
    }

    renderRow(node, index, total) {
        const selected = this.model.selection.includes(node.path);
        const focused = this.model.focus === node.path;
        const badges = decorate(node);
        const row = h('div', {
            class: 'fs-tree__row',
            role: 'treeitem',
            id: `fs-node-${index}`,
            'aria-level': String(node.level),
            'aria-posinset': String(index + 1),
            'aria-setsize': String(total),
            'aria-selected': String(selected),
            'aria-expanded': node.type === 'dir' ? String(this.model.expanded.has(node.path)) : null,
            'aria-describedby': node.readOnly ? 'fs-readonly-hint' : null,
            tabindex: focused ? '0' : '-1',
            dataset: {path: node.path, readonly: String(!!node.readOnly)},
            style: {paddingInlineStart: `${(node.level - 1) * 12 + 4}px`},
        }, [
            h('span', {
                class: 'fs-tree__twisty', 'aria-hidden': 'true',
                text: node.type === 'dir' ? (node.state === 'loading' ? '◌' : this.model.expanded.has(node.path) ? '▾' : '▸') : '',
            }),
            h('span', {class: 'fs-tree__icon', 'aria-hidden': 'true', text: node.type === 'dir' ? '📁' : '📄'}),
            h('span', {class: 'fs-tree__label', text: node.name}),
            node.readOnly ? h('span', {class: 'fs-tree__badge', 'aria-hidden': 'true', text: '🔒'}) : null,
            ...badges.map((b) => h('span', {
                class: 'fs-tree__badge',
                title: b.tooltip || '',
                'aria-hidden': 'true',
                text: b.badge || ''
            })),
            node.type === 'file' && node.size != null
                ? h('span', {class: 'fs-tree__meta', text: formatBytes(node.size)}) : null,
        ]);
        if (focused) this._pendingFocus = row;
        return row;
    }

     /**
      * Selection/focus changes touch attributes only. A full repaint would
      * rebuild every row, which (a) reset the scroll position and (b) broke
      * double-click: the two clicks landed on different elements, so the
      * dblclick target was the container and no node could be resolved (#3, #9).
      */
     refreshSelection() {
         for (const row of this.inner.querySelectorAll('.fs-tree__row')) {
             const path = row.dataset.path;
             row.setAttribute('aria-selected', String(this.model.selection.includes(path)));
             row.setAttribute('tabindex', this.model.focus === path ? '0' : '-1');
         }
     }

    focusPath(path, {scroll = true} = {}) {
        this.model.focus = path;
         this.refreshSelection();
        const index = this.rows.findIndex((r) => r.path === path);
        if (index < 0) return;
        if (scroll) {
            const top = index * (this.rowHeight || 22);
            if (top < this.el.scrollTop || top > this.el.scrollTop + this.el.clientHeight - (this.rowHeight || 22)) {
                this.el.scrollTop = Math.max(0, top - this.el.clientHeight / 2);
                this.paint();
            }
        }
        this.el.querySelector(`.fs-tree__row[data-path="${cssEscape(path)}"]`)?.focus();
    }

    nodeAt(event) {
        const row = event.target instanceof Element ? event.target.closest('.fs-tree__row') : null;
        return row ? this.model.node(row.dataset.path) : null;
    }
     /** Fallback used by dblclick when the event target is the container. */
     nodeAtPoint(event) {
         const el = document.elementFromPoint(event.clientX, event.clientY);
         const row = el instanceof Element ? el.closest('.fs-tree__row') : null;
         return row ? this.model.node(row.dataset.path) : null;
     }


    onClick(event) {
        const node = this.nodeAt(event);
        if (!node) return;
        const additive = event.ctrlKey || event.metaKey;
        const range = event.shiftKey;
        this.applySelection(node, {additive, range});
         const twisty = !!event.target.closest('.fs-tree__twisty');
         /* With a precise pointer only the twisty reacts to a single click, so
            the tree is not "over-reactive" while browsing; on a touch screen a
            single tap has to do everything. */
         if (node.type === 'dir' && (twisty || this.coarse)) {
            this.model.toggle(node.path);
         } else if (node.type === 'file' && this.coarse && !additive && !range) {
             this.props.onActivate?.(node, {preview: true});
        }
    }

    onDblClick(event) {
         /* The dblclick target is the nearest common ancestor of the two clicks,
            so resolve by hit-test (and finally by focus) as well (#3). */
         const node = this.nodeAt(event)
             || this.nodeAtPoint(event)
             || (this.model.focus ? this.model.node(this.model.focus) : null);
        if (!node) return;
        event.preventDefault();
        if (node.type === 'dir') this.model.toggle(node.path);
        else this.props.onActivate?.(node, {preview: false});
    }

    onContextMenu(event) {
        const node = this.nodeAt(event);
        event.preventDefault();
        if (node && !this.model.selection.includes(node.path)) {
            this.applySelection(node, {});
            announce(`Selected ${node.name}`);
        }
        this.props.onContextMenu?.({x: event.clientX, y: event.clientY, node});
    }

    applySelection(node, {additive, range}) {
        const selection = this.model.selection.slice();
        if (additive) {
            const index = selection.indexOf(node.path);
            if (index >= 0) selection.splice(index, 1); else selection.push(node.path);
            this.model.select(selection, {focus: node.path});
        } else if (range && this.model.focus) {
            const from = this.rows.findIndex((r) => r.path === this.model.focus);
            const to = this.rows.findIndex((r) => r.path === node.path);
            if (from >= 0 && to >= 0) {
                const [a, b] = from < to ? [from, to] : [to, from];
                this.model.select(this.rows.slice(a, b + 1).map((r) => r.path), {focus: node.path});
            }
        } else {
            this.model.select([node.path], {focus: node.path});
        }
        this.focusPath(node.path, {scroll: false});
    }

    async onKeydown(event) {
        const index = this.rows.findIndex((r) => r.path === this.model.focus);
        const node = this.rows[index];
        const move = (next) => {
            const target = this.rows[Math.max(0, Math.min(this.rows.length - 1, next))];
            if (!target) return;
            this.model.select(event.shiftKey ? Array.from(new Set([...this.model.selection, target.path])) : [target.path], {focus: target.path});
            this.focusPath(target.path);
        };
        switch (event.key) {
            case 'ArrowDown':
                event.preventDefault();
                move(index + 1);
                return;
            case 'ArrowUp':
                event.preventDefault();
                move(index - 1);
                return;
            case 'Home':
                event.preventDefault();
                move(0);
                return;
            case 'End':
                event.preventDefault();
                move(this.rows.length - 1);
                return;
            case 'ArrowRight':
                if (!node) return;
                event.preventDefault();
                if (node.type === 'dir' && !this.model.expanded.has(node.path)) await this.model.expand(node.path);
                else move(index + 1);
                return;
            case 'ArrowLeft': {
                if (!node) return;
                event.preventDefault();
                if (node.type === 'dir' && this.model.expanded.has(node.path)) {
                    this.model.collapse(node.path);
                    return;
                }
                const parentIndex = this.rows.findIndex((r) => r.path === node.path.replace(/\/[^/]+$/, ''));
                if (parentIndex >= 0) move(parentIndex);
                return;
            }
            case 'Enter':
                if (!node) return;
                event.preventDefault();
                if (node.type === 'dir') this.model.toggle(node.path);
                else this.props.onActivate?.(node, {preview: false});
                return;
            case ' ':
                if (!node) return;
                event.preventDefault();
                this.applySelection(node, {additive: true});
                return;
            case '*':
                event.preventDefault();
                for (const row of this.rows.filter((r) => r.level === (node?.level ?? 1) && r.type === 'dir')) {
                    await this.model.expand(row.path);
                }
                return;
            default:
                break;
        }
        if (event.key.length === 1 && !event.ctrlKey && !event.metaKey && !event.altKey) {
            this.typeahead += event.key.toLowerCase();
            clearTimeout(this.typeaheadTimer);
            this.typeaheadTimer = setTimeout(() => {
                this.typeahead = '';
            }, 800);
            const hit = this.rows.find((r) => r.name.toLowerCase().startsWith(this.typeahead));
            if (hit) {
                this.model.select([hit.path], {focus: hit.path});
                this.focusPath(hit.path);
            }
        }
    }

    installDnd() {
        this.track(on(this.el, 'dragover', (event) => {
            event.preventDefault();
            this.el.classList.add('is-dropping');
        }));
        this.track(on(this.el, 'dragleave', () => this.el.classList.remove('is-dropping')));
        this.track(on(this.el, 'drop', (event) => {
            event.preventDefault();
            this.el.classList.remove('is-dropping');
            const node = this.nodeAt(event);
            const target = node ? (node.type === 'dir' ? node.path : node.path.replace(/\/[^/]+$/, '')) : '/';
            const files = Array.from(event.dataTransfer?.files || []);
            if (files.length) this.props.onDropFiles?.(files, target || '/');
        }));
    }
}

function cssEscape(value) {
    return (window.CSS && CSS.escape) ? CSS.escape(value) : String(value).replace(/["\\]/g, '\\$&');
}