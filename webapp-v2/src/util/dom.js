/** Small DOM helpers. No framework, no virtual DOM. */

export function el(tag, props = {}, children = []) {
    const node = document.createElement(tag);
    for (const [key, value] of Object.entries(props)) {
        if (value == null) continue;
        if (key === 'class') node.className = value;
        else if (key === 'text') node.textContent = value;
        else if (key === 'html') node.innerHTML = value;
        else if (key === 'dataset') Object.assign(node.dataset, value);
        else if (key === 'style') Object.assign(node.style, value);
        else if (key.startsWith('on') && typeof value === 'function') {
            node.addEventListener(key.slice(2).toLowerCase(), value);
        } else node.setAttribute(key, value === true ? '' : String(value));
    }
    for (const child of [].concat(children)) {
        if (child == null) continue;
        node.appendChild(typeof child === 'string' ? document.createTextNode(child) : child);
    }
    return node;
}

/** §6.3 visibility guard: skip anything in an inactive tab pane or collapsed section. */
export function isVisible(node) {
    return !!(node && node.offsetParent !== null);
}

export function idle(fn) {
    if (typeof requestIdleCallback === 'function') requestIdleCallback(fn);
    else setTimeout(fn, 0);
}

export function nextFrame(fn) {
    if (typeof requestAnimationFrame === 'function') requestAnimationFrame(fn);
    else setTimeout(fn, 16);
}

/** Parse an HTML fragment into a detached container. */
export function parseHtml(html) {
    const container = document.createElement('div');
    container.innerHTML = html;
    return container;
}

/** Direct children matching a selector — never descends into nested containers (§8.1). */
export function directChildren(parent, selector) {
    return Array.from(parent.children).filter((child) => child.matches(selector));
}
/**
  * Compact `tag#id.class[data-…] < parent < …` description for structured logs (§16).
  * Never throws: it is only ever called from error paths.
  */
export function describeNode(node, depth = 3) {
     try {
         if (!node || node.nodeType !== 1) return String(node);
         const parts = [];
         let cursor = node;
         while (cursor && cursor.nodeType === 1 && parts.length < depth) {
             const id = cursor.id ? `#${cursor.id}` : '';
             const classes = cursor.classList?.length
                 ? `.${Array.from(cursor.classList).slice(0, 4).join('.')}`
                 : '';
             const attrs = ['data-id', 'data-message-id', 'data-message-action', 'data-action', 'data-for-tab']
                 .map((name) => (cursor.hasAttribute(name) ? `[${name}=${cursor.getAttribute(name)}]` : ''))
                 .join('');
             parts.push(`${cursor.tagName.toLowerCase()}${id}${classes}${attrs}`);
             cursor = cursor.parentElement;
         }
         return parts.join(' < ');
     } catch {
         return '<undescribable node>';
     }
}