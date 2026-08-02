// Normalise and tokenise reference text into a clean character stream.

/**
 * Normalise text: lowercase, collapse whitespace, strip to a
 * configurable alphabet (default a-z + space).
 *
 * @param {string} text
 * @param {object} [opts]
 * @param {RegExp} [opts.allowed] characters to keep
 * @param {boolean} [opts.keepSpaces]
 * @returns {string}
 */
export function normaliseText(text, opts = {}) {
  const { keepSpaces = false } = opts;
  let out = (text || '').toLowerCase();
  // Replace any run of non-letters with a single space.
  out = out.replace(/[^a-z]+/g, ' ');
  out = out.replace(/\s+/g, ' ').trim();
  if (!keepSpaces) {
    out = out.replace(/ /g, '');
  }
  return out;
}

/**
 * Split normalised text into word tokens.
 * @param {string} text
 * @returns {string[]}
 */
export function tokenise(text) {
  return normaliseText(text, { keepSpaces: true }).split(' ').filter(Boolean);
}
