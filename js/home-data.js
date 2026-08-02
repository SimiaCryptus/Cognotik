/*
 * Builds the Featured Laboratories, Essays, and Short Demonstrations grids
 * from a single labs.json file, then loads js/home.js which wires
 * up the cards (README loading, modal behavior, video mounts, etc.).
 *
 * The generated markup mirrors exactly what was previously hard-coded in
 * index.html so that js/home.js continues to work unchanged.
 *
 * labs.json shape:
 *   {
 *     "featured": [ ...featured lab cards... ],
 *     "essays":   [ ...essay cards... ],
 *     "demos":    [ ...short demonstration cards... ]
 *   }
 */
(function () {
  'use strict';

  function el(html) {
    const t = document.createElement('template');
    t.innerHTML = html.trim();
    return t.content.firstElementChild;
  }

  // Build a "featured" style card (used for both experiments and essays).
  function buildFeaturedCard(item) {
    const card = document.createElement('div');
    card.className = 'featured-card';
    card.setAttribute('data-href', item.href);
    if (item.readme) card.setAttribute('data-readme', item.readme);
    if (item.video) card.setAttribute('data-video', item.video);
    card.setAttribute('tabindex', '0');
    card.setAttribute('role', 'button');

    const header = document.createElement('div');
    header.className = 'featured-card-header';

    const icon = document.createElement('div');
    icon.className = 'featured-card-icon';
    icon.setAttribute('aria-hidden', 'true');
    icon.textContent = item.icon || '';
    header.appendChild(icon);

    const title = document.createElement('h2');
    title.className = 'featured-card-title';
    title.textContent = item.title || '';
    header.appendChild(title);

    const launch = document.createElement('a');
    launch.className = 'featured-card-launch';
    launch.href = item.href;
    if (item.launchLabel) launch.setAttribute('aria-label', item.launchLabel);
    launch.innerHTML = 'Open <span class="arrow">→</span>';
    header.appendChild(launch);

    card.appendChild(header);

    if (item.video) {
      const media = document.createElement('div');
      media.className = 'featured-card-media';
      media.setAttribute('data-video-mount', '');
      card.appendChild(media);
    }

    if (item.subtitle) {
      const tag = document.createElement('p');
      tag.className = 'featured-card-tag';
      tag.setAttribute('style', 'font-size: 0.85em; opacity: 0.7; margin-bottom: 0.5em');
      tag.textContent = item.subtitle;
      card.appendChild(tag);
    }

    const pitch = document.createElement('p');
    pitch.className = 'featured-card-pitch';
    pitch.innerHTML = item.pitch || '';
    card.appendChild(pitch);

    const preview = document.createElement('div');
    preview.className = 'readme-preview';
    preview.innerHTML = '<p class="readme-loading">Loading description…</p>';
    card.appendChild(preview);

    return card;
  }

  // Build a compact "card" (Short Demonstrations).
  function buildDemoCard(item) {
    const card = document.createElement('a');
    card.className = 'card';
    card.href = item.href;

    const glyph = document.createElement('div');
    glyph.className = 'card-glyph';
    glyph.setAttribute('aria-hidden', 'true');
    glyph.textContent = item.glyph || '';
    card.appendChild(glyph);

    const title = document.createElement('h2');
    title.textContent = item.title || '';
    card.appendChild(title);

    const p = document.createElement('p');
    p.innerHTML = item.pitch || '';
    card.appendChild(p);

    if (item.tag) {
      const tag = document.createElement('span');
      tag.className = 'tag';
      tag.textContent = item.tag;
      card.appendChild(tag);
    }

    return card;
  }

  function fill(container, items, builder) {
    if (!container || !Array.isArray(items)) return;
    const frag = document.createDocumentFragment();
    items.forEach((item) => frag.appendChild(builder(item)));
    container.appendChild(frag);
  }

  function loadHomeScript() {
    const s = document.createElement('script');
    s.src = 'js/home.js';
    document.body.appendChild(s);
  }

  function fetchJSON(url) {
    return fetch(url).then((r) => {
      if (!r.ok) throw new Error('Failed to load ' + url + ': ' + r.status);
      return r.json();
    });
  }

  function init() {
    // Pull featured labs from labs.json, essays from essays.json, and
    // short demonstrations from experiments.json. Each is loaded
    // independently so a single failure doesn't blank the whole page.
    const safeFetch = (url, fallback) =>
      fetchJSON(url).catch((e) => {
        console.error(e);
        return fallback;
      });

    Promise.all([
      safeFetch('labs.json', { featured: [] }),
      safeFetch('essays.json', { essays: [] }),
      safeFetch('experiments.json', { demos: [] }),
      safeFetch('games.json', { games: [] }),
    ])
      .then(([labs, essaysData, experiments, gamesData]) => {
        const featured = (labs && labs.featured) || (experiments && experiments.featured) || [];
        const essays = (essaysData && essaysData.essays) || (labs && labs.essays) || [];
        const demos = (experiments && experiments.demos) || (labs && labs.demos) || [];
        const games = (gamesData && gamesData.games) || (labs && labs.games) || [];

        fill(document.getElementById('featuredGrid'), featured, buildFeaturedCard);
        fill(document.getElementById('essaysGrid'), essays, buildFeaturedCard);
        fill(document.getElementById('demoGrid'), demos, buildDemoCard);
        fill(document.getElementById('gamesGrid'), games, buildFeaturedCard);
        // Signal that the dynamic grids are now in the DOM so consumers
        // (e.g. home.js) can correct anchor-based scroll positions that
        // were computed before the page was fully built.
        window.__homeGridsReady = true;
        document.dispatchEvent(new CustomEvent('home-grids-ready'));
      })
      .finally(() => {
        // Load home.js after the grids are populated so its DOM queries succeed.
        loadHomeScript();
      });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
