// index.js — tokenizer registry

import { tokenizer as charTok } from './charTokenizer.js';
import { tokenizer as wsTok } from './whitespaceTokenizer.js';
import { tokenizer as wpTok } from './wordPunctTokenizer.js';
import { tokenizer as reTok, setRegexPattern } from './regexTokenizer.js';

const REGISTRY = new Map([
  [charTok.id, charTok],
  [wsTok.id, wsTok],
  [wpTok.id, wpTok],
  [reTok.id, reTok],
]);
// Apply the regex pattern from config before tokenizing with 'regex'.
export function applyRegexConfig(cfg) {
  if (cfg && typeof cfg.regexPattern === 'string') {
    setRegexPattern(cfg.regexPattern);
  }
}

export function getTokenizer(id) {
  return REGISTRY.get(id) || charTok;
}

export function listTokenizers() {
  return Array.from(REGISTRY.values());
}
