// bidirectional.js — wraps forward + backward Markov models

import { MarkovModel } from './markovModel.js';
import { combine as combineProbs } from './scoring.js';

export class BidirectionalModel {
  constructor(order) {
    this.order = order;
    this.forward = new MarkovModel(order);
    this.backward = new MarkovModel(order);
    this.tokens = [];
  }

  build(tokenStrings, onProgress) {
    this.tokens = tokenStrings;
    this.forward.build(tokenStrings, (p) => onProgress && onProgress(p * 0.5));
    const reversed = tokenStrings.slice().reverse();
    this.backward.build(reversed, (p) => onProgress && onProgress(0.5 + p * 0.5));
  }

  // Left context for position i (forward).
  leftContext(tokens, i) {
    return tokens.slice(Math.max(0, i - this.order), i);
  }

  // Right context for position i (backward, most-recent-last in reversed dir).
  rightContext(tokens, i) {
    // tokens to the right, closest first in reversed order
    const right = tokens.slice(i + 1, i + 1 + this.order).reverse();
    return right;
  }
  // Combined next-token distribution given already-oriented left/right
  // contexts. Returns an array of { token, p, pForward, pBackward } sorted by
  // combined probability, limited to `limit` entries (union of both sides'
  // top candidates). Used by the CA rule layer.
  combinedDistribution(leftCtx, rightCtx, opts = {}) {
    const {
      combine = 'average',
      limit = 32,
      smoothingK = 0,
      backoff = true,
      floorProb = 1e-6,
    } = opts;
    const probOpts = { smoothingK, backoff, floorProb };
    const candOpts = { backoff };
    const fCands = this.forward.topCandidates(leftCtx, limit * 2, candOpts);
    const bCands = this.backward.topCandidates(rightCtx, limit * 2, candOpts);
    const union = new Map();
    for (const c of fCands) union.set(c.token, true);
    for (const c of bCands) union.set(c.token, true);
    const scored = [];
    for (const tok of union.keys()) {
      const pF = this.forward.prob(leftCtx, tok, probOpts);
      const pB = this.backward.prob(rightCtx, tok, probOpts);
      const p = combineProbs(pF, pB, combine);
      scored.push({ token: tok, p, pForward: pF, pBackward: pB });
    }
    scored.sort((a, b) => b.p - a.p);
    return scored.slice(0, limit);
  }
}
