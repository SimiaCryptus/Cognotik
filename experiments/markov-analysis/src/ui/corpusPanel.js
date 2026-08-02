// corpusPanel.js — corpus upload/paste + stats

import { el, clear, formatBytes } from '../util/dom.js';
import { getTokenizer } from '../tokenizers/index.js';

const SOFT_LIMIT = 2 * 1024 * 1024;

export function createCorpusPanel(container, { onCorpus, getConfig, onBuild }) {
  let corpus = '';

  const textarea = el('textarea', {
    rows: 8,
    placeholder: 'Paste or type corpus text...',
  });

  const fileInput = el('input', {
    type: 'file',
    accept: '.txt,text/plain',
  });

  const stats = el('div', { class: 'corpus-stats' });
  const buildBtn = el('button', { type: 'button', text: 'Build Model' });
  const progress = el('div', { class: 'progress' });

  function normalize(text) {
    let t = text.replace(/\r\n?/g, '\n');
    if (getConfig().lowercase) t = t.toLowerCase();
    return t;
  }

  function updateStats() {
    const cfg = getConfig();
    const norm = normalize(corpus);
    const tok = getTokenizer(cfg.tokenizerId);
    const tokenCount = norm ? tok.tokenize(norm).length : 0;
    const bytes = new Blob([corpus]).size;
    clear(stats);
    const warn = bytes > SOFT_LIMIT;
    stats.appendChild(
      el('div', {
        class: warn ? 'warn' : '',
        text:
          `Chars: ${norm.length} · Tokens: ${tokenCount} · Size: ${formatBytes(bytes)}` +
          (warn ? ' (large!)' : ''),
      })
    );
    onCorpus(norm, tokenCount);
  }

  textarea.addEventListener('input', () => {
    corpus = textarea.value;
    updateStats();
  });

  fileInput.addEventListener('change', async () => {
    const f = fileInput.files[0];
    if (!f) return;
    corpus = await f.text();
    textarea.value = corpus;
    updateStats();
  });

  buildBtn.addEventListener('click', () => {
    onBuild(normalize(corpus));
  });

  container.appendChild(el('h2', { text: 'Corpus' }));
  container.appendChild(el('div', {}, [el('label', { text: 'Upload: ' }), fileInput]));
  container.appendChild(textarea);
  container.appendChild(stats);
  container.appendChild(el('div', { class: 'actions' }, [buildBtn, progress]));

  updateStats();

  return {
    updateStats,
    setProgress(pct) {
      progress.textContent = pct != null ? `Building… ${Math.round(pct * 100)}%` : '';
    },
    getCorpus: () => normalize(corpus),
  };
}
