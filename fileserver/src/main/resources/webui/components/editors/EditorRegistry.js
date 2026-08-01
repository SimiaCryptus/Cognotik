import {fs} from '../../core/fsclient.js';
import {caps} from '../../core/capabilities.js';
import {FsError} from '../../core/errors.js';

const editors = [];

export function registerEditor(spec) {
    if (!spec?.id || typeof spec.create !== 'function') throw new Error('an editor requires id and create()');
    editors.push({priority: 100, canOpen: () => false, ...spec});
    editors.sort((a, b) => b.priority - a.priority);
    return spec;
}

export function allEditors() {
    return editors.slice();
}

export function pickEditor(stat, {exclude = []} = {}) {
    return editors.find((editor) => !exclude.includes(editor.id) && safeCanOpen(editor, stat));
}

function safeCanOpen(editor, stat) {
    try {
        return !!editor.canOpen(stat);
    } catch (e) {
        console.warn(`canOpen of ${editor.id} threw`, e);
        return false;
    }
}

/** Binary sniff reusing the server's rule: a NUL byte in the first 8 KiB. */
export async function looksBinary(path) {
    const {bytes} = await fs.readFile(path, {range: [0, 8191]});
    return !!bytes && bytes.some((b) => b === 0);
}

export async function readTextFor(tab, {signal} = {}) {
    if (tab.virtual) return {text: tab.content ?? '', etag: null, mtimeMs: 0};
    if (tab.size > caps.limits.maxFileSize) {
        throw new FsError('EFBIG', {syscall: 'read', path: tab.path});
    }
    const result = await fs.readText(tab.path, {signal});
    return result;
}
