// Evaluate an expression AST to an exact number.
//
// Integer-only semantics: division is only legal when it divides exactly.
// The v2 model encodes subtraction as a negated term and division as a
// reciprocal factor, so evaluation walks sums/products and applies the
// per-atom neg/recip flags.

import { isNum, isSum, isProduct, isGroup } from './expression.js';

// Raw (unsigned) value of a node ignoring its own neg/recip flags.
function rawValue(node) {
  if (isNum(node)) return node.value;
  if (isGroup(node)) return evaluate(node.child);
  if (isSum(node)) {
    let acc = 0;
    for (const t of node.terms) acc += evaluate(t);
    return acc;
  }
  if (isProduct(node)) {
    let acc = 1;
    for (const f of node.factors) acc *= evaluate(f);
    return acc;
  }
  throw new Error('evaluate: unknown node kind');
}

export function evaluate(node) {
  let v = rawValue(node);
  if ((isNum(node) || isGroup(node)) && node.neg) v = -v;
  if ((isNum(node) || isGroup(node)) && node.recip) {
    if (v === 0) throw new Error('Division by zero');
    // Only report a value; exactness is enforced where products fold.
    v = 1 / v;
  }
  return v;
}

// Convenience: does `a` evaluate to the same value as `b`?
export function sameValue(a, b) {
  return evaluate(a) === evaluate(b);
}
