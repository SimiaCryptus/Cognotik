import {Component} from '../base.js';
import {h, clear, on} from '../../core/dom.js';
import {fs} from '../../core/fsclient.js';
import {caps} from '../../core/capabilities.js';
import {bus} from '../../core/bus.js';
import {announce} from '../../core/a11y.js';
import {raise} from '../../core/errors.js';
import {executeCommand} from '../../core/commands.js';
import config from '../../config.js';

/* ------------------------------------------------------------ xterm loader */

let xtermPromise = null;

/** Loads xterm.js (+ the fit addon) from the configured CDN, once. */
export function loadXterm() {
    if (!config.terminal.xterm.enabled) return Promise.resolve(null);
    if (window.Terminal) return Promise.resolve(window.Terminal);
    if (xtermPromise) return xtermPromise;
    const urls = config.terminal.xterm;
    xtermPromise = new Promise((resolve, reject) => {
        if (!document.querySelector(`link[href="${urls.css}"]`)) {
            document.head.appendChild(h('link', {rel: 'stylesheet', href: urls.css}));
        }
        const script = document.createElement('script');
        script.src = urls.js;
        script.onload = () => {
            /* The fit addon is a nicety: a failure must not break the terminal. */
            const fit = document.createElement('script');
            fit.src = urls.fit;
            fit.onload = () => resolve(window.Terminal);
            fit.onerror = () => resolve(window.Terminal);
            document.head.appendChild(fit);
        };
        script.onerror = () => reject(new Error('xterm.js could not be fetched'));
        document.head.appendChild(script);
    }).catch((error) => {
        xtermPromise = null;
        throw error;
    });
    return xtermPromise;
}

function cssVar(name, fallback) {
    return (getComputedStyle(document.documentElement).getPropertyValue(name) || fallback).trim();
}

function xtermTheme() {
    return {
        background: cssVar('--fs-bg', '#ffffff'),
        foreground: cssVar('--fs-fg', '#1c1e21'),
        cursor: cssVar('--fs-accent', '#0d6efd'),
        selectionBackground: cssVar('--fs-bg-active', '#dfe6f5'),
    };
}

/* -------------------------------------------------------------- line editor */

/**
 * The server attaches the child to pipes, not a PTY, so there is no remote
 * echo and no line discipline: both live here. Keeping them client-side also
 * means history and Ctrl+C stay responsive over a slow link.
 */
class LineEditor {
    constructor({echo, submit}) {
        this.echo = echo;
        this.submit = submit;
        this.line = '';
        this.history = [];
        this.cursor = 0;
    }

    data(chunk) {
        if (chunk === '\u001b[A') return this.recall(-1);
        if (chunk === '\u001b[B') return this.recall(1);
        if (chunk.startsWith('\u001b')) return undefined;   /* ignore other CSI */
        for (const ch of chunk) {
            if (ch === '\r' || ch === '\n') this.enter();
            else if (ch === '\u007f' || ch === '\b') this.backspace();
            else if (ch === '\u0003') this.abort();
            else if (ch === '\u0015') this.killLine();
            else if (ch >= ' ') {
                this.line += ch;
                this.echo(ch);
            }
        }
        return undefined;
    }

    enter() {
        this.echo('\r\n');
        const value = this.line;
        this.line = '';
        if (value.trim()) {
            this.history.push(value);
            if (this.history.length > 200) this.history.shift();
        }
        this.cursor = this.history.length;
        this.submit(`${value}\n`);
    }

    backspace() {
        if (!this.line) return;
        this.line = this.line.slice(0, -1);
        this.echo('\b \b');
    }

    killLine() {
        this.echo('\b \b'.repeat(this.line.length));
        this.line = '';
    }

    abort() {
        this.echo('^C\r\n');
        this.line = '';
    }

    recall(delta) {
        if (!this.history.length) return;
        this.cursor = Math.min(this.history.length, Math.max(0, this.cursor + delta));
        const next = this.history[this.cursor] ?? '';
        this.killLine();
        this.line = next;
        this.echo(next);
    }
}

/* ------------------------------------------------------------------- views */

class XtermView {
    constructor({Terminal, onSubmit}) {
        this.el = h('div', {class: 'fs-terminal__screen'});
        this.term = new Terminal({
            convertEol: true,
            cursorBlink: true,
            scrollback: config.terminal.scrollback,
            fontFamily: cssVar('--fs-font-mono', 'monospace'),
            fontSize: 12,
            theme: xtermTheme(),
        });
        this.editor = new LineEditor({
            echo: (text) => this.term.write(text),
            submit: onSubmit,
        });
    }

    mount(host) {
        host.appendChild(this.el);
        this.term.open(this.el);
        const FitAddon = window.FitAddon?.FitAddon;
        if (FitAddon) {
            this.fit = new FitAddon();
            this.term.loadAddon(this.fit);
        }
        this.refit();
        this.term.onData((data) => this.editor.data(data));
        if (window.ResizeObserver) {
            this.observer = new ResizeObserver(() => this.refit());
            this.observer.observe(this.el);
        }
        this.themeSub = bus.on('theme:changed', () => {
            this.term.options.theme = xtermTheme();
        });
    }

    refit() {
        try {
            this.fit?.fit();
        } catch (e) { /* the panel may be hidden */
        }
    }

    write(text) {
        this.term.write(text);
    }

    clear() {
        this.term.clear();
    }

    focus() {
        this.term.focus();
    }

    setVisible(visible) {
        this.el.hidden = !visible;
        if (visible) this.refit();
    }

    get size() {
        return {cols: this.term.cols, rows: this.term.rows};
    }

    dispose() {
        this.observer?.disconnect();
        this.themeSub?.();
        try {
            this.term.dispose();
        } catch (e) { /* already gone */
        }
        this.el.remove();
    }
}

/** Accessible, dependency-free console used when the CDN is unreachable. */
class PlainView {
    constructor({onSubmit}) {
        this.out = h('pre', {
            class: 'fs-terminal__out', tabindex: '0', role: 'log',
            'aria-live': 'polite', 'aria-label': 'Terminal output',
        });
        this.input = h('input', {
            class: 'fs-terminal__input', type: 'text', autocomplete: 'off', spellcheck: 'false',
            'aria-label': 'Terminal input', placeholder: 'Type a command and press Enter',
        });
        this.el = h('div', {class: 'fs-terminal__fallback'}, [this.out, this.input]);
        this.history = [];
        this.cursor = 0;
        on(this.input, 'keydown', (event) => {
            if (event.key === 'Enter') {
                event.preventDefault();
                const value = this.input.value;
                this.input.value = '';
                if (value.trim()) this.history.push(value);
                this.cursor = this.history.length;
                this.write(`${value}\n`);
                onSubmit(`${value}\n`);
            } else if (event.key === 'ArrowUp' || event.key === 'ArrowDown') {
                event.preventDefault();
                this.cursor = Math.min(this.history.length, Math.max(0, this.cursor + (event.key === 'ArrowUp' ? -1 : 1)));
                this.input.value = this.history[this.cursor] ?? '';
            }
        });
    }

    mount(host) {
        host.appendChild(this.el);
    }

    write(text) {
        this.out.appendChild(document.createTextNode(text));
        while (this.out.childNodes.length > 600) this.out.removeChild(this.out.firstChild);
        this.out.scrollTop = this.out.scrollHeight;
    }

    clear() {
        clear(this.out);
    }

    focus() {
        this.input.focus();
    }

    setVisible(visible) {
        this.el.hidden = !visible;
    }

    get size() {
        return {cols: 80, rows: 24};
    }

    dispose() {
        this.el.remove();
    }
}

/* ---------------------------------------------------------------- session */

class Session {
    constructor(info, {onData, onExit}) {
        this.info = info;
        this.id = info.id;
        this.seq = 0;
        this.onData = onData;
        this.onExit = onExit;
        this.exited = !info.running;
        this.closed = false;
        this.connect();
    }

    connect() {
        if (this.closed || this.exited) return;
        this.source = new EventSource(fs.terminalStreamUrl(this.id, this.seq));
        this.source.addEventListener('data', (event) => {
            let payload = null;
            try {
                payload = JSON.parse(event.data);
            } catch (e) {
                return;
            }
            this.seq = payload.seq + 1;
            this.onData(payload.data);
        });
        this.source.addEventListener('exit', (event) => {
            let code = null;
            try {
                code = JSON.parse(event.data || '{}').code;
            } catch (e) { /* ignore */
            }
            this.exited = true;
            this.disconnect();
            this.onExit(code);
        });
        /* Resume from `seq`, so a dropped stream never duplicates output. */
        this.source.onerror = () => {
            this.disconnect();
            if (!this.closed && !this.exited) this.retry = setTimeout(() => this.connect(), 1000);
        };
    }

    disconnect() {
        clearTimeout(this.retry);
        this.source?.close();
        this.source = null;
    }

    write(data) {
        if (this.exited) return Promise.resolve();
        return fs.terminalInput(this.id, data).catch((error) => raise(error, {operation: 'terminal'}));
    }

    resize(cols, rows) {
        if (this.exited) return Promise.resolve();
        return fs.terminalResize(this.id, cols, rows).catch(() => {
        });
    }

    kill() {
        return fs.terminalSignal(this.id, 'SIGKILL').catch(() => {
        });
    }

    async dispose() {
        this.closed = true;
        this.disconnect();
        await fs.terminalClose(this.id).catch(() => {
        });
    }
}

/* ------------------------------------------------------------------ panel */

export class TerminalPanel extends Component {
    render() {
        this.entries = [];
        this.active = null;
        this.tabs = h('div', {class: 'fs-terminal__tabs', role: 'tablist', 'aria-label': 'Terminal sessions'});
        this.host = h('div', {class: 'fs-terminal__host'});
        this.status = h('span', {class: 'fs-terminal__status', role: 'status'});
        this.el = h('section', {class: 'fs-terminal'}, [
            h('div', {class: 'fs-terminal__toolbar', role: 'toolbar', 'aria-label': 'Terminal'}, [
                this.tabs,
                h('span', {style: {flex: '1'}}),
                this.status,
                this.button('＋', 'New terminal', () => this.openSession({})),
                this.button('■', 'Kill session', () => this.killActive()),
                this.button('⌫', 'Clear', () => this.active?.view.clear()),
                this.button('✕', 'Hide terminal', () => executeCommand('view.toggleTerminal')),
            ]),
            this.host,
        ]);
        return this.el;
    }

    button(icon, label, onclick) {
        return h('button', {type: 'button', title: label, onclick}, [
            h('span', {'aria-hidden': 'true', text: icon}),
            h('span', {class: 'sr-only', text: label}),
        ]);
    }

    mounted() {
        if (!caps.has('terminal')) {
            this.status.textContent = 'unavailable';
            this.host.appendChild(h('p', {
                class: 'fs-explorer__status',
                text: 'This server does not provide terminal sessions.',
            }));
            this.ready = Promise.resolve();
            return;
        }
        this.status.textContent = 'line mode (no TTY)';
        this.ready = loadXterm()
            .then((Terminal) => {
                this.Terminal = Terminal;
            })
            .catch((error) => {
                console.warn('xterm.js unavailable; using the plain console', error);
                this.Terminal = null;
            });
    }

    focus() {
        this.active?.view.focus();
    }

    /** Starts a session; `command` (if any) is typed into it once it is live. */
    async openSession({cwd = '/', command, label} = {}) {
        if (!caps.has('terminal')) return null;
        await this.ready;
        let info;
        try {
            info = await fs.terminalOpen({cwd, label, cols: 80, rows: 24});
        } catch (error) {
            raise(error, {operation: 'terminal', path: cwd});
            return null;
        }
        const entry = {info, view: null, session: null, exited: false};
        const view = this.Terminal
            ? new XtermView({Terminal: this.Terminal, onSubmit: (data) => entry.session?.write(data)})
            : new PlainView({onSubmit: (data) => entry.session?.write(data)});
        entry.view = view;
        view.mount(this.host);
        entry.session = new Session(info, {
            onData: (text) => view.write(text),
            onExit: (code) => {
                entry.exited = true;
                this.renderTabs();
                announce(`${info.label} exited with code ${code}`);
            },
        });
        this.entries.push(entry);
        this.activate(entry);
        const size = view.size;
        entry.session.resize(size.cols, size.rows);
        if (command) {
            view.write(`${command}\n`);
            await entry.session.write(`${command}\n`);
        }
        announce(`Terminal opened in ${cwd}`);
        return entry;
    }

    activate(entry) {
        this.active = entry;
        for (const item of this.entries) item.view.setVisible(item === entry);
        this.renderTabs();
        entry.view.focus();
    }

    renderTabs() {
        clear(this.tabs);
        for (const entry of this.entries) {
            const selected = entry === this.active;
            this.tabs.appendChild(h('div', {
                class: 'fs-terminal__tab', role: 'tab', 'aria-selected': String(selected),
                tabindex: selected ? '0' : '-1',
                onclick: () => this.activate(entry),
                onkeydown: (event) => {
                    if (event.key === 'Enter' || event.key === ' ') {
                        event.preventDefault();
                        this.activate(entry);
                    }
                },
            }, [
                h('span', {text: `${entry.info.label}${entry.exited ? ' (exited)' : ''}`}),
                h('button', {
                    type: 'button', class: 'fs-tab__close', 'aria-label': `Close ${entry.info.label}`,
                    onclick: (event) => {
                        event.stopPropagation();
                        this.closeSession(entry);
                    },
                }, [h('span', {'aria-hidden': 'true', text: '✕'})]),
            ]));
        }
    }

    async killActive() {
        if (!this.active || this.active.exited) return;
        await this.active.session.kill();
    }

    async closeSession(entry) {
        const index = this.entries.indexOf(entry);
        if (index < 0) return;
        this.entries.splice(index, 1);
        await entry.session.dispose();
        entry.view.dispose();
        const next = this.entries[Math.min(index, this.entries.length - 1)] || null;
        this.active = null;
        if (next) this.activate(next);
        else this.renderTabs();
    }

    destroyed() {
        for (const entry of this.entries.slice()) {
            entry.session?.dispose();
            entry.view?.dispose();
        }
        this.entries.length = 0;
    }
}