/**
 * annotateUI.js — select text in a rendered viewer -> floating "Annotate"
 * button -> annotation dialog. (M2.2 authoring half.)
 */

import {DOCUMENT_TARGET, esc, makeAnchor, resolveAnchor} from './core.js';
import {ANNOTATION_KINDS, createAnnotation} from './annotations.js';
import {SEVERITIES} from './core.js';

const buildDialog = () => {
    const dialog = document.createElement('dialog');
    dialog.className = 'confirm-dialog annotate-dialog';
    dialog.innerHTML = `
        <form method="dialog" class="annotate-form">
          <h3 class="annotate-title">💬 New annotation</h3>
          <p class="annotate-quote" id="annotate-quote"></p>
          <label class="field-label" for="annotate-body">Note</label>
          <textarea id="annotate-body" rows="4" placeholder="What should change here, and why?"></textarea>
          <label class="field-label" for="annotate-suggested">Suggested replacement (optional)</label>
          <textarea id="annotate-suggested" rows="2" placeholder="Verbatim text to use instead"></textarea>
          <div class="annotate-row">
            <label class="field-label" for="annotate-kind">Kind
              <select id="annotate-kind" class="model-select">
                ${ANNOTATION_KINDS.map(k => `<option value="${k}">${k}</option>`).join('')}
              </select>
            </label>
            <label class="field-label" for="annotate-severity">Severity
              <select id="annotate-severity" class="model-select">
                ${SEVERITIES.map(s => `<option value="${s}"${s === 'minor' ? ' selected' : ''}>${s}</option>`).join('')}
              </select>
            </label>
          </div>
          <div class="button-row confirm-dialog-actions">
            <button class="btn btn-secondary" value="cancel" type="submit">Cancel</button>
            <button class="btn btn-primary" value="confirm" type="submit">Save annotation</button>
          </div>
        </form>`;
    document.body.appendChild(dialog);
    return dialog;
};

/**
 * @param {object} opts
 * @param {() => ({content:string, ref:object}|null)} opts.getArticle  current article content + ref
 * @param {(annotation:object) => void} opts.onCreate
 * @param {string[]} opts.viewerIds  viewers whose selections can be annotated
 */
export function initAnnotator({getArticle, onCreate, viewerIds}) {
    const dialog = buildDialog();
    const popup = document.createElement('button');
    popup.type = 'button';
    popup.className = 'btn btn-sm btn-primary annotate-popup is-hidden';
    popup.textContent = '💬 Annotate';
    document.body.appendChild(popup);

    let pendingSelection = null;

    const hidePopup = () => {
        popup.classList.add('is-hidden');
        pendingSelection = null;
    };

    const withinViewer = node => {
        const el = node?.nodeType === Node.TEXT_NODE ? node.parentElement : node;
        return viewerIds.some(id => document.getElementById(id)?.contains(el));
    };

    document.addEventListener('selectionchange', () => {
        const selection = document.getSelection();
        const text = selection?.toString() ?? '';
        if (!text.trim() || text.trim().length < 4 || !withinViewer(selection.anchorNode)) {
            if (!dialog.open) hidePopup();
            return;
        }
        const rect = selection.getRangeAt(0).getBoundingClientRect();
        pendingSelection = text.trim();
        popup.style.top = `${window.scrollY + rect.bottom + 8}px`;
        popup.style.left = `${window.scrollX + rect.left}px`;
        popup.classList.remove('is-hidden');
    });

    popup.addEventListener('click', () => {
        if (!pendingSelection) return;
        const quote = pendingSelection;
        const article = getArticle();
        if (!article) {
            hidePopup();
            return;
        }
        dialog.querySelector('#annotate-quote').innerHTML =
            `<span class="annotate-quote-mark">“</span>${esc(quote.slice(0, 240))}<span class="annotate-quote-mark">”</span>`;
        dialog.querySelector('#annotate-body').value = '';
        dialog.querySelector('#annotate-suggested').value = '';
        dialog.returnValue = 'cancel';
        hidePopup();
        dialog.showModal();

        const onClose = () => {
            dialog.removeEventListener('close', onClose);
            if (dialog.returnValue !== 'confirm') return;
            const body = dialog.querySelector('#annotate-body').value.trim();
            if (!body) return;

            const resolved = resolveAnchor(article.content, {quote, headingPath: []});
            const target = resolved.state === 'orphaned'
                ? DOCUMENT_TARGET
                : {
                    scope: 'range',
                    anchorState: resolved.state,
                    anchor: makeAnchor(article.content, resolved.start, resolved.end)
                };

            onCreate(createAnnotation({
                sourceRef: article.ref,
                target,
                body,
                kind: dialog.querySelector('#annotate-kind').value,
                severity: dialog.querySelector('#annotate-severity').value,
                suggestedText: dialog.querySelector('#annotate-suggested').value.trim() || undefined
            }));
        };
        dialog.addEventListener('close', onClose);
    });

    document.addEventListener('scroll', hidePopup, {passive: true});
    return {hidePopup};
}