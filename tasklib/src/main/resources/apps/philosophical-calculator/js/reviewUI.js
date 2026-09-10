/**
 * reviewUI.js — the 🗂 Review stage: revision history + diff, the annotation
 * sidebar (M2.2) and the aggregated revision queue (M3.2).
 */

import {
    ARTICLE_DRAFT_ID,
    SEVERITIES,
    describeTarget,
    esc,
    renderDiffHtml
} from './core.js';
import {getRevision, headRevision, restoreRevision} from './drafts.js';
import {
    ANNOTATION_KINDS,
    ANNOTATION_RESOLUTIONS,
    addReply,
    annotationStats,
    filterAnnotations,
    isOrphaned,
    resolveAnnotation
} from './annotations.js';
import {
    REVISION_ACTIONS,
    REVISION_STATUSES,
    allItems,
    filterItems,
    findDuplicates,
    groupItems,
    mergeItems,
    priorityScore,
    queueStats,
    setItemStatus,
    updateItem
} from './revisions.js';

const el = (tag, className, text) => {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text != null) node.textContent = text;
    return node;
};

const select = (id, options, value) => {
    const node = el('select', 'model-select review-select');
    node.id = id;
    for (const opt of options) {
        const option = el('option', null, opt.label ?? opt);
        option.value = opt.value ?? opt;
        if ((opt.value ?? opt) === value) option.selected = true;
        node.appendChild(option);
    }
    return node;
};

const shortTime = iso => {
    try {
        return new Date(iso).toLocaleString([], {month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'});
    } catch {
        return iso;
    }
};

function buildDiffOverlay() {
    const existing = document.getElementById('diff-overlay');
    if (existing) return existing;
    const overlay = el('div', 'zoom-overlay');
    overlay.id = 'diff-overlay';
    overlay.setAttribute('role', 'dialog');
    overlay.setAttribute('aria-modal', 'true');
    overlay.innerHTML = `
        <div class="zoom-overlay-header">
          <span class="zoom-title" id="diff-title"></span>
          <div class="zoom-header-buttons">
            <label class="diff-toggle"><input type="checkbox" id="diff-only-changed"> only changed</label>
            <button type="button" class="btn btn-sm btn-toolbar" id="diff-close">✕ Close</button>
          </div>
        </div>
        <div class="zoom-overlay-body" id="diff-body"></div>`;
    document.body.appendChild(overlay);
    overlay.querySelector('#diff-close').addEventListener('click', () => {
        overlay.classList.remove('visible');
        document.body.classList.remove('scroll-locked');
    });
    return overlay;
}

/**
 * @param {object} opts
 * @param {object} opts.store
 * @param {(target:object)=>void} opts.onJump
 * @param {(message:string)=>Promise<boolean>} opts.confirmAction
 * @param {(message:string, kind?:string)=>void} opts.toast
 * @param {(content:string, revision:number)=>Promise<void>} opts.onRestore
 */
export function initReviewUI({store, onJump, confirmAction, toast, onRestore}) {
    const overlay = buildDiffOverlay();
    const hosts = {
        revisions: document.getElementById('revisions-panel'),
        annotations: document.getElementById('annotations-panel'),
        queue: document.getElementById('queue-panel')
    };

    const annotationQuery = {search: '', resolutions: ['open', 'accepted'], kinds: [], orphanedOnly: false, sortBy: 'document-order'};
    const itemQuery = {
        search: '', lensIds: [], actions: [], statuses: ['proposed', 'accepted', 'deferred'],
        groupBy: 'lens', sortBy: 'priority', sortDir: 'desc', orphanedOnly: false
    };
     const TRIAGE_STATUSES = ['proposed', 'accepted', 'deferred'];

    const article = () => store.get().drafts?.[ARTICLE_DRAFT_ID];
    const annotationsOf = () => store.get().annotations?.[ARTICLE_DRAFT_ID] ?? [];
    const queueOf = () => store.get().queues?.[ARTICLE_DRAFT_ID];

    const showDiff = (from, to) => {
        const draft = article();
        const a = getRevision(draft, from);
        const b = getRevision(draft, to);
        if (!a || !b) return;
        const onlyChanged = overlay.querySelector('#diff-only-changed');
        const paint = () => {
            overlay.querySelector('#diff-body').innerHTML =
                renderDiffHtml(a.content, b.content, {onlyChanged: onlyChanged.checked});
        };
        overlay.querySelector('#diff-title').textContent = `diff v${from} → v${to}`;
        onlyChanged.onchange = paint;
        paint();
        overlay.classList.add('visible');
        document.body.classList.add('scroll-locked');
    };

    /* ---------------- Revisions ---------------- */

    function renderRevisions() {
        const host = hosts.revisions;
        if (!host) return;
        host.replaceChildren();
        const draft = article();
        if (!draft) {
            host.appendChild(el('p', 'hint', 'No draft yet. Run “Draft Article” or import one in the Input tab.'));
            return;
        }
        const head = headRevision(draft);
        const summary = el('p', 'hint',
            `${draft.revisions.length} revision(s) · head v${head.revision} · ${head.wordCount} words`);
        host.appendChild(summary);

        const list = el('div', 'revision-list');
        for (const rev of [...draft.revisions].sort((a, b) => b.revision - a.revision)) {
            const row = el('div', `revision-row${rev.revision === draft.headRevision ? ' is-head' : ''}`);
            const head1 = el('div', 'revision-head');
            head1.append(
                el('span', 'revision-num', `v${rev.revision}`),
                el('span', `origin-badge origin-${rev.origin}`, rev.origin),
                el('span', 'revision-meta', `${rev.createdBy?.name ?? 'you'} · ${shortTime(rev.createdAt)} · ${rev.wordCount}w`)
            );
            if (rev.frozen) head1.appendChild(el('span', 'origin-badge origin-frozen', '❄ frozen'));
            row.appendChild(head1);
            if (rev.changelog) row.appendChild(el('p', 'revision-changelog', rev.changelog));
            if (rev.label) row.appendChild(el('p', 'revision-changelog', rev.label));

            const actions = el('div', 'button-row');
            const diffBtn = el('button', 'btn btn-sm btn-toolbar', '± Diff vs head');
            diffBtn.type = 'button';
            diffBtn.disabled = rev.revision === draft.headRevision;
            diffBtn.addEventListener('click', () => showDiff(rev.revision, draft.headRevision));

            const restoreBtn = el('button', 'btn btn-sm btn-toolbar', '↺ Restore');
            restoreBtn.type = 'button';
            restoreBtn.disabled = rev.revision === draft.headRevision;
            restoreBtn.addEventListener('click', async () => {
                if (!await confirmAction(`Restore v${rev.revision}? A new revision is appended; nothing is lost.`)) return;
                const {draft: next, revision} = restoreRevision(article(), rev.revision);
                store.update(state => {
                    state.drafts[ARTICLE_DRAFT_ID] = next;
                });
                await onRestore?.(revision.content, revision.revision);
                toast(`Restored v${rev.revision} as v${revision.revision}`, 'success');
                render();
            });
            actions.append(diffBtn, restoreBtn);
            row.appendChild(actions);
            list.appendChild(row);
        }
        host.appendChild(list);
    }

    /* ---------------- Annotations ---------------- */

    function renderAnnotations() {
        const host = hosts.annotations;
        if (!host) return;
        host.replaceChildren();

        const all = annotationsOf();
        const stats = annotationStats(all);
        host.appendChild(el('p', 'hint',
            `${stats.total} annotation(s) · ${stats.feedable} will be fed to Update Article · ${stats.orphaned} orphaned`));

        const filters = el('div', 'filter-row');
        const search = el('input', 'review-input');
        search.type = 'search';
        search.placeholder = 'Search annotations…';
        search.value = annotationQuery.search;
        search.addEventListener('input', () => {
            annotationQuery.search = search.value;
            renderAnnotations();
        });

        const resolution = select('ann-resolution',
            [{value: '', label: 'open + accepted'}, ...ANNOTATION_RESOLUTIONS, {value: 'all', label: 'all'}],
            annotationQuery.resolutions.length === 2 ? '' : (annotationQuery.resolutions[0] ?? 'all'));
        resolution.addEventListener('change', () => {
            const v = resolution.value;
            annotationQuery.resolutions = v === '' ? ['open', 'accepted'] : v === 'all' ? [] : [v];
            renderAnnotations();
        });

        const kind = select('ann-kind', [{value: '', label: 'any kind'}, ...ANNOTATION_KINDS], annotationQuery.kinds[0] ?? '');
        kind.addEventListener('change', () => {
            annotationQuery.kinds = kind.value ? [kind.value] : [];
            renderAnnotations();
        });

        const orphaned = el('label', 'diff-toggle');
        const orphanBox = document.createElement('input');
        orphanBox.type = 'checkbox';
        orphanBox.checked = annotationQuery.orphanedOnly;
        orphanBox.addEventListener('change', () => {
            annotationQuery.orphanedOnly = orphanBox.checked;
            renderAnnotations();
        });
        orphaned.append(orphanBox, document.createTextNode(' orphaned only'));

        filters.append(search, resolution, kind, orphaned);
        host.appendChild(filters);

        const matched = filterAnnotations(all, annotationQuery);
        if (!matched.length) {
            host.appendChild(el('p', 'hint',
                'No annotations match. Select text in a rendered article view to add one.'));
            return;
        }

        const list = el('div', 'annotation-list');
        for (const annotation of matched) {
            const card = el('div', `annotation-card kind-${annotation.kind}`);
            const head = el('div', 'annotation-head');
            head.append(
                el('span', `chip chip-${annotation.kind}`, annotation.kind),
                el('span', `chip chip-sev-${annotation.severity}`, annotation.severity),
                el('span', `chip chip-status`, annotation.resolution)
            );
            if (isOrphaned(annotation)) head.appendChild(el('span', 'chip chip-orphan', '⚠ orphaned'));
            card.appendChild(head);

            const where = el('button', 'annotation-target', describeTarget(annotation.target));
            where.type = 'button';
            where.title = 'Jump to this passage';
            where.addEventListener('click', () => onJump(annotation.target));
            card.append(where, el('p', 'annotation-body', annotation.body));
            if (annotation.suggestedText) {
                card.appendChild(el('pre', 'markdown-source annotation-suggested', annotation.suggestedText));
            }
            for (const reply of annotation.replies) {
                card.appendChild(el('p', 'annotation-reply', `↳ ${reply.author.kind}: ${reply.body}`));
            }

            const actions = el('div', 'button-row');
            for (const [label, resolutionValue] of [
                ['✓ Accept', 'accepted'], ['✕ Reject', 'rejected'], ['⌛ Obsolete', 'obsolete']
            ]) {
                const btn = el('button', 'btn btn-sm btn-toolbar', label);
                btn.type = 'button';
                btn.addEventListener('click', () => {
                    store.update(state => {
                        state.annotations[ARTICLE_DRAFT_ID] = annotationsOf().map(a =>
                            a.id === annotation.id ? resolveAnnotation(a, resolutionValue) : a);
                    });
                    renderAnnotations();
                });
                actions.appendChild(btn);
            }
            const replyBtn = el('button', 'btn btn-sm btn-toolbar', '↳ Reply');
            replyBtn.type = 'button';
            replyBtn.addEventListener('click', () => {
                const input = el('textarea', 'reply-input');
                input.rows = 2;
                input.placeholder = 'Reply…';
                const save = el('button', 'btn btn-sm btn-primary', 'Save reply');
                save.type = 'button';
                save.addEventListener('click', () => {
                    if (!input.value.trim()) return;
                    store.update(state => {
                        state.annotations[ARTICLE_DRAFT_ID] = annotationsOf().map(a =>
                            a.id === annotation.id ? addReply(a, input.value.trim()) : a);
                    });
                    renderAnnotations();
                });
                card.append(input, save);
            });
            actions.appendChild(replyBtn);
            card.appendChild(actions);
            list.appendChild(card);
        }
        host.appendChild(list);
    }

    /* ---------------- Revision queue ---------------- */

    function renderQueue() {
        const host = hosts.queue;
        if (!host) return;
        host.replaceChildren();

        const queue = queueOf();
        const items = allItems(queue);
        const stats = queueStats(items);
        host.appendChild(el('p', 'hint',
            `${stats.total} item(s) · ${stats.accepted} accepted · ${stats.duplicates} duplicate(s) · ` +
            `${Object.keys(stats.byLens).length} lens(es)`));

        const filters = el('div', 'filter-row');
        const search = el('input', 'review-input');
        search.type = 'search';
        search.placeholder = 'Search items…';
        search.value = itemQuery.search;
        search.addEventListener('input', () => {
            itemQuery.search = search.value;
            renderQueue();
        });

        const lens = select('queue-lens',
            [{value: '', label: 'all lenses'}, ...Object.keys(stats.byLens)], itemQuery.lensIds[0] ?? '');
        lens.addEventListener('change', () => {
            itemQuery.lensIds = lens.value ? [lens.value] : [];
            renderQueue();
        });

        const status = select('queue-status',
             [{value: '', label: 'triage (default)'}, ...REVISION_STATUSES, {value: 'all', label: 'all'}],
             itemQuery.statuses.length === 0 ? 'all'
                 : itemQuery.statuses.length === 1 ? itemQuery.statuses[0] : '');
        status.addEventListener('change', () => {
             itemQuery.statuses = status.value === '' ? [...TRIAGE_STATUSES]
                : status.value === 'all' ? [] : [status.value];
            renderQueue();
        });

        const action = select('queue-action',
            [{value: '', label: 'any action'}, ...REVISION_ACTIONS], itemQuery.actions[0] ?? '');
        action.addEventListener('change', () => {
            itemQuery.actions = action.value ? [action.value] : [];
            renderQueue();
        });

        const groupBy = select('queue-group',
            [{value: 'lens', label: 'group: lens'}, {value: 'section', label: 'group: section'},
                {value: 'action', label: 'group: action'}, {value: 'status', label: 'group: status'},
                {value: 'none', label: 'group: none'}], itemQuery.groupBy);
        groupBy.addEventListener('change', () => {
            itemQuery.groupBy = groupBy.value;
            renderQueue();
        });

        const sortBy = select('queue-sort',
            [{value: 'priority', label: 'sort: priority'}, {value: 'document-order', label: 'sort: document order'},
                {value: 'severity', label: 'sort: severity'}, {value: 'lens', label: 'sort: lens'},
                {value: 'created', label: 'sort: created'}], itemQuery.sortBy);
        sortBy.addEventListener('change', () => {
            itemQuery.sortBy = sortBy.value;
            renderQueue();
        });

        filters.append(search, lens, status, action, groupBy, sortBy);
        host.appendChild(filters);
         // Extraction problems (e.g. a prose-only Socratic dialogue yielded no
         // list-shaped suggestions) — otherwise an empty batch looks like a no-op.
         const warnings = (queue?.batches ?? [])
             .filter(b => !itemQuery.lensIds.length || itemQuery.lensIds.includes(b.lensId))
             .flatMap(b => (b.warnings ?? []).map(w => `${b.lensId}: ${w}`));
         if (warnings.length) {
             const box = el('div', 'hint queue-warnings');
             for (const w of warnings) box.appendChild(el('div', null, `⚠ ${w}`));
             host.appendChild(box);
         }


        const draft = article();
        const bulk = el('div', 'button-row');
        const acceptAll = el('button', 'btn btn-sm btn-toolbar', '✓ Accept all visible');
        acceptAll.type = 'button';
        const mergeDupes = el('button', 'btn btn-sm btn-toolbar', '⇉ Merge duplicates');
        mergeDupes.type = 'button';
        bulk.append(acceptAll, mergeDupes);
        host.appendChild(bulk);

        const matched = filterItems(items, {...itemQuery, headRevision: draft?.headRevision});

        acceptAll.addEventListener('click', () => {
            store.update(state => {
                let q = state.queues[ARTICLE_DRAFT_ID];
                for (const item of matched) q = updateItem(q, item.id, i => setItemStatus(i, 'accepted'));
                state.queues[ARTICLE_DRAFT_ID] = q;
            });
            toast(`Accepted ${matched.length} item(s)`, 'success');
            renderQueue();
        });

        mergeDupes.addEventListener('click', () => {
            const groups = findDuplicates(items);
            if (!groups.length) {
                toast('No duplicates found', 'info');
                return;
            }
            store.update(state => {
                let q = state.queues[ARTICLE_DRAFT_ID];
                for (const [winner, ...losers] of groups) {
                    const merged = mergeItems(winner, losers);
                    q = updateItem(q, winner.id, () => merged.winner);
                    for (const loser of merged.losers) q = updateItem(q, loser.id, () => loser);
                }
                state.queues[ARTICLE_DRAFT_ID] = q;
            });
            toast(`Merged ${groups.reduce((n, g) => n + g.length - 1, 0)} duplicate(s)`, 'success');
            renderQueue();
        });

        if (!matched.length) {
             host.appendChild(el('p', 'hint', items.length
                 ? `No items match the current filters (${items.length} in the queue).`
                 : 'No revision items yet. Run an analysis lens — its suggestions are ingested automatically.'));
            return;
        }

        for (const group of groupItems(matched, itemQuery.groupBy)) {
            if (itemQuery.groupBy !== 'none') {
                host.appendChild(el('h4', 'queue-group-title', `${group.key} (${group.items.length})`));
            }
            const list = el('div', 'queue-list');
            for (const item of group.items) list.appendChild(renderItem(item, draft));
            host.appendChild(list);
        }
    }

    function renderItem(item, draft) {
        const card = el('div', `queue-item status-${item.status}`);
        const head = el('div', 'queue-item-head');
        head.append(
            el('span', `chip chip-action`, item.action),
            el('span', `chip chip-sev-${item.severity}`, item.severity),
            el('span', 'chip chip-lens', item.lensId),
            el('span', 'chip chip-score', `p${priorityScore(item)}`)
        );
        if (draft && item.sourceRef.revision < draft.headRevision) {
            head.appendChild(el('span', 'chip chip-stale', `from v${item.sourceRef.revision}`));
        }
        card.appendChild(head);

        const title = el('div', 'queue-item-title', item.title);
        title.contentEditable = 'true';
        title.spellcheck = false;
        title.addEventListener('blur', () => {
            const value = title.textContent.trim();
            if (!value || value === item.title) return;
            store.update(state => {
                state.queues[ARTICLE_DRAFT_ID] = updateItem(state.queues[ARTICLE_DRAFT_ID], item.id,
                    i => ({...i, title: value, userEdited: true}));
            });
        });
        card.appendChild(title);

        const where = el('button', 'annotation-target', describeTarget(item.target));
        where.type = 'button';
        where.addEventListener('click', () => onJump(item.target));
        card.appendChild(where);

        if (item.rationale?.trim()) card.appendChild(el('p', 'queue-item-rationale', item.rationale));
        if (item.suggestedText) {
            card.appendChild(el('pre', 'markdown-source annotation-suggested', item.suggestedText));
        }

        const actions = el('div', 'button-row');
        for (const [label, status] of [
            ['✓ Accept', 'accepted'], ['✕ Reject', 'rejected'], ['⌛ Defer', 'deferred'], ['↺ Reset', 'proposed']
        ]) {
            const btn = el('button', 'btn btn-sm btn-toolbar', label);
            btn.type = 'button';
            btn.addEventListener('click', () => {
                store.update(state => {
                    state.queues[ARTICLE_DRAFT_ID] =
                        updateItem(state.queues[ARTICLE_DRAFT_ID], item.id, i => setItemStatus(i, status));
                });
                renderQueue();
            });
            actions.appendChild(btn);
        }

        const severity = select(`sev-${item.id}`, SEVERITIES, item.severity);
        severity.classList.add('btn-sm');
        severity.addEventListener('change', () => {
            store.update(state => {
                state.queues[ARTICLE_DRAFT_ID] = updateItem(state.queues[ARTICLE_DRAFT_ID], item.id,
                    i => ({...i, severity: severity.value, userEdited: true}));
            });
            renderQueue();
        });
        actions.appendChild(severity);
        card.appendChild(actions);
        return card;
    }

    function render() {
        renderRevisions();
        renderAnnotations();
        renderQueue();
    }

    store.subscribe(() => {
        /* keep the panels honest without re-rendering on every keystroke */
    });
    return {render, showDiff};
}