// caEngine.js — holds the tape, applies one generation

// Build a "padded" neighborhood-aware tape read is handled in caRule via a
// boundary-augmented tape. To keep the rule simple (it just calls
// tape.slice and tape[i]), we build an augmented array per boundary mode
// when needed. For efficiency and simplicity we instead pass the boundary
// to the rule through a lightweight accessor object.

function makeAccessor(arr, boundary) {
  const n = arr.length;
  return {
    length: n,
    at(i) {
      if (i >= 0 && i < n) return arr[i];
      if (boundary === 'periodic') return arr[((i % n) + n) % n];
      if (boundary === 'reflective') {
        // mirror
        let idx = i;
        if (idx < 0) idx = -idx - 1;
        if (idx >= n) idx = 2 * n - idx - 1;
        idx = Math.max(0, Math.min(n - 1, idx));
        return arr[idx];
      }
      return undefined; // fixed: no neighbor
    },
    // slice with boundary handling; returns array of tokens (drops
    // undefined for fixed boundaries).
    neigh(from, to) {
      const out = [];
      for (let j = from; j < to; j++) {
        const v = this.at(j);
        if (v !== undefined) out.push(v);
      }
      return out;
    },
  };
}

export class CaEngine {
  // rule: localRule(accessor, i) -> token  (accessor has .at, .neigh, .length)
  // opts: { policy, boundary, rho, rng }
  constructor(rule, opts = {}) {
    this.rule = rule;
    this.policy = opts.policy || 'sync';
    this.boundary = opts.boundary || 'periodic';
    this.rho = opts.rho != null ? opts.rho : 0.3;
    this.rng = opts.rng || Math.random;
  }

  shuffledIndices(n) {
    const arr = Array.from({ length: n }, (_, i) => i);
    for (let i = n - 1; i > 0; i--) {
      const j = Math.floor(this.rng() * (i + 1));
      [arr[i], arr[j]] = [arr[j], arr[i]];
    }
    return arr;
  }

  // Returns { tape: nextArray, changed: bool[] }.
  step(tape) {
    const n = tape.length;
    const changed = new Array(n).fill(false);
    let next;

    if (this.policy === 'sync') {
      const acc = makeAccessor(tape, this.boundary);
      next = new Array(n);
      for (let i = 0; i < n; i++) {
        const t = this.rule(acc, i);
        next[i] = t;
        changed[i] = t !== tape[i];
      }
    } else if (this.policy === 'async') {
      next = tape.slice();
      for (const i of this.shuffledIndices(n)) {
        const acc = makeAccessor(next, this.boundary);
        const t = this.rule(acc, i);
        changed[i] = t !== next[i];
        next[i] = t;
      }
    } else {
      next = tape.slice();
      const order = this.shuffledIndices(n);
      const count = Math.max(1, Math.round(this.rho * n));
      for (const i of order.slice(0, count)) {
        const acc = makeAccessor(next, this.boundary);
        const t = this.rule(acc, i);
        changed[i] = t !== next[i];
        next[i] = t;
      }
    }
    return { tape: next, changed };
  }
}
