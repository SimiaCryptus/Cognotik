import {marked} from 'marked';
import {el} from '../util/dom.js';
import {createLogger} from '../util/logger.js';
import {highlightAllUnder} from '../render/prism.js';
import {getAppConfig} from '../config/app-config.js';

/** reverse-spec §11 — composer (input area). */

const log = createLogger('InputArea');
const SEND_TIMEOUT = 10_000;

/** §11.3 — `$1` is replaced by the selection (or the literal `text`). */
export const MARKDOWN_TOOLBAR = Object.freeze([
    {label: 'H', title: 'Heading', template: '# $1'},
    {label: 'B', title: 'Bold', template: '**$1**'},
    {label: 'I', title: 'Italic', template: '*$1*'},
    {label: '</>', title: 'Inline code', template: '`$1`'},
    {label: '{ }', title: 'Code block', template: '```\n$1\n```'},
    {label: '•', title: 'Bullet list', template: '- $1'},
    {label: '❝', title: 'Quote', template: '> $1'},
    {label: '☑', title: 'Task', template: '- [ ] $1'},
    {label: '🔗', title: 'Link', template: '[$1](url)'},
    {label: '🖼', title: 'Image', template: '![$1](image-url)'},
    {
        label: '▦',
        title: 'Table',
        template: '| $1 | Header | Header |\n| --- | --- | --- |\n|  |  |  |\n|  |  |  |'
    }
]);

export class Composer {
    constructor({host, transport, store}) {
        this.host = host;
        this.transport = transport;
        this.store = store;
        this.sending = false;
        this.connected = false;
        this.collapsed = false;
        this.previewOpen = false;
        this._build();
        this._updateState();
    }

    /* ---------------------------------------------------------------- build */

    _build() {
        this.textarea = el('textarea', {
            class: 'chat-input',
            rows: '3',
            placeholder: 'Type a message… (Enter to send, Shift+Enter for a newline)',
            onInput: () => this._onInput(),
            onKeydown: (event) => this._onKeyDown(event)
        });

        this.toolbar = el(
            'div',
            {class: 'markdown-toolbar', role: 'toolbar'},
            MARKDOWN_TOOLBAR.map((entry) =>
                el('button', {
                    class: 'toolbar-button',
                    type: 'button',
                    title: entry.title,
                    'aria-label': entry.title,
                    text: entry.label,
                    onClick: (event) => {
                        event.preventDefault();
                        this.insertMarkdown(entry.template);
                    }
                })
            )
        );

        this.preview = el('div', {class: 'composer-preview', hidden: true});

        this.previewToggle = el('button', {
            class: 'preview-toggle',
            type: 'button',
            text: 'Preview',
            onClick: () => this.togglePreview()
        });

        this.sendButton = el('button', {
            class: 'send-button cmd-button',
            type: 'button',
            text: 'Send',
            onClick: () => this.submit()
        });

        this.banner = el('div', {
            class: 'connection-banner websocket-dependent',
            role: 'status',
            hidden: true,
            text: 'Connection lost. Reconnecting… (Your message will be preserved)'
        });
         this.status = el('div', {class: 'composer-status', role: 'status', hidden: true}, [
             el('span', {class: 'spinner-border', 'aria-hidden': 'true'}, [
                 el('span', {class: 'sr-only', text: 'Loading...'})
             ]),
             el('span', {class: 'composer-status-text', text: 'Working…'})
         ]);


        this.collapseToggle = el('button', {
            class: 'composer-collapse-toggle',
            type: 'button',
            'aria-label': 'Collapse input',
             'aria-expanded': 'true',
            text: '▾',
            onClick: () => this.toggleCollapsed()
        });

        this.collapsedPlaceholder = el('button', {
            class: 'composer-collapsed',
            type: 'button',
            hidden: true,
            text: 'Click to expand input',
            onClick: () => this.toggleCollapsed()
        });

        this.body = el('div', {class: 'composer-body'}, [
            this.toolbar,
            this.textarea,
            this.preview,
            el('div', {class: 'composer-actions'}, [this.previewToggle, this.sendButton])
        ]);

        this.rootEl = el('div', {class: 'composer input-area', id: 'main-input'}, [
            this.banner,
            el('div', {class: 'composer-header'}, [this.collapseToggle, this.collapsedPlaceholder]),
            this.body
        ]);

        this.host.appendChild(this.rootEl);
        this.focus();
    }

    /* ------------------------------------------------------------- behaviour */

    focus() {
        if (!this.collapsed) this.textarea.focus();
    }

    toggleCollapsed() {
        this.collapsed = !this.collapsed;
         // `hidden` alone is not enough: `.composer-body { display:flex }` is an author
         // rule and beats the UA `[hidden]{display:none}` rule. `[hidden]` is forced in
         // base.css, and the collapsed state is also mirrored as a class for styling.
        this.body.hidden = this.collapsed;
        this.collapsedPlaceholder.hidden = !this.collapsed;
         this.rootEl.classList.toggle('collapsed', this.collapsed);
        this.collapseToggle.textContent = this.collapsed ? '▸' : '▾';
         this.collapseToggle.setAttribute('aria-expanded', this.collapsed ? 'false' : 'true');
         this.collapseToggle.setAttribute('aria-label', this.collapsed ? 'Expand input' : 'Collapse input');
        if (!this.collapsed) this.focus();
    }

    togglePreview() {
        this.previewOpen = !this.previewOpen;
        this.preview.hidden = !this.previewOpen;
        this.previewToggle.classList.toggle('active', this.previewOpen);
        if (this.previewOpen) this._renderPreview();
    }

    _renderPreview() {
        if (!this.previewOpen) return;
        try {
            this.preview.innerHTML = marked.parse(this.textarea.value || '', {gfm: true, breaks: false});
        } catch (err) {
            log.warn('Preview render failed', err);
            this.preview.textContent = this.textarea.value;
        }
        highlightAllUnder(this.preview);
    }

    _onInput() {
        this._renderPreview();
        this._updateState();
    }

    _onKeyDown(event) {
        if (event.key !== 'Enter' || event.shiftKey) return;
        event.preventDefault();
        this.submit();
    }

    /** §11.3 insertion, re-selecting the inserted body. */
    insertMarkdown(template) {
        const area = this.textarea;
        const start = area.selectionStart;
        const end = area.selectionEnd;
        const selected = area.value.slice(start, end) || 'text';
        const replacement = template.replace(/\$1/g, selected);
        const offset = template.indexOf('$1');

        area.value = area.value.slice(0, start) + replacement + area.value.slice(end);
        const bodyStart = offset >= 0 ? start + offset : start + replacement.length;
        area.selectionStart = bodyStart;
        area.selectionEnd = bodyStart + (offset >= 0 ? selected.length : 0);
        area.focus();
        this._onInput();
    }

    /** Clears only after the send resolves; the draft survives reconnects (§11.1). */
    async submit() {
        const text = this.textarea.value;
        if (!text.trim() || this.sending) return;

        this.sending = true;
        this._updateState();
        const timeout = new Promise((resolve) => setTimeout(() => resolve('timeout'), SEND_TIMEOUT));
        try {
            const result = await Promise.race([this.transport.sendMessage(text).then(() => 'sent'), timeout]);
            if (result === 'sent') {
                this.textarea.value = '';
                this._renderPreview();
            } else {
                log.warn('Send still queued after timeout — draft preserved');
            }
        } catch (err) {
            log.error('Send failed', err);
        } finally {
            this.sending = false;
            this._updateState();
            this.focus();
        }
    }

    setConnected(connected) {
        this.connected = connected;
        this.banner.hidden = connected;
        this._updateState();
    }
     /** Pending/progress status: driven by spinner markers in the server messages. */
     setPending(count = 0) {
         const pending = count > 0;
         if (this.pending === pending) return;
         this.pending = pending;
         this.status.hidden = !pending;
         document.body.classList.toggle('processing', pending);
     }


    /** §11.2 conditional hiding for single-shot apps. */
    updateVisibility() {
        const {inputCnt, stickyInput} = getAppConfig();
        const rendered = this.store.renderedCount();
        const hidden = inputCnt > 0 && rendered > inputCnt;
        this.rootEl.hidden = hidden;
        this.rootEl.classList.toggle('sticky', stickyInput !== false);
    }

    _updateState() {
        const blank = !this.textarea.value.trim();
        this.sendButton.disabled = blank || this.sending || !this.connected;
        this.updateVisibility();
    }
}