import {h} from '../../core/dom.js';
import {readTextFor} from './EditorRegistry.js';
import {isTextLike, languageFor} from '../../core/mime.js';
import {bus} from '../../core/bus.js';
import config from '../../config.js';

let loading = null;

function loadMonaco() {
    if (window.monaco?.editor) return Promise.resolve(window.monaco);
    if (loading) return loading;
    loading = new Promise((resolve, reject) => {
        window.require = {paths: {vs: config.monaco.base}};
        const script = document.createElement('script');
        script.src = `${config.monaco.base}/loader.min.js`;
        script.onload = () => {
            try {
                window.require(['vs/editor/editor.main'], () => resolve(window.monaco), reject);
            } catch (e) {
                reject(e);
            }
        };
        script.onerror = () => reject(new Error('Monaco loader could not be fetched'));
        document.head.appendChild(script);
    });
    return loading;
}

function themeName() {
    const theme = document.documentElement.getAttribute('data-theme') || 'auto';
    if (theme === 'hc') return 'hc-black';
    if (theme === 'auto') return matchMedia('(prefers-color-scheme: dark)').matches ? 'vs-dark' : 'vs';
    return theme === 'dark' ? 'vs-dark' : 'vs';
}

export class MonacoEditor {
    static id = 'monaco';

    static canOpen(stat) {
        return config.monaco.enabled && isTextLike(stat) && (stat.size ?? 0) <= config.monaco.maxBytes;
    }

    constructor(ctx) {
        this.ctx = ctx;
        this.el = h('div', {class: 'fs-monaco'});
    }

    async load() {
        const monaco = await loadMonaco();
        const {text} = await readTextFor(this.ctx.tab);
        this.original = text ?? '';
        this.monaco = monaco;
        this.editor = monaco.editor.create(this.el, {
            value: this.original,
            language: this.ctx.tab.languageId || languageFor(this.ctx.tab.path),
            theme: themeName(),
            readOnly: this.ctx.readOnly,
            automaticLayout: true,
            accessibilitySupport: 'auto',
            ariaLabel: this.ctx.tab.path,
            minimap: {enabled: true},
            renderWhitespace: 'selection',
            scrollBeyondLastLine: false,
        });
        this.editor.onDidChangeModelContent(() => this.ctx.onDirty(this.editor.getValue() !== this.original));
        this.editor.onDidChangeCursorPosition((event) => this.ctx.onCursor?.({
            line: event.position.lineNumber,
            column: event.position.column,
            selectionCount: this.editor.getModel().getValueInRange(this.editor.getSelection()).length,
        }));
        this._themeSub = bus.on('theme:changed', () => monaco.editor.setTheme(themeName()));
        const {line, col} = this.ctx.tab;
        if (line) {
            this.editor.revealLineInCenter(line);
            this.editor.setPosition({lineNumber: line, column: col || 1});
        }
    }

    setValue(text) {
        const state = this.editor.saveViewState();
        this.original = text;
        this.editor.setValue(text);
        this.editor.restoreViewState(state);
        this.ctx.onDirty(false);
    }

    getValue() {
        return this.editor.getValue();
    }

    focus() {
        this.editor?.focus();
    }

    getSelection() {
        if (!this.editor) return null;
        const model = this.editor.getModel();
        const selections = this.editor.getSelections() || [];
        const text = selections.map((s) => model.getValueInRange(s)).join('\n');
        const first = selections[0];
        return {
            path: this.ctx.tab.path,
            languageId: model.getLanguageId(),
            isEmpty: !text,
            wholeDocument: text.length === model.getValueLength(),
            ranges: selections.map((s) => ({
                startLine: s.startLineNumber, startColumn: s.startColumn,
                endLine: s.endLineNumber, endColumn: s.endColumn,
            })),
            text,
            before: first ? model.getValueInRange({
                startLineNumber: Math.max(1, first.startLineNumber - 20), startColumn: 1,
                endLineNumber: first.startLineNumber, endColumn: first.startColumn,
            }) : '',
            after: first ? model.getValueInRange({
                startLineNumber: first.endLineNumber, startColumn: first.endColumn,
                endLineNumber: Math.min(model.getLineCount(), first.endLineNumber + 20),
                endColumn: model.getLineMaxColumn(Math.min(model.getLineCount(), first.endLineNumber + 20)),
            }) : '',
            documentText: async () => model.getValue(),
        };
    }

    /** One Mod+Z undoes an entire tool run (§19.10). */
    applyEdits(edits, {undoLabel} = {}) {
        const model = this.editor.getModel();
        this.editor.pushUndoStop?.();
        this.editor.pushStackElement?.();
        model.pushStackElement();
        model.pushEditOperations(
            this.editor.getSelections(),
            edits.map((edit) => (edit.wholeDocument
                ? {range: model.getFullModelRange(), text: edit.text}
                : {
                    range: new this.monaco.Range(edit.range.startLine, edit.range.startColumn, edit.range.endLine, edit.range.endColumn),
                    text: edit.text,
                })),
            () => null,
        );
        model.pushStackElement();
        void undoLabel;
        this.ctx.onDirty(true);
    }

    dispose() {
        this._themeSub?.();
        this.editor?.getModel()?.dispose();
        this.editor?.dispose();
    }
}
