// Random problem generator.
//
// Produces a level-like spec whose `start` string evaluates to a tidy
// target and is solvable with the given verb set. Two flavours:
//   additive       -> a ± b (± c)
//   multiplicative -> a × b, or (a × b) ÷ b style cancels

import { defineLevel, Stars } from './level.js';

function randInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function pick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

export function generateAdditive({ terms = 2, minTerm = 1, maxTerm = 30 } = {}) {
  const parts = [String(randInt(minTerm, maxTerm))];
  for (let i = 1; i < terms; i++) {
    const op = pick(['+', '-']);
    parts.push(op, String(randInt(minTerm, maxTerm)));
  }
  const start = parts.join(' ');
  console.log(`[generator] generateAdditive`, { terms, minTerm, maxTerm, start });
  return defineLevel({
    id: `rand-add-${Date.now()}`,
    start,
    allowedVerbs: ['split', 'swap', 'group', 'ungroup', 'combine', 'cancel'],
    allowedOps: ['+', '-'],
    stars: [Stars.solve(), Stars.fewMoves(terms)],
    hint: 'Combine adjacent numbers to simplify.',
  });
}

export function generateMultiplicative({ minFactor = 2, maxFactor = 9 } = {}) {
  const lo = Math.max(2, minFactor);
  const a = randInt(lo, maxFactor);
  const b = randInt(lo, maxFactor);
  // 50/50: plain product, or product-then-divide cancel.
  let start;
  if (Math.random() < 0.5) {
    start = `${a} * ${b}`;
  } else {
    start = `(${a} * ${b}) / ${b}`;
  }
  console.log(`[generator] generateMultiplicative`, { a, b, start });
  return defineLevel({
    id: `rand-mul-${Date.now()}`,
    start,
    allowedVerbs: ['split', 'factorize', 'swap', 'group', 'ungroup', 'combine', 'cancel'],
    allowedOps: ['*', '/'],
    stars: [Stars.solve()],
    hint: 'Cancel matching factors, then combine.',
  });
}
// Mixed additive + multiplicative problem, e.g. "4 + 3 * 5 - 2".
// Multiplicative sub-terms are kept small so the result stays tidy, and
// division is only introduced as an exact product-then-divide pair.
export function generateMixed({
  terms = 3,
  minTerm = 1,
  maxTerm = 12,
  minFactor = 2,
  maxFactor = 9,
} = {}) {
  const lo = Math.max(2, minFactor);
  const parts = [makeTerm(0)];
  for (let i = 1; i < terms; i++) {
    parts.push(pick(['+', '-']), makeTerm(i));
  }
  const start = parts.join(' ');
  console.log(`[generator] generateMixed`, { terms, start });
  return defineLevel({
    id: `rand-mix-${Date.now()}`,
    start,
    allowedVerbs: ['split', 'factorize', 'swap', 'group', 'ungroup', 'combine', 'cancel'],
    allowedOps: ['+', '-', '*', '/'],
    stars: [Stars.solve()],
    hint: 'Handle products first, then combine sums.',
  });
  // Each term is either a plain number or a small product/quotient.
  function makeTerm() {
    const r = Math.random();
    if (r < 0.45) {
      const a = randInt(lo, maxFactor);
      const b = randInt(lo, maxFactor);
      return `${a} * ${b}`;
    }
    if (r < 0.6) {
      const a = randInt(lo, maxFactor);
      const b = randInt(lo, maxFactor);
      // exact product-then-divide
      return `(${a} * ${b}) / ${b}`;
    }
    return String(randInt(minTerm, maxTerm));
  }
}

export function generateRandom({
  allowMultiply = true,
  minTerm = 1,
  maxTerm = 30,
  ops = null,
} = {}) {
  // `ops` counts operators; number of terms is ops + 1.
  const terms = ops ? ops + 1 : pick([2, 3]);
  if (!allowMultiply) {
    return generateAdditive({ terms, minTerm, maxTerm });
  }
  // Three flavours when multiply is enabled: additive, multiplicative, and
  // mixed (a blend of + / - with * / /). Mixed needs at least two terms.
  const r = Math.random();
  if (r < 0.4) {
    return generateAdditive({ terms, minTerm, maxTerm });
  }
  if (r < 0.7 && terms >= 2) {
    return generateMixed({
      terms,
      minTerm,
      maxTerm: Math.min(maxTerm, 12),
      minFactor: Math.max(2, minTerm),
      maxFactor: Math.max(2, Math.min(maxTerm, 9)),
    });
  }
  return generateMultiplicative({
    minFactor: Math.max(2, minTerm),
    maxFactor: Math.max(2, maxTerm),
  });
}
