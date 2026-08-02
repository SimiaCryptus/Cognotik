// Selectable presets: reference text corpora and target word lists.
// Each preset supplies a reference text (to train the Markov model) and a
// list of target words to hide in the grid.
//
// Presets now live as packaged text files under ./presets/<name>/:
//   - reference.txt  (corpus used to train the Markov model)
//   - wordlist.txt   (newline-separated target words to hide)
//
// Rather than bundling the corpora into JS, we keep lightweight metadata
// here (key + human-readable label) and lazily fetch the text files when a
// preset is applied.

// Static metadata for each available preset. The order here determines the
// order options appear in the UI <select>.
export const PRESETS = {
  science: { label: 'Science' },
  fantasy: { label: 'Fantasy' },
  kids: { label: 'Kids' },
  computers: { label: 'Computers' },
  nature: { label: 'Nature' },
  space: { label: 'Space' },
  ocean: { label: 'Ocean' },
  food: { label: 'Food' },
};

export const DEFAULT_PRESET = 'fantasy';

// Relative URLs of the packaged reference + word-list text files for each
// preset. These are what get persisted to the URL (so links don't carry the
// entire corpus). The preset name is only used by the UI to resolve these
// pre-packaged files; nothing downstream depends on the preset key.
export function presetReferenceUrl(presetKey) {
  return `./presets/${presetKey}/reference.txt`;
}

export function presetWordsUrl(presetKey) {
  return `./presets/${presetKey}/wordlist.txt`;
}

/** Fetch the raw text contents of a packaged preset file. */
async function fetchText(url) {
  const res = await fetch(url);
  if (!res.ok) {
    throw new Error(`Failed to load preset file: ${url} (${res.status})`);
  }
  return res.text();
}

/**
 * Load a preset's reference text and word list from the packaged text files.
 * Returns `{ referenceText, words }` or `null` if the preset is unknown.
 */
export async function loadPreset(presetKey) {
  if (!PRESETS[presetKey]) return null;
  const [referenceText, wordsRaw] = await Promise.all([
    fetchText(presetReferenceUrl(presetKey)),
    fetchText(presetWordsUrl(presetKey)),
  ]);
  const words = wordsRaw
    .split(/\r?\n/)
    .map((w) => w.trim())
    .filter(Boolean);
  return { referenceText, words };
}

/** Apply a preset's text + words to the relevant form controls. */
export async function applyPreset(root, presetKey) {
  const preset = await loadPreset(presetKey);
  if (!preset) return;
  const textEl = root.querySelector('#cfg-reftext');
  const wordsEl = root.querySelector('#cfg-words');
  if (textEl) textEl.value = preset.referenceText;
  if (wordsEl) wordsEl.value = preset.words.join('\n');
  // Point the URL fields at the packaged relative files so a shared link
  // references those resources rather than embedding the raw text.
  const refUrlEl = root.querySelector('#cfg-reftext-url');
  const wordsUrlEl = root.querySelector('#cfg-words-url');
  if (refUrlEl) refUrlEl.value = presetReferenceUrl(presetKey);
  if (wordsUrlEl) wordsUrlEl.value = presetWordsUrl(presetKey);
}

/** Populate a <select> element with preset options. */
export function populatePresetSelect(selectEl) {
  if (!selectEl) return;
  selectEl.innerHTML = '';
  for (const [key, preset] of Object.entries(PRESETS)) {
    const opt = document.createElement('option');
    opt.value = key;
    opt.textContent = preset.label;
    if (key === DEFAULT_PRESET) opt.selected = true;
    selectEl.appendChild(opt);
  }
}
