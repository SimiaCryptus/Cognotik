// Pointer-event gesture recognizer. Attaches to the stage root and emits
// semantic gesture events via callbacks:
//
//   onTap(id, el)            - a quick tap on a tile
//   onDragCombine(fromId,toId) - dragged one tile onto an adjacent tile
//   onDragSwap(fromId,toId)  - dragged a tile past a neighbor
//   onLongPress(id, el)      - held on a tile (radial-menu fallback)
//
// It is intentionally small: a state machine over pointerdown/move/up.

const TAP_SLOP = 10; // px of movement still considered a tap
const LONG_PRESS_MS = 500; // hold duration for long press
const SWAP_THRESHOLD = 0.6; // fraction of neighbor width to count as swap

export class GestureRecognizer {
  constructor(root, handlers = {}) {
    this.root = root;
    this.h = handlers;
    this._state = null;
    this._longTimer = null;
    this._bind();
  }

  _bind() {
    this.root.addEventListener('pointerdown', (e) => this._down(e));
    this.root.addEventListener('pointermove', (e) => this._move(e));
    this.root.addEventListener('pointerup', (e) => this._up(e));
    this.root.addEventListener('pointercancel', () => this._cancel());
  }

  _tileAt(x, y) {
    const node = document.elementFromPoint(x, y);
    return node ? node.closest('.tile') : null;
  }

  _down(e) {
    const tile = e.target.closest && e.target.closest('.tile');
    if (!tile) return;
    // Operator-sign glyph tiles are decorative and not selectable.
    if (tile.classList.contains('tile-op-sign')) return;
    this._state = {
      id: tile.dataset.id,
      kind: tile.dataset.kind,
      el: tile,
      startX: e.clientX,
      startY: e.clientY,
      moved: false,
      dragging: false,
    };
    this._longTimer = setTimeout(() => {
      if (this._state && !this._state.moved) {
        this._state.longFired = true;
        this.h.onLongPress && this.h.onLongPress(this._state.id, this._state.el);
      }
    }, LONG_PRESS_MS);
  }

  _move(e) {
    const s = this._state;
    if (!s) return;
    const dx = e.clientX - s.startX;
    const dy = e.clientY - s.startY;
    if (Math.hypot(dx, dy) > TAP_SLOP) {
      s.moved = true;
      s.dragging = true;
      clearTimeout(this._longTimer);
      s.el.classList.add('dragging');
      s.el.style.transform = `translate(${dx}px, ${dy}px)`;
    }
  }

  _up(e) {
    const s = this._state;
    clearTimeout(this._longTimer);
    if (!s) return;
    if (s.dragging) {
      s.el.classList.remove('dragging');
      s.el.style.transform = '';
      const target = this._tileAt(e.clientX, e.clientY);
      if (target && target !== s.el) {
        const toId = target.dataset.id;
        // Determine combine vs swap by drag distance relative to
        // the target tile's width.
        const rect = target.getBoundingClientRect();
        const dx = Math.abs(e.clientX - s.startX);
        if (dx > rect.width * SWAP_THRESHOLD) {
          this.h.onDragSwap && this.h.onDragSwap(s.id, toId);
        } else {
          this.h.onDragCombine && this.h.onDragCombine(s.id, toId);
        }
      }
    } else if (!s.longFired) {
      this.h.onTap && this.h.onTap(s.id, s.el, s.kind);
    }
    this._state = null;
  }

  _cancel() {
    clearTimeout(this._longTimer);
    if (this._state) {
      this._state.el.classList.remove('dragging');
      this._state.el.style.transform = '';
    }
    this._state = null;
  }
}
