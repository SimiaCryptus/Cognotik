import { loadGlossary } from './loader.js';
import { ConceptGraph } from './graph.js';
import { Store } from './store.js';
import { PathExplorer } from './path.js';
import { filterConcepts, collectDomains, toSummary } from './filter.js';
import { renderConceptList, renderDetail, renderPathDetail, el } from './render.js';

const dom = {
  list: document.getElementById('concept-list'),
  detail: document.getElementById('detail'),
  search: document.getElementById('search'),
  filterDomain: document.getElementById('filter-domain'),
  filterDifficulty: document.getElementById('filter-difficulty'),
  status: document.getElementById('status'),
  modeButtons: document.querySelectorAll('.mode-switch button'),
};

async function bootstrap() {
  let glossary;
  try {
    glossary = await loadGlossary();
  } catch (err) {
    dom.status.textContent = 'Failed to load glossary.';
    console.error(err);
    return;
  }

  const graph = new ConceptGraph(glossary);
  const store = new Store();
  const path = new PathExplorer(graph, store);

  const count = graph.all().length;
  if (count === 0) {
    dom.status.textContent = 'No concepts loaded. Add JSON shards next to dir.txt.';
  } else {
    dom.status.textContent = `${count} concepts loaded.`;
  }

  populateDomainFilter(graph);
  wireControls(store);
  store.subscribe(() => render(graph, store, path));
  render(graph, store, path);
}

function populateDomainFilter(graph) {
  const domains = collectDomains(graph.all());
  for (const d of domains) {
    dom.filterDomain.append(el('option', { value: d }, d));
  }
}

function wireControls(store) {
  dom.search.addEventListener('input', (e) => store.set({ search: e.target.value }));
  dom.filterDomain.addEventListener('change', (e) => store.set({ filterDomain: e.target.value }));
  dom.filterDifficulty.addEventListener('change', (e) =>
    store.set({ filterDifficulty: e.target.value })
  );
  dom.modeButtons.forEach((btn) => {
    btn.addEventListener('click', () => {
      const mode = btn.dataset.mode;
      dom.modeButtons.forEach((b) => b.classList.toggle('active', b === btn));
      store.set({ mode });
    });
  });
}

function selectConcept(store, id) {
  store.set({ selectedId: id });
}

function render(graph, store, path) {
  const { state } = store;
  if (state.mode === 'explore') {
    renderExplore(graph, store);
  } else {
    renderPath(graph, store, path);
  }
}

function renderExplore(graph, store) {
  const { state } = store;
  const summaries = filterConcepts(graph.all(), state);
  renderConceptList(dom.list, summaries, state.selectedId, (id) => selectConcept(store, id));

  if (state.selectedId) {
    renderDetail(dom.detail, state.selectedId, graph, (id) => selectConcept(store, id));
  } else {
    dom.detail.replaceChildren(el('p', { class: 'placeholder' }, 'Select a concept to begin.'));
  }
}

function renderPath(graph, store, path) {
  const { state } = store;
  const frontierIds = path.frontier();

  // Build summaries for the frontier, respecting search/filter.
  let frontierConcepts = frontierIds.map((id) => graph.get(id)).filter(Boolean);

  const filtered = filterConcepts(frontierConcepts, state);
  renderConceptList(dom.list, filtered, state.selectedId, (id) => selectConcept(store, id));

  // Progress indicator in status bar.
  const p = path.progress();
  dom.status.textContent = `Path mode · ${p.known}/${p.total} known · ${frontierIds.length} on frontier`;

  if (state.selectedId) {
    renderPathDetail(
      dom.detail,
      state.selectedId,
      graph,
      store,
      (id) => selectConcept(store, id),
      (action, id) => {
        if (action === 'know') path.markKnown(id);
        else path.markUnknown(id);
        // After acting, advance selection to next frontier item if any.
        const next = path.frontier().filter((f) => f !== id)[0] || null;
        store.set({ selectedId: next });
      }
    );
  } else {
    dom.detail.replaceChildren(
      el(
        'p',
        { class: 'placeholder' },
        frontierIds.length
          ? 'Pick a concept from the frontier to certify your knowledge.'
          : 'Nothing on the frontier yet — load concepts or reset progress.'
      )
    );
  }
}

bootstrap();
