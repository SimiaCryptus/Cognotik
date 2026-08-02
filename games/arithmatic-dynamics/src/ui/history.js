// History ribbon — renders each committed step as a compact row showing
// the pre-transform expression and the verb applied. Tapping a row emits
// a `rewind` request via the callback.

import { el, clear } from '../util/dom.js';

export class History {
  constructor(root, { onRewind } = {}) {
    this.root = root;
    this.onRewind = onRewind;
    this.steps = [];
  }

  reset() {
    this.steps = [];
    this.render();
  }

  push(text, verb) {
    this.steps.push({ text, verb });
    this.render();
  }

  // Drop steps from index onward (used on undo/rewind).
  truncate(count) {
    this.steps.length = Math.max(0, count);
    this.render();
  }

  render() {
    clear(this.root);
    const title = el('div', { class: 'history-title' }, 'Your path');
    this.root.appendChild(title);
    this.steps.forEach((step, i) => {
      const row = el(
        'div',
        {
          class: 'history-row',
          dataset: { index: String(i) },
          onClick: () => this.onRewind && this.onRewind(i),
        },
        [
          el('span', { class: 'history-expr' }, step.text),
          el('span', { class: 'history-verb' }, step.verb),
        ],
      );
      this.root.appendChild(row);
    });
  }
}
