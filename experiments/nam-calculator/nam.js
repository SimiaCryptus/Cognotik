// bindings/wasm/nam.js
//
// Ergonomic JavaScript wrapper around the Emscripten/embind module. This
// turns the low-level embind surface (DigitVector, enter/exit context) into
// an idiomatic JS API that mirrors the Python bindings:
//
//   - digits() returns a plain JS Array.
//   - precision_context(digits, fn) emulates Python's `with` block, running
//     `fn` inside a scoped, RAII precision context (nesting-safe).
//   - comparison stays interval-honest: -1 | 1 | null and true | false | null.
//
// This file is an ES module (matching the -sEXPORT_ES6=1 WASM glue), so it
// can be `import`ed directly in the browser:
//
//   import createNam from './nam_wasm.js';
//   import loadNam   from './nam.js';
//   const nam = await loadNam(createNam);
//
// In Node (ESM), use the same `import` form, or a dynamic import:
//   const loadNam = (await import('./nam.js')).default;
'use strict';

// `moduleFactory` is the Emscripten MODULARIZE factory (default export of
// nam_wasm.js). Returns a promise resolving to the ergonomic facade.
export default async function loadNam(moduleFactory) {
  const M = await moduleFactory();

  // Convert an embind DigitVector into a plain JS Array, freeing the
  // C++-owned vector afterwards (embind requires explicit delete()).
  function toArray(vec) {
    const out = new Array(vec.size());
    for (let i = 0; i < vec.size(); ++i) out[i] = vec.get(i);
    vec.delete();
    return out;
  }

  // Wrap a raw embind Number so its digit-returning methods hand back
  // plain JS arrays and forks return wrapped Numbers.
  function wrap(raw) {
    return {
      _raw: raw,
      base() {
        return raw.base();
      },
      fork_cost() {
        return raw.fork_cost();
      },
      in_base(b) {
        return wrap(raw.in_base(b));
      },
      streaming() {
        return wrap(raw.streaming());
      },
      cached(maxDigits) {
        return wrap(raw.cached(maxDigits));
      },
      fork() {
        const arr = raw.fork();
        return [wrap(arr[0]), wrap(arr[1])];
      },
      skip(n) {
        const r = raw.skip(n);
        return r === null ? null : wrap(r);
      },
      next_digit() {
        return raw.next_digit();
      },
      digits(n) {
        return n === undefined ? toArray(raw.digits_ctx()) : toArray(raw.digits(n));
      },
      agrees_with(other, digits) {
        return raw.agrees_with(other._raw, digits);
      },
      definitely_less_than(other, maxDigits = 30) {
        return raw.definitely_less_than(other._raw, maxDigits);
      },
      compare(other, maxDigits = 30) {
        return raw.compare(other._raw, maxDigits);
      },
      // Interval-honest arithmetic combiners. Each returns a fresh
      // wrapped Number whose digit stream is produced honestly.
      add(other) {
        return wrap(raw.add(other._raw));
      },
      sub(other) {
        return wrap(raw.sub(other._raw));
      },
      mul(other) {
        return wrap(raw.mul(other._raw));
      },
      div(other) {
        return wrap(raw.div(other._raw));
      },
      // Integer part of an arithmetic result (number | null pending).
      integer_part() {
        return raw.integer_part();
      },
      // Tier / generator introspection. Strings cross the boundary so
      // JS never depends on the C++ enum integer encoding.
      tier() {
        return raw.tier();
      },
      gen() {
        return raw.gen();
      },
      // Series-tier complexity probe: live accumulator bit-width
      // (0 for the constant-state automaton tier).
      accumulator_bitwidth() {
        return raw.accumulator_bitwidth();
      },
      // Frequency histogram over the first `n` emitted digits, as a
      // plain JS Array of length == base. A pending stall yields a
      // shorter effective count, never a fabricated entry.
      digit_histogram(n) {
        return raw.digit_histogram(n);
      },
      // Lossless JSON serialization (automaton tier). Series/arith
      // tiers emit a partial, honestly-documented payload.
      to_json() {
        return raw.to_json();
      },
      to_string(digits) {
        return raw.to_string(digits);
      },
      toString() {
        return raw.repr();
      },
    };
  }

  return {
    // Ergonomic constructors.
    rational: (p, q, base = 10) => wrap(M.rational(p, q, base)),
    sqrt: (D, base = 10) => wrap(M.sqrt(D, base)),
    padic: (a, b, p) => wrap(M.padic(a, b, p)),
    e: (base = 10) => wrap(M.e(base)),
    ln2: (base = 10) => wrap(M.ln2(base)),
    one_over_e: (base = 10) => wrap(M.one_over_e(base)),
    pi_quarter: (base = 10) => wrap(M.pi_quarter(base)),
    catalan: (base = 10) => wrap(M.catalan(base)),

    // Scoped precision context as a callback-style `with`. Restores the
    // prior precision even if `fn` throws (RAII semantics preserved).
    precision_context(digits, fn) {
      const ctx = new M.PrecisionContext(digits);
      ctx.enter();
      try {
        return fn();
      } finally {
        ctx.exit();
        ctx.delete();
      }
    },

    current_precision() {
      return M.current_precision();
    },
    // Reconstruct an automaton-tier Number from a JSON string produced
    // by Number.to_json(). Throws for series/arith payloads (they need
    // external spec/operand context) -- honest about self-containment.
    from_json(jsonString) {
      return wrap(M.from_json(jsonString));
    },
  };
}
