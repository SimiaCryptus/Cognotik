// Which verbs are legal for a given selection of tile/node ids (v2 model).
//
// legalVerbs(expr, selection, allowed?) -> Verb[]
//
// Selection semantics:
//   single Num                          -> split
//   single group (no inverse)           -> ungroup
//   single member of a sum/product      -> group (wrap self)
//   two adjacent Num siblings           -> combine (and cancel if inverse)
//   two adjacent siblings               -> swap, group

import { isNum, isSum, isProduct, isGroup, findNode } from './expression.js';
import { evaluate } from './value.js';

function locate(expr, id) {
  const found = findNode(expr, id);
  if (!found) return null;
  const inContainer =
    found.parent && (isSum(found.parent) || isProduct(found.parent))
      ? { container: found.parent, index: found.index }
      : null;
  return { node: found.node, inContainer };
}

export function legalVerbs(expr, selection, allowed = null, opts = {}) {
  const difficulty = opts.difficulty || 'easy';
  const ids = Array.isArray(selection) ? selection : [selection];
  const verbs = new Set();
  if (ids.length === 1) {
    const loc = locate(expr, ids[0]);
    if (loc) {
      const { node } = loc;
      if (isNum(node)) {
        verbs.add('split');
        verbs.add('factorize');
      }
      // Groups can always be ungrouped: plain groups splice inline, and
      // negated/reciprocated groups distribute their inverse.
      if (isGroup(node)) verbs.add('ungroup');
    }
  } else if (ids.length === 2) {
    const a = locate(expr, ids[0]);
    const b = locate(expr, ids[1]);
    if (
      a &&
      b &&
      a.inContainer &&
      b.inContainer &&
      a.inContainer.container === b.inContainer.container
    ) {
      const container = a.inContainer.container;
      const ia = a.inContainer.index;
      const ib = b.inContainer.index;
      // Sums and products are commutative, so any two members of the same
      // container may be paired (swapped/grouped/combined/cancelled),
      // whether or not they are physically adjacent.
      void ia;
      void ib;
      {
        verbs.add('swap');
        verbs.add('group');
        if (isNum(a.node) && isNum(b.node)) {
          // Combine is always available on two numbers; whether it is
          // applied automatically or requires manual entry is decided at
          // apply time (see combineNeedsInput / difficultyAllows).
          verbs.add('combine');
          if (cancelOk(container, a.node, b.node)) verbs.add('cancel');
        }
        // Distribute: in a product, a plain (non-inverse) factor may be
        // distributed across a grouped sum. Offer it when exactly one of
        // the pair is a group wrapping a sum and the other is a plain
        // factor, with no reciprocal/negation flags in play.
        if (isProduct(container) && distributeOk(a.node, b.node)) {
          verbs.add('distribute');
        }
        // Extract: two members of a sum that are products sharing a
        // common numeric factor may have that factor collected out.
        if (isSum(container) && extractOk([a.node, b.node])) {
          verbs.add('extract');
        }
      }
    }
  } else if (ids.length > 2) {
    // Extract may collect a common factor across more than two terms of
    // the same sum.
    const locs = ids.map((id) => locate(expr, id));
    if (
      locs.every(
        (l) => l && l.inContainer && l.inContainer.container === locs[0].inContainer.container,
      )
    ) {
      const container = locs[0].inContainer.container;
      if (isSum(container) && extractOk(locs.map((l) => l.node))) {
        verbs.add('extract');
      }
    }
  }

  let result = [...verbs];
  if (allowed) result = result.filter((v) => allowed.includes(v));
  return result;
}

export function combineOk(container, a, b, difficulty = 'easy') {
  const va = evaluate(a);
  const vb = evaluate(b);
  let result;
  if (isSum(container)) {
    result = va + vb;
  } else {
    result = va * vb;
    // product: exact integer result only
    if (!Number.isInteger(result)) return false;
  }
  return difficultyAllows(difficulty, [va, vb], result);
}
// Does combining these two numbers require the player to enter the
// answer themselves? True when the difficulty gate does not permit the
// combination to be folded automatically. Products with a non-integer
// result can never be combined (return false — no manual entry helps).
export function combineNeedsInput(container, a, b, difficulty = 'easy') {
  const va = evaluate(a);
  const vb = evaluate(b);
  if (isProduct(container)) {
    const result = va * vb;
    if (!Number.isInteger(result)) return false;
  }
  return !combineOk(container, a, b, difficulty);
}

// Is a number's absolute value factorizable using only the given factors?
function factorizableBy(n, factors) {
  let v = Math.abs(n);
  if (v === 0) return false;
  for (const f of factors) {
    if (f <= 1) continue;
    while (v % f === 0) v /= f;
  }
  // A number counts as "nice" if it reduces fully to 1, or reduces to a
  // small single-digit residue (e.g. 15 = 3 * 5 leaves 3). This lets
  // round-ish numbers like 15 participate in combinations.
  return v === 1 || v < 10;
}

// Difficulty gate for a candidate combination.
//   easy   - anything
//   medium - each operand and the result is either magnitude < 5 or
//            factorizable using only 2 and 5
//   hard   - never auto-combines; every combination requires the player
//            to enter the answer manually
//   custom - each operand and the result is either magnitude < threshold
//            or factorizable using the allowed factors. Parameterized via
//            opts: { threshold, factors }.
export function difficultyAllows(difficulty, operands, result) {
  const vals = [...operands, result];
  if (difficulty === 'easy') return true;
  // Hard mode: nothing auto-combines — the player always types the answer.
  if (difficulty === 'hard') return false;
  // Resolve threshold + factors for the named/custom difficulty.
  let threshold;
  let factors;
  if (typeof difficulty === 'object' && difficulty) {
    // Allow passing a parameter object directly.
    threshold = difficulty.threshold;
    factors = difficulty.factors || [2, 5];
  } else if (difficulty === 'medium') {
    threshold = 5;
    factors = [2, 5];
  } else {
    return true;
  }
  // At least two of the three numbers (both operands and the result)
  // must be below the threshold OR factorizable using only the allowed
  // factors. This lets e.g. 5 + 10 or 1 + 25 combine even when one of
  // the numbers is "ugly".
  const satisfied = vals.filter(
    (v) => Math.abs(v) <= threshold || factorizableBy(v, factors),
  ).length;
  return satisfied >= 2;
}

function cancelOk(container, a, b) {
  if (isSum(container)) return evaluate(a) + evaluate(b) === 0;
  return evaluate(a) * evaluate(b) === 1;
}
// Distribution is legal when exactly one of the pair is a plain group
// (no neg/recip) wrapping a sum, and the other is a plain, non-reciprocal
// factor.
function distributeOk(a, b) {
  const groupNode =
    isGroup(a) && !a.neg && !a.recip ? a : isGroup(b) && !b.neg && !b.recip ? b : null;
  const factorNode = groupNode === a ? b : groupNode === b ? a : null;
  if (!groupNode || !factorNode) return false;
  if (!isSum(groupNode.child)) return false;
  if (isGroup(factorNode) && (factorNode.neg || factorNode.recip)) return false;
  if (isNum(factorNode) && factorNode.recip) return false;
  return true;
}
// Extraction is legal when every selected term is a product (or plain
// group wrapping a product) and they share a common non-reciprocal
// numeric factor.
function extractOk(nodes) {
  if (!nodes || nodes.length < 2) return false;
  const factorsOf = (node) => {
    if (isProduct(node)) return node.factors;
    if (isGroup(node) && !node.neg && !node.recip && isProduct(node.child)) {
      return node.child.factors;
    }
    return null;
  };
  const lists = nodes.map(factorsOf);
  if (lists.some((l) => l === null)) return false;
  const [first, ...rest] = lists;
  for (const f of first) {
    if (!isNum(f) || f.recip) continue;
    const value = f.value;
    if (rest.every((list) => list.some((g) => isNum(g) && !g.recip && g.value === value))) {
      return true;
    }
  }
  return false;
}
