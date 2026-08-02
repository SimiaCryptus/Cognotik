import { normaliseText } from './textPipeline.js';

/**
 * Order-N character Markov model with back-off.
 *
 * Internal storage: Map<contextString, Map<char, count>>.
 * Contexts of all lengths 0..order are stored so that back-off is
 * cheap: predict() simply tries successively shorter suffixes.
 */
export class MarkovModel {
  constructor(order = 3) {
    this.order = order;
    /** @type {Map<string, Map<string, number>>} */
    this.table = new Map();
    /** @type {Map<string, number>} cached totals per context */
    this._totals = new Map();
    this.alphabet = new Set();
  }

  _bump(context, char) {
    let row = this.table.get(context);
    if (!row) {
      row = new Map();
      this.table.set(context, row);
    }
    row.set(char, (row.get(char) || 0) + 1);
    this._totals.set(context, (this._totals.get(context) || 0) + 1);
  }

  /**
   * Train on raw text. Builds frequency maps for every context length
   * from 0 (unigram) up to `order`.
   * @param {string} text
   * @param {number} [order]
   * @param {object} [opts]
   * @param {boolean} [opts.reverse] train on the reversed character stream
   */
  train(text, order = this.order, opts = {}) {
    const { reverse = false } = opts;
    this.order = order;
    let clean = normaliseText(text);
    if (reverse) clean = [...clean].reverse().join('');
    for (const ch of clean) this.alphabet.add(ch);

    for (let i = 0; i < clean.length; i++) {
      const char = clean[i];
      // record contexts of length 0..order ending right before i
      const maxK = Math.min(this.order, i);
      for (let k = 0; k <= maxK; k++) {
        const ctx = clean.slice(i - k, i);
        this._bump(ctx, char);
      }
    }
    return this;
  }

  /**
   * Predict the next-character distribution given a context string.
   * Uses back-off: if the full context is unseen, trims the front
   * until a known context (down to '') is found.
   * @param {string} context
   * @returns {Map<string, number>} normalised distribution
   */
  predict(context) {
    let ctx = context == null ? '' : String(context);
    if (ctx.length > this.order) ctx = ctx.slice(ctx.length - this.order);

    while (ctx.length > 0 && !this.table.has(ctx)) {
      ctx = ctx.slice(1);
    }
    const row = this.table.get(ctx) || this.table.get('');
    const total = this._totals.get(ctx) || this._totals.get('') || 0;
    const dist = new Map();
    if (!row || total === 0) return dist;
    for (const [char, count] of row) {
      dist.set(char, count / total);
    }
    return dist;
  }

  /** Serialise to a plain JSON-friendly object. */
  toJSON() {
    const table = {};
    for (const [ctx, row] of this.table) {
      table[ctx] = Object.fromEntries(row);
    }
    return {
      order: this.order,
      alphabet: [...this.alphabet],
      table,
    };
  }

  /** Rebuild a model from toJSON() output. */
  static fromJSON(obj) {
    const m = new MarkovModel(obj.order);
    m.alphabet = new Set(obj.alphabet || []);
    for (const ctx of Object.keys(obj.table)) {
      const row = new Map(Object.entries(obj.table[ctx]));
      let total = 0;
      for (const v of row.values()) total += v;
      m.table.set(ctx, row);
      m._totals.set(ctx, total);
    }
    return m;
  }
}
