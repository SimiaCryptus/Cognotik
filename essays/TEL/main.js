// ============================================================
// EXTENSION LADDER — INTERACTIVE JAVASCRIPT
// ============================================================

(function () {
  'use strict';

  // ============================================================
  // UTILITY HELPERS
  // ============================================================

  /**
   * Throttle a function to run at most once per `limit` ms.
   */
  function throttle(fn, limit) {
    let lastCall = 0;
    return function (...args) {
      const now = Date.now();
      if (now - lastCall >= limit) {
        lastCall = now;
        fn.apply(this, args);
      }
    };
  }

  /**
   * Debounce a function — only fires after `delay` ms of silence.
   */
  function debounce(fn, delay) {
    let timer;
    return function (...args) {
      clearTimeout(timer);
      timer = setTimeout(() => fn.apply(this, args), delay);
    };
  }

  // ============================================================
  // 1. MOBILE NAVIGATION TOGGLE
  // ============================================================

  function initMobileNav() {
    const toggle = document.querySelector('.nav-toggle');
    const navMenu = document.querySelector('.nav-menu');
    const siteHeader = document.querySelector('.site-header');

    if (!toggle || !navMenu) return;

    toggle.addEventListener('click', () => {
      const isExpanded = toggle.getAttribute('aria-expanded') === 'true';
      toggle.setAttribute('aria-expanded', String(!isExpanded));
      navMenu.classList.toggle('nav-menu--open', !isExpanded);
      toggle.classList.toggle('nav-toggle--active', !isExpanded);
      siteHeader.classList.toggle('site-header--nav-open', !isExpanded);
    });

    // Close menu when a nav link is clicked (mobile UX)
    navMenu.querySelectorAll('.nav-link').forEach((link) => {
      link.addEventListener('click', () => {
        toggle.setAttribute('aria-expanded', 'false');
        navMenu.classList.remove('nav-menu--open');
        toggle.classList.remove('nav-toggle--active');
        siteHeader.classList.remove('site-header--nav-open');
      });
    });

    // Close menu on outside click
    document.addEventListener('click', (e) => {
      if (!siteHeader.contains(e.target)) {
        toggle.setAttribute('aria-expanded', 'false');
        navMenu.classList.remove('nav-menu--open');
        toggle.classList.remove('nav-toggle--active');
        siteHeader.classList.remove('site-header--nav-open');
      }
    });
  }

  // ============================================================
  // 2. STICKY HEADER + SCROLL SHADOW
  // ============================================================

  function initStickyHeader() {
    const header = document.querySelector('.site-header');
    if (!header) return;

    const onScroll = throttle(() => {
      if (window.scrollY > 10) {
        header.classList.add('site-header--scrolled');
      } else {
        header.classList.remove('site-header--scrolled');
      }
    }, 100);

    window.addEventListener('scroll', onScroll, { passive: true });
    onScroll(); // run once on init
  }

  // ============================================================
  // 3. ACTIVE NAV LINK — INTERSECTION OBSERVER
  // ============================================================

  function initActiveNavHighlight() {
    const sections = document.querySelectorAll('.article-section, .hero-section');
    const navLinks = document.querySelectorAll('.nav-link');

    if (!sections.length || !navLinks.length) return;

    // Build a map: section id → nav link
    const linkMap = new Map();
    navLinks.forEach((link) => {
      const href = link.getAttribute('href');
      if (href && href.startsWith('#')) {
        linkMap.set(href.slice(1), link);
      }
    });

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            // Remove active from all
            navLinks.forEach((l) => l.classList.remove('nav-link--active'));
            const activeLink = linkMap.get(entry.target.id);
            if (activeLink) activeLink.classList.add('nav-link--active');
          }
        });
      },
      {
        rootMargin: '-20% 0px -70% 0px',
        threshold: 0,
      }
    );

    sections.forEach((section) => {
      if (section.id) observer.observe(section);
    });
  }

  // ============================================================
  // 4. BACK-TO-TOP BUTTON
  // ============================================================

  function initBackToTop() {
    const btn = document.querySelector('.back-to-top');
    if (!btn) return;

    const onScroll = throttle(() => {
      if (window.scrollY > 400) {
        btn.hidden = false;
        btn.classList.add('back-to-top--visible');
      } else {
        btn.classList.remove('back-to-top--visible');
        // Delay hiding so CSS transition can play
        setTimeout(() => {
          if (!btn.classList.contains('back-to-top--visible')) {
            btn.hidden = true;
          }
        }, 300);
      }
    }, 150);

    window.addEventListener('scroll', onScroll, { passive: true });

    btn.addEventListener('click', () => {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    });
  }

  // ============================================================
  // 5. SMOOTH SCROLL FOR ANCHOR LINKS
  // ============================================================

  function initSmoothScroll() {
    document.querySelectorAll('a[href^="#"]').forEach((anchor) => {
      anchor.addEventListener('click', (e) => {
        const targetId = anchor.getAttribute('href').slice(1);
        const target = document.getElementById(targetId);
        if (!target) return;

        e.preventDefault();

        const headerHeight = document.querySelector('.site-header')?.offsetHeight || 0;
        const targetTop = target.getBoundingClientRect().top + window.scrollY - headerHeight - 16;

        window.scrollTo({ top: targetTop, behavior: 'smooth' });

        // Update URL without triggering scroll
        history.pushState(null, '', `#${targetId}`);

        // Move focus for accessibility
        target.setAttribute('tabindex', '-1');
        target.focus({ preventScroll: true });
      });
    });
  }

  // ============================================================
  // 6. SCROLL-REVEAL ANIMATIONS — INTERSECTION OBSERVER
  // ============================================================

  function initScrollReveal() {
    const revealTargets = document.querySelectorAll(
      '.article-section, .mechanism-step, .ladder-level, .operator-card, ' +
        '.pattern-example-card, .philosophy-reading, .comparison-table-wrapper, ' +
        '.pull-quote, .summary-statement, .inline-aside, .article-caveat'
    );

    if (!revealTargets.length) return;

    // Add initial hidden state via JS (so CSS-only users still see content)
    revealTargets.forEach((el, i) => {
      el.classList.add('reveal');
      // Stagger children within groups
      el.style.setProperty('--reveal-delay', `${(i % 5) * 60}ms`);
    });

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('reveal--visible');
            observer.unobserve(entry.target);
          }
        });
      },
      { rootMargin: '0px 0px -60px 0px', threshold: 0.08 }
    );

    revealTargets.forEach((el) => observer.observe(el));
  }

  // ============================================================
  // 7. LADDER LEVEL INTERACTIVE EXPAND / COLLAPSE
  // ============================================================

  function initLadderInteraction() {
    const levels = document.querySelectorAll('.ladder-level');
    if (!levels.length) return;

    levels.forEach((level) => {
      const header = level.querySelector('.ladder-level-header');
      const description = level.querySelector('.ladder-level-description');
      const tags = level.querySelector('.ladder-level-tags');
      const note = level.querySelector('.ladder-level-note');
      const milestone = level.querySelector('.ladder-level-milestone');

      if (!header) return;

      // Make header keyboard-focusable
      header.setAttribute('role', 'button');
      header.setAttribute('tabindex', '0');
      header.setAttribute('aria-expanded', 'true');

      // Wrap collapsible content
      const collapsible = document.createElement('div');
      collapsible.classList.add('ladder-level-collapsible');
      [description, tags, note, milestone].forEach((el) => {
        if (el) collapsible.appendChild(el);
      });
      level.querySelector('.ladder-level-content').appendChild(collapsible);

      const toggle = () => {
        const isExpanded = header.getAttribute('aria-expanded') === 'true';
        header.setAttribute('aria-expanded', String(!isExpanded));
        level.classList.toggle('ladder-level--collapsed', isExpanded);
        collapsible.classList.toggle('ladder-level-collapsible--hidden', isExpanded);
      };

      header.addEventListener('click', toggle);
      header.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          toggle();
        }
      });

      // Hover highlight
      level.addEventListener('mouseenter', () => level.classList.add('ladder-level--hovered'));
      level.addEventListener('mouseleave', () => level.classList.remove('ladder-level--hovered'));
    });
  }

  // ============================================================
  // 8. MECHANISM STEPS — SEQUENTIAL HIGHLIGHT ON SCROLL
  // ============================================================

  function initMechanismSteps() {
    const steps = document.querySelectorAll('.mechanism-step');
    if (!steps.length) return;

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            const stepIndex = parseInt(entry.target.dataset.step, 10) - 1;
            // Stagger the highlight
            setTimeout(() => {
              entry.target.classList.add('mechanism-step--active');
            }, stepIndex * 200);
            observer.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.5 }
    );

    steps.forEach((step) => observer.observe(step));
  }

  // ============================================================
  // 9. PHILOSOPHY READINGS — ACCORDION / TOGGLE
  // ============================================================

  function initPhilosophyReadings() {
    const readings = document.querySelectorAll('.philosophy-reading');
    if (!readings.length) return;

    readings.forEach((reading) => {
      const title = reading.querySelector('.philosophy-reading-title');
      const text = reading.querySelector('.philosophy-reading-text');
      if (!title || !text) return;

      title.setAttribute('role', 'button');
      title.setAttribute('tabindex', '0');
      title.setAttribute('aria-expanded', 'false');

      // Start collapsed
      reading.classList.add('philosophy-reading--collapsed');

      const toggle = () => {
        const isExpanded = title.getAttribute('aria-expanded') === 'true';

        // Close all others
        readings.forEach((r) => {
          const t = r.querySelector('.philosophy-reading-title');
          if (t && t !== title) {
            t.setAttribute('aria-expanded', 'false');
            r.classList.add('philosophy-reading--collapsed');
          }
        });

        title.setAttribute('aria-expanded', String(!isExpanded));
        reading.classList.toggle('philosophy-reading--collapsed', isExpanded);
      };

      title.addEventListener('click', toggle);
      title.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          toggle();
        }
      });
    });
  }

  // ============================================================
  // 10. HERO LADDER MOTIF — STAGGERED ENTRANCE ANIMATION
  // ============================================================

  function initHeroLadder() {
    const rungs = document.querySelectorAll('.ladder-rung');
    if (!rungs.length) return;

    // Animate rungs in from bottom to top with stagger
    rungs.forEach((rung, i) => {
      rung.style.opacity = '0';
      rung.style.transform = 'translateY(20px)';
      rung.style.transition = `opacity 0.5s ease ${i * 120}ms, transform 0.5s ease ${i * 120}ms`;

      setTimeout(
        () => {
          rung.style.opacity = '';
          rung.style.transform = '';
        },
        300 + i * 120
      );
    });

    // Pulse on hover
    rungs.forEach((rung) => {
      rung.addEventListener('mouseenter', () => {
        rung.classList.add('ladder-rung--pulse');
      });
      rung.addEventListener('animationend', () => {
        rung.classList.remove('ladder-rung--pulse');
      });
    });
  }

  // ============================================================
  // 11. READING PROGRESS BAR
  // ============================================================

  function initReadingProgress() {
    // Create the progress bar element
    const bar = document.createElement('div');
    bar.classList.add('reading-progress-bar');
    bar.setAttribute('role', 'progressbar');
    bar.setAttribute('aria-label', 'Reading progress');
    bar.setAttribute('aria-valuemin', '0');
    bar.setAttribute('aria-valuemax', '100');
    bar.setAttribute('aria-valuenow', '0');
    document.body.prepend(bar);

    const article = document.querySelector('.article-main');
    if (!article) return;

    const onScroll = throttle(() => {
      const articleTop = article.offsetTop;
      const articleHeight = article.offsetHeight;
      const windowHeight = window.innerHeight;
      const scrolled = window.scrollY;

      const progress = Math.min(
        100,
        Math.max(0, ((scrolled - articleTop + windowHeight * 0.5) / articleHeight) * 100)
      );

      bar.style.width = `${progress}%`;
      bar.setAttribute('aria-valuenow', Math.round(progress));
    }, 50);

    window.addEventListener('scroll', onScroll, { passive: true });
    onScroll();
  }

  // ============================================================
  // 12. COMPARISON TABLE — ROW HIGHLIGHT ON HOVER + JUMP ROW BADGE
  // ============================================================

  function initComparisonTable() {
    const rows = document.querySelectorAll('.comparison-table-row');
    if (!rows.length) return;

    rows.forEach((row) => {
      row.addEventListener('mouseenter', () =>
        row.classList.add('comparison-table-row--highlighted')
      );
      row.addEventListener('mouseleave', () =>
        row.classList.remove('comparison-table-row--highlighted')
      );
    });

    // Animate the "jump" row (ℝ — uncountable) with a special entrance
    const jumpRow = document.querySelector('.comparison-table-row--jump');
    if (!jumpRow) return;

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            jumpRow.classList.add('comparison-table-row--jump-animate');
            observer.unobserve(jumpRow);
          }
        });
      },
      { threshold: 0.8 }
    );
    observer.observe(jumpRow);
  }

  // ============================================================
  // 13. OPERATOR CARDS — FLIP / FOCUS INTERACTION
  // ============================================================

  function initOperatorCards() {
    const cards = document.querySelectorAll('.operator-card');
    if (!cards.length) return;

    cards.forEach((card) => {
      card.setAttribute('tabindex', '0');

      card.addEventListener('mouseenter', () => card.classList.add('operator-card--focused'));
      card.addEventListener('mouseleave', () => card.classList.remove('operator-card--focused'));
      card.addEventListener('focus', () => card.classList.add('operator-card--focused'));
      card.addEventListener('blur', () => card.classList.remove('operator-card--focused'));
    });
  }

  // ============================================================
  // 14. PATTERN EXAMPLE CARDS — STAGGER ON SCROLL
  // ============================================================

  function initPatternCards() {
    const cards = document.querySelectorAll('.pattern-example-card');
    if (!cards.length) return;

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry, i) => {
          if (entry.isIntersecting) {
            setTimeout(() => {
              entry.target.classList.add('pattern-example-card--visible');
            }, i * 150);
            observer.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.2 }
    );

    cards.forEach((card) => {
      card.classList.add('pattern-example-card--hidden');
      observer.observe(card);
    });
  }

  // ============================================================
  // 15. ACCESSIBILITY SPECTRUM BAR — ANIMATE WIDTHS ON SCROLL
  // ============================================================

  function initAccessibilitySpectrum() {
    const segments = document.querySelectorAll('.accessibility-segment');
    if (!segments.length) return;

    // Store target widths and start at 0
    segments.forEach((seg) => {
      const targetWidth = seg.style.getPropertyValue('--segment-width') || '33%';
      seg.dataset.targetWidth = targetWidth;
      seg.style.setProperty('--segment-width', '0%');
    });

    const bar = document.querySelector('.accessibility-spectrum-bar');
    if (!bar) return;

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            segments.forEach((seg, i) => {
              setTimeout(() => {
                seg.style.setProperty('--segment-width', seg.dataset.targetWidth);
                seg.classList.add('accessibility-segment--animated');
              }, i * 200);
            });
            observer.unobserve(bar);
          }
        });
      },
      { threshold: 0.5 }
    );

    observer.observe(bar);
  }

  // ============================================================
  // 16. PULL QUOTE — HIGHLIGHT ON SCROLL
  // ============================================================

  function initPullQuote() {
    const quote = document.querySelector('.pull-quote');
    if (!quote) return;

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            quote.classList.add('pull-quote--visible');
            observer.unobserve(quote);
          }
        });
      },
      { threshold: 0.6 }
    );

    observer.observe(quote);
  }

  // ============================================================
  // 17. SUMMARY STATEMENT — TYPEWRITER-STYLE REVEAL
  // ============================================================

  function initSummaryStatement() {
    const statement = document.querySelector('.summary-statement');
    if (!statement) return;

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            statement.classList.add('summary-statement--visible');
            observer.unobserve(statement);
          }
        });
      },
      { threshold: 0.5 }
    );

    observer.observe(statement);
  }

  // ============================================================
  // 18. KEYBOARD NAVIGATION ENHANCEMENTS
  // ============================================================

  function initKeyboardEnhancements() {
    // Allow Escape to close mobile nav
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') {
        const toggle = document.querySelector('.nav-toggle');
        const navMenu = document.querySelector('.nav-menu');
        if (toggle && navMenu && navMenu.classList.contains('nav-menu--open')) {
          toggle.setAttribute('aria-expanded', 'false');
          navMenu.classList.remove('nav-menu--open');
          toggle.classList.remove('nav-toggle--active');
          toggle.focus();
        }
      }
    });

    // Skip-to-content link (create if not present)
    if (!document.querySelector('.skip-link')) {
      const skip = document.createElement('a');
      skip.href = '#main-content';
      skip.classList.add('skip-link');
      skip.textContent = 'Skip to main content';
      document.body.prepend(skip);
    }
  }

  // ============================================================
  // 19. CONCEPT LIST — TOOLTIP / EXPAND ON CLICK
  // ============================================================

  function initConceptList() {
    const items = document.querySelectorAll('.concept-item');
    if (!items.length) return;

    items.forEach((item) => {
      item.setAttribute('tabindex', '0');
      item.setAttribute('role', 'button');
      item.setAttribute('aria-expanded', 'false');

      const desc = item.querySelector('.concept-description');
      if (desc) {
        desc.classList.add('concept-description--collapsible');
      }

      const toggle = () => {
        const isExpanded = item.getAttribute('aria-expanded') === 'true';
        item.setAttribute('aria-expanded', String(!isExpanded));
        item.classList.toggle('concept-item--expanded', !isExpanded);
      };

      item.addEventListener('click', toggle);
      item.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          toggle();
        }
      });
    });
  }

  // ============================================================
  // 20. ESTIMATED READING TIME — DYNAMIC CALCULATION
  // ============================================================

  function initReadingTime() {
    const readingTimeEl = document.querySelector('.hero-meta-reading-time');
    const article = document.querySelector('.article-body');
    if (!readingTimeEl || !article) return;

    const text = article.innerText || article.textContent || '';
    const wordCount = text.trim().split(/\s+/).length;
    const wordsPerMinute = 220;
    const minutes = Math.ceil(wordCount / wordsPerMinute);

    readingTimeEl.textContent = `~${minutes} min read`;
  }

  // ============================================================
  // 21. LADDER CONNECTOR LABELS — ANIMATE ON SCROLL
  // ============================================================

  function initLadderConnectors() {
    const connectors = document.querySelectorAll('.ladder-connector');
    if (!connectors.length) return;

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('ladder-connector--visible');
            observer.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.5 }
    );

    connectors.forEach((c) => observer.observe(c));
  }

  // ============================================================
  // 22. SECTION HEADING — COPY ANCHOR LINK ON CLICK
  // ============================================================

  function initHeadingAnchors() {
    const headings = document.querySelectorAll('.section-heading[id], h2[id], h3[id]');

    headings.forEach((heading) => {
      const id = heading.id;
      if (!id) return;

      const anchor = document.createElement('a');
      anchor.href = `#${id}`;
      anchor.classList.add('heading-anchor');
      anchor.setAttribute('aria-label', `Link to section: ${heading.textContent.trim()}`);
      anchor.innerHTML = '<span aria-hidden="true">#</span>';

      heading.appendChild(anchor);

      anchor.addEventListener('click', (e) => {
        e.preventDefault();
        const url = `${window.location.origin}${window.location.pathname}#${id}`;
        if (navigator.clipboard) {
          navigator.clipboard.writeText(url).then(() => {
            showToast('Link copied to clipboard!');
          });
        }
        history.pushState(null, '', `#${id}`);
        heading.scrollIntoView({ behavior: 'smooth', block: 'start' });
      });
    });
  }

  // ============================================================
  // 23. TOAST NOTIFICATION
  // ============================================================

  function showToast(message, duration = 2500) {
    let toast = document.querySelector('.toast-notification');
    if (!toast) {
      toast = document.createElement('div');
      toast.classList.add('toast-notification');
      toast.setAttribute('role', 'status');
      toast.setAttribute('aria-live', 'polite');
      document.body.appendChild(toast);
    }

    toast.textContent = message;
    toast.classList.add('toast-notification--visible');

    clearTimeout(toast._hideTimer);
    toast._hideTimer = setTimeout(() => {
      toast.classList.remove('toast-notification--visible');
    }, duration);
  }

  // ============================================================
  // 24. HERO SECTION — PARALLAX SCROLL EFFECT (subtle)
  // ============================================================

  function initHeroParallax() {
    const hero = document.querySelector('.hero-section');
    const motif = document.querySelector('.hero-ladder-motif');
    if (!hero || !motif) return;

    // Only on non-reduced-motion devices
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

    const onScroll = throttle(() => {
      const scrolled = window.scrollY;
      const heroHeight = hero.offsetHeight;
      if (scrolled > heroHeight) return;

      const offset = scrolled * 0.3;
      motif.style.transform = `translateY(${offset}px)`;
    }, 16);

    window.addEventListener('scroll', onScroll, { passive: true });
  }

  // ============================================================
  // 25. INLINE ASIDES — TOGGLE EXPAND ON MOBILE
  // ============================================================

  function initInlineAsides() {
    const asides = document.querySelectorAll('.inline-aside');
    if (!asides.length) return;

    asides.forEach((aside) => {
      const heading = aside.querySelector('.inline-aside-heading');
      const text = aside.querySelector('.inline-aside-text');
      if (!heading || !text) return;

      heading.setAttribute('role', 'button');
      heading.setAttribute('tabindex', '0');
      heading.setAttribute('aria-expanded', 'true');

      const toggle = () => {
        const isExpanded = heading.getAttribute('aria-expanded') === 'true';
        heading.setAttribute('aria-expanded', String(!isExpanded));
        aside.classList.toggle('inline-aside--collapsed', isExpanded);
      };

      heading.addEventListener('click', toggle);
      heading.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          toggle();
        }
      });
    });
  }

  // ============================================================
  // INIT — Run all modules on DOMContentLoaded
  // ============================================================

  function init() {
    initMobileNav();
    initStickyHeader();
    initActiveNavHighlight();
    initBackToTop();
    initSmoothScroll();
    initScrollReveal();
    initLadderInteraction();
    initMechanismSteps();
    initPhilosophyReadings();
    initHeroLadder();
    initReadingProgress();
    initComparisonTable();
    initOperatorCards();
    initPatternCards();
    initAccessibilitySpectrum();
    initPullQuote();
    initSummaryStatement();
    initKeyboardEnhancements();
    initConceptList();
    initReadingTime();
    initLadderConnectors();
    initHeadingAnchors();
    initHeroParallax();
    initInlineAsides();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
