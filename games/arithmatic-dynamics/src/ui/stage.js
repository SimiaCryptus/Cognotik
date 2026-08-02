// Renders the live expression as a flat row of tiles, keyed by node id,
// and highlights the current selection. Uses FLIP to animate position
// changes between renders when motion is allowed.

import { el, clear, prefersReducedMotion } from '../util/dom.js';
import { isNum, isSum, isProduct, isGroup } from '../core/expression.js';
import { numberTile, operatorTile, parenTile } from './tile.js';

export class Stage {
  constructor(root, { onSelect } = {}) {
    this.root = root;
    this.onSelect = onSelect;
    this.selection = null;
    this._lastRects = new Map();
  }

  // selection may be a single id or an array of ids.
  setSelection(id) {
    this.selection = id;
  }

  _isSelected(id) {
    if (Array.isArray(this.selection)) return this.selection.includes(id);
    return this.selection === id;
  }

  render(expr) {
    this._capturePositions();
    clear(this.root);
    const row = el('div', { class: 'expr-row' });
    for (const tile of this._tilesFor(expr)) row.appendChild(tile);
    this.root.appendChild(row);
    this._playFlip();
  }

  // Flatten the AST into an ordered list of tile elements.
  // `signHandled` is true when the parent container (sum/product) has
  // already emitted this node's leading neg/recip sign glyph, so we must
  // not repeat it here.
  _tilesFor(node, signHandled = false) {
    const opts = { onSelect: this.onSelect };

    if (isNum(node)) {
      return [numberTile(node, { ...opts, selected: this._isSelected(node.id) })];
    }

    if (isGroup(node)) {
      const pieces = [
        parenTile(node.id, 'open', { ...opts, selected: this._isSelected(node.id) }),
        ...this._tilesFor(node.child),
        parenTile(node.id, 'close', { ...opts, selected: this._isSelected(node.id) }),
      ];
      // Represent group-level neg/recip with pseudo-operator glyphs, but
      // only when the parent container did not already emit that sign
      // (otherwise we'd render a double minus, e.g. "- -(20 - 5)").
      if (!signHandled) {
        if (node.neg) pieces.unshift(this._glyphTile(node.id, '−'));
        if (node.recip) pieces.unshift(this._glyphTile(node.id, '1 ÷'));
      }
      return pieces;
    }

    if (isSum(node)) {
      const out = [];
      node.terms.forEach((t, i) => {
        const opGlyph = this._termSign(t, i === 0);
        if (opGlyph) out.push(this._glyphTile(node.id, opGlyph));
        out.push(...this._tilesFor(t, true));
      });
      return out;
    }

    if (isProduct(node)) {
      const out = [];
      node.factors.forEach((f, i) => {
        const opGlyph = this._factorSign(f, i === 0);
        if (opGlyph) out.push(this._glyphTile(node.id, opGlyph));
        out.push(...this._tilesFor(f, true));
      });
      return out;
    }

    return [];
  }

  _termSign(term, first) {
    const neg = (isNum(term) || isGroup(term)) && term.neg;
    if (first) return neg ? '−' : '';
    return neg ? '−' : '+';
  }

  _factorSign(factor, first) {
    const recip = (isNum(factor) || isGroup(factor)) && factor.recip;
    if (first) return recip ? '1 ÷' : '';
    return recip ? '÷' : '×';
  }

  _glyphTile(id, glyph) {
    return operatorTile(
      { id: `${id}:op:${glyph}`, op: glyph, kind: 'op' },
      {
        onSelect: null,
        selected: false,
        rawGlyph: glyph,
      },
    );
  }

  _capturePositions() {
    this._lastRects.clear();
    const tiles = this.root.querySelectorAll('.tile');
    for (const t of tiles) {
      this._lastRects.set(t.dataset.key, t.getBoundingClientRect());
    }
  }

  _playFlip() {
    if (prefersReducedMotion()) return;
    const tiles = this.root.querySelectorAll('.tile');
    for (const t of tiles) {
      const prev = this._lastRects.get(t.dataset.key);
      if (!prev) continue;
      const next = t.getBoundingClientRect();
      const dx = prev.left - next.left;
      const dy = prev.top - next.top;
      if (dx === 0 && dy === 0) continue;
      t.style.transform = `translate(${dx}px, ${dy}px)`;
      t.style.transition = 'none';
      requestAnimationFrame(() => {
        t.style.transform = '';
        t.style.transition = 'transform 220ms ease';
      });
    }
  }
}
