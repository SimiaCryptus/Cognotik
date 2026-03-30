// ============================================================
//  Super Mario Bros Clone — Input Handler
//  Manages keyboard and on-screen touch controls.
//  Exposes InputHandler with isDown() and isPressed() methods.
// ============================================================

/**
 * Canonical action names used throughout the game.
 * @enum {string}
 */
export const ACTION = Object.freeze({
  LEFT:  'LEFT',
  RIGHT: 'RIGHT',
  JUMP:  'JUMP',
  RUN:   'RUN',
  PAUSE: 'PAUSE',
});

/**
 * Maps KeyboardEvent.code values to ACTION names.
 * Multiple keys can map to the same action.
 */
const KEY_MAP = {
  ArrowLeft:  ACTION.LEFT,
  KeyA:       ACTION.LEFT,
  ArrowRight: ACTION.RIGHT,
  KeyD:       ACTION.RIGHT,
  ArrowUp:    ACTION.JUMP,
  KeyW:       ACTION.JUMP,
  Space:      ACTION.JUMP,
  ShiftLeft:  ACTION.RUN,
  ShiftRight: ACTION.RUN,
  KeyZ:       ACTION.RUN,
  KeyX:       ACTION.RUN,
  Enter:      ACTION.PAUSE,
  KeyP:       ACTION.PAUSE,
};

// ── Touch button element IDs → ACTION ───────────────────────
const TOUCH_MAP = {
  'btn-left':  ACTION.LEFT,
  'btn-right': ACTION.RIGHT,
  'btn-a':     ACTION.JUMP,
  'btn-b':     ACTION.RUN,
  'btn-start': ACTION.PAUSE,
};

// ============================================================
//  InputHandler class
// ============================================================
export class InputHandler {
  constructor() {
    /** @type {Map<string, boolean>} currently held actions */
    this._held    = new Map();
    /** @type {Map<string, boolean>} actions pressed this frame */
    this._pressed = new Map();
    /** @type {Map<string, boolean>} actions released this frame */
    this._released = new Map();

    // Initialise all actions to false
    for (const action of Object.values(ACTION)) {
      this._held.set(action, false);
      this._pressed.set(action, false);
      this._released.set(action, false);
    }

    this._bindKeyboard();
    this._bindTouch();
  }

  // ── Public API ─────────────────────────────────────────────

  /**
   * Returns true while the action key/button is held down.
   * @param {string} action
   */
  isDown(action) {
    return this._held.get(action) === true;
  }

  /**
   * Returns true only on the first frame the action was pressed.
   * @param {string} action
   */
  isPressed(action) {
    return this._pressed.get(action) === true;
  }

  /**
   * Returns true only on the first frame the action was released.
   * @param {string} action
   */
  isReleased(action) {
    return this._released.get(action) === true;
  }

  /**
   * Must be called at the END of each game-loop frame to clear
   * single-frame pressed/released flags.
   */
  update() {
    for (const action of Object.values(ACTION)) {
      this._pressed.set(action, false);
      this._released.set(action, false);
    }
  }

  // ── Private helpers ────────────────────────────────────────

  _press(action) {
    if (!action) return;
    if (!this._held.get(action)) {
      this._pressed.set(action, true);
    }
    this._held.set(action, true);
  }

  _release(action) {
    if (!action) return;
    if (this._held.get(action)) {
      this._released.set(action, true);
    }
    this._held.set(action, false);
  }

  // ── Keyboard ───────────────────────────────────────────────

  _bindKeyboard() {
    window.addEventListener('keydown', (e) => {
      const action = KEY_MAP[e.code];
      if (action) {
        e.preventDefault();
        this._press(action);
      }
    });

    window.addEventListener('keyup', (e) => {
      const action = KEY_MAP[e.code];
      if (action) {
        e.preventDefault();
        this._release(action);
      }
    });

    // Release all actions when window loses focus
    window.addEventListener('blur', () => {
      for (const action of Object.values(ACTION)) {
        this._release(action);
      }
    });
  }

  // ── Touch / On-screen buttons ──────────────────────────────

  _bindTouch() {
    for (const [id, action] of Object.entries(TOUCH_MAP)) {
      const el = document.getElementById(id);
      if (!el) continue;

      // Touch events (mobile)
      el.addEventListener('touchstart', (e) => {
        e.preventDefault();
        el.classList.add('pressed');
        this._press(action);
      }, { passive: false });

      el.addEventListener('touchend', (e) => {
        e.preventDefault();
        el.classList.remove('pressed');
        this._release(action);
      }, { passive: false });

      el.addEventListener('touchcancel', (e) => {
        e.preventDefault();
        el.classList.remove('pressed');
        this._release(action);
      }, { passive: false });

      // Mouse events (desktop testing of touch UI)
      el.addEventListener('mousedown', (e) => {
        e.preventDefault();
        el.classList.add('pressed');
        this._press(action);
      });

      el.addEventListener('mouseup', (e) => {
        e.preventDefault();
        el.classList.remove('pressed');
        this._release(action);
      });

      el.addEventListener('mouseleave', (e) => {
        if (this._held.get(action)) {
          el.classList.remove('pressed');
          this._release(action);
        }
      });
    }
  }
}