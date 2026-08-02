// markovModel.js — single-direction Markov model with interned tokens

const START = -1; // sentinel token id
const KEY_SEP = ',';

export class MarkovModel {
  constructor(order) {
    this.order = order;
    this.tokenTable = []; // tokenId -> string
    this.tokenIndex = new Map(); // string -> tokenId
    this.counts = new Map(); // contextKey -> Map<tokenId, count>
    this.totals = new Map(); // contextKey -> total
  }

  intern(token) {
    let id = this.tokenIndex.get(token);
    if (id === undefined) {
      id = this.tokenTable.length;
      this.tokenTable.push(token);
      this.tokenIndex.set(token, id);
    }
    return id;
  }

  get vocabSize() {
    return this.tokenTable.length;
  }

  static keyOf(ctxIds) {
    return ctxIds.join(KEY_SEP);
  }

  // Build from a sequence of token strings (already in direction order).
  build(tokenStrings, onProgress) {
    const n = this.order;
    const ids = new Array(tokenStrings.length);
    for (let i = 0; i < tokenStrings.length; i++) {
      ids[i] = this.intern(tokenStrings[i]);
    }
    // Pad with START sentinels at the beginning.
    const seq = [];
    for (let k = 0; k < n; k++) seq.push(START);
    for (let i = 0; i < ids.length; i++) seq.push(ids[i]);

    const step = Math.max(1, Math.floor(seq.length / 100));
    for (let i = n; i < seq.length; i++) {
      const ctx = seq.slice(i - n, i);
      const next = seq[i];
      // record all backoff levels for this position
      for (let len = n; len >= 0; len--) {
        const sub = ctx.slice(n - len);
        const key = MarkovModel.keyOf(sub);
        let m = this.counts.get(key);
        if (!m) {
          m = new Map();
          this.counts.set(key, m);
        }
        m.set(next, (m.get(next) || 0) + 1);
        this.totals.set(key, (this.totals.get(key) || 0) + 1);
      }
      if (onProgress && i % step === 0) {
        onProgress((i - n) / ids.length);
      }
    }
    if (onProgress) onProgress(1);
  }

  // context: array of token strings (most recent last, length up to order)
  contextIds(contextTokens) {
    const n = this.order;
    const ids = [];
    const slice = contextTokens.slice(-n);
    const pad = n - slice.length;
    for (let k = 0; k < pad; k++) ids.push(START);
    for (const t of slice) {
      const id = this.tokenIndex.get(t);
      ids.push(id === undefined ? START : id);
    }
    return ids;
  }

  // Probability of `token` given contextTokens, with optional backoff.
  prob(contextTokens, token, { smoothingK = 0, backoff = true, floorProb = 1e-6 } = {}) {
    const ctxIds = this.contextIds(contextTokens);
    const V = this.vocabSize || 1;
    const tokenId = this.tokenIndex.get(token);
    const maxLen = ctxIds.length;
    for (let len = maxLen; len >= 0; len--) {
      const sub = ctxIds.slice(maxLen - len);
      const key = MarkovModel.keyOf(sub);
      const m = this.counts.get(key);
      if (m) {
        const total = this.totals.get(key) || 0;
        const c = (tokenId !== undefined && m.get(tokenId)) || 0;
        if (smoothingK > 0) {
          return (c + smoothingK) / (total + smoothingK * V);
        }
        if (c > 0) return c / total;
      }
      if (!backoff) break;
    }
    return floorProb;
  }

  // Top candidate tokens (as strings) given context, with prob.
  topCandidates(contextTokens, limit, opts = {}) {
    const ctxIds = this.contextIds(contextTokens);
    const maxLen = ctxIds.length;
    for (let len = maxLen; len >= 0; len--) {
      const sub = ctxIds.slice(maxLen - len);
      const key = MarkovModel.keyOf(sub);
      const m = this.counts.get(key);
      if (m && m.size) {
        const total = this.totals.get(key) || 0;
        const arr = [];
        for (const [id, c] of m) {
          if (id === START) continue;
          arr.push({ token: this.tokenTable[id], p: c / total });
        }
        arr.sort((a, b) => b.p - a.p);
        return arr.slice(0, limit);
      }
      if (!opts.backoff) break;
    }
    return [];
  }

  summary(tokenizerId, corpusLength, tokenCount) {
    return {
      tokenizerId,
      order: this.order,
      tokenCount,
      uniqueTokens: this.vocabSize,
      corpusLength,
      builtAt: Date.now(),
    };
  }
}
