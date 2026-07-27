import {allCommands, executeCommand, isEnabled, getCommand} from './commands.js';
import {buildContext} from './context.js';
import {persist} from './persist.js';
import {on} from './dom.js';

export const isMac = /Mac|iPhone|iPad|iPod/.test(navigator.platform || navigator.userAgent || '');

const SPECIAL = {
    ArrowUp: 'Up', ArrowDown: 'Down', ArrowLeft: 'Left', ArrowRight: 'Right',
    ' ': 'Space', Escape: 'Escape', Enter: 'Enter', Delete: 'Delete', Backspace: 'Backspace',
    Tab: 'Tab', Home: 'Home', End: 'End', PageUp: 'PageUp', PageDown: 'PageDown',
};

function normKey(key) {
    if (SPECIAL[key]) return SPECIAL[key];
    return key.length === 1 ? key.toUpperCase() : key;
}

export function chordFromEvent(event) {
    const parts = [];
    const mod = isMac ? event.metaKey : event.ctrlKey;
    const secondary = isMac ? event.ctrlKey : event.metaKey;
    if (mod) parts.push('Mod');
    if (secondary) parts.push(isMac ? 'Ctrl' : 'Meta');
    if (event.altKey) parts.push('Alt');
    if (event.shiftKey) parts.push('Shift');
    const key = normKey(event.key);
    if (['Control', 'Meta', 'Alt', 'Shift'].includes(event.key)) return null;
    parts.push(key);
    return parts.join('+');
}

export function normalizeChord(chord) {
    const raw = String(chord).split('+').map((p) => p.trim()).filter(Boolean);
    const key = normKey(raw.pop());
    const flags = new Set(raw.map((p) => (p === 'Cmd' || p === 'Command' ? 'Mod' : p)));
    const ordered = ['Mod', 'Ctrl', 'Meta', 'Alt', 'Shift'].filter((f) => flags.has(f));
    return [...ordered, key].join('+');
}

export function describeChord(chord) {
    return normalizeChord(chord)
        .replace('Mod', isMac ? '⌘' : 'Ctrl')
        .replace('Alt', isMac ? '⌥' : 'Alt')
        .replace('Shift', isMac ? '⇧' : 'Shift');
}

function bindings() {
    const overrides = persist.get('keymap', {}) || {};
    const map = new Map();
    for (const command of allCommands()) {
        const keys = overrides[command.id] || command.keys || [];
        for (const chord of keys) map.set(normalizeChord(chord), command.id);
    }
    return map;
}

let map = new Map();

export function rebuildKeymap() {
    map = bindings();
    return map;
}

const TEXT_INPUTS = new Set(['INPUT', 'TEXTAREA', 'SELECT']);

export function initKeymap(root = document) {
    rebuildKeymap();
    return on(root, 'keydown', async (event) => {
        const chord = chordFromEvent(event);
        if (!chord) return;
        const id = map.get(chord);
        if (!id) return;
        const target = event.target;
        const typing = target instanceof HTMLElement
            && (TEXT_INPUTS.has(target.tagName) || target.isContentEditable || target.closest('.monaco-editor'));
        /* Inside text fields only accelerators carrying a modifier win. */
        if (typing && !/(Mod|Ctrl|Meta|Alt)\+/.test(chord) && chord !== 'Escape') return;
        const command = getCommand(id);
        const ctx = buildContext({origin: 'keybinding'});
        if (!isEnabled(command, ctx)) return;
        event.preventDefault();
        event.stopPropagation();
        await executeCommand(id, ctx);
    }, true);
}

export function keysFor(commandId) {
    const overrides = persist.get('keymap', {}) || {};
    const command = getCommand(commandId);
    return overrides[commandId] || command?.keys || [];
}
