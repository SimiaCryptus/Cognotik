// Reusable tiny DOM helpers / widgets.

export function el(tag, props = {}, children = []) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(props)) {
    if (k === 'class') node.className = v;
    else if (k === 'text') node.textContent = v;
    else if (k === 'html') node.innerHTML = v;
    else if (k.startsWith('on') && typeof v === 'function') {
      node.addEventListener(k.slice(2).toLowerCase(), v);
    } else if (v !== null && v !== undefined && v !== false) {
      node.setAttribute(k, v);
    }
  }
  for (const c of [].concat(children)) {
    if (c == null) continue;
    node.appendChild(typeof c === 'string' ? document.createTextNode(c) : c);
  }
  return node;
}

export function clear(node) {
  while (node.firstChild) node.removeChild(node.firstChild);
}

export function statTile(num, lbl) {
  return el('div', { class: 'stat' }, [
    el('div', { class: 'num', text: String(num) }),
    el('div', { class: 'lbl', text: lbl }),
  ]);
}

export function progressBar(pct) {
  const inner = el('div');
  inner.style.width = `${Math.max(0, Math.min(100, pct))}%`;
  return el('div', { class: 'progress' }, [inner]);
}

export function setProgress(barEl, pct) {
  const inner = barEl.querySelector('div');
  if (inner) inner.style.width = `${Math.max(0, Math.min(100, pct))}%`;
}
