import {h, clear, on} from '../../core/dom.js';
import {trapFocus, announce} from '../../core/a11y.js';
import {ui} from '../../core/ui.js';

let current = null;

/** Fuzzy subsequence score; higher is better, null means "no match". */
export function fuzzyScore(query, text) {
    if (!query) return 0;
    const haystack = text.toLowerCase();
    const needle = query.toLowerCase();
    let score = 0;
    let index = 0;
    let previous = -1;
    for (const ch of needle) {
        const found = haystack.indexOf(ch, index);
        if (found < 0) return null;
        score += found === previous + 1 ? 3 : 1;
        if (found === 0) score += 2;
        previous = found;
        index = found + 1;
    }
    return score - haystack.length * 0.01;
}

/**
 * One overlay serving the command palette, quick open and "Alt+Enter"
 * available actions: combobox + listbox with aria-activedescendant.
 */
export function openQuickPick({title = '', placeholder = '', items, onAccept, initialQuery = ''} = {}) {
    close();
    return new Promise((resolve) => {
        const listId = 'fs-quickpick-list';
        const input = h('input', {
            class: 'fs-quickpick__input', type: 'text', role: 'combobox',
            'aria-expanded': 'true', 'aria-controls': listId, 'aria-autocomplete': 'list',
            'aria-label': title || placeholder || 'Search', placeholder, autocomplete: 'off', value: initialQuery,
        });
        const list = h('ul', {
            class: 'fs-quickpick__list',
            role: 'listbox',
            id: listId,
            'aria-label': title || 'Results'
        });
        const panel = h('div', {class: 'fs-quickpick'}, [
            title ? h('p', {class: 'sr-only', text: title}) : null, input, list,
        ]);
        const overlay = h('div', {class: 'fs-overlay'}, [panel]);

        let all = [];
        let filtered = [];
        let index = 0;
        let release = null;

        function close(value) {
            release?.();
            overlay.remove();
            current = null;
            resolve(value ?? null);
        }

        current = {close};

        function paint() {
            clear(list);
            filtered.forEach((item, i) => {
                list.appendChild(h('li', {
                    class: 'fs-quickpick__item', role: 'option', id: `fs-qp-${i}`,
                    'aria-selected': String(i === index), 'aria-disabled': item.disabled ? 'true' : null,
                    title: item.disabledReason || '',
                    onclick: () => accept(item),
                }, [
                    item.icon ? h('span', {'aria-hidden': 'true', text: item.icon}) : null,
                    h('span', {text: item.label}),
                    item.description ? h('span', {class: 'detail', text: item.description}) : null,
                    item.keys?.length ? h('span', {class: 'detail', text: item.keys.join(' / ')}) : null,
                ]));
            });
            input.setAttribute('aria-activedescendant', filtered.length ? `fs-qp-${index}` : '');
            list.children[index]?.scrollIntoView({block: 'nearest'});
        }

        function filter() {
            const query = input.value.trim();
            filtered = !query ? all.slice(0, 200) : all
                .map((item) => ({item, score: fuzzyScore(query, `${item.label} ${item.description || ''}`)}))
                .filter((entry) => entry.score !== null)
                .sort((a, b) => b.score - a.score)
                .slice(0, 200)
                .map((entry) => entry.item);
            index = 0;
            paint();
            announce(`${filtered.length} results`);
        }

        function accept(item) {
            if (!item || item.disabled) return;
            close(item);
            Promise.resolve(onAccept?.(item)).catch((e) => console.error(e));
            item.run?.();
        }

        on(input, 'input', filter);
        on(input, 'keydown', (event) => {
            switch (event.key) {
                case 'ArrowDown':
                    event.preventDefault();
                    index = Math.min(filtered.length - 1, index + 1);
                    paint();
                    break;
                case 'ArrowUp':
                    event.preventDefault();
                    index = Math.max(0, index - 1);
                    paint();
                    break;
                case 'Home':
                    event.preventDefault();
                    index = 0;
                    paint();
                    break;
                case 'End':
                    event.preventDefault();
                    index = filtered.length - 1;
                    paint();
                    break;
                case 'Enter':
                    event.preventDefault();
                    accept(filtered[index]);
                    break;
                case 'Escape':
                    event.preventDefault();
                    close(null);
                    break;
                default:
                    break;
            }
        });
        on(overlay, 'mousedown', (event) => {
            if (event.target === overlay) close(null);
        });

        document.body.appendChild(overlay);
        release = trapFocus(panel, {initial: input});

        Promise.resolve(typeof items === 'function' ? items() : items)
            .then((resolved) => {
                all = resolved || [];
                filter();
            })
            .catch((error) => {
                all = [];
                filter();
                console.error(error);
            });
    });
}

export function close() {
    current?.close(null);
}

export function initQuickPick() {
    ui.quickPick = ({title, items, placeholder}) => openQuickPick({title, items, placeholder});
}
