// charTokenizer.js — each Unicode code point is a token

export const tokenizer = {
  id: 'char',
  label: 'Character',
  tokenize(text) {
    return Array.from(text);
  },
  tokenizeWithSpans(text) {
    const spans = [];
    let i = 0;
    for (const ch of text) {
      spans.push({ token: ch, start: i, end: i + ch.length });
      i += ch.length;
    }
    return spans;
  },
};
