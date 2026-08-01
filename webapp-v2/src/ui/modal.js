import {el, nextFrame} from '../util/dom.js';
import {createLogger} from '../util/logger.js';
import {highlightAllUnder} from '../render/prism.js';
import {bus, Events} from '../core/bus.js';

/** reverse-spec §14 — modal fetch/host. Fixes §20.5 (full pipeline over modal HTML). */

const log = createLogger('Modal');

let overlay = null;
let titleEl = null;
let bodyEl = null;
let sessionIdRef = () => '';

/** §14.1 URL construction (session propagation included). */
export function getModalUrl(endpoint, sessionId) {
    const loc = window.location;
    const port = loc.port ? `:${loc.port}` : '';
    const base = `${loc.protocol}//${loc.hostname}${port}`;
    let url = endpoint.startsWith('/') ? base + endpoint : base + loc.pathname + endpoint;
    if (endpoint.endsWith('/')) url += `${sessionId}/`;
    else url += `${endpoint.includes('?') ? '&' : '?'}sessionId=${encodeURIComponent(sessionId)}`;
    return url;
}

export function installModal({mount, getSessionId}) {
    sessionIdRef = getSessionId;

    titleEl = el('h2', {class: 'modal-title', id: 'modal-title'});
    bodyEl = el('div', {class: 'modal-body'});
    const content = el('div', {
        class: 'modal-content',
        role: 'dialog',
        'aria-modal': 'true',
        'aria-labelledby': 'modal-title',
        onClick: (event) => event.stopPropagation()
    }, [
        el('div', {class: 'modal-header'}, [
            titleEl,
            el('button', {class: 'modal-close', type: 'button', 'aria-label': 'Close', text: '×', onClick: closeModal})
        ]),
        bodyEl
    ]);

    overlay = el('div', {class: 'modal-overlay', hidden: true, onClick: closeModal}, [content]);
    mount.appendChild(overlay);

    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape' && !overlay.hidden) closeModal();
    });
    return {openModal, closeModal};
}

export function closeModal() {
    if (!overlay) return;
    overlay.hidden = true;
    bodyEl.innerHTML = '';
}

export async function openModal(endpoint) {
    if (!overlay) {
        log.error('Modal not installed');
        return;
    }
    const sessionId = sessionIdRef();
    const url = getModalUrl(endpoint, sessionId);

    overlay.hidden = false;
    titleEl.textContent = endpoint;
    bodyEl.innerHTML = '<div class="loading">Loading...</div>';

    // fileIndex/ is hosted in an iframe; no fetch, no post-processing (§14.2).
    if (endpoint === 'fileIndex/') {
        nextFrame(() => {
            bodyEl.innerHTML = '';
            bodyEl.appendChild(
                el('iframe', {
                    src: url,
                    title: 'File Index',
                    sandbox: 'allow-scripts allow-same-origin allow-forms allow-popups',
                    style: {width: '90vw', height: '80vh', border: 'none'}
                })
            );
        });
        return;
    }

    try {
        const response = await fetch(url, {
            mode: 'cors',
            credentials: 'include',
            headers: {Accept: 'text/html,application/json, */*'}
        });
        if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
        const text = await response.text();
        nextFrame(() => {
            bodyEl.innerHTML = text;
            highlightAllUnder(bodyEl);
            // Modal HTML may contain tabs/collapsibles/mermaid/math (§20.5).
            bus.emit(Events.MODAL_CONTENT_READY, bodyEl);
        });
    } catch (err) {
        log.error('Failed to load modal content', {url, error: err});
        bodyEl.innerHTML =
            `<div class="error">Error loading content: ${escapeHtml(err.message)}` +
            `<br><br>Attempted URL: ${escapeHtml(url)}</div>`;
    }
}

/** Reinstates the `[data-modal]` document listener that §20.6 made unreachable. */
export function installModalTriggers(target = document) {
    const handler = (event) => {
        const trigger = event.target.closest?.('[data-modal]');
        if (!trigger) return;
        event.preventDefault();
        openModal(trigger.getAttribute('data-modal'));
    };
    target.addEventListener('click', handler);
    return () => target.removeEventListener('click', handler);
}

function escapeHtml(value) {
    return String(value).replace(/[&<>"']/g, (c) =>
        ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
}