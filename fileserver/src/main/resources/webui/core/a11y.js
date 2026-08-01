import {h} from './dom.js';

let polite = null;
let assertive = null;

export function initA11y(root = document.body) {
    polite = h('div', {class: 'sr-only', role: 'status', 'aria-live': 'polite', 'aria-atomic': 'true'});
    assertive = h('div', {class: 'sr-only', role: 'alert', 'aria-live': 'assertive', 'aria-atomic': 'true'});
    root.appendChild(polite);
    root.appendChild(assertive);
}

let lastMessage = '';

export function announce(message, {assertive: urgent = false} = {}) {
    const target = urgent ? assertive : polite;
    if (!target || !message) return;
    /* Repeating the identical string is not re-announced; nudge it. */
    const text = message === lastMessage ? `${message}\u200b` : message;
    lastMessage = message;
    target.textContent = '';
    requestAnimationFrame(() => {
        target.textContent = text;
    });
}

const FOCUSABLE = [
    'a[href]', 'button:not([disabled])', 'input:not([disabled])', 'select:not([disabled])',
    'textarea:not([disabled])', '[tabindex]:not([tabindex="-1"])',
].join(',');

export function focusables(container) {
    return Array.from(container.querySelectorAll(FOCUSABLE))
        .filter((el) => el.offsetParent !== null || el === document.activeElement);
}

/** Focus trap + focus restoration; returns a release function. */
export function trapFocus(container, {initial} = {}) {
    const previous = document.activeElement;
    const onKeydown = (event) => {
        if (event.key !== 'Tab') return;
        const items = focusables(container);
        if (!items.length) {
            event.preventDefault();
            return;
        }
        const first = items[0];
        const last = items[items.length - 1];
        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
        }
    };
    container.addEventListener('keydown', onKeydown);
    (initial || focusables(container)[0] || container).focus?.();
    return () => {
        container.removeEventListener('keydown', onKeydown);
        if (previous && previous.isConnected) previous.focus?.();
    };
}
