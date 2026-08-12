import { SECTIONS, SITE } from './config.js';
  import { flatten, indexPages, loadSections } from './menu.js';
  import { renderDocument } from './renderer.js';
  import { navigate, onRouteChange, parseRoute } from './router.js';

  const els = {
    nav: document.getElementById('nav'),
    content: document.getElementById('content'),
    toc: document.getElementById('toc'),
    tocList: document.getElementById('toc-list'),
    search: document.getElementById('search-input'),
    searchClear: document.getElementById('search-clear'),
    pageCount: document.getElementById('page-count'),
    sidebar: document.getElementById('sidebar'),
    scrim: document.getElementById('scrim'),
    menuToggle: document.getElementById('menu-toggle'),
    themeToggle: document.getElementById('theme-toggle')
  };

  const state = {
    sections: [],
    flat: [],
    byRoute: new Map(),
    byFile: new Map(),
    query: '',
    route: parseRoute(),
    cache: new Map()
  };

  /* ------------------------------------------------------------------ boot */

  initTheme();
  wireChrome();

  loadSections(SECTIONS)
    .then((sections) => {
      state.sections = sections;
      state.flat = flatten(sections);
      const { byRoute, byFile } = indexPages(sections);
      state.byRoute = byRoute;
      state.byFile = byFile;
      renderNav();
      els.pageCount.textContent = `${state.flat.length} pages · ${sections.length} sections`;
      onRouteChange(render);
    })
    .catch((error) => {
      els.nav.innerHTML = '';
      els.content.innerHTML = errorPanel('Failed to load the documentation menu', error.message);
    });

  /* --------------------------------------------------------------- chrome */

  function wireChrome() {
    els.themeToggle.addEventListener('click', () => {
      const next = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
      document.documentElement.dataset.theme = next;
      try { localStorage.setItem('cognotik-docs-theme', next); } catch { /* ignore */ }
    });

    els.menuToggle.addEventListener('click', () => toggleSidebar());
    els.scrim.addEventListener('click', () => toggleSidebar(false));

    els.search.addEventListener('input', debounce(() => {
      state.query = els.search.value.trim();
      els.searchClear.hidden = state.query.length === 0;
      filterNav();
      if (state.query) renderSearch();
      else render(state.route);
    }, 120));

    els.searchClear.addEventListener('click', () => {
      els.search.value = '';
      els.search.dispatchEvent(new Event('input'));
      els.search.focus();
    });

    document.addEventListener('keydown', (event) => {
      if (event.key === '/' && document.activeElement !== els.search) {
        event.preventDefault();
        els.search.focus();
      } else if (event.key === 'Escape' && document.activeElement === els.search) {
        els.search.blur();
      }
    });

    // Delegated handlers for dynamic content.
    document.addEventListener('click', (event) => {
      const copy = event.target.closest('.copy-btn');
      if (copy) return copyCode(copy);

      const tocLink = event.target.closest('.toc-list a');
      if (tocLink) {
        event.preventDefault();
        scrollToHeading(tocLink.getAttribute('href').slice(1));
        return;
      }

      const anchor = event.target.closest('.markdown .heading-anchor');
      if (anchor) {
        event.preventDefault();
        scrollToHeading(anchor.getAttribute('href').slice(1));
        return;
      }

      if (event.target.closest('a[href^="#/"]')) toggleSidebar(false);
    });
  }

  function initTheme() {
    let stored = null;
    try { stored = localStorage.getItem('cognotik-docs-theme'); } catch { /* ignore */ }
    const prefersLight = window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches;
    document.documentElement.dataset.theme = stored || (prefersLight ? 'light' : 'dark');
  }

  function toggleSidebar(force) {
    const open = force === undefined ? !els.sidebar.classList.contains('open') : force;
    els.sidebar.classList.toggle('open', open);
    els.scrim.hidden = !open;
    els.menuToggle.setAttribute('aria-expanded', String(open));
  }

  /* ------------------------------------------------------------ navigation */

  function renderNav() {
    els.nav.innerHTML = state.sections
      .map((section) => {
        const items = section.pages
          .map(
            (page) => `
              <li class="nav-item" data-haystack="${escapeAttr((page.name + ' ' + page.shortDescription).toLowerCase())}">
                <a href="${page.route}" data-key="${escapeAttr(section.id + '/' + page.slug)}">${escapeHtml(page.name)}</a>
              </li>`
          )
          .join('');
        const problem = section.error ? `<li class="nav-error">${escapeHtml(section.error)}</li>` : '';
        return `
          <section class="nav-group" data-section="${escapeAttr(section.id)}">
            <h2 class="nav-heading">
              <a href="#/${escapeAttr(section.id)}">
                <span class="nav-icon" aria-hidden="true">${escapeHtml(section.icon || '•')}</span>
                ${escapeHtml(section.label)}
                <span class="nav-badge">${section.pages.length}</span>
              </a>
            </h2>
            <ul class="nav-list">${items}${problem}</ul>
          </section>`;
      })
      .join('');
  }

  function filterNav() {
    const query = state.query.toLowerCase();
    els.nav.querySelectorAll('.nav-group').forEach((group) => {
      let visible = 0;
      group.querySelectorAll('.nav-item').forEach((item) => {
        const match = !query || item.dataset.haystack.includes(query);
        item.hidden = !match;
        if (match) visible++;
      });
      group.hidden = query.length > 0 && visible === 0;
    });
  }

  function markActive(key) {
    els.nav.querySelectorAll('a[data-key]').forEach((link) => {
      link.classList.toggle('active', link.dataset.key === key);
    });
    els.nav.querySelectorAll('.nav-group').forEach((group) => {
      const section = key ? key.split('/')[0] : null;
      group.classList.toggle('current', group.dataset.section === section);
    });
  }

  /* ----------------------------------------------------------------- views */

  function render(route) {
    state.route = route;
    if (state.query) return renderSearch();
    if (route.view === 'page') return renderPage(route);
    if (route.view === 'section') return renderSectionIndex(route);
    return renderHome();
  }

  function renderHome() {
    markActive(null);
    hideToc();
    setTitle(null);
    els.content.innerHTML = `
      <article class="page">
        <header class="hero">
          <h1>${escapeHtml(SITE.title)}</h1>
          <p>${escapeHtml(SITE.tagline)}</p>
        </header>
        ${state.sections
          .map(
            (section) => `
          <section class="home-section">
            <h2>
              <a href="#/${escapeAttr(section.id)}">
                <span class="nav-icon" aria-hidden="true">${escapeHtml(section.icon || '•')}</span>
                ${escapeHtml(section.label)}
              </a>
              <span class="nav-badge">${section.pages.length}</span>
            </h2>
            ${section.blurb ? `<p class="muted">${escapeHtml(section.blurb)}</p>` : ''}
            <div class="card-grid">${section.pages.map(card).join('')}</div>
          </section>`
          )
          .join('')}
      </article>`;
    els.content.scrollIntoView({ block: 'start' });
    window.scrollTo({ top: 0 });
  }

  function renderSectionIndex(route) {
    const section = state.sections.find((candidate) => candidate.id === route.section);
    if (!section) return renderMissing(`No such section: ${route.section}`);
    markActive(`${section.id}/`);
    hideToc();
    setTitle(section.label);
    els.content.innerHTML = `
      <article class="page">
        ${breadcrumb([{ label: 'Home', href: '#/' }, { label: section.label }])}
        <header class="hero compact">
          <h1><span class="nav-icon" aria-hidden="true">${escapeHtml(section.icon || '•')}</span> ${escapeHtml(section.label)}</h1>
          ${section.blurb ? `<p>${escapeHtml(section.blurb)}</p>` : ''}
        </header>
        ${section.error ? errorPanel('Menu problem', section.error) : ''}
        <div class="card-grid">${section.pages.map(card).join('')}</div>
      </article>`;
    window.scrollTo({ top: 0 });
  }

  async function renderPage(route) {
    const key = `${route.section}/${route.slug}`;
    const page = state.byRoute.get(key);
    if (!page) return renderMissing(`Page not found: ${key}`);

    markActive(key);
    setTitle(page.name);
    els.content.innerHTML = `<article class="page"><p class="loading">Loading ${escapeHtml(page.name)}…</p></article>`;

    let markdown;
    try {
      markdown = await fetchMarkdown(page.file);
    } catch (error) {
      hideToc();
      els.content.innerHTML = `<article class="page">
        ${breadcrumb([{ label: 'Home', href: '#/' }, { label: page.sectionLabel, href: `#/${page.section}` }, { label: page.name }])}
        ${errorPanel(`Could not load ${page.file}`, error.message)}
      </article>`;
      return;
    }

    const { html, toc } = renderDocument(markdown, {
      basePath: page.file,
      resolveLink: (path) => {
        const target = state.byFile.get(path.toLowerCase());
        return target ? target.route : null;
      }
    });

    const neighbours = siblings(page);
    els.content.innerHTML = `
      <article class="page">
        ${breadcrumb([
          { label: 'Home', href: '#/' },
          { label: page.sectionLabel, href: `#/${page.section}` },
          { label: page.name }
        ])}
        ${page.shortDescription ? `<p class="page-lede">${escapeHtml(page.shortDescription)}</p>` : ''}
        <div class="markdown">${html}</div>
        <nav class="pager">
          ${neighbours.previous ? pagerLink(neighbours.previous, 'Previous') : '<span></span>'}
          ${neighbours.next ? pagerLink(neighbours.next, 'Next') : '<span></span>'}
        </nav>
        <footer class="source">Source: <a href="${escapeAttr(page.file)}">${escapeHtml(page.file)}</a></footer>
      </article>`;

    renderToc(toc);
    window.scrollTo({ top: 0 });
    els.content.focus({ preventScroll: true });
  }

  function renderSearch() {
    const query = state.query.toLowerCase();
    const matches = state.flat.filter((page) =>
      (page.name + ' ' + page.shortDescription + ' ' + page.file).toLowerCase().includes(query)
    );
    hideToc();
    setTitle(`Search: ${state.query}`);
    els.content.innerHTML = `
      <article class="page">
        <header class="hero compact">
          <h1>Search</h1>
          <p>${matches.length} result${matches.length === 1 ? '' : 's'} for “${escapeHtml(state.query)}”</p>
        </header>
        ${matches.length ? `<div class="card-grid">${matches.map(card).join('')}</div>`
                         : '<p class="muted">Nothing matched. Try a shorter query.</p>'}
      </article>`;
    window.scrollTo({ top: 0 });
  }

  function renderMissing(message) {
    markActive(null);
    hideToc();
    setTitle('Not found');
    els.content.innerHTML = `<article class="page">
      ${breadcrumb([{ label: 'Home', href: '#/' }, { label: 'Not found' }])}
      ${errorPanel('Not found', message)}
      <p><a class="button" href="#/">Back to the index</a></p>
    </article>`;
  }

  /* ------------------------------------------------------------------ toc */

  function renderToc(toc) {
    if (!toc.length) return hideToc();
    els.tocList.innerHTML = toc
      .map((entry) => `<li class="toc-l${entry.level}"><a href="#${escapeAttr(entry.id)}">${escapeHtml(entry.text)}</a></li>`)
      .join('');
    els.toc.hidden = false;
    observeHeadings(toc);
  }

  function hideToc() {
    els.toc.hidden = true;
    els.tocList.innerHTML = '';
    disconnectObserver();
  }

  let observer = null;

  function disconnectObserver() {
    if (observer) observer.disconnect();
    observer = null;
  }

  function observeHeadings(toc) {
    disconnectObserver();
    if (!('IntersectionObserver' in window)) return;
    observer = new IntersectionObserver(
      (entries) => {
        entries
          .filter((entry) => entry.isIntersecting)
          .forEach((entry) => {
            els.tocList.querySelectorAll('a').forEach((link) => {
              link.classList.toggle('active', link.getAttribute('href') === `#${entry.target.id}`);
            });
          });
      },
      { rootMargin: '-80px 0px -70% 0px', threshold: 0 }
    );
    toc.forEach((entry) => {
      const heading = document.getElementById(entry.id);
      if (heading) observer.observe(heading);
    });
  }

  function scrollToHeading(id) {
    const heading = document.getElementById(id);
    if (!heading) return;
    const top = heading.getBoundingClientRect().top + window.scrollY - 80;
    window.scrollTo({ top, behavior: 'smooth' });
  }

  /* -------------------------------------------------------------- helpers */

  async function fetchMarkdown(path) {
    if (state.cache.has(path)) return state.cache.get(path);
    const response = await fetch(path, { cache: 'no-cache' });
    if (!response.ok) throw new Error(`${response.status} ${response.statusText}`);
    const text = await response.text();
    state.cache.set(path, text);
    return text;
  }

  function siblings(page) {
    const index = state.flat.findIndex((candidate) => candidate.route === page.route);
    return { previous: state.flat[index - 1] || null, next: state.flat[index + 1] || null };
  }

  function card(page) {
    return `
      <a class="card" href="${page.route}">
        <span class="card-kicker">${escapeHtml(page.sectionLabel)}</span>
        <span class="card-title">${escapeHtml(page.name)}</span>
        ${page.shortDescription ? `<span class="card-text">${escapeHtml(page.shortDescription)}</span>` : ''}
      </a>`;
  }

  function pagerLink(page, kind) {
    return `<a class="pager-link ${kind.toLowerCase()}" href="${page.route}">
      <span class="pager-kind">${kind}</span>
      <span class="pager-name">${escapeHtml(page.name)}</span>
    </a>`;
  }

  function breadcrumb(items) {
    return `<nav class="breadcrumb">${items
      .map((item) => (item.href ? `<a href="${escapeAttr(item.href)}">${escapeHtml(item.label)}</a>` : `<span>${escapeHtml(item.label)}</span>`))
      .join('<i aria-hidden="true">/</i>')}</nav>`;
  }

  function errorPanel(title, detail) {
    return `<div class="panel error"><strong>${escapeHtml(title)}</strong><p>${escapeHtml(detail)}</p>
      <p class="muted">If you are opening this file directly from disk, serve the folder over HTTP
      (for example <code>python3 -m http.server</code>) so that <code>fetch()</code> can read it.</p></div>`;
  }

  function setTitle(name) {
    document.title = name ? `${name} · ${SITE.title}` : SITE.title;
  }

  async function copyCode(button) {
    const pre = button.closest('.code-block').querySelector('pre');
    const text = pre ? pre.innerText : '';
    try {
      await navigator.clipboard.writeText(text);
      button.textContent = 'Copied';
    } catch {
      button.textContent = 'Press ⌘/Ctrl+C';
    }
    setTimeout(() => { button.textContent = 'Copy'; }, 1500);
  }

  function debounce(fn, wait) {
    let timer = null;
    return (...args) => {
      clearTimeout(timer);
      timer = setTimeout(() => fn(...args), wait);
    };
  }

  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, (character) => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[character]));
  }

  const escapeAttr = escapeHtml;

  // Exposed for quick debugging in the browser console.
  window.__docs = state;
  // Keep the router import referenced even if tree-shaken by a bundler.
  export { navigate };