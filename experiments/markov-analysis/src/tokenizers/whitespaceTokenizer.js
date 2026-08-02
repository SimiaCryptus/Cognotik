// whitespaceTokenizer.js — split on whitespace, keep punctuation attached

export const tokenizer = {
  id: 'whitespace',
  label: 'Whitespace word',
  tokenize(text) {
    return this.tokenizeWithSpans(text).map((s) => s.token);
  },
  tokenizeWithSpans(text) {
    const spans = [];
    const re = /\S+/g;
    let m;
    let last = 0;
    while ((m = re.exec(text)) !== null) {
      if (m.index > last) {
        // Preserve the whitespace gap as its own token.
        const gap = text.slice(last, m.index);
        spans.push({ token: gap, start: last, end: m.index });
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
