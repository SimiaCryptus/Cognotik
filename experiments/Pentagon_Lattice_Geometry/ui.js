// ui.js
// Side-panel rendering of selected tile info and walk history.

// ── Docs panel ────────────────────────────────────────────────────────────────
let _docCache = {}; // filename → rendered HTML
let _activeDoc = 'README.md';
// Glossary file listing (kept in sync with ./glossary/)
const GLOSSARY_FILES = [
  { file: 'glossary/README.md', label: 'Glossary Index' },
  { file: 'glossary/MAP.md', label: 'Concept Map' },
  { file: 'glossary/A_algebraic_structures.md', label: 'A · Algebraic Structures' },
  { file: 'glossary/B_geometric_constructions.md', label: 'B · Geometric Constructions' },
  { file: 'glossary/C_higher_dimensional_polytopes.md', label: 'C · Higher-Dim Polytopes' },
  { file: 'glossary/D_group_theory.md', label: 'D · Group Theory' },
  { file: 'glossary/E_topology_bundles.md', label: 'E · Topology & Bundles' },
  { file: 'glossary/F_dimensions_scaling.md', label: 'F · Dimensions & Scaling' },
  { file: 'glossary/G_spectral_graph_theory.md', label: 'G · Spectral Graph Theory' },
  { file: 'glossary/H_fractals_self_similarity.md', label: 'H · Fractals' },
  { file: 'glossary/I_cellular_automata.md', label: 'I · Cellular Automata' },
  { file: 'glossary/J_physics.md', label: 'J · Physics' },
  { file: 'glossary/K_extremal.md', label: 'K · Extremal' },
  { file: 'glossary/L_reconnection.md', label: 'L · Reconnection' },
  { file: 'glossary/M_algorithmic.md', label: 'M · Algorithmic' },
  { file: 'glossary/N_notation.md', label: 'N · Notation' },
  { file: 'glossary/O_project_terms.md', label: 'O · Project Terms' },
];
function isGlossaryDoc(filename) {
  return typeof filename === 'string' && filename.startsWith('glossary/');
}

// ── MathJax helpers ──────────────────────────────────────────────────────────
/**
 * Render markdown while preserving LaTeX math blocks.
 * marked will mangle $...$ and $$...$$ if we let it see them raw,
 * so we extract all math spans first, replace with placeholders,
 * run marked, then restore the originals.
 */
function markedWithMath(src) {
  const stash = [];
  const mermaidStash = [];
  // First, extract mermaid fenced code blocks so marked doesn't escape
  // their contents. We'll re-inject them as <div class="mermaid"> nodes.
  let pre = src.replace(/```mermaid\s*\n([\s\S]+?)\n```/g, (_, code) => {
    mermaidStash.push(code);
    return `@@MERMAID${mermaidStash.length - 1}@@`;
  });
  // Order matters: match display math ($$) before inline ($).
  const escaped = pre
    // Display math: $$...$$  (possibly multi-line)
    .replace(/\$\$([\s\S]+?)\$\$/g, (_, inner) => {
      stash.push(`\\[${inner}\\]`);
      return `@@MATH${stash.length - 1}@@`;
    })
    // Inline math: $...$  (single line only)
    .replace(/\$([^\n$]+?)\$/g, (_, inner) => {
      stash.push(`\\(${inner}\\)`);
      return `@@MATH${stash.length - 1}@@`;
    });
  let html = marked.parse(escaped);
  // Restore math, but make sure the placeholder wasn't HTML-escaped.
  html = html.replace(/@@MATH(\d+)@@/g, (_, i) => stash[Number(i)]);
  // Restore mermaid blocks as <div class="mermaid"> nodes. The placeholder
  // may have been wrapped in <p>…</p> by marked; strip such wrappers.
  html = html.replace(
    /<p>\s*@@MERMAID(\d+)@@\s*<\/p>/g,
    (_, i) => `<div class="mermaid">${escapeHtmlText(mermaidStash[Number(i)])}</div>`
  );
  html = html.replace(
    /@@MERMAID(\d+)@@/g,
    (_, i) => `<div class="mermaid">${escapeHtmlText(mermaidStash[Number(i)])}</div>`
  );
  // marked sometimes wraps a lone placeholder in <p>; that's fine for MathJax.
  return html;
}
// Minimal HTML-escape for text content destined for a <div class="mermaid">.
function escapeHtmlText(s) {
  return s.replace(/[&<>]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' })[c]);
}
/**
 * Rewrite relative .md links inside rendered HTML so that clicks navigate
 * the in-app doc viewer instead of doing a hard page load.
 * `basePath` is the directory of the source document (e.g. "glossary/").
 */
function rewriteMdLinks(containerEl, basePath) {
  if (!containerEl) return;
  const anchors = containerEl.querySelectorAll('a[href]');
  anchors.forEach((a) => {
    const href = a.getAttribute('href');
    if (!href) return;
    // Skip absolute URLs, anchors, mailto, etc.
    if (/^[a-z]+:\/\//i.test(href) || href.startsWith('#') || href.startsWith('mailto:')) return;
    // Strip any trailing #fragment for the resolution step.
    const [pathPart, frag] = href.split('#');
    if (!pathPart) return;
    if (!pathPart.endsWith('.md')) return;
    // Resolve relative to basePath.
    let resolved;
    if (pathPart.startsWith('/')) {
      resolved = pathPart.replace(/^\/+/, '');
    } else {
      resolved = (basePath || '') + pathPart;
    }
    // Collapse "foo/../bar" segments.
    const parts = [];
    resolved.split('/').forEach((seg) => {
      if (seg === '' || seg === '.') return;
      if (seg === '..') parts.pop();
      else parts.push(seg);
    });
    resolved = parts.join('/');
    a.dataset.doc = resolved;
    a.dataset.frag = frag || '';
    a.href = 'javascript:void(0)';
    a.addEventListener('click', (ev) => {
      ev.preventDefault();
      // Find which view we're in (modal vs sidebar) and route accordingly.
      const inModal = !!containerEl.closest('#doc-modal');
      if (inModal) {
        _navigateModal(resolved);
      } else {
        _navigateSidebar(resolved);
      }
    });
  });
}
// These get filled in by initDocs().
let _navigateSidebar = (doc) => showDoc(doc);
let _navigateModal = (doc) => {};

/**
 * Ask MathJax to typeset a DOM element.
 * Safe to call even before MathJax has finished loading.
 */
function typesetMath(el) {
  if (!el) return;
  if (window.MathJax && window.MathJax.typesetPromise) {
    window.MathJax.typesetPromise([el]).catch((err) => console.warn('MathJax typeset error:', err));
  } else {
    // MathJax not yet loaded; retry once it signals readiness.
    window.addEventListener(
      'load',
      () => {
        if (window.MathJax && window.MathJax.typesetPromise) {
          window.MathJax.typesetPromise([el]).catch((err) =>
            console.warn('MathJax typeset error:', err)
          );
        }
      },
      { once: true }
    );
  }
}
/**
 * Render any <div class="mermaid"> nodes inside an element. Safe to call
 * even if mermaid hasn't loaded yet — we retry on window 'load'.
 */
function renderMermaid(el) {
  if (!el) return;
  const nodes = el.querySelectorAll('div.mermaid');
  if (nodes.length === 0) return;
  // Mermaid's run() processes nodes that haven't been processed yet.
  // We must clear any data-processed flag in case the same HTML was
  // cached and re-inserted.
  nodes.forEach((n) => {
    n.removeAttribute('data-processed');
  });
  const doRun = () => {
    try {
      window.mermaid.run({ nodes });
    } catch (err) {
      console.warn('Mermaid render error:', err);
    }
  };
  if (window.mermaid && window.mermaid.run) {
    doRun();
  } else {
    window.addEventListener(
      'load',
      () => {
        if (window.mermaid && window.mermaid.run) doRun();
      },
      { once: true }
    );
  }
}

async function loadDoc(filename) {
  if (_docCache[filename] !== undefined) return _docCache[filename];
  try {
    const resp = await fetch(filename);
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    const text = await resp.text();
    // marked is loaded as a plain <script> tag (global), not an ES module.
    // We protect LaTeX delimiters from marked's escape processing by
    // temporarily replacing them before parsing, then restoring them.
    const html =
      typeof marked !== 'undefined'
        ? markedWithMath(text)
        : `<pre>${text.replace(
            /[&<>]/g,
            (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' })[c]
          )}</pre>`;
    _docCache[filename] = html;
    return html;
  } catch (err) {
    const msg = `<em style="color:#f38ba8">Could not load ${filename}: ${err.message}</em>`;
    _docCache[filename] = msg;
    return msg;
  }
}

async function showDoc(filename) {
  _activeDoc = filename;
  const contentEl = document.getElementById('doc-content');
  if (!contentEl) return;
  contentEl.innerHTML = '<em>Loading…</em>';
  contentEl.innerHTML = await loadDoc(filename);
  // Compute base path for relative link resolution.
  const basePath = filename.includes('/')
    ? filename.substring(0, filename.lastIndexOf('/') + 1)
    : '';
  rewriteMdLinks(contentEl, basePath);
  // If this is a glossary doc, inject a sub-nav at the top.
  if (isGlossaryDoc(filename)) {
    _injectGlossaryNav(contentEl, filename, /*inModal=*/ false);
  }
  typesetMath(contentEl);
  renderMermaid(contentEl);
}
function _injectGlossaryNav(containerEl, currentFile, inModal) {
  const nav = document.createElement('div');
  nav.className = 'glossary-subnav';
  nav.style.cssText =
    'display:flex;flex-wrap:wrap;gap:4px;margin-bottom:10px;' +
    'padding-bottom:8px;border-bottom:1px solid var(--border, #333);';
  GLOSSARY_FILES.forEach(({ file, label }) => {
    const btn = document.createElement('button');
    btn.className = 'glossary-subnav-btn';
    btn.textContent = label;
    btn.style.cssText =
      'font-size:10px;padding:2px 6px;cursor:pointer;' +
      'background:' +
      (file === currentFile ? 'var(--accent2,#4a5)' : '#222') +
      ';' +
      'color:#ddd;border:1px solid #444;border-radius:3px;';
    btn.addEventListener('click', (e) => {
      e.preventDefault();
      if (inModal) _navigateModal(file);
      else _navigateSidebar(file);
    });
    nav.appendChild(btn);
  });
  containerEl.insertBefore(nav, containerEl.firstChild);
}

export function initDocs() {
  const tabs = document.querySelectorAll('.doc-tab');
  tabs.forEach((btn) => {
    if (btn.id === 'docMaximize') return; // handled separately
    btn.addEventListener('click', () => {
      tabs.forEach((b) => b.classList.remove('active'));
      btn.classList.add('active');
      showDoc(btn.dataset.doc);
    });
  });
  // Wire the sidebar navigation callback so in-doc links work.
  _navigateSidebar = (doc) => {
    // Highlight the closest top-level tab if it matches; for glossary
    // sub-pages we light up the "Glossary" tab.
    tabs.forEach((b) => {
      if (b.id === 'docMaximize') return;
      const isMatch =
        b.dataset.doc === doc || (isGlossaryDoc(doc) && b.dataset.doc === 'glossary/README.md');
      b.classList.toggle('active', isMatch);
    });
    showDoc(doc);
  };
  // Load the default tab immediately when the section is first opened.
  const section = document.getElementById('docs-section');
  if (section) {
    // Lazy-load on first open of the <details> element.
    let loaded = false;
    section.addEventListener('toggle', () => {
      if (section.open && !loaded) {
        loaded = true;
        showDoc(_activeDoc);
      }
    });
  }
  // ── Maximize button ──────────────────────────────────────────────────────
  const maximizeBtn = document.getElementById('docMaximize');
  const modal = document.getElementById('doc-modal');
  const modalClose = document.getElementById('docModalClose');
  const modalBody = document.getElementById('doc-modal-content');
  const modalTabs = document.getElementById('docModalTabs');
  if (!maximizeBtn || !modal) return;
  // Build tab buttons inside the modal that mirror the sidebar tabs.
  const DOC_TABS = [
    { label: 'Overview', doc: 'README.md' },
    { label: 'Concept', doc: 'idea.md' },
    { label: 'Erdos', doc: 'erdos.md' },
    { label: 'Irregular', doc: 'affine.md' },
    { label: 'Polyhedra', doc: 'polyhedra.md' },
    { label: 'Pinwheel', doc: 'pinwheels.md' },
    { label: 'Multipolygon', doc: 'multipolygon.md' },
    { label: 'Einstein', doc: 'einstein.md' },
    { label: 'Insights', doc: 'insights.md' },
    { label: 'Glossary', doc: 'glossary/README.md' },
  ];

  function syncModalTabs() {
    modalTabs.querySelectorAll('.doc-tab').forEach((b) => {
      const isMatch =
        b.dataset.doc === _activeDoc ||
        (isGlossaryDoc(_activeDoc) && b.dataset.doc === 'glossary/README.md');
      b.classList.toggle('active', isMatch);
    });
  }

  DOC_TABS.forEach(({ label, doc }) => {
    const btn = document.createElement('button');
    btn.className = 'doc-tab';
    btn.dataset.doc = doc;
    btn.textContent = label;
    btn.addEventListener('click', async () => {
      _activeDoc = doc;
      syncModalTabs();
      // Also sync the sidebar tabs.
      document.querySelectorAll('.doc-tab:not(.doc-maximize)').forEach((b) => {
        const isMatch =
          b.dataset.doc === doc || (isGlossaryDoc(doc) && b.dataset.doc === 'glossary/README.md');
        b.classList.toggle('active', isMatch);
      });
      modalBody.innerHTML = '<em>Loading…</em>';
      modalBody.innerHTML = await loadDoc(doc);
      const basePath = doc.includes('/') ? doc.substring(0, doc.lastIndexOf('/') + 1) : '';
      rewriteMdLinks(modalBody, basePath);
      if (isGlossaryDoc(doc)) {
        _injectGlossaryNav(modalBody, doc, /*inModal=*/ true);
      }
      typesetMath(modalBody);
      renderMermaid(modalBody);
    });
    modalTabs.appendChild(btn);
  });
  // Wire the modal navigation callback used by in-doc link clicks.
  _navigateModal = async (doc) => {
    _activeDoc = doc;
    syncModalTabs();
    document.querySelectorAll('.doc-tab:not(.doc-maximize)').forEach((b) => {
      const isMatch =
        b.dataset.doc === doc || (isGlossaryDoc(doc) && b.dataset.doc === 'glossary/README.md');
      b.classList.toggle('active', isMatch);
    });
    modalBody.innerHTML = '<em>Loading…</em>';
    modalBody.innerHTML = await loadDoc(doc);
    const basePath = doc.includes('/') ? doc.substring(0, doc.lastIndexOf('/') + 1) : '';
    rewriteMdLinks(modalBody, basePath);
    if (isGlossaryDoc(doc)) {
      _injectGlossaryNav(modalBody, doc, /*inModal=*/ true);
    }
    typesetMath(modalBody);
  };

  async function openModal() {
    modal.style.display = 'flex';
    document.body.style.overflow = 'hidden';
    syncModalTabs();
    modalBody.innerHTML = '<em>Loading…</em>';
    modalBody.innerHTML = await loadDoc(_activeDoc);
    const basePath = _activeDoc.includes('/')
      ? _activeDoc.substring(0, _activeDoc.lastIndexOf('/') + 1)
      : '';
    rewriteMdLinks(modalBody, basePath);
    if (isGlossaryDoc(_activeDoc)) {
      _injectGlossaryNav(modalBody, _activeDoc, /*inModal=*/ true);
    }
    typesetMath(modalBody);
    renderMermaid(modalBody);
  }

  function closeModal() {
    modal.style.display = 'none';
    document.body.style.overflow = '';
  }

  maximizeBtn.addEventListener('click', openModal);
  modalClose.addEventListener('click', closeModal);
  // Close on backdrop click.
  modal.addEventListener('click', (e) => {
    if (e.target === modal) closeModal();
  });
  // Close on Escape key.
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && modal.style.display !== 'none') {
      e.stopPropagation();
      closeModal();
    }
  });
}

export function renderTileInfo(el, tile, lattice) {
  if (!tile) {
    el.innerHTML = '<em>No tile selected.</em>';
    return;
  }
  const [cxF, cyF] = tile.centroidF;
  const html = [];

  // --- Summary section ---
  html.push(`<div class="section">Identity</div>`);
  html.push(kv('Index', `<code>#${tile.index}</code>`));
  html.push(kv('Depth', `${tile.depth} hop${tile.depth === 1 ? '' : 's'} from origin`));
  html.push(
    kv(
      'Sheet',
      `<span class="pill sheet">s${tile.sheet}</span>
       <span style="color:var(--muted);font-size:11px">Z₂ orientation cover · mod ${lattice.groupOrder}</span>`
    )
  );
  const n = tile.n || (tile.vertsF ? tile.vertsF.length : 5);
  if (tile.isSierpinski) {
    html.push(kv('Type', `<span class="pill">Sierpiński triangle</span>`));
    html.push(kv('Scale', `<span class="pill">1/${Math.pow(2, tile.depth)}</span>`));
  } else if (tile.isPinwheel) {
    html.push(kv('Type', `<span class="pill">Pinwheel triangle</span>`));
    html.push(
      kv(
        'Edges',
        `<span class="pill">legs active</span> ` +
          `<span class="pill" style="background:#5a1d1d;color:#ffb">hyp inactive</span>`
      )
    );
    html.push(
      kv(
        'Orient (Z₄)',
        `<span class="pill">o${tile.orient}</span>` +
          ` <span style="color:var(--muted);font-size:11px">Klein 4-group</span>`
      )
    );
    html.push(kv('Reflection σ', `<span class="pill">${tile.sigma}</span>`));
  } else {
    const isOdd = n % 2 === 1;
    if (isOdd) {
      html.push(
        kv(
          'Orientation σ',
          `<span class="pill">${tile.sigma === 0 ? '↑' : '↓'}</span>
           <span style="color:var(--muted);font-size:11px">Z₂ fiber (orientation flips each edge; holonomy trivial)</span>`
        )
      );
    }
    html.push(
      kv(
        'Edge frame',
        `<span class="pill">f${tile.orient}</span>
         <span style="color:var(--muted);font-size:11px">vertex-labeling ∈ Z${n}</span>`
      )
    );
    html.push(kv('n-gon', `<span class="pill">${n}-gon</span>`));
  }

  // --- Centroid ---
  html.push(`<div class="section">Centroid</div>`);
  html.push(floatBlock(cxF, cyF));

  // --- Vertices ---
  html.push(`<div class="section">Vertices</div>`);
  for (let i = 0; i < tile.verts.length; i++) {
    const [fx, fy] = tile.vertsF[i];
    html.push(`<div class="vertex-row">
        <span class="pill">v${i}</span>
       ${floatBlock(fx, fy)}
      </div>`);
  }

  // --- Neighbors ---
  html.push(`<div class="section">Neighbors</div>`);
  for (let k = 0; k < tile.neighbors.length; k++) {
    const nIdx = tile.neighbors[k];
    const isInactive = tile.activeEdges && !tile.activeEdges[k];
    const edgeLabel = isInactive
      ? `edge ${k + 1} <span style="color:#ff8a8a;font-size:10px">[inactive]</span>`
      : `edge ${k + 1}`;
    if (nIdx === null) {
      html.push(`<div class="neighbor-row missing">
           <span class="pill edge">${edgeLabel}</span>
           <span>${isInactive ? 'no generator (boundary)' : 'out of lattice'}</span>
          <span></span>
        </div>`);
      continue;
    }
    const nb = lattice.tiles[nIdx];
    html.push(`<div class="neighbor-row">
         <span class="pill edge">${edgeLabel}</span>
      <span>→ #${nb.index} · <span style="color:var(--accent3)">s${nb.sheet}</span> · <span style="color:var(--accent2)">o${nb.orient}</span></span>
        <span class="delta">Δs = +${tile.neighborSheetDeltas[k]}</span>
      </div>`);
  }

  el.innerHTML = html.join('');
}

function kv(label, content) {
  return `<div class="kv">
      <span class="k">${label}</span>
      <span class="v">${content}</span>
    </div>`;
}

function floatBlock(fx, fy) {
  return `<div class="alg-block">
     <div><span class="axis">x</span>= ${fx.toFixed(8)}</div>
     <div><span class="axis">y</span>= ${fy.toFixed(8)}</div>
   </div>`;
}

function escapeHtml(s) {
  return s.replace(/[&<>]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' })[c]);
}

export function appendWalkStep(listEl, tile, edgeK, reason) {
  const li = document.createElement('li');
  const r = reason ? ` <span style="color:var(--muted)">(${reason})</span>` : '';
  const n = tile.n || (tile.vertsF ? tile.vertsF.length : 5);
  li.innerHTML =
    edgeK !== null
      ? `<span class="pill edge">e${edgeK + 1}</span> → #${tile.index}
         <span class="pill sheet">s${tile.sheet}</span>
         <span class="pill">o${tile.orient}</span>${r}`
      : `<span style="color:var(--accent)">●</span> start #${tile.index}
         <span class="pill sheet">s${tile.sheet}</span>${r}`;
  listEl.appendChild(li);
  listEl.scrollTop = listEl.scrollHeight;
}

export function clearWalk(listEl) {
  listEl.innerHTML = '';
}
