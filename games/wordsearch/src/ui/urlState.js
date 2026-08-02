// Persist and restore form configuration via URL query parameters.
// This enables shareable, linkable puzzle/game configurations.

// Map of form control ids <-> URL param keys. We use short-ish keys but keep
// them readable. Each entry describes how to read/write the value.
const FIELDS = [
  { id: 'cfg-preset', key: 'preset', type: 'value' },
  { id: 'cfg-width', key: 'w', type: 'value' },
  { id: 'cfg-height', key: 'h', type: 'value' },
  { id: 'cfg-order', key: 'order', type: 'value' },
  { id: 'cfg-lattice', key: 'lattice', type: 'value' },
  { id: 'cfg-combiner', key: 'combiner', type: 'value' },
  { id: 'cfg-sampling', key: 'sampling', type: 'value' },
  { id: 'cfg-reftext', key: 'reftext', type: 'value', persist: false },
  { id: 'cfg-words', key: 'words', type: 'value', persist: false },
  { id: 'cfg-reftext-url', key: 'reftexturl', type: 'value' },
  { id: 'cfg-words-url', key: 'wordsurl', type: 'value' },
  { id: 'cfg-wordlist-url', key: 'wordlisturl', type: 'value' },
  { id: 'cfg-wordcount', key: 'wordcount', type: 'value' },
  { id: 'cfg-max-adjacency', key: 'maxadj', type: 'value' },
  { id: 'cfg-fontscale', key: 'fontscale', type: 'value' },
  { id: 'cfg-fontfamily', key: 'fontfamily', type: 'value' },
  { id: 'cfg-seed', key: 'seed', type: 'value' },
  { id: 'cfg-no-backwards', key: 'nobackwards', type: 'checked' },
  { id: 'cfg-debug', key: 'debug', type: 'checked' },
  { id: 'cfg-no-avoid-words', key: 'noavoidwords', type: 'checked' },
];
/**
 * Read the current mode from the URL, if present.
 * @param {URLSearchParams} [params]
 * @returns {string|null}
 */
export function getModeFromUrl(params = new URLSearchParams(window.location.search)) {
  const m = params.get('mode');
  if (m === 'design' || m === 'watch' || m === 'play' || m === 'collapse') return m;
  return null;
}
/**
 * Persist the current mode to the URL (preserving other params).
 * @param {string} mode
 */
export function persistModeToUrl(mode) {
  const params = new URLSearchParams(window.location.search);
  if (mode) params.set('mode', mode);
  else params.delete('mode');
  const qs = params.toString();
  const newUrl = `${window.location.pathname}${qs ? `?${qs}` : ''}${window.location.hash}`;
  window.history.replaceState(null, '', newUrl);
}

/**
 * Returns true if the current URL contains any of our known config params.
 * @param {URLSearchParams} [params]
 */
export function hasUrlConfig(params = new URLSearchParams(window.location.search)) {
  return FIELDS.some((f) => params.has(f.key)) || params.has('mode');
}

/**
 * Apply config values from the URL onto the form controls.
 * Only fields present in the URL are applied; others keep their defaults.
 * @param {Document|HTMLElement} root
 * @param {URLSearchParams} [params]
 */
export function applyConfigFromUrl(
  root = document,
  params = new URLSearchParams(window.location.search)
) {
  for (const f of FIELDS) {
    if (!params.has(f.key)) continue;
    const el = root.querySelector(`#${f.id}`);
    if (!el) continue;
    const raw = params.get(f.key);
    if (f.type === 'checked') {
      el.checked = raw === '1' || raw === 'true';
    } else {
      el.value = raw;
    }
  }
}

/**
 * Read current form control values and serialise them into the URL (using
 * history.replaceState so we don't spam the back-stack).
 * @param {Document|HTMLElement} root
 */
export function persistConfigToUrl(root = document) {
  const existing = new URLSearchParams(window.location.search);
  const params = new URLSearchParams();
  // Preserve the current mode across config writes.
  if (existing.has('mode')) params.set('mode', existing.get('mode'));
  for (const f of FIELDS) {
    if (f.persist === false) continue;
    const el = root.querySelector(`#${f.id}`);
    if (!el) continue;
    if (f.type === 'checked') {
      if (el.checked) params.set(f.key, '1');
    } else {
      const v = el.value;
      if (v != null && v !== '') params.set(f.key, v);
    }
  }
  const qs = params.toString();
  const newUrl = `${window.location.pathname}${qs ? `?${qs}` : ''}${window.location.hash}`;
  window.history.replaceState(null, '', newUrl);
}
