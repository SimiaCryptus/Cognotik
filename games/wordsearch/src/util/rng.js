// Deterministic seeded RNG utilities for linkable, reproducible games.

/**
 * Hash a string into a 32-bit integer seed (xfnv1a).
 * @param {string} str
 * @returns {number}
 */
export function hashSeed(str) {
  let h = 2166136261 >>> 0;
  for (let i = 0; i < str.length; i++) {
    h = Math.imul(h ^ str.charCodeAt(i), 16777619);
  }
  return h >>> 0;
}

/**
 * Mulberry32 PRNG. Returns a function producing floats in [0, 1).
 * @param {number} seed 32-bit integer
 * @returns {() => number}
 */
export function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/**
 * Build a deterministic RNG from an arbitrary seed string. If the seed is
 * empty / falsy, returns Math.random (non-deterministic).
 * @param {string} [seedStr]
 * @returns {() => number}
 */
export function makeRng(seedStr) {
  if (seedStr == null || seedStr === '') return Math.random;
  return mulberry32(hashSeed(String(seedStr)));
}
