import {createLogger} from '../util/logger.js';
import {el} from '../util/dom.js';
import {expandReferences} from './references.js';
import {applyVerboseWrappers} from './verbose.js';
import {runPipeline} from './pipeline.js';
import {isReferenceId} from '../core/store.js';
import {bus, Events} from '../core/bus.js';

/**
 * reverse-spec §4.3 render loop, §5 reference expansion, §7 delegated interactive
  * controls. Nodes are reconciled by id/version — no virtual DOM, no re-mount.
  *
  * NOTE: the client renders NO input affordances of its own. Every textbox/button/link
  * is part of the server-emitted HTML; we only delegate its clicks/keys back over the
  * socket (§7). Anything else duplicates server UI on every message.
 */

const log = createLogger('MessageList');

/** §7.2 class-based action fallback. */
const CLASS_ACTIONS = [
    ['href-link', 'link'],
    ['play-button', 'run'],
    ['regen-button', 'regen'],
    ['cancel-button', 'stop'],
    ['text-submit-button', 'text-submit']
];

const SCROLL_STICK_THRESHOLD = 40;

export class MessageListView {
    /**
     * @param {object} options
     * @param {HTMLElement} options.scroller  scrollable container
     * @param {HTMLElement} options.list      element that holds message nodes
     * @param {import('../core/store.js').MessageStore} options.store
     * @param {import('../core/transport.js').Transport} options.transport
     * @param {boolean} options.interactive   false in archive mode (§13)
     */
    constructor({scroller, list, store, transport, interactive = true}) {
        this.scroller = scroller;
        this.list = list;
        this.store = store;
        this.transport = transport;
        this.interactive = interactive;

        /** id -> DOM node */
        this.nodes = new Map();
        /** ids whose DOM must be rebuilt even if the version is unchanged */
        this.dirty = new Set();
        this.stickToBottom = true;

        this._onStoreChange = this._onStoreChange.bind(this);
        this.store.addEventListener('change', this._onStoreChange);
        this._installScrollTracking();
        if (this.interactive) this._installDelegatedHandlers();
    }

    destroy() {
        this.store.removeEventListener('change', this._onStoreChange);
    }

    /* --------------------------------------------------------------- render */

    _onStoreChange(event) {
        const {changed, dirtyHosts} = event.detail || {};
        for (const id of dirtyHosts || []) this.dirty.add(id);
        // Reference messages are never rendered directly (§4.3).
        for (const id of changed || []) if (!isReferenceId(id)) this.dirty.add(id);
        this.render();
    }

    render() {
        const messages = this.store.rendered();
        const present = new Set();
        let cursor = null;

        for (const message of messages) {
            present.add(message.id);
            let node = this.nodes.get(message.id);
            const stale =
                !node ||
                this.dirty.has(message.id) ||
                node.dataset.version !== String(message.version);

            if (stale) {
                const drafts = node ? captureDrafts(node) : null;
                const fresh = this._buildMessage(message);
                if (node?.parentNode) node.replaceWith(fresh);
                if (drafts) restoreDrafts(fresh, drafts);
                this.nodes.set(message.id, fresh);
                node = fresh;
            }

            if (!node.parentNode || node.previousElementSibling !== cursor) {
                if (cursor) cursor.after(node);
                else this.list.prepend(node);
            }
            cursor = node;
        }

        for (const [id, node] of Array.from(this.nodes)) {
            if (present.has(id)) continue;
            node.remove();
            this.nodes.delete(id);
            this.store.setDependencies(id, []);
        }
         const pending = messages.reduce((total, message) => total + (message.isPending ? 1 : 0), 0);

        this.dirty.clear();
        this._maybeScroll();
        bus.emit(Events.MESSAGES_RENDERED, {count: messages.length});
         bus.emit(Events.PENDING_CHANGED, {pending, count: messages.length});
        runPipeline(); // §6.2 trigger: message list mutated
    }

    _buildMessage(message) {
        const wrapper = el('div', {
             class: `message message-${message.type}${message.isPending ? ' message-pending' : ''}`,
             dataset: {
                 messageId: message.id,
                 version: String(message.version),
                 type: message.type,
                 pending: message.isPending ? 'true' : 'false'
             }
        });

        const body = el('div', {class: 'message-body'});
        if (message.isHtml) {
            const {html, refs} = expandReferences(message.content, this.store);
            body.innerHTML = html;
            this.store.setDependencies(message.id, refs);
        } else {
            body.textContent = message.content;
            this.store.setDependencies(message.id, []);
        }
        applyVerboseWrappers(body);
        wrapper.appendChild(body);

        return wrapper;
    }

    /* --------------------------------------------------------------- scroll */

    _installScrollTracking() {
        if (!this.scroller) return;
        this.scroller.addEventListener('scroll', () => {
            const distance =
                this.scroller.scrollHeight - this.scroller.scrollTop - this.scroller.clientHeight;
            this.stickToBottom = distance < SCROLL_STICK_THRESHOLD;
        }, {passive: true});
    }

    /** §19.3.5 — auto-scroll to bottom unless the user scrolled up. */
    _maybeScroll() {
        if (!this.scroller || !this.stickToBottom) return;
        this.scroller.scrollTop = this.scroller.scrollHeight;
    }

    /* ------------------------------------------------------ §7 interactions */

    _installDelegatedHandlers() {
        this.list.addEventListener('click', (event) => this._onClick(event));
        this.list.addEventListener('keydown', (event) => this._onKeyDown(event));
    }

    _onClick(event) {
        const target = event.target;
        if (!(target instanceof Element)) return;

        // §7.4 — tab clicks belong to the tab system, not the action handler.
        if (target.closest('.tabs .tab-button')) return;

        const action = resolveAction(target);
        if (!action) return;
        const messageId = resolveMessageId(target);
        if (!messageId) {
            log.warn('Action without a resolvable message id', {action});
            return;
        }

        event.preventDefault();
        event.stopPropagation();
        this.dispatchAction(messageId, action);
    }

    _onKeyDown(event) {
        const target = event.target;
        if (!(target instanceof Element)) return;
         // Server-injected inputs: `.reply-input`, or any textarea carrying a data-id.
         if (!target.matches('.reply-input, textarea[data-id]')) return;
        if (event.key !== 'Enter' || event.shiftKey) return;
        event.preventDefault();
        const messageId = target.getAttribute('data-id') || resolveMessageId(target);
        if (messageId) this.dispatchAction(messageId, 'text-submit');
    }

    /** §7.3 dispatch. Actions are forwarded verbatim — never whitelisted. */
    dispatchAction(messageId, action) {
        if (action !== 'text-submit') {
            log.debug('Posting action', {messageId, action});
            this.transport.sendAction(messageId, action);
            return;
        }

         const id = cssEscape(messageId);
         const input =
             document.querySelector(`.reply-input[data-id="${id}"]`) ||
             document.querySelector(`textarea[data-id="${id}"], input[type="text"][data-id="${id}"]`);
        const text = input?.value ?? '';
        if (!text.trim()) return;
        this.transport.sendUserText(messageId, text);
        if (input) {
            input.value = '';
            input.style.height = 'auto';
        }
    }
}

/* ------------------------------------------------------------------ helpers */

/** §7.1 identifier extraction. */
export function resolveMessageId(node) {
    return (
        node.getAttribute?.('data-message-id') ||
        node.closest?.('[data-message-id]')?.getAttribute('data-message-id') ||
        node.getAttribute?.('data-id') ||
        null
    );
}

/** §7.2 action extraction: explicit attributes first, then class fallback. */
export function resolveAction(node) {
    const explicit =
        node.getAttribute?.('data-message-action') ||
        node.getAttribute?.('data-action') ||
        node.closest?.('[data-message-action]')?.getAttribute('data-message-action') ||
        node.closest?.('[data-action]')?.getAttribute('data-action');
    if (explicit) return explicit;

    for (const [className, action] of CLASS_ACTIONS) {
        if (node.classList?.contains(className)) return action;
    }
    // `.href-link` is frequently an ancestor of the clicked text node's element.
    if (node.closest?.('.href-link')) return 'link';
    for (const [className, action] of CLASS_ACTIONS) {
        if (node.closest?.(`.${className}`)) return action;
    }
    return null;
}

/** Drafts inside SERVER-emitted reply inputs must survive a message re-render. */

function captureDrafts(node) {
    const drafts = new Map();
     node.querySelectorAll('.reply-input, textarea[data-id], input[type="text"][data-id]').forEach((input) => {
        const id = input.getAttribute('data-id');
        if (id && input.value) drafts.set(id, input.value);
    });
    return drafts.size ? drafts : null;
}

function restoreDrafts(node, drafts) {
     node.querySelectorAll('.reply-input, textarea[data-id], input[type="text"][data-id]').forEach((input) => {
        const id = input.getAttribute('data-id');
        if (id && drafts.has(id)) input.value = drafts.get(id);
    });
}

function cssEscape(value) {
    return typeof CSS !== 'undefined' && CSS.escape ? CSS.escape(value) : String(value).replace(/"/g, '\\"');
}