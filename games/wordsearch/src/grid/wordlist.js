// Helpers for handling (potentially large) target word lists.

import { normaliseText } from '../markov/textPipeline.js';
// Cache for the externally-loaded forbidden word list (wordlist.txt) so we
// only fetch/parse it once per session.
let externalWordsCache = null;
let externalWordsUrl = null;
/**
 * Fetch and cache the project-level `wordlist.txt` (located alongside
 * index.html, i.e. one level above the `src/` directory). The list is used
 * as an additional dictionary of "real" words we must avoid accidentally
 * forming while filling the grid. Returns a cleaned array of words.
 *
 * Safe to call in non-browser/test environments: if `fetch` is unavailable
 * or the request fails, it resolves to an empty list.
 * @param {string} [url]
 * @param {object} [opts]
 * @param {boolean} [opts.force] re-fetch even if a list is already cached
 * @returns {Promise<string[]>}
 */
export async function loadExternalWordList(url = 'wordlist.txt', opts = {}) {
  const { force = false } = opts;
  // Reuse the cache only when the URL hasn't changed and no reload is forced.
  if (externalWordsCache && !force && url === externalWordsUrl) return externalWordsCache;
  externalWordsUrl = url;
  if (typeof fetch !== 'function') {
    externalWordsCache = [];
    return externalWordsCache;
  }
  try {
    const resp = await fetch(url);
    if (!resp.ok) {
      console.warn(`[wordlist] fetch ${url} failed: ${resp.status}`);
      externalWordsCache = [];
      return externalWordsCache;
    }
    const text = await resp.text();
    externalWordsCache = cleanWordList(text.split(/\r?\n/));
    console.log(`[wordlist] loaded ${externalWordsCache.length} words from ${url}`);
  } catch {
    console.warn(`[wordlist] error loading ${url}`);
    externalWordsCache = [];
  }
  return externalWordsCache;
}

/** Override / preload the external word cache (handy for tests). */
export function setExternalWordList(words = []) {
  externalWordsCache = cleanWordList(words);
}

/** Synchronously read the currently-cached external word list. */
export function getExternalWordList() {
  return externalWordsCache || [];
}

/**
 * Clean a raw word list: normalise, drop empties, de-duplicate.
 * @param {string[]} words
 * @returns {string[]}
 */
export function cleanWordList(words = []) {
  const seen = new Set();
  const out = [];
  for (const w of words) {
    const n = normaliseText(w);
    if (n && !seen.has(n)) {
      seen.add(n);
      out.push(n);
    }
  }
  return out;
}

/**
 * Randomly select up to `count` words from the cleaned list. When count is
 * 0 / falsy or >= list length, the whole (cleaned) list is returned.
 * Uses a partial Fisher–Yates shuffle so it scales to large lists.
 * @param {string[]} words
 * @param {number} count
 * @param {() => number} [rng]
 * @returns {string[]}
 */
export function selectWords(words = [], count = 0, rng = Math.random) {
  const clean = cleanWordList(words);
  if (!count || count >= clean.length) return clean;
  const arr = clean.slice();
  for (let i = 0; i < count; i++) {
    const j = i + Math.floor(rng() * (arr.length - i));
    const tmp = arr[i];
    arr[i] = arr[j];
    arr[j] = tmp;
  }
  return arr.slice(0, count);
}

/**
 * Build a lookup structure for fast "would this complete a word?" checks
 * during grid filling. We store every word AND its reverse (so the check
 * is direction-agnostic), plus the maximum word length to bound how far
 * back we need to read along each direction.
 *
 * `extraWords` lets callers merge in an additional dictionary (e.g. the
 * project-level wordlist.txt) so common real words are never accidentally
 * formed by the random fill letters.
 * @param {string[]} words
 * @param {string[]} [extraWords]
 * @returns {{set:Set<string>, maxLen:number}}
 */
export function buildForbiddenIndex(words = [], extraWords = getExternalWordList(), minLen = 4) {
  const set = new Set();
  let maxLen = 0;
  const all = cleanWordList([...words, ...extraWords]);
  console.log(
    `[wordlist] buildForbiddenIndex: target=${words.length} extra=${extraWords.length} combined=${all.length}`
  );
  for (const w of all) {
    // Short fragments (a, is, the, cat...) occur so often that forbidding
    // them removes nearly every candidate letter and collapses the fill
    // distribution. Only guard against longer, "real" words.
    if (w.length < minLen) continue;
    set.add(w);
    set.add([...w].reverse().join(''));
    if (w.length > maxLen) maxLen = w.length;
  }
  console.log(`[wordlist] forbidden index: ${set.size} entries, maxLen=${maxLen}`);
  return { set, maxLen };
}
