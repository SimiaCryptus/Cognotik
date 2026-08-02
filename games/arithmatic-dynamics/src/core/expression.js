// Expression AST node types + construction helpers.
//
// New model (v2): arithmetic is represented with flat, n-ary "sum" and
// "product" nodes whose members are *atoms* that carry their own inverse.
//
//   Num     - { kind: 'num',   id, value, neg, recip }
//               neg   : additive inverse   (3 - 2  ==  sum[3, -2])
//               recip : multiplicative inv (6 / 5  ==  product[6, /5])
//   Sum     - { kind: 'sum',     id, terms: Node[] }     (a + b + c ...)
//   Product - { kind: 'product', id, factors: Node[] }   (a * b * c ...)
//   Group   - { kind: 'group',   id, child, neg, recip } (parenthesised)
//
// A term/factor may itself be a Group (so negatives and reciprocals can
// live inside parentheses: -(2 + 3), 1/(2 + 3)).
//
// All nodes are treated as immutable. Transformations produce new trees;
// ids are preserved where a node is conceptually "the same" so the UI can
// animate transitions.

import { nextId } from '../util/id.js';

export const OPS = ['+', '-', '*', '/'];
export const COMMUTATIVE = new Set(['+', '*']);

// Inverse pairing used by cancellation and split hints.
export const INVERSE = { '+': '-', '-': '+', '*': '/', '/': '*' };

export function num(value, { neg = false, recip = false, id = nextId('num') } = {}) {
  if (!Number.isFinite(value)) {
    throw new Error(`num() requires a finite value, got ${value}`);
  }
  return { kind: 'num', id, value, neg, recip };
}

export function sum(terms, id = nextId('sum')) {
  return { kind: 'sum', id, terms };
}

export function product(factors, id = nextId('prd')) {
  return { kind: 'product', id, factors };
}

export function group(child, { neg = false, recip = false, id = nextId('grp') } = {}) {
  return { kind: 'group', id, child, neg, recip };
}

export function isNum(node) {
  return node && node.kind === 'num';
}

export function isSum(node) {
  return node && node.kind === 'sum';
}

export function isProduct(node) {
  return node && node.kind === 'product';
}

export function isGroup(node) {
  return node && node.kind === 'group';
}

// Back-compat shim: some old code asks "isOp". A sum/product plays that role.
export function isOp(node) {
  return isSum(node) || isProduct(node);
}

// The list of member children for a container node (sum/product), or [].
export function membersOf(node) {
  if (isSum(node)) return node.terms;
  if (isProduct(node)) return node.factors;
  return [];
}

export function withMembers(node, members) {
  if (isSum(node)) return { ...node, terms: members };
  if (isProduct(node)) return { ...node, factors: members };
  return node;
}

// Toggle helpers for atom inverses.
export function negate(node) {
  if (isNum(node) || isGroup(node)) return { ...node, neg: !node.neg };
  // Wrap other kinds in a negated group.
  return group(node, { neg: true });
}

export function reciprocate(node) {
  if (isNum(node) || isGroup(node)) return { ...node, recip: !node.recip };
  return group(node, { recip: true });
}

// Deep-clone a subtree, assigning fresh ids. Useful when duplicating a
// sub-expression during a transformation would otherwise collide.
export function cloneWithFreshIds(node) {
  if (isNum(node)) return num(node.value, { neg: node.neg, recip: node.recip });
  if (isGroup(node))
    return group(cloneWithFreshIds(node.child), { neg: node.neg, recip: node.recip });
  if (isSum(node)) return sum(node.terms.map(cloneWithFreshIds));
  if (isProduct(node)) return product(node.factors.map(cloneWithFreshIds));
  throw new Error(`cloneWithFreshIds: unknown node kind`);
}

// Find a node (and its parent + which member index it hangs from) by id.
// Returns { node, parent, index } or null.
export function findNode(root, id, parent = null, index = null) {
  if (!root) return null;
  if (root.id === id) return { node: root, parent, index };
  if (isGroup(root)) {
    return findNode(root.child, id, root, 'child');
  }
  if (isSum(root) || isProduct(root)) {
    const members = membersOf(root);
    for (let i = 0; i < members.length; i++) {
      const hit = findNode(members[i], id, root, i);
      if (hit) return hit;
    }
  }
  return null;
}

// Return a new tree where the node with `id` is replaced by `replacement`.
export function replaceNode(root, id, replacement) {
  if (!root) return root;
  if (root.id === id) return replacement;
  if (isGroup(root)) {
    const child = replaceNode(root.child, id, replacement);
    return child === root.child ? root : { ...root, child };
  }
  if (isSum(root) || isProduct(root)) {
    const members = membersOf(root);
    let changed = false;
    const next = members.map((m) => {
      const r = replaceNode(m, id, replacement);
      if (r !== m) changed = true;
      return r;
    });
    return changed ? withMembers(root, next) : root;
  }
  return root;
}

// Collect every node id present in the tree (for validation / debugging).
export function collectIds(root, acc = new Set()) {
  if (!root) return acc;
  acc.add(root.id);
  if (isGroup(root)) collectIds(root.child, acc);
  if (isSum(root) || isProduct(root)) {
    for (const m of membersOf(root)) collectIds(m, acc);
  }
  return acc;
}
