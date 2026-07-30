import {el} from '../util/dom.js';

/** reverse-spec §1.2 step 3 — mount the shell DOM. */
export function mountShell(root) {
    root.classList.add('app');

    const list = el('div', {class: 'message-list', id: 'message-list', role: 'log'});
    const scroller = el('div', {class: 'message-list-container', id: 'message-list-container'}, [list]);
    const composerHost = el('div', {class: 'chat-input-container', id: 'chat-input-container'});
    const modalRoot = el('div', {class: 'modal-root', id: 'modal-root'});

    root.appendChild(scroller);
    root.appendChild(composerHost);
    root.appendChild(modalRoot);

    return {root, scroller, list, composerHost, modalRoot};
}