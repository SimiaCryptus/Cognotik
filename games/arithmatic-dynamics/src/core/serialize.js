// AST <-> plain string (v2 model).
//
//   serialize(ast) -> "4 + 19"
//   parse("3 - 2")  -> sum[3, -2]
//
// Subtraction/division are parsed into signed atoms:
//   3 - 2   => sum([ num(3), num(2, {neg}) ])
//   6 / 5   => product([ num(6), num(5, {recip}) ])
//   -(2+3)  => group(sum([2,3]), {neg})
//   1/(2+3) => group(sum([2,3]), {recip})

import { num, sum, product, group, isNum, isSum, isProduct, isGroup } from './expression.js';

export function serialize(node) {
  return stringify(node, 0);
}

// parentPrec: 0 top, 1 additive, 2 multiplicative
function stringify(node, parentPrec) {
  if (isNum(node)) {
    let s = String(node.value);
    if (node.recip) s = `1 / ${s}`;
    if (node.neg) s = `-${node.value}`; // neg on a number
    if (node.neg && node.recip) s = `1 / -${node.value}`;
    return s;
  }
  if (isGroup(node)) {
    let inner = `(${stringify(node.child, 0)})`;
    if (node.recip) inner = `1 / ${inner}`;
    if (node.neg) inner = `-${inner}`;
    return inner;
  }
  if (isSum(node)) {
    const parts = node.terms.map((t, i) => termText(t, i === 0));
    const s = parts.join(' ');
    return 1 < parentPrec ? `(${s})` : s;
  }
  if (isProduct(node)) {
    const parts = node.factors.map((f, i) => factorText(f, i === 0));
    const s = parts.join(' ');
    return 2 < parentPrec ? `(${s})` : s;
  }
  throw new Error('serialize: unknown node kind');
}

function bareValue(node) {
  // stringify a term/factor without its leading sign/recip operator.
  if (isNum(node)) return String(node.value);
  if (isGroup(node)) return `(${stringify(node.child, 0)})`;
  return stringify(node, 2);
}

function termText(node, first) {
  const neg = (isNum(node) || isGroup(node)) && node.neg;
  const body =
    (isNum(node) || isGroup(node)) && node.recip ? `1 / ${bareValue(node)}` : bareValue(node);
  if (first) return neg ? `-${body}` : body;
  return neg ? `- ${body}` : `+ ${body}`;
}

function factorText(node, first) {
  const recip = (isNum(node) || isGroup(node)) && node.recip;
  const neg = (isNum(node) || isGroup(node)) && node.neg;
  let body = bareValue(node);
  if (neg) body = `(-${body})`;
  if (first) return recip ? `1 / ${body}` : body;
  return recip ? `/ ${body}` : `* ${body}`;
}

export function parse(input) {
  const tokens = tokenize(input);
  let pos = 0;

  function peek() {
    return tokens[pos];
  }
  function next() {
    return tokens[pos++];
  }

  function parseExpr() {
    const terms = [];
    let sign = 1;
    // optional leading sign
    if (peek() === '+' || peek() === '-') {
      sign = next() === '-' ? -1 : 1;
    }
    terms.push(applySign(parseTerm(), sign));
    while (peek() === '+' || peek() === '-') {
      const s = next() === '-' ? -1 : 1;
      terms.push(applySign(parseTerm(), s));
    }
    if (terms.length === 1) return terms[0];
    return sum(terms);
  }

  function parseTerm() {
    const factors = [];
    factors.push(parseFactor());
    while (peek() === '*' || peek() === '/') {
      const recip = next() === '/';
      const f = parseFactor();
      factors.push(recip ? reciprocateNode(f) : f);
    }
    if (factors.length === 1) return factors[0];
    return product(factors);
  }

  function parseFactor() {
    const t = peek();
    if (t === '(') {
      next();
      const inner = parseExpr();
      if (next() !== ')') throw new Error('Expected closing paren');
      return group(inner);
    }
    if (t === undefined) throw new Error('Unexpected end of input');
    const value = Number(next());
    if (!Number.isFinite(value)) throw new Error(`Bad number: ${t}`);
    return num(value);
  }

  const ast = parseExpr();
  if (pos !== tokens.length) {
    throw new Error(`Trailing tokens starting at "${tokens[pos]}"`);
  }
  return ast;
}

// Apply an additive sign to a freshly-parsed term.
function applySign(node, sign) {
  if (sign >= 0) return node;
  if (isNum(node)) return { ...node, neg: !node.neg };
  if (isGroup(node)) return { ...node, neg: !node.neg };
  // negate a product: negate its whole value via a wrapping group.
  return group(node, { neg: true });
}

function reciprocateNode(node) {
  if (isNum(node)) return { ...node, recip: !node.recip };
  if (isGroup(node)) return { ...node, recip: !node.recip };
  return group(node, { recip: true });
}

function tokenize(input) {
  const tokens = [];
  const re = /\s*(\d+|[()+\-*/])\s*/y;
  let idx = 0;
  while (idx < input.length) {
    re.lastIndex = idx;
    const m = re.exec(input);
    if (!m) throw new Error(`Unexpected char at ${idx}: "${input[idx]}"`);
    tokens.push(m[1]);
    idx = re.lastIndex;
  }
  return tokens;
}

// Structural equality ignoring ids — handy for round-trip tests.
export function structurallyEqual(a, b) {
  if (isNum(a) && isNum(b))
    return a.value === b.value && !!a.neg === !!b.neg && !!a.recip === !!b.recip;
  if (isGroup(a) && isGroup(b))
    return !!a.neg === !!b.neg && !!a.recip === !!b.recip && structurallyEqual(a.child, b.child);
  if (isSum(a) && isSum(b)) {
    return (
      a.terms.length === b.terms.length && a.terms.every((t, i) => structurallyEqual(t, b.terms[i]))
    );
  }
  if (isProduct(a) && isProduct(b)) {
    return (
      a.factors.length === b.factors.length &&
      a.factors.every((f, i) => structurallyEqual(f, b.factors[i]))
    );
  }
  return false;
}
