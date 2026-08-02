/* ─────────────────────────────────────────────────────────────
       Mathematical Explorations — Home page interactivity
       - Background particle/constellation canvas
       - Markdown rendering with Mermaid + MathJax + relative-link fix
       - README cache and expand-into-modal behavior
       ───────────────────────────────────────────────────────────── */

/* ── Background canvas: drifting particles + constellation ── */

(function initBackgroundCanvas() {
  const canvas = document.getElementById('bgCanvas');
  if (!canvas) return;
  if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    return;
  }
  const ctx = canvas.getContext('2d');
  let width = 0;
  let height = 0;
  let dpr = Math.min(window.devicePixelRatio || 1, 2);
  let particles = [];
  const mouse = { x: -9999, y: -9999, active: false };

  function resize() {
    width = window.innerWidth;
    height = window.innerHeight;
    canvas.width = width * dpr;
    canvas.height = height * dpr;
    canvas.style.width = width + 'px';
    canvas.style.height = height + 'px';
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    const targetCount = Math.min(120, Math.floor((width * height) / 14000));
    if (particles.length !== targetCount) {
      particles = new Array(targetCount).fill(0).map(() => spawn());
    }
  }

  function spawn() {
    return {
      x: Math.random() * width,
      y: Math.random() * height,
      vx: (Math.random() - 0.5) * 0.25,
      vy: (Math.random() - 0.5) * 0.25,
      r: Math.random() * 1.4 + 0.4,
      hue: Math.random() < 0.5 ? 212 : 268, // blue or purple-ish
      alpha: Math.random() * 0.5 + 0.25,
    };
  }

  function step() {
    ctx.clearRect(0, 0, width, height);
    // Update
    for (const p of particles) {
      p.x += p.vx;
      p.y += p.vy;
      if (p.x < -10) p.x = width + 10;
      if (p.x > width + 10) p.x = -10;
      if (p.y < -10) p.y = height + 10;
      if (p.y > height + 10) p.y = -10;
    }
    // Connections
    const maxDist = 130;
    for (let i = 0; i < particles.length; i++) {
      const a = particles[i];
      for (let j = i + 1; j < particles.length; j++) {
        const b = particles[j];
        const dx = a.x - b.x;
        const dy = a.y - b.y;
        const d2 = dx * dx + dy * dy;
        if (d2 < maxDist * maxDist) {
          const d = Math.sqrt(d2);
          const t = 1 - d / maxDist;
          ctx.strokeStyle = `hsla(${(a.hue + b.hue) / 2}, 70%, 70%, ${t * 0.14})`;
          ctx.lineWidth = 0.6;
          ctx.beginPath();
          ctx.moveTo(a.x, a.y);
          ctx.lineTo(b.x, b.y);
          ctx.stroke();
        }
      }
      // Mouse repulsion / attraction
      if (mouse.active) {
        const dx = a.x - mouse.x;
        const dy = a.y - mouse.y;
        const d2 = dx * dx + dy * dy;
        const R = 160;
        if (d2 < R * R) {
          const d = Math.sqrt(d2) || 1;
          const force = (1 - d / R) * 0.4;
          a.vx += (dx / d) * force * 0.15;
          a.vy += (dy / d) * force * 0.15;
          // Cap velocity
          const sp = Math.hypot(a.vx, a.vy);
          const maxSp = 1.2;
          if (sp > maxSp) {
            a.vx = (a.vx / sp) * maxSp;
            a.vy = (a.vy / sp) * maxSp;
          }
        } else {
          // Drift back toward gentle baseline
          a.vx *= 0.995;
          a.vy *= 0.995;
        }
      }
      // Particle dot
      ctx.fillStyle = `hsla(${a.hue}, 80%, 75%, ${a.alpha})`;
      ctx.beginPath();
      ctx.arc(a.x, a.y, a.r, 0, Math.PI * 2);
      ctx.fill();
    }
    requestAnimationFrame(step);
  }

  window.addEventListener('resize', resize);
  window.addEventListener('mousemove', (e) => {
    mouse.x = e.clientX;
    mouse.y = e.clientY;
    mouse.active = true;
  });
  window.addEventListener('mouseleave', () => {
    mouse.active = false;
  });
  resize();
  step();
})();

/* ── Marked configuration with Mermaid passthrough ──────────── */

if (window.marked) {
  marked.setOptions({
    gfm: true,
    breaks: false,
    headerIds: false,
    mangle: false,
  });
  const mermaidRenderer = new marked.Renderer();
  const defaultCodeRenderer = mermaidRenderer.code.bind(mermaidRenderer);
  mermaidRenderer.code = function (code, infostring, escaped) {
    const lang = (infostring || '').trim().split(/\s+/)[0];
    if (lang === 'mermaid') {
      return '<div class="mermaid tex2jax_ignore">' + code + '</div>';
    }
    return defaultCodeRenderer(code, infostring, escaped);
  };
  marked.use({ renderer: mermaidRenderer });
}

if (window.mermaid) {
  mermaid.initialize({
    startOnLoad: false,
    theme: 'dark',
    securityLevel: 'loose',
  });
}

let mermaidCounter = 0;

async function renderMathAndDiagrams(rootEl) {
  if (!rootEl) return;
  if (window.mermaid) {
    const blocks = rootEl.querySelectorAll(".mermaid:not([data-processed='true'])");
    for (const el of blocks) {
      const src = el.textContent;
      const id = 'mermaid-svg-' + ++mermaidCounter;
      try {
        const { svg, bindFunctions } = await mermaid.render(id, src);
        el.innerHTML = svg;
        if (bindFunctions) bindFunctions(el);
        el.setAttribute('data-processed', 'true');
      } catch (err) {
        el.innerHTML =
          '<pre style="color:#f97583">Mermaid render error: ' +
          (err && err.message ? err.message : String(err)) +
          '</pre>';
        console.warn('Mermaid render failed:', err);
      }
    }
  }
  if (window.MathJax && window.MathJax.typesetPromise) {
    try {
      await window.MathJax.typesetPromise([rootEl]);
    } catch (err) {
      console.warn('MathJax typeset failed:', err);
    }
  }
}

/* ── Relative-link rewriting for embedded READMEs ──────────── */

function rewriteRelative(html, baseDir) {
  const tmp = document.createElement('div');
  tmp.innerHTML = html;
  tmp.querySelectorAll('img[src]').forEach((img) => {
    const src = img.getAttribute('src');
    if (!/^([a-z]+:)?\/\//i.test(src) && !src.startsWith('/') && !src.startsWith('data:')) {
      img.setAttribute('src', baseDir + src);
    }
    img.setAttribute('loading', 'lazy');
  });
  tmp.querySelectorAll('a[href]').forEach((a) => {
    const href = a.getAttribute('href');
    if (!/^([a-z]+:)?\/\//i.test(href) && !href.startsWith('#') && !href.startsWith('/')) {
      a.setAttribute('href', baseDir + href);
    }
    a.addEventListener('click', (e) => e.stopPropagation());
  });
  return tmp.innerHTML;
}

/* ── README loading + modal ────────────────────────────────── */

const readmeCache = new Map();

async function loadReadmes() {
  const cards = document.querySelectorAll('.featured-card[data-readme]');
  for (const card of cards) {
    const path = card.dataset.readme;
    const baseDir = path.substring(0, path.lastIndexOf('/') + 1);
    const target = card.querySelector('.readme-preview');
    try {
      const res = await fetch(path);
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const md = await res.text();
      const html = marked.parse(md);
      const rendered = rewriteRelative(html, baseDir);
      target.innerHTML = rendered;
      readmeCache.set(path, rendered);
      renderMathAndDiagrams(target);
    } catch (err) {
      target.innerHTML =
        '<p class="readme-loading">Description unavailable. Click to launch the lab.</p>';
      console.warn('Failed to load README:', path, err);
    }
  }
  // READMEs have finished loading (and thus changed page height). Let the
  // anchor-scroll fixer re-correct the viewport position.
  document.dispatchEvent(new CustomEvent('home-readmes-ready'));
}

loadReadmes();

const overlay = document.getElementById('modalOverlay');
const modalIcon = document.getElementById('modalIcon');
const modalTitle = document.getElementById('modalTitle');
const modalLaunch = document.getElementById('modalLaunch');
const modalBody = document.getElementById('modalBody');
const modalClose = document.getElementById('modalClose');

function openModal(card) {
  const href = card.dataset.href;
  const path = card.dataset.readme;
  const baseDir = path.substring(0, path.lastIndexOf('/') + 1);
  const icon = card.querySelector('.featured-card-icon')?.textContent || '';
  const title = card.querySelector('.featured-card-title')?.textContent || '';
  modalIcon.textContent = icon;
  modalTitle.textContent = title;
  modalLaunch.href = href;
  mountModalVideo(card, title);
  if (readmeCache.has(path)) {
    modalBody.innerHTML = readmeCache.get(path);
    renderMathAndDiagrams(modalBody);
  } else {
    modalBody.innerHTML = '<p class="readme-loading">Loading description…</p>';
    fetch(path)
      .then((r) => {
        if (!r.ok) throw new Error('HTTP ' + r.status);
        return r.text();
      })
      .then((md) => {
        const rendered = rewriteRelative(marked.parse(md), baseDir);
        readmeCache.set(path, rendered);
        modalBody.innerHTML = rendered;
        renderMathAndDiagrams(modalBody);
      })
      .catch((err) => {
        modalBody.innerHTML = '<p class="readme-loading">Description unavailable.</p>';
        console.warn('Failed to load README:', path, err);
      });
  }
  overlay.classList.add('open');
  overlay.setAttribute('aria-hidden', 'false');
  document.body.classList.add('modal-open');
  overlay.scrollTop = 0;
}

function closeModal() {
  overlay.classList.remove('open');
  overlay.setAttribute('aria-hidden', 'true');
  document.body.classList.remove('modal-open');
  clearModalVideo();
}
/* ── Modal video helpers ───────────────────────────────────── */
const modalMedia = document.getElementById('modalMedia');
function clearModalVideo() {
  if (!modalMedia) return;
  const v = modalMedia.querySelector('video');
  if (v) {
    v.pause();
  }
  modalMedia.innerHTML = '';
  modalMedia.classList.remove('is-playing');
}
function mountModalVideo(card, title) {
  if (!modalMedia) return;
  clearModalVideo();
  const src = card.dataset.video;
  if (!src) return;
  const video = document.createElement('video');
  video.className = 'featured-card-video';
  video.muted = true;
  video.loop = true;
  video.playsInline = true;
  video.controls = true;
  video.preload = 'metadata';
  video.setAttribute('aria-label', (title || 'demo') + ' — demonstration video');
  const source = document.createElement('source');
  source.src = src;
  source.type = 'video/mp4';
  video.appendChild(source);
  modalMedia.appendChild(video);
  const reduceMotion =
    window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  // Touch-friendly play button overlay
  const playBtn = document.createElement('button');
  playBtn.className = 'featured-card-play';
  playBtn.type = 'button';
  playBtn.setAttribute('aria-label', 'Play ' + (title || 'demo') + ' video');
  modalMedia.appendChild(playBtn);
  const togglePlay = () => {
    if (video.paused) {
      const p = video.play();
      if (p && typeof p.catch === 'function') p.catch(() => {});
    } else {
      video.pause();
    }
  };
  playBtn.addEventListener('click', togglePlay);
  video.addEventListener('play', () => modalMedia.classList.add('is-playing'));
  video.addEventListener('pause', () => modalMedia.classList.remove('is-playing'));
  if (!reduceMotion) {
    const p = video.play();
    if (p && typeof p.catch === 'function') p.catch(() => {});
  }
}

document.querySelectorAll('.featured-card[data-readme]').forEach((card) => {
  card.addEventListener('click', (e) => {
    if (e.target.closest('.featured-card-launch')) return;
    if (e.target.closest('a') && !e.target.closest('.featured-card-launch')) return;
    e.preventDefault();
    openModal(card);
  });
  card.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      openModal(card);
    }
  });
});

modalClose.addEventListener('click', closeModal);
overlay.addEventListener('click', (e) => {
  if (e.target === overlay) closeModal();
});
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape' && overlay.classList.contains('open')) closeModal();
});
/* ── Featured-card demo videos ─────────────────────────────── */
/* Lazily mount <video> elements for cards that declare a
    data-video source. Videos load when scrolled into view,
    play on hover / focus, and pause otherwise to save resources.
    Respects prefers-reduced-motion. */
(function initFeaturedVideos() {
  const reduceMotion =
    window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const cards = document.querySelectorAll('.featured-card[data-video]');
  if (!cards.length) return;
  function buildVideo(card) {
    const mount = card.querySelector('[data-video-mount]');
    if (!mount || mount.dataset.built === 'true') return null;
    const src = card.dataset.video;
    const title = card.querySelector('.featured-card-title')?.textContent || 'demo';
    const video = document.createElement('video');
    video.className = 'featured-card-video';
    video.muted = true;
    video.loop = true;
    video.playsInline = true;
    video.preload = 'metadata';
    video.setAttribute('aria-label', title + ' — demonstration video');
    video.setAttribute('tabindex', '-1');
    const source = document.createElement('source');
    source.src = src;
    source.type = 'video/mp4';
    video.appendChild(source);
    mount.appendChild(video);
    // Track play state so the overlay button can hide itself.
    video.addEventListener('play', () => mount.classList.add('is-playing'));
    video.addEventListener('pause', () => mount.classList.remove('is-playing'));
    // Touch-friendly play button overlay (works without hover).
    const playBtn = document.createElement('button');
    playBtn.className = 'featured-card-play';
    playBtn.type = 'button';
    playBtn.setAttribute('aria-label', 'Play ' + title + ' video');
    playBtn.addEventListener('click', (e) => {
      // Don't trigger the card's modal-open click handler.
      e.stopPropagation();
      e.preventDefault();
      if (video.paused) {
        safePlay(video);
      } else {
        video.pause();
      }
    });
    mount.appendChild(playBtn);
    mount.dataset.built = 'true';
    return video;
  }
  function safePlay(video) {
    if (!video || reduceMotion) return;
    const p = video.play();
    if (p && typeof p.catch === 'function') p.catch(() => {});
  }
  // Mount videos as cards enter the viewport.
  const io =
    'IntersectionObserver' in window
      ? new IntersectionObserver(
          (entries) => {
            for (const entry of entries) {
              if (entry.isIntersecting) {
                buildVideo(entry.target);
                io.unobserve(entry.target);
              }
            }
          },
          { rootMargin: '200px' }
        )
      : null;
  cards.forEach((card) => {
    if (io) {
      io.observe(card);
    } else {
      buildVideo(card);
    }
    const start = () => {
      const video = buildVideo(card) || card.querySelector('.featured-card-video');
      safePlay(video);
    };
    const stop = () => {
      const video = card.querySelector('.featured-card-video');
      if (video) {
        video.pause();
      }
    };
    card.addEventListener('mouseenter', start);
    card.addEventListener('mouseleave', stop);
    card.addEventListener('focusin', start);
    card.addEventListener('focusout', stop);
  });
})();
/* ── Section navigation: scroll-spy + back-to-top ──────────── */
(function initSectionNavigation() {
  const sections = Array.from(document.querySelectorAll('.section-title[id]'));
  const navLinks = Array.from(document.querySelectorAll('.nav-section-link'));
  const backToTop = document.getElementById('backToTop');
  // Map section id -> nav link for quick highlighting.
  const linkFor = new Map();
  navLinks.forEach((link) => {
    const id = (link.getAttribute('href') || '').replace(/^#/, '');
    if (id) linkFor.set(id, link);
  });
  // Smooth-scroll for section shortcut links (also works when
  // scroll-behavior isn't honored) and keeps the URL hash tidy.
  navLinks.forEach((link) => {
    link.addEventListener('click', (e) => {
      const id = (link.getAttribute('href') || '').replace(/^#/, '');
      const target = document.getElementById(id);
      if (!target) return;
      e.preventDefault();
      target.scrollIntoView({ behavior: 'smooth', block: 'start' });
      history.replaceState(null, '', '#' + id);
    });
  });
  // Highlight the nav link for whichever section is currently in view.
  if ('IntersectionObserver' in window && sections.length) {
    const spy = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return;
          const active = linkFor.get(entry.target.id);
          if (!active) return;
          navLinks.forEach((l) => l.classList.remove('is-active'));
          active.classList.add('is-active');
        });
      },
      { rootMargin: '-40% 0px -55% 0px', threshold: 0 }
    );
    sections.forEach((s) => spy.observe(s));
  }
  // Back-to-top visibility + action.
  if (backToTop) {
    const toggleBackToTop = () => {
      if (window.scrollY > window.innerHeight * 0.75) {
        backToTop.classList.add('is-visible');
      } else {
        backToTop.classList.remove('is-visible');
      }
    };
    window.addEventListener('scroll', toggleBackToTop, { passive: true });
    toggleBackToTop();
    backToTop.addEventListener('click', () => {
      window.scrollTo({ top: 0, behavior: 'smooth' });
      history.replaceState(null, '', window.location.pathname);
    });
  }
})();
/* ── Correct anchor scroll after dynamic grids are built ───── */
/* The featured/essays/demos/games grids are populated asynchronously,
   so a hash present on initial page load (e.g. #essays) is resolved by
   the browser before those cards exist — leaving the viewport parked at
   the wrong offset. Re-run the scroll once the DOM is complete. */
(function initAnchorScrollFix() {
  function scrollToHash(smooth) {
    const id = decodeURIComponent((window.location.hash || '').replace(/^#/, ''));
    if (!id) return;
    const target = document.getElementById(id);
    if (!target) return;
    target.scrollIntoView({ behavior: smooth ? 'smooth' : 'auto', block: 'start' });
  }
  if (!window.location.hash) return;
  const correct = () => requestAnimationFrame(() => scrollToHash(false));
  // 1) Grids may already be built (home.js is normally loaded *after* the
  //    grids are populated and the 'home-grids-ready' event has fired, so
  //    a plain event listener would miss it — check the flag too).
  if (window.__homeGridsReady) {
    correct();
  }
  document.addEventListener('home-grids-ready', correct, { once: true });
  // 2) README markdown loads asynchronously and changes page height, which
  //    invalidates the offset computed above — re-correct once it settles.
  document.addEventListener('home-readmes-ready', correct, { once: true });
  // 3) Final safety net: after images/videos have loaded and shifted layout.
  window.addEventListener('load', () => correct(), { once: true });
})();
