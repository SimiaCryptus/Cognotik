// regexTokenizer.js — tokenize using a configurable regex pattern

// Preset patterns available in the config UI.
export const REGEX_PRESETS = [
  { value: '[\\p{L}\\p{N}]+|[^\\s\\p{L}\\p{N}]', label: 'Word + punctuation' },
  { value: '\\S+', label: 'Whitespace (non-space runs)' },
  { value: '.', label: 'Single characters' },
  {
    value: "[\\p{L}\\p{N}']+|[.,!?;:]|[^\\s\\p{L}\\p{N}]",
    label: 'Words (with apostrophes) + punctuation',
  },
  { value: '\\w+|[^\\w\\s]', label: 'ASCII words + symbols' },
  { value: '(?<=[\\saeiou]).*?([aeiou]+|\\s+)', label: 'Vowel Splits' },
  /*(?<=[\saeiou]).*?([aeiou]+|\s+)*/
];

const DEFAULT_PATTERN = REGEX_PRESETS[0].value;

// The pattern is mutable so the registry can pick up config changes.
let currentPattern = DEFAULT_PATTERN;
let compiled = null;
let compiledSource = null;

export function setRegexPattern(pattern) {
  currentPattern = pattern || DEFAULT_PATTERN;
}

function getRegex() {
  if (compiled && compiledSource === currentPattern) {
    compiled.lastIndex = 0;
    return compiled;
  }
  try {
    compiled = new RegExp(currentPattern, 'gu');
  } catch (e) {
    // Fall back to a safe default if the pattern is invalid.
    compiled = new RegExp(DEFAULT_PATTERN, 'gu');
  }
  compiledSource = currentPattern;
  compiled.lastIndex = 0;
  return compiled;
}

export const tokenizer = {
  id: 'regex',
  label: 'Custom regex',
  tokenize(text) {
    return this.tokenizeWithSpans(text).map((s) => s.token);
  },
  tokenizeWithSpans(text) {
    const spans = [];
    const re = getRegex();
    let m;
    let guard = 0;
    let last = 0;
    while ((m = re.exec(text)) !== null) {
      if (m[0] === '') {
        re.lastIndex++;
        if (guard++ > text.length) break;
        continue;
      }
      if (m.index > last) {
        // Preserve the unmatched region between matches.
        spans.push({ token: text.slice(last, m.index), start: last, end: m.index });
      }
      spans.push({ token: m[0], start: m.index, end: m.index + m[0].length });
      last = m.index + m[0].length;
    }
    if (last < text.length) {
      spans.push({ token: text.slice(last), start: last, end: text.length });
    }
    return spans;
  },
};
