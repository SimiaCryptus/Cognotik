// GameSession — holds the current expression, undo/redo stacks, and
// tracks metrics (move count, verbs used) for star evaluation.
//
// The session is UI-agnostic: the UI subscribes to events and issues
// intents via apply(). Every apply pushes the previous state so undo is
// first-class and unlimited.

import { Emitter } from '../util/events.js';
import { isNum } from '../core/expression.js';
import { evaluate } from '../core/value.js';
import { serialize, parse } from '../core/serialize.js';
import { VERBS } from '../core/transformations.js';

export class GameSession extends Emitter {
  constructor(level) {
    super();
    this.level = level || null;
    const start = level && level.start ? level.start : '0';
    this.expr = typeof start === 'string' ? parse(start) : start;
    this.allowedVerbs = (level && level.allowedVerbs) || [
      'split',
      'factorize',
      'swap',
      'group',
      'ungroup',
      'combine',
      'cancel',
      'distribute',
      'extract',
    ];
    this._undo = [];
    this._redo = [];
    this.moveCount = 0;
    this.verbsUsed = new Set();
  }

  // Apply a named transformation. `args` are spread after the expr.
  // Returns the new expression, or throws if the verb is illegal.
  apply(verb, ...args) {
    if (!this.allowedVerbs.includes(verb)) {
      console.warn(`[GameSession] Verb not allowed in this level: ${verb}`, {
        allowedVerbs: this.allowedVerbs,
      });
      throw new Error(`Verb not allowed in this level: ${verb}`);
    }
    const fn = VERBS[verb];
    if (!fn) {
      console.error(`[GameSession] Unknown verb: ${verb}`);
      throw new Error(`Unknown verb: ${verb}`);
    }

    const before = this.expr;
    const next = fn(before, ...args);
    // Value-preservation belt-and-suspenders check.
    if (evaluate(next) !== evaluate(before)) {
      console.error(`[GameSession] Transformation ${verb} changed value`, {
        before: evaluate(before),
        after: evaluate(next),
      });
      throw new Error(`Transformation ${verb} changed value`);
    }
    console.log(`[GameSession] apply "${verb}"`, {
      from: serialize(before),
      to: serialize(next),
      moveCount: this.moveCount + 1,
    });

    this._undo.push({
      expr: before,
      moveCount: this.moveCount,
      verbsUsed: new Set(this.verbsUsed),
    });
    this._redo.length = 0;
    this.expr = next;
    this.moveCount += 1;
    this.verbsUsed.add(verb);

    this.emit('changed', { expr: this.expr, verb });
    if (this.isSolved()) {
      console.log(`[GameSession] solved!`, { expr: serialize(this.expr) });
      this.emit('solved', { expr: this.expr });
    }
    return this.expr;
  }

  canUndo() {
    return this._undo.length > 0;
  }

  canRedo() {
    return this._redo.length > 0;
  }

  undo() {
    if (!this.canUndo()) return;
    const snapshot = this._undo.pop();
    console.log(`[GameSession] undo`, {
      from: serialize(this.expr),
      to: serialize(snapshot.expr),
    });
    this._redo.push({
      expr: this.expr,
      moveCount: this.moveCount,
      verbsUsed: new Set(this.verbsUsed),
    });
    this.expr = snapshot.expr;
    this.moveCount = snapshot.moveCount;
    this.verbsUsed = snapshot.verbsUsed;
    this.emit('changed', { expr: this.expr, undo: true });
  }

  redo() {
    if (!this.canRedo()) return;
    const snapshot = this._redo.pop();
    console.log(`[GameSession] redo`, {
      from: serialize(this.expr),
      to: serialize(snapshot.expr),
    });
    this._undo.push({
      expr: this.expr,
      moveCount: this.moveCount,
      verbsUsed: new Set(this.verbsUsed),
    });
    this.expr = snapshot.expr;
    this.moveCount = snapshot.moveCount;
    this.verbsUsed = snapshot.verbsUsed;
    this.emit('changed', { expr: this.expr, redo: true });
  }

  reset() {
    const start = this.level && this.level.start ? this.level.start : '0';
    console.log(`[GameSession] reset`, { start });
    this.expr = typeof start === 'string' ? parse(start) : start;
    this._undo.length = 0;
    this._redo.length = 0;
    this.moveCount = 0;
    this.verbsUsed = new Set();
    this.emit('changed', { expr: this.expr, reset: true });
  }

  isSolved() {
    return isNum(this.expr);
  }

  serialize() {
    return serialize(this.expr);
  }
}
