// Player-facing verbs as pure transformations (v2 model).
//
// The tree is a flat sum/product of atoms; verbs operate on atoms and on
// adjacent pairs within a container, not on binary graph branches.
//
// Verbs:
//   split(expr, numId, { into })    - replace a Num with an equal expr
//   swap(expr, aId, bId)            - swap two adjacent members of a sum/prod
//   group(expr, [ids])              - wrap a contiguous run of members
//   ungroup(expr, groupId)          - splice a group's contents back inline
//   combine(expr, aId, bId)         - fold two adjacent atoms into one Num
//   cancel(expr, aId, bId)          - remove an inverse pair (x, -x)/(x, /x)

import {
  num,
  sum,
  product,
  group as mkGroup,
  isNum,
  isSum,
  isProduct,
  isGroup,
  findNode,
  replaceNode,
  cloneWithFreshIds,
  membersOf,
  withMembers,
  negate,
  reciprocate,
} from './expression.js';
import { evaluate } from './value.js';
import { parse } from './serialize.js';

// --- helpers ------------------------------------------------------------

// Signed/reciprocal-aware value of a single atom.
function atomValue(node) {
  return evaluate(node);
}
// Apply an additive/multiplicative inverse to a freshly-parsed replacement.
function negateNode(node) {
  return negate(node);
}
function reciprocateNode(node) {
  return reciprocate(node);
}

function findMemberIndex(container, id) {
  return membersOf(container).findIndex((m) => m.id === id);
}

// Locate the container (sum/product) that directly holds `id`, plus index.
function locateInContainer(expr, id) {
  const found = findNode(expr, id);
  if (!found || !found.parent || !(isSum(found.parent) || isProduct(found.parent))) {
    return null;
  }
  return { container: found.parent, index: found.index, node: found.node };
}

// --- split --------------------------------------------------------------
export function split(expr, numId, { into }) {
  const found = findNode(expr, numId);
  if (!found || !isNum(found.node)) {
    console.warn('[transform] split: target is not a Num', {
      numId,
      into,
      node: found && found.node,
    });
    throw new Error('split: target is not a Num');
  }
  let replacement = typeof into === 'string' ? parse(into) : into;
  replacement = cloneWithFreshIds(replacement);
  console.log('[transform] split begin', {
    numId,
    into,
    targetValue: evaluate(found.node),
    replacementValue: evaluate(replacement),
  });
  // If the target number is negated/reciprocated, the chooser presents
  // options for its bare magnitude. Fold the target's inverse flags onto
  // the replacement so values match.
  const target = found.node;
  if (target.neg) replacement = negateNode(replacement);
  if (target.recip) replacement = reciprocateNode(replacement);
  if (evaluate(replacement) !== evaluate(found.node)) {
    console.warn('[transform] split: replacement value mismatch', {
      targetValue: evaluate(found.node),
      replacementValue: evaluate(replacement),
    });
    throw new Error('split: replacement value does not match');
  }
  // A split must break a number into genuinely "smaller" parts: for
  // a -> b + c we require every part to be strictly smaller in
  // magnitude than a (i.e. Σ parts² < a²). See splitPartsAreSmaller.
  if (!splitPartsAreSmaller(found.node, replacement)) {
    const a = evaluate(found.node);
    const parts = isSum(replacement)
      ? replacement.terms.map((t) => evaluate(t))
      : [evaluate(replacement)];
    console.warn('[transform] split rejected: parts not smaller', {
      original: a,
      parts,
      rule: 'genuine break-apart (>=2 non-zero parts, none equal to original)',
    });
    throw new Error(
      `split: parts must form a genuine break-apart (need >= 2 non-zero parts, none equal to the original ${a})`,
    );
  }
  // Preserve unambiguity by wrapping structured replacements in a group.
  const wrapped = isSum(replacement) || isProduct(replacement) ? mkGroup(replacement) : replacement;
  return replaceNode(expr, numId, wrapped);
}
// Enforce the "smaller numbers" split rule. Only applies to additive
// splits (a -> b + c + ...). Every resulting top-level term must have
// a strictly smaller magnitude than the original (a genuine break-apart),
// and there must be at least two parts.
function splitPartsAreSmaller(original, replacement) {
  const a = evaluate(original);
  if (!isSum(replacement)) return true; // non-additive splits unaffected
  const parts = replacement.terms.map((t) => evaluate(t));
  if (parts.length < 2) {
    console.warn('[transform] splitPartsAreSmaller: need >= 2 parts', { original: a, parts });
    return false;
  }
  // Rule: a split must be a *genuine* break-apart. We require at least
  // two parts, none of them zero, and no single part equal to the
  // original (which would make the others sum to zero — a no-op dressed
  // up as a split). This permits pedagogically useful "rounding" splits
  // such as 19 -> 20 - 1, where one part temporarily exceeds the
  // original magnitude, while still rejecting trivial decompositions.
  const maxPart = Math.max(...parts.map((v) => Math.abs(v)));
  const sumSquares = parts.reduce((acc, v) => acc + v * v, 0);
  const anyZero = parts.some((v) => v === 0);
  const anyEqualsOriginal = parts.some((v) => v === a);
  const genuine = !anyZero && !anyEqualsOriginal;
  console.log('[transform] splitPartsAreSmaller check', {
    original: a,
    parts,
    maxPartMagnitude: maxPart,
    aMagnitude: Math.abs(a),
    sumSquares,
    aSquared: a * a,
    anyZero,
    anyEqualsOriginal,
    genuine,
  });
  return genuine;
}
// --- factorize ----------------------------------------------------------
// Multiplicative split: replace a Num with an equal product expression.
export function factorize(expr, numId, { into }) {
  const found = findNode(expr, numId);
  if (!found || !isNum(found.node)) {
    console.warn('[transform] factorize: target is not a Num', {
      numId,
      into,
      node: found && found.node,
    });
    throw new Error('factorize: target is not a Num');
  }
  let replacement = typeof into === 'string' ? parse(into) : into;
  replacement = cloneWithFreshIds(replacement);
  const target = found.node;
  if (target.neg) replacement = negateNode(replacement);
  if (target.recip) replacement = reciprocateNode(replacement);
  if (evaluate(replacement) !== evaluate(found.node)) {
    console.warn('[transform] factorize: replacement value mismatch', {
      targetValue: evaluate(found.node),
      replacementValue: evaluate(replacement),
    });
    throw new Error('factorize: replacement value does not match');
  }
  const wrapped = isSum(replacement) || isProduct(replacement) ? mkGroup(replacement) : replacement;
  return replaceNode(expr, numId, wrapped);
}

// --- swap ---------------------------------------------------------------
// Swap two adjacent members of the same sum/product.
export function swap(expr, aId, bId) {
  const a = locateInContainer(expr, aId);
  const b = locateInContainer(expr, bId);
  if (!a || !b || a.container !== b.container) {
    throw new Error('swap: targets are not siblings in a container');
  }
  if (isProduct(a.container)) {
    // products are commutative; ok
  } else if (isSum(a.container)) {
    // sums are commutative for signed atoms; ok
  } else {
    throw new Error('swap: unsupported container');
  }
  const members = membersOf(a.container).slice();
  [members[a.index], members[b.index]] = [members[b.index], members[a.index]];
  const next = withMembers(a.container, members);
  return replaceNode(expr, a.container.id, next);
}

// --- group / ungroup ----------------------------------------------------
// Group a contiguous run of members (by id list) inside their container.
export function group(expr, ids) {
  const idList = Array.isArray(ids) ? ids : [ids];
  const first = locateInContainer(expr, idList[0]);
  if (!first) throw new Error('group: target not inside a container');
  const container = first.container;
  const members = membersOf(container);
  const indices = idList.map((id) => findMemberIndex(container, id)).sort((x, y) => x - y);
  if (indices.some((i) => i < 0)) throw new Error('group: ids not all siblings');
  // Members of a commutative container may be grouped even when they are
  // not physically adjacent. Gather the selected members (in index order)
  // and splice a single group in at the position of the first selection.
  let run = indices.map((i) => members[i]);

  // Sign/reciprocal extraction: when grouping several members that all
  // carry the same inverse flag, factor that inverse out onto the group
  // and strip it from each inner member. For example, in a sum:
  //   a - b - c   =>   a - (b + c)
  // and in a product:
  //   a / b / c   =>   a / (b * c)
  let groupNeg = false;
  let groupRecip = false;
  if (isSum(container)) {
    const allNeg = run.length >= 2 && run.every((m) => (isNum(m) || isGroup(m)) && m.neg);
    if (allNeg) {
      groupNeg = true;
      run = run.map((m) => negate(m));
    }
  } else if (isProduct(container)) {
    const allRecip = run.length >= 2 && run.every((m) => (isNum(m) || isGroup(m)) && m.recip);
    if (allRecip) {
      groupRecip = true;
      run = run.map((m) => reciprocate(m));
    }
  }

  const inner = isSum(container) ? sum(run) : product(run);
  const wrapped = mkGroup(inner, { neg: groupNeg, recip: groupRecip });
  const selected = new Set(indices);
  const next = [];
  for (let i = 0; i < members.length; i++) {
    if (i === indices[0]) next.push(wrapped);
    if (selected.has(i)) continue;
    next.push(members[i]);
  }
  const result = replaceNode(expr, container.id, withMembers(container, next));
  if (evaluate(result) !== evaluate(expr)) {
    throw new Error('group: would change value');
  }
  return result;
}

export function ungroup(expr, groupId) {
  const loc = locateInContainer(expr, groupId);
  const found = findNode(expr, groupId);
  if (!found || !isGroup(found.node)) throw new Error('ungroup: target is not a group');
  const grp = found.node;
  // A group carrying an inverse can still be ungrouped by *distributing*
  // that inverse across the child's members:
  //   -(a + b)  =>  (-a) + (-b)      (neg over a sum)
  //   1/(a * b) =>  (1/a) * (1/b)    (recip over a product)
  // Distribution is only well-defined when the inverse matches the
  // child container kind; otherwise we leave the flag on the child.
  let child = grp.child;
  if (grp.neg) {
    if (isSum(child)) {
      child = withMembers(
        child,
        membersOf(child).map((m) => negate(m)),
      );
    } else {
      child = negate(child);
    }
  }
  if (grp.recip) {
    if (isProduct(child)) {
      child = withMembers(
        child,
        membersOf(child).map((m) => reciprocate(m)),
      );
    } else {
      child = reciprocate(child);
    }
  }
  // If splicing into a matching container, flatten members inline.
  if (loc) {
    const container = loc.container;
    const members = membersOf(container).slice();
    const inlineMembers =
      (isSum(container) && isSum(child)) || (isProduct(container) && isProduct(child))
        ? membersOf(child)
        : [child];
    members.splice(loc.index, 1, ...inlineMembers);
    const next = withMembers(container, members);
    if (evaluate(next) !== evaluate(expr)) {
      throw new Error('ungroup: would change value');
    }
    return replaceNode(expr, container.id, next);
  }
  // Top-level group: just unwrap.
  const candidate = replaceNode(expr, groupId, child);
  if (evaluate(candidate) !== evaluate(expr)) {
    throw new Error('ungroup: would change value');
  }
  return candidate;
}
// --- distribute ---------------------------------------------------------
// Multiplicative distribution: given a factor and a group (both members of
// the same product), multiply each member of the group's child by the
// factor, e.g.  2 * (3 + 4)  =>  (2 * 3 + 2 * 4).
// Only well-defined when the group wraps a sum. The factor is removed from
// the product and folded into each term of the group's sum.
export function distribute(expr, factorId, groupId) {
  const a = locateInContainer(expr, factorId);
  const b = locateInContainer(expr, groupId);
  if (!a || !b || a.container !== b.container) {
    throw new Error('distribute: targets are not siblings');
  }
  const container = a.container;
  if (!isProduct(container)) {
    throw new Error('distribute: container is not a product');
  }
  const groupNode = isGroup(b.node) ? b.node : null;
  const factorNode = a.node;
  if (!groupNode) throw new Error('distribute: target is not a group');
  if (isGroup(factorNode)) {
    // Only allow a plain (non-grouped) factor for simplicity.
  }
  // The group must wrap a sum (with no inverse flags) to distribute over.
  if (groupNode.neg || groupNode.recip) {
    throw new Error('distribute: cannot distribute into an inverted group');
  }
  if ((isNum(factorNode) || isGroup(factorNode)) && factorNode.recip) {
    throw new Error('distribute: cannot distribute a reciprocal factor');
  }
  const child = groupNode.child;
  if (!isSum(child)) {
    throw new Error('distribute: group does not wrap a sum');
  }
  // Multiply each term of the sum by a fresh copy of the factor.
  const newTerms = membersOf(child).map((term) => {
    const f = cloneWithFreshIds(factorNode);
    // A negated term keeps its sign by wrapping the product in a negated
    // group; otherwise pair the factor with the (sign-stripped) term.
    const termNeg = (isNum(term) || isGroup(term)) && term.neg;
    const bareTerm = termNeg
      ? isNum(term)
        ? { ...term, neg: false }
        : { ...term, neg: false }
      : term;
    const prod = product([f, cloneWithFreshIds(bareTerm)]);
    return termNeg ? mkGroup(prod, { neg: true }) : mkGroup(prod);
  });
  const newSum = sum(newTerms);
  const newGroup = mkGroup(newSum);
  // Remove the factor and replace the group with the distributed sum.
  const members = membersOf(container).slice();
  const fi = a.index;
  const gi = b.index;
  const hi = Math.max(fi, gi);
  const lo = Math.min(fi, gi);
  members.splice(hi, 1);
  members.splice(lo, 1, gi > fi ? newGroup : newGroup);
  let next = withMembers(container, members);
  if (membersOf(next).length === 1) {
    next = membersOf(next)[0];
  }
  const candidate = replaceNode(expr, container.id, next);
  if (evaluate(candidate) !== evaluate(expr)) {
    throw new Error('distribute: would change value');
  }
  return candidate;
}
// --- extract (collect common factor) -----------------------------------
// Reverse of distribute: given two or more members of the same sum, each
// of which is a product (or group wrapping a product) that shares a common
// factor, pull that factor out:
//    2 * 3 + 2 * 5   =>   2 * (3 + 5)
// `ids` selects the sum members to collect. The common factor is inferred
// as a member that appears (by structural value/shape) in every selected
// term.
export function extract(expr, ids) {
  const idList = Array.isArray(ids) ? ids : [ids];
  if (idList.length < 2) throw new Error('extract: need at least two terms');
  const first = locateInContainer(expr, idList[0]);
  if (!first) throw new Error('extract: target not inside a container');
  const container = first.container;
  if (!isSum(container)) throw new Error('extract: container is not a sum');
  const members = membersOf(container);
  const indices = idList.map((id) => findMemberIndex(container, id)).sort((x, y) => x - y);
  if (indices.some((i) => i < 0)) throw new Error('extract: ids not all siblings');
  // Each selected term must be a product (possibly wrapped in a plain group).
  const selected = indices.map((i) => members[i]);
  const productsOf = (node) => {
    if (isProduct(node)) return node.factors.slice();
    if (isGroup(node) && !node.neg && !node.recip && isProduct(node.child)) {
      return node.child.factors.slice();
    }
    return null;
  };
  const factorLists = selected.map(productsOf);
  if (factorLists.some((f) => f === null)) {
    throw new Error('extract: every selected term must be a product');
  }
  // Find a common factor by plain-number value present in every term.
  const commonValue = findCommonFactorValue(factorLists);
  if (commonValue === null) {
    throw new Error('extract: no common factor across selected terms');
  }
  // Remove one instance of the common factor from each term; the residue
  // becomes a term of the inner sum.
  const innerTerms = factorLists.map((factors) => {
    const idx = factors.findIndex((f) => isNum(f) && !f.recip && f.value === commonValue);
    factors.splice(idx, 1);
    let residue;
    if (factors.length === 0) {
      residue = num(1);
    } else if (factors.length === 1) {
      residue = cloneWithFreshIds(factors[0]);
    } else {
      residue = product(factors.map(cloneWithFreshIds));
    }
    return residue;
  });
  const innerSum = sum(innerTerms);
  const collected = product([num(commonValue), mkGroup(innerSum)]);
  // Rebuild the sum: replace the first selected slot with the collected
  // product and drop the rest.
  const selectedSet = new Set(indices);
  const next = [];
  for (let i = 0; i < members.length; i++) {
    if (i === indices[0]) next.push(collected);
    if (selectedSet.has(i)) continue;
    next.push(members[i]);
  }
  let result = withMembers(container, next);
  if (membersOf(result).length === 1) {
    result = membersOf(result)[0];
  }
  const candidate = replaceNode(expr, container.id, result);
  if (evaluate(candidate) !== evaluate(expr)) {
    throw new Error('extract: would change value');
  }
  return candidate;
}
// Given per-term factor lists, return a plain-number factor value present
// (as a non-reciprocal Num) in every list, or null if none.
function findCommonFactorValue(factorLists) {
  const [firstList, ...rest] = factorLists;
  for (const f of firstList) {
    if (!isNum(f) || f.recip) continue;
    const value = f.value;
    const inAll = rest.every((list) => list.some((g) => isNum(g) && !g.recip && g.value === value));
    if (inAll) return value;
  }
  return null;
}

// --- combine ------------------------------------------------------------
// Fold two adjacent Num atoms in a sum/product into a single Num.
// When `expected` is provided (from manual entry), it must equal the true
// arithmetic result, guarding against wrong answers.
export function combine(expr, aId, bId, expected) {
  const a = locateInContainer(expr, aId);
  const b = locateInContainer(expr, bId);
  if (!a || !b || a.container !== b.container) {
    throw new Error('combine: targets are not siblings');
  }
  if (!isNum(a.node) || !isNum(b.node)) {
    throw new Error('combine: both operands must be numbers');
  }
  const container = a.container;
  let value;
  if (isSum(container)) {
    value = atomValue(a.node) + atomValue(b.node);
  } else {
    // product: enforce exact integer result
    const prod = atomValue(a.node) * atomValue(b.node);
    if (!Number.isInteger(prod)) throw new Error('combine: non-exact division');
    value = prod;
  }
  if (expected !== undefined && expected !== null && Number(expected) !== value) {
    throw new Error(`combine: entered answer ${expected} is not correct (expected ${value})`);
  }
  const members = membersOf(container).slice();
  const lo = Math.min(a.index, b.index);
  const hi = Math.max(a.index, b.index);
  const folded = value < 0 ? num(-value, { neg: true }) : num(value);
  // Members may be non-adjacent; remove the higher index first, then
  // replace the lower index with the folded result.
  members.splice(hi, 1);
  members.splice(lo, 1, folded);
  let next = withMembers(container, members);
  // Collapse a single-member container.
  if (membersOf(next).length === 1) {
    next = membersOf(next)[0];
  }
  return replaceNode(expr, container.id, next);
}

// --- cancel -------------------------------------------------------------
// Remove an inverse pair of adjacent atoms: (x, -x) in a sum -> 0 removed,
// (x, /x) in a product -> 1 removed.
export function cancel(expr, aId, bId) {
  const a = locateInContainer(expr, aId);
  const b = locateInContainer(expr, bId);
  if (!a || !b || a.container !== b.container) {
    throw new Error('cancel: targets are not siblings');
  }
  const container = a.container;
  const va = atomValue(a.node);
  const vb = atomValue(b.node);
  if (isSum(container)) {
    if (va + vb !== 0) throw new Error('cancel: terms do not cancel');
  } else {
    if (va * vb !== 1) throw new Error('cancel: factors do not cancel');
  }
  const members = membersOf(container).slice();
  const lo = Math.min(a.index, b.index);
  const hi = Math.max(a.index, b.index);
  // Remove both members (higher index first so the lower stays valid).
  members.splice(hi, 1);
  members.splice(lo, 1);
  let next;
  if (members.length === 0) {
    next = isSum(container) ? num(0) : num(1);
  } else if (members.length === 1) {
    next = members[0];
  } else {
    next = withMembers(container, members);
  }
  const candidate = replaceNode(expr, container.id, next);
  if (evaluate(candidate) !== evaluate(expr)) {
    throw new Error('cancel: value mismatch (unexpected)');
  }
  return candidate;
}

// Registry so the session layer can dispatch by name.
export const VERBS = {
  split,
  factorize,
  swap,
  group,
  ungroup,
  combine,
  cancel,
  distribute,
  extract,
};
