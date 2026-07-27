import {h, clear} from '../../core/dom.js';
import {bus} from '../../core/bus.js';
import {store} from '../../core/store.js';
import {ui} from '../../core/ui.js';
import {announce} from '../../core/a11y.js';

let container = null;
let seq = 0;

export function initToasts(root = document.body) {
    container = h('div', {class: 'fs-toasts', 'aria-live': 'polite', 'aria-relevant': 'additions text'});
    root.appendChild(container);
    bus.on('error:raised', ({severity, message, code}) => show({severity, message, code}));
    ui.toast = show;
    return container;
}

export function show({severity = 'info', message, code, actions = [], timeout} = {}) {
    if (!container || !message) return null;
    const id = `n${++seq}`;
    const notification = {id, severity, message, code, at: Date.now()};
    store.set({notifications: [...store.get().notifications, notification].slice(-200)});

    const toast = h('div', {
        class: 'fs-toast', role: severity === 'error' ? 'alert' : 'status',
        dataset: {severity}, 'aria-live': severity === 'error' ? 'assertive' : 'polite',
    }, [
        h('div', {}, [
            h('p', {text: message}),
            code ? h('p', {class: 'fs-toast__code', text: code}) : null,
            actions.length ? h('div', {}, actions.map((action) => h('button', {
                type: 'button', text: action.label, onclick: () => {
                    dismiss();
                    action.run?.();
                },
            }))) : null,
        ]),
        h('button', {
            type: 'button', class: 'fs-toast__close', 'aria-label': 'Dismiss notification',
            onclick: () => dismiss(),
        }, [h('span', {'aria-hidden': 'true', text: '✕'})]),
    ]);

    let timer = null;
    const dismiss = () => {
        clearTimeout(timer);
        toast.remove();
    };
    const arm = () => {
        timer = setTimeout(dismiss, timeout ?? (severity === 'error' ? 12000 : 6000));
    };
    toast.addEventListener('mouseenter', () => clearTimeout(timer));
    toast.addEventListener('focusin', () => clearTimeout(timer));
    toast.addEventListener('mouseleave', arm);
    toast.addEventListener('focusout', arm);
    container.appendChild(toast);
    arm();
    announce(message, {assertive: severity === 'error'});
    return {id, dismiss};
}

export function clearToasts() {
    if (container) clear(container);
}
