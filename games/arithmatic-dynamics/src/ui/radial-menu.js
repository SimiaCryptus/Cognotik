// Contextual verb menu. Given a set of legal verbs and an anchor point,
// it shows large icon buttons. Selecting one emits the verb via callback.

import { el, clear, on } from '../util/dom.js';

const VERB_ICONS = {
  split: { glyph: '✂', label: 'Split' },
  factorize: { glyph: '⋈', label: 'Factorize' },
  swap: { glyph: '⇄', label: 'Swap' },
  group: { glyph: '( )', label: 'Group' },
  ungroup: { glyph: ')(', label: 'Ungroup' },
  combine: { glyph: '⊕', label: 'Combine' },
  cancel: { glyph: '✦', label: 'Cancel' },
  distribute: { glyph: '⊗', label: 'Distribute' },
  extract: { glyph: '⊙', label: 'Extract' },
};

export class RadialMenu {
  constructor(root, { onVerb } = {}) {
    this.root = root;
    this.onVerb = onVerb;
    this._offDoc = null;
    this.hide();
  }

  show(verbs, anchorRect) {
    clear(this.root);
    if (!verbs || verbs.length === 0) {
      this.hide();
      return;
    }
    const menu = el('div', { class: 'radial' });
    for (const verb of verbs) {
      const info = VERB_ICONS[verb] || { glyph: '?', label: verb };
      const btn = el(
        'button',
        {
          class: 'radial-btn',
          type: 'button',
          dataset: { verb },
          onClick: (e) => {
            e.stopPropagation();
            this.hide();
            this.onVerb && this.onVerb(verb);
          },
        },
        [
          el('span', { class: 'radial-glyph' }, info.glyph),
          el('span', { class: 'radial-label' }, info.label),
        ],
      );
      menu.appendChild(btn);
    }
    this.root.appendChild(menu);
    this.root.classList.add('visible');

    if (anchorRect) {
      const parentRect = this.root.parentElement.getBoundingClientRect();
      const x = anchorRect.left - parentRect.left + anchorRect.width / 2;
      const y = anchorRect.top - parentRect.top;
      this.root.style.left = `${x}px`;
      this.root.style.top = `${y}px`;
    }

    // Dismiss on outside interaction.
    if (this._offDoc) this._offDoc();
    this._offDoc = on(document, 'pointerdown', (e) => {
      if (!this.root.contains(e.target)) this.hide();
    });
  }

  hide() {
    this.root.classList.remove('visible');
    clear(this.root);
    if (this._offDoc) {
      this._offDoc();
      this._offDoc = null;
    }
  }
}
