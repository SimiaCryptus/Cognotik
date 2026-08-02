// Helpers for loading reference text / word lists from (relative or absolute)
// URLs. Serving/hosting those URLs is the user's responsibility; failures are
// non-fatal and resolve to null so callers can fall back to inline values.

/**
 * Fetch the text contents of a URL. Returns null on any failure or when
 * fetch is unavailable (e.g. some test environments).
 * @param {string} url
 * @returns {Promise<string|null>}
 */
export async function fetchText(url) {
  if (!url || typeof fetch !== 'function') return null;
  try {
    const resp = await fetch(url);
    if (!resp.ok) {
      console.warn(`[remoteText] fetch ${url} failed: ${resp.status}`);
      return null;
    }
    return await resp.text();
  } catch {
    console.warn(`[remoteText] error loading ${url}`);
    return null;
  }
}

/**
 * Load a reference-text URL into the #cfg-reftext control. Returns true on
 * success.
 * @param {Document|HTMLElement} root
 * @param {string} url
 */
export async function loadReferenceFromUrl(root, url) {
  const text = await fetchText(url);
  if (text == null) return false;
  const el = root.querySelector('#cfg-reftext');
  if (el) el.value = text;
  return true;
}

/**
 * Load a target-words URL into the #cfg-words control. Returns true on
 * success.
 * @param {Document|HTMLElement} root
 * @param {string} url
 */
export async function loadWordsFromUrl(root, url) {
  const text = await fetchText(url);
  if (text == null) return false;
  const el = root.querySelector('#cfg-words');
  if (el) el.value = text;
  return true;
}
