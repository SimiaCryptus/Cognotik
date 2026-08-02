/**
 * Pure-ish DOM rendering helpers.
 */
let _mermaidCounter = 0;
// ─── Marked configuration ────────────────────────────────────────────────────
/** Apply our standard marked options once marked is available. */
function configureMarked() {
  if (!window.marked) return;
  window.marked.setOptions({
    gfm: true, // GitHub-flavoured markdown (tables, strikethrough, …)
    breaks: false, // don't turn single newlines into <br>
    pedantic: false,
  });
}
// Try immediately (marked loaded via <script defer> before this module runs)
// and also register a fallback for the polling path.
configureMarked();

/**
 * Render a markdown string into an HTML element, then schedule
 * MathJax typesetting and Mermaid diagram rendering for it.
 *
 * Falls back to plain text if `marked` is unavailable.
 */
export function renderMarkdown(text, { inline = false } = {}) {
  const container = document.createElement(inline ? 'span' : 'div');
  container.className = inline ? 'md md-inline' : 'md';
  const src = text == null ? '' : String(text);
  if (window.marked && typeof window.marked.parse === 'function') {
    try {
      container.innerHTML = inline ? window.marked.parseInline(src) : window.marked.parse(src);
    } catch (err) {
      console.warn('Markdown parse failed:', err);
      container.textContent = src;
    }
  } else {
    // marked not ready yet — show raw text now and upgrade once it loads.
    container.textContent = src;
    whenMarkedReady(() => {
      configureMarked();
      try {
        container.innerHTML = inline ? window.marked.parseInline(src) : window.marked.parse(src);
        prepareMermaid(container);
        schedulePostProcess(container);
      } catch (err) {
        console.warn('Deferred markdown parse failed:', err);
      }
    });
  }
  // Convert fenced ```mermaid blocks (rendered by marked as
  // <pre><code class="language-mermaid">) into mermaid-ready nodes.
  prepareMermaid(container);
  // Schedule async post-processing (math + diagrams) after the caller has
  // had a chance to attach the container to the DOM.
  schedulePostProcess(container);
  return container;
}
/**
 * Schedule MathJax + Mermaid processing on `container` after the current
 * call-stack unwinds (giving the caller time to attach it to the DOM).
 */
function schedulePostProcess(container) {
  // Use a microtask so the container is in the DOM before we call mermaid.run.
  Promise.resolve().then(() => {
    typeset(container);
    runMermaid(container);
  });
}

/** Run `fn` once `window.marked` becomes available (polls briefly). */
function whenMarkedReady(fn, attempts = 50) {
  if (window.marked && typeof window.marked.parse === 'function') {
    fn();
    return;
  }
  if (attempts <= 0) return;
  setTimeout(() => whenMarkedReady(fn, attempts - 1), 100);
}

/** Turn marked's code blocks for mermaid into <div class="mermaid"> nodes. */
function prepareMermaid(root) {
  const blocks = root.querySelectorAll('pre > code.language-mermaid, pre > code.lang-mermaid');
  for (const code of blocks) {
    const pre = code.parentElement;
    const div = document.createElement('div');
    div.className = 'mermaid';
    div.id = `mermaid-${++_mermaidCounter}`;
    // Use textContent so HTML entities are decoded back to raw source.
    div.textContent = code.textContent;
    pre.replaceWith(div);
  }
}

/** Run MathJax typesetting on a subtree, if MathJax is ready. */
function typeset(node) {
  const mj = window.MathJax;
  if (!mj) return;
  const doTypeset = () => {
    if (typeof mj.typesetPromise === 'function') {
      mj.typesetPromise([node]).catch((err) => console.warn('MathJax typeset failed:', err));
    }
  };
  // MathJax may still be loading; defer until startup is ready.
  if (mj.startup && mj.startup.promise) {
    mj.startup.promise.then(doTypeset).catch(doTypeset);
  } else {
    doTypeset();
  }
}

/** Render any <div class="mermaid"> diagrams inside a subtree. */
function runMermaid(node) {
  const mermaid = window.mermaid;
  if (!mermaid || typeof mermaid.run !== 'function') return;
  // Only render diagrams that haven't already been processed.
  const nodes = [...node.querySelectorAll('.mermaid')].filter(
    (n) => n.dataset.processed !== 'true' && !n.querySelector('svg')
  );
  if (!nodes.length) return;
  // mermaid.initialize may not have been called yet if the CDN script just
  // arrived — retry a few times before giving up.
  tryRunMermaid(nodes, 0);
}
function tryRunMermaid(nodes, attempt) {
  const mermaid = window.mermaid;
  // If mermaid isn't initialised yet, wait and retry (up to ~2 s).
  if (!mermaid || typeof mermaid.run !== 'function') {
    if (attempt < 20) setTimeout(() => tryRunMermaid(nodes, attempt + 1), 100);
    return;
  }
  try {
    const result = mermaid.run({ nodes });
    // mermaid.run returns a Promise in v10+.
    if (result && typeof result.then === 'function') {
      result
        .then(() => nodes.forEach((n) => (n.dataset.processed = 'true')))
        .catch((err) => {
          console.warn('Mermaid render failed:', err);
          // Mark as processed anyway to avoid infinite retry loops.
          nodes.forEach((n) => (n.dataset.processed = 'true'));
        });
    } else {
      nodes.forEach((n) => (n.dataset.processed = 'true'));
    }
  } catch (err) {
    console.warn('Mermaid render failed:', err);
  }
}

export function el(tag, attrs = {}, children = []) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (k === 'class') node.className = v;
    else if (k === 'dataset') Object.assign(node.dataset, v);
    else if (k.startsWith('on') && typeof v === 'function') {
      node.addEventListener(k.slice(2).toLowerCase(), v);
    } else if (v !== null && v !== undefined) {
      node.setAttribute(k, v);
    }
  }
  for (const child of [].concat(children)) {
    if (child == null) continue;
    node.append(child.nodeType ? child : document.createTextNode(child));
  }
  return node;
}

export function badge(text, cls) {
  return el('span', { class: `badge ${cls}` }, text);
}

/** Render the searchable concept list. */
export function renderConceptList(container, summaries, selectedId, onSelect) {
  container.replaceChildren();
  if (summaries.length === 0) {
    container.append(el('li', { class: 'ci-empty' }, 'No matches.'));
    return;
  }
  for (const c of summaries) {
    const li = el(
      'li',
      {
        class: c.id === selectedId ? 'selected' : '',
        dataset: { id: c.id },
        onclick: () => onSelect(c.id),
      },
      [
        el('div', { class: 'ci-meta' }, [
          badge(c.difficulty, c.difficulty),
          badge(c.domain, 'domain'),
        ]),
        el('div', { class: 'ci-term' }, renderMarkdown(c.term, { inline: true })),
        el(
          'div',
          { class: 'ci-summary' },
          c.summary ? renderMarkdown(c.summary, { inline: true }) : ''
        ),
      ]
    );
    container.append(li);
  }
}

function relChips(label, ids, graph, onSelect) {
  if (!ids.length) return null;
  const chips = ids.map((id) => {
    const target = graph.get(id);
    if (!target) {
      return el('span', { class: 'chip missing' }, id);
    }
    return el('span', { class: 'chip', onclick: () => onSelect(id) }, target.term);
  });
  return el('div', { class: 'rel-group' }, [el('div', { class: 'rel-label' }, label), ...chips]);
}

function refIds(refs) {
  return (refs || []).map((r) => r.id);
}

/** Render the full detail view of a concept. */
export function renderDetail(container, id, graph, onSelect) {
  const c = graph.get(id);
  container.replaceChildren();
  if (!c) {
    container.append(el('p', { class: 'placeholder' }, 'Concept not found.'));
    return;
  }

  const header = el('div', {}, [
    el('div', { class: 'ci-meta' }, [
      badge(c.difficulty, c.difficulty),
      badge(c.domain, 'domain'),
      ...(c.crossDomains || []).map((d) => badge(d, 'domain')),
    ]),
    el('h2', {}, c.term),
    c.aliases && c.aliases.length
      ? el('div', { class: 'summary' }, `aka ${c.aliases.join(', ')}`)
      : null,
    c.summary ? el('div', { class: 'summary' }, renderMarkdown(c.summary, { inline: true })) : null,
  ]);
  container.append(header);

  if (c.definition) {
    container.append(el('section', {}, [el('h3', {}, 'Definition'), renderMarkdown(c.definition)]));
  }

  const rels = c.relations || {};
  const relSection = el('section', {}, [el('h3', {}, 'Relations')]);
  const groups = [
    relChips('Requires', refIds(rels.requires), graph, onSelect),
    relChips('Enables', refIds(rels.enabledBy), graph, onSelect),
    relChips('Related', refIds(rels.related), graph, onSelect),
    relChips('Synonyms', refIds(rels.synonyms), graph, onSelect),
  ].filter(Boolean);
  if (groups.length) {
    relSection.append(...groups);
    container.append(relSection);
  }

  if (c.formulas && c.formulas.length) {
    const sec = el('section', {}, [el('h3', {}, 'Formulas')]);
    for (const f of c.formulas) {
      sec.append(
        el('div', { class: 'formula' }, [
          // Wrap in $…$ so MathJax renders it; fall back to raw text.
          renderMarkdown(f.expression ? `$${f.expression}$` : ''),
          f.description
            ? el('div', { class: 'rel-label' }, renderMarkdown(f.description, { inline: true }))
            : null,
        ])
      );
    }
    container.append(sec);
  }

  if (c.examples && c.examples.length) {
    const sec = el('section', {}, [el('h3', {}, 'Examples')]);
    for (const ex of c.examples) {
      sec.append(
        el('div', { class: 'example' }, [
          el('strong', {}, ex.title || ''),
          el('p', {}, ex.body || ''),
        ])
      );
    }
    container.append(sec);
  }

  if (c.references && c.references.length) {
    const sec = el('section', {}, [el('h3', {}, 'References')]);
    const ul = el('ul', {});
    for (const ref of c.references) {
      const isUrl = /^https?:\/\//.test(ref);
      ul.append(el('li', {}, isUrl ? el('a', { href: ref, target: '_blank' }, ref) : ref));
    }
    sec.append(ul);
    container.append(sec);
  }
}

/** Render a detail view augmented with path-mode actions. */
export function renderPathDetail(container, id, graph, store, onSelect, onAction) {
  renderDetail(container, id, graph, onSelect);
  if (!id || !graph.get(id)) return;

  const known = store.state.known.has(id);
  const controls = el('div', { class: 'path-actions', style: 'margin-top:1.5rem' }, [
    el(
      'button',
      { class: 'know', onclick: () => onAction('know', id) },
      known ? '✓ Known' : 'I know this'
    ),
    el(
      'button',
      { class: 'dont-know', onclick: () => onAction('dont-know', id) },
      "I don't know this"
    ),
  ]);
  container.prepend(controls);
}
