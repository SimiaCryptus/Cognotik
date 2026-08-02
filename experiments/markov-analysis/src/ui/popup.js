// popup.js — replacement popup component (keyboard accessible)

import { el, clear, formatPct } from '../util/dom.js';

let activePopup = null;

function closeActive() {
  if (activePopup) {
    activePopup.remove();
    activePopup = null;
    document.removeEventListener('keydown', onKey, true);
    document.removeEventListener('mousedown', onOutside, true);
  }
}

function onKey(e) {
  if (e.key === 'Escape') {
    e.stopPropagation();
    closeActive();
  }
}

function onOutside(e) {
  if (activePopup && !activePopup.contains(e.target)) closeActive();
}

// onReplace: optional (token) => void, invoked when a candidate is chosen.
export function showPopup(anchorEl, result, onReplace) {
  closeActive();

  const maxP = Math.max(...result.topReplacements.map((r) => r.p), 1e-12);

  const header = el('div', { class: 'popup-head-row' }, [
    el('span', { class: 'popup-col-tok', text: 'token' }),
    el('span', { class: 'popup-col-bar', text: '' }),
    el('span', { class: 'popup-col-num', text: 'fwd' }),
    el('span', { class: 'popup-col-num', text: 'bwd' }),
    el('span', { class: 'popup-col-num', text: 'joint' }),
  ]);

  const rows = result.topReplacements.map((r) => {
    const isOrig = r.token === result.token;
    const row = el(
      'div',
      {
        class: 'popup-row' + (isOrig ? ' original' : '') + (onReplace ? ' clickable' : ''),
        role: onReplace ? 'button' : null,
        tabindex: onReplace ? '0' : null,
        title: onReplace ? 'Click to replace in test text' : r.token,
      },
      [
        el('span', { class: 'popup-tok', text: JSON.stringify(r.token).slice(1, -1) }),
        el('span', { class: 'popup-bar-wrap' }, [
          el('span', { class: 'popup-bar', style: `width:${((r.p / maxP) * 100).toFixed(1)}%` }),
        ]),
        el('span', { class: 'popup-p', text: formatPct(r.pForward) }),
        el('span', { class: 'popup-p', text: formatPct(r.pBackward) }),
        el('span', { class: 'popup-p', text: formatPct(r.p) }),
      ]
    );

    if (onReplace && !isOrig) {
      const doReplace = () => {
        closeActive();
        onReplace(r.token);
      };
      row.addEventListener('click', doReplace);
      row.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          doReplace();
        }
      });
    }
    return row;
  });

  const popup = el(
    'div',
    {
      class: 'popup',
      role: 'dialog',
      'aria-label': 'Top replacements',
      tabindex: '-1',
    },
    [
      el('button', {
        class: 'popup-close',
        'aria-label': 'Close',
        text: '×',
        onClick: closeActive,
      }),
      el('h3', {
        text: `Top ${result.topReplacements.length} — fwd=${formatPct(result.pForward)}, bwd=${formatPct(result.pBackward)}, joint=${formatPct(result.pCombined)}`,
      }),
      header,
      ...rows,
    ]
  );

  document.body.appendChild(popup);
  const rect = anchorEl.getBoundingClientRect();
  popup.style.top = `${window.scrollY + rect.bottom + 4}px`;
  popup.style.left = `${window.scrollX + rect.left}px`;

  activePopup = popup;
  popup.focus();
  document.addEventListener('keydown', onKey, true);
  document.addEventListener('mousedown', onOutside, true);
}
