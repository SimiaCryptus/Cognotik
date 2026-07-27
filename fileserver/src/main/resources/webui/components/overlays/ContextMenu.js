import {h, on} from '../../core/dom.js';
import {actionsForAnchor} from '../../core/actions.js';
import {executeCommand} from '../../core/commands.js';
import {keysFor, describeChord} from '../../core/keymap.js';
import {trapFocus} from '../../core/a11y.js';

let open = null;
let hintSeq = 0;

/**
 * The single menu renderer used by the menubar, explorer, tab and editor
 * surfaces — differing only in the anchor queried and the DataContext built.
 */
export function openContextMenu({anchor, x, y, ctx, label, onClose, depth = 0, invoker}) {
    closeContextMenu();
    const groups = actionsForAnchor(anchor, ctx);
    const menu = h('div', {class: 'fs-menu', role: 'menu', 'aria-label': label || anchor});
    const items = [];

    if (!groups.length) {
        menu.appendChild(h('p', {class: 'fs-explorer__status', text: 'No actions available'}));
    }

    groups.forEach((group, groupIndex) => {
        if (groupIndex) menu.appendChild(h('hr', {role: 'separator'}));
        const container = h('div', {role: 'group', 'aria-label': group.group.replace(/^\d+_/, '')});
        for (const entry of group.items) {
            const {presentation} = entry;
            if (entry.kind === 'submenu') {
                const button = h('button', {
                    type: 'button', role: 'menuitem', tabindex: '-1',
                    'aria-haspopup': 'menu', 'aria-expanded': 'false', text: presentation.text,
                    onclick: (event) => {
                        const rect = event.currentTarget.getBoundingClientRect();
                        event.currentTarget.setAttribute('aria-expanded', 'true');
                        openSubmenu(entry.submenu.id, rect.right, rect.top, ctx, depth + 1);
                    },
                });
                items.push(button);
                container.appendChild(button);
                continue;
            }
            const chord = keysFor(entry.action.id)[0];
            const hintId = presentation.disabledReason && !presentation.enabled ? `fs-menu-hint-${++hintSeq}` : null;
            const button = h('button', {
                type: 'button', role: 'menuitem', tabindex: '-1',
                'aria-disabled': presentation.enabled ? null : 'true',
                'aria-describedby': hintId,
                title: presentation.enabled ? (presentation.description || '') : presentation.disabledReason,
                dataset: {id: entry.action.id},
                onclick: () => {
                    if (!presentation.enabled) return;
                    closeContextMenu();
                    executeCommand(entry.action.id, ctx);
                },
            }, [
                presentation.icon ? h('span', {'aria-hidden': 'true', text: presentation.icon}) : null,
                h('span', {text: presentation.text}),
                chord ? h('span', {class: 'keys', text: describeChord(chord)}) : null,
            ]);
            items.push(button);
            container.appendChild(button);
            if (hintId) container.appendChild(h('span', {
                id: hintId,
                class: 'sr-only',
                text: presentation.disabledReason
            }));
        }
        menu.appendChild(container);
    });

    document.body.appendChild(menu);
    position(menu, x, y);
    const release = trapFocus(menu, {initial: items[0]});
    let index = 0;
    const focusItem = (next) => {
        index = (next + items.length) % items.length;
        items[index]?.focus();
    };
    const offKeys = on(menu, 'keydown', (event) => {
        switch (event.key) {
            case 'ArrowDown':
                event.preventDefault();
                focusItem(index + 1);
                break;
            case 'ArrowUp':
                event.preventDefault();
                focusItem(index - 1);
                break;
            case 'Home':
                event.preventDefault();
                focusItem(0);
                break;
            case 'End':
                event.preventDefault();
                focusItem(items.length - 1);
                break;
            case 'Escape':
                event.preventDefault();
                closeContextMenu();
                break;
            default:
                if (event.key.length === 1) {
                    const hit = items.findIndex((item) => item.textContent.toLowerCase().startsWith(event.key.toLowerCase()));
                    if (hit >= 0) focusItem(hit);
                }
        }
    });
    const offOutside = on(document, 'mousedown', (event) => {
        if (!menu.contains(event.target)) closeContextMenu();
    }, true);

    open = {
        close() {
            offKeys();
            offOutside();
            release();
            menu.remove();
            open = null;
            onClose?.();
            invoker?.setAttribute?.('aria-expanded', 'false');
        },
    };
    return open;
}

function openSubmenu(anchor, x, y, ctx, depth) {
    if (depth > 3) return;
    const parent = open;
    open = null;
    openContextMenu({anchor, x, y, ctx, depth, onClose: () => parent?.close?.()});
}

function position(menu, x, y) {
    const rect = menu.getBoundingClientRect();
    const left = Math.min(x, window.innerWidth - rect.width - 8);
    const top = Math.min(y, window.innerHeight - rect.height - 8);
    menu.style.left = `${Math.max(4, left)}px`;
    menu.style.top = `${Math.max(4, top)}px`;
}

export function closeContextMenu() {
    open?.close();
}
