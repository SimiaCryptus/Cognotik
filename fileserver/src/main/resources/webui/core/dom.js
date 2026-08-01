/** Tiny DOM helpers. User-controlled strings only ever reach the DOM as text. */
const PROPS = new Set(['value', 'checked', 'disabled', 'selected', 'multiple', 'htmlFor']);

export function h(tag, props = {}, children = []) {
    const el = document.createElement(tag);
    for (const [key, value] of Object.entries(props || {})) {
        if (value === null || value === undefined || value === false) continue;
        if (key === 'class') el.className = value;
        else if (key === 'text') el.textContent = String(value);
        else if (key === 'dataset') Object.assign(el.dataset, value);
        else if (key === 'style' && typeof value === 'object') Object.assign(el.style, value);
        else if (key.startsWith('on') && typeof value === 'function') {
            el.addEventListener(key.slice(2).toLowerCase(), value);
        } else if (PROPS.has(key)) el[key] = value;
        else el.setAttribute(key, value === true ? '' : String(value));
    }
    append(el, children);
    return el;
}

export function append(parent, children) {
    const list = Array.isArray(children) ? children : [children];
    for (const child of list) {
        if (child === null || child === undefined || child === false) continue;
        if (Array.isArray(child)) append(parent, child);
        else if (child instanceof Node) parent.appendChild(child);
        else parent.appendChild(document.createTextNode(String(child)));
    }
    return parent;
}

export function text(value) {
    return document.createTextNode(String(value ?? ''));
}

export function clear(el) {
    while (el && el.firstChild) el.removeChild(el.firstChild);
    return el;
}

export function on(target, type, handler, options) {
    target.addEventListener(type, handler, options);
    return () => target.removeEventListener(type, handler, options);
}

export function delegate(root, type, selector, handler) {
    return on(root, type, (event) => {
        const match = event.target instanceof Element ? event.target.closest(selector) : null;
        if (match && root.contains(match)) handler(event, match);
    });
}

/** Coalesce layout-affecting writes into one frame. */
const scheduled = new Set();

export function schedule(fn) {
    if (scheduled.has(fn)) return;
    scheduled.add(fn);
    requestAnimationFrame(() => {
        scheduled.delete(fn);
        fn();
    });
}

export function debounce(fn, ms) {
    let handle = null;
    return (...args) => {
        if (handle) clearTimeout(handle);
        handle = setTimeout(() => {
            handle = null;
            fn(...args);
        }, ms);
    };
}
