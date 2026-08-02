// dom.js — small DOM helpers

export function el(tag, attrs = {}, children = []) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (k === 'class') node.className = v;
    else if (k === 'text') node.textContent = v;
    else if (k.startsWith('on') && typeof v === 'function') {
      node.addEventListener(k.slice(2).toLowerCase(), v);
    } else if (v !== null && v !== undefined) {
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

export function formatBytes(n) {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / (1024 * 1024)).toFixed(2)} MB`;
}
// Format a probability (0..1) as a percentage with 3 significant digits.
// Examples: 0.901 -> "90.1%", 0.00001 -> "0.00100%"
export function formatPct(p) {
  if (p == null || Number.isNaN(p)) return '—';
  const pct = p * 100;
  if (pct === 0) return '0%';
  if (pct >= 100) return '100%';
  // 3 significant digits
  let str = pct.toPrecision(3);
  // Avoid scientific notation for small values.
  if (str.includes('e') || str.includes('E')) {
    const decimals = Math.max(0, 2 - Math.floor(Math.log10(pct)));
    str = pct.toFixed(decimals);
  }
  // Strip trailing zeros only past the decimal if it's a whole-ish number.
  return `${str}%`;
}
