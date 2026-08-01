import {h, on} from '../../core/dom.js';
import {readTextFor} from './EditorRegistry.js';
import {isTextLike} from '../../core/mime.js';

/** Fallback editor: a labelled <textarea>, no syntax highlighting. */
export class PlainTextEditor {
    static id = 'plaintext';

    static canOpen(stat) {
        return isTextLike(stat) || (stat?.size ?? 0) === 0;
    }

    constructor(ctx) {
        this.ctx = ctx;
        this.textarea = h('textarea', {
            class: 'fs-plaintext', spellcheck: 'false', wrap: 'off',
            'aria-label': `${ctx.tab.path}${ctx.readOnly ? ' (read-only)' : ''}`,
            readonly: ctx.readOnly ? '' : null,
        });
        this.el = this.textarea;
        this._unsubs = [
            on(this.textarea, 'input', () => this.ctx.onDirty(this.textarea.value !== this.original)),
            on(this.textarea, 'keyup', () => this.reportCursor()),
            on(this.textarea, 'click', () => this.reportCursor()),
        ];
    }

    async load() {
        const {text} = await readTextFor(this.ctx.tab);
        this.setValue(text ?? '');
    }

    setValue(text) {
        const selection = [this.textarea.selectionStart, this.textarea.selectionEnd];
        this.original = text;
        this.textarea.value = text;
        try {
            this.textarea.setSelectionRange(selection[0], selection[1]);
        } catch (e) { /* ignore */
        }
        this.ctx.onDirty(false);
    }

    getValue() {
        return this.textarea.value;
    }

    focus() {
        this.textarea.focus();
    }

    reportCursor() {
        const upTo = this.textarea.value.slice(0, this.textarea.selectionStart).split('\n');
        this.ctx.onCursor?.({
            line: upTo.length,
            column: upTo[upTo.length - 1].length + 1,
            selectionCount: this.textarea.selectionEnd - this.textarea.selectionStart,
        });
    }

    getSelection() {
        const value = this.textarea.value;
        const start = this.textarea.selectionStart;
        const end = this.textarea.selectionEnd;
        const before = value.slice(Math.max(0, start - 2048), start);
        const after = value.slice(end, end + 2048);
        const startLines = value.slice(0, start).split('\n');
        const endLines = value.slice(0, end).split('\n');
        return {
            path: this.ctx.tab.path,
            languageId: 'plaintext',
            isEmpty: start === end,
            wholeDocument: start === 0 && end === value.length,
            ranges: [{
                startLine: startLines.length, startColumn: startLines[startLines.length - 1].length + 1,
                endLine: endLines.length, endColumn: endLines[endLines.length - 1].length + 1,
            }],
            text: value.slice(start, end),
            before, after,
            documentText: async () => value,
        };
    }

    /** Reduced EditorHandle: one undo step is not guaranteed. */
    applyEdits(edits) {
        for (const edit of edits) {
            if (edit.wholeDocument) this.textarea.value = edit.text;
        }
        this.ctx.onDirty(true);
    }

    dispose() {
        this._unsubs.forEach((un) => un());
    }
}
