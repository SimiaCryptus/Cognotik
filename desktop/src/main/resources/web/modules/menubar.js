/**
 * Site-wide Menubar Component
 *
 * Renders a reusable top-bar with logo, a consolidated "Appearance"
 * (look & feel) popup containing the palette, theme and layout selectors,
 * and configurable action buttons.
 *
 * The menubar is integrated with the central theme system:
 *   - If ThemeManager / the themes.js manifest are not loaded yet, they are
 *     loaded automatically (see `autoLoadTheme`, `themeManifestSrc`,
 *     `themeManagerSrc`).
 *   - The palette and mode selectors are bound to ThemeManager.
 *   - The Appearance button shows a swatch of the active palette.
 *   - Theme changes made in other windows/iframes are followed live
 *     (see `syncThemeAcrossWindows`).
 *
 * Usage:
 *   <div id="menubar-container"></div>
 *   <script src="/themes.js"></script>          (optional – auto-loaded)
 *   <script src="/modules/theme.js"></script>   (optional – auto-loaded)
 *   <script src="/modules/menubar.js"></script>
 *   Menubar.render('#menubar-container', {
 *       title: 'Cognotik',
 *       showLayoutSelector: true,
 *       buttons: [
 *           { id: 'settings-btn', icon: '⚙️', label: 'Settings', onClick: () => {...} },
 *           ...
 *       ]
 *   });
 */
(function (global) {
    'use strict';

    function escapeHtml(s) {
        return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
            return {
                '&': '&amp;', '<': '&lt;', '>': '&gt;',
                '"': '&quot;', "'": '&#39;'
            }[c];
        });
    }

    const DEFAULT_OPTIONS = {
        logoSrc: 'logo.svg',
        logoAlt: 'Cognotik Logo',
        title: 'Cognotik',
        titleClickable: true,
        titleAriaLabel: 'About Cognotik',
        titleHref: null, // if set, title becomes a link
        showLayoutSelector: false,
        layoutOptions: [
            { value: 'grid', label: '▦ Grid' },
            { value: 'compact', label: '▪ Compact' },
            { value: 'list', label: '☰ List' },
            { value: 'masonry', label: '▤ Masonry' }
        ],
        showThemeSelector: true,
        // Palette selector (Point-CAD, Sepia, Solarized, Nord, …).
        // Only kept when the themes.js manifest is (or becomes) available.
        showPaletteSelector: true,
        // Show a swatch of the active palette on the Appearance button.
        showPaletteSwatch: true,
        // Group layout / palette / theme selectors into a single
        // "Appearance" popup. Set to false to render them inline.
        appearanceMenu: true,
        appearanceLabel: 'Appearance',
        appearanceIcon: '🎨',
        appearanceTitle: 'Look & Feel',
        // Central theme integration
        autoLoadTheme: true,
        themeManifestSrc: '/themes.js',
        themeManagerSrc: '/modules/theme.js',
        syncThemeAcrossWindows: true,
        buttons: []
    };

    // ------------------------------------------------------------------
    // Central theme system integration
    // ------------------------------------------------------------------

    let themeReadyPromise = null;
    let themeListenersInstalled = false;
    let storageSyncInstalled = false;
    let indicatorUpdateScheduled = false;

    function hasPaletteManifest() {
        return !!(global.ThemeManager &&
            typeof global.ThemeManager.getManifest === 'function' &&
            global.ThemeManager.getManifest());
    }

    function loadScript(src) {
        return new Promise(function (resolve, reject) {
            const existing = document.querySelector('script[data-menubar-src="' + src + '"]');
            if (existing) {
                if (existing.__loaded) { resolve(); return; }
                existing.addEventListener('load', function () { resolve(); });
                existing.addEventListener('error', function () { reject(new Error('Failed to load ' + src)); });
                return;
            }
            const s = document.createElement('script');
            s.src = src;
            s.async = false; // preserve order: manifest before manager
            s.setAttribute('data-menubar-src', src);
            s.addEventListener('load', function () { s.__loaded = true; resolve(); });
            s.addEventListener('error', function () { reject(new Error('Failed to load ' + src)); });
            (document.head || document.documentElement).appendChild(s);
        });
    }

    /**
     * Resolves with ThemeManager (or null) once the central theme system is
     * available, loading the manifest and manager scripts when necessary.
     */
    function ensureThemeSystem(options) {
        if (global.ThemeManager && hasPaletteManifest()) {
            return Promise.resolve(global.ThemeManager);
        }
        if (options.autoLoadTheme === false) {
            return Promise.resolve(global.ThemeManager || null);
        }
        if (!themeReadyPromise) {
            const managerWasLoaded = !!global.ThemeManager;
            const manifestStep = global.CognotikThemes
                ? Promise.resolve()
                : loadScript(options.themeManifestSrc).catch(function (e) {
                    console.warn('[Menubar] Theme manifest unavailable:', e.message);
                });
            themeReadyPromise = manifestStep
                .then(function () {
                    if (!global.ThemeManager) return loadScript(options.themeManagerSrc);
                    // Manager ran before the manifest existed: re-apply so the
                    // stored palette takes effect.
                    if (managerWasLoaded && global.CognotikThemes &&
                        typeof global.ThemeManager.init === 'function') {
                        global.ThemeManager.init();
                    }
                })
                .then(function () { return global.ThemeManager || null; })
                .catch(function (e) {
                    console.warn('[Menubar] Theme manager unavailable:', e.message);
                    return global.ThemeManager || null;
                });
        }
        return themeReadyPromise;
    }

    function getActivePalette() {
        const TM = global.ThemeManager;
        if (!TM || !hasPaletteManifest()) return null;
        const manifest = TM.getManifest();
        if (!manifest || !Array.isArray(manifest.themes)) return null;
        const id = typeof TM.getCurrentPalette === 'function'
            ? TM.getCurrentPalette()
            : document.documentElement.getAttribute('data-palette');
        for (let i = 0; i < manifest.themes.length; i++) {
            if (manifest.themes[i].id === id) return manifest.themes[i];
        }
        return null;
    }

    function updateAppearanceIndicators() {
        indicatorUpdateScheduled = false;
        const root = document.documentElement;
        const scheme = root.getAttribute('data-scheme') === 'dark' ? 'dark' : 'light';
        const mode = (global.ThemeManager && typeof global.ThemeManager.getCurrentTheme === 'function')
            ? global.ThemeManager.getCurrentTheme()
            : (root.getAttribute('data-mode') || '');
        const palette = getActivePalette();
        const preview = palette && palette.preview
            ? (palette.preview[scheme] || palette.preview.light || palette.preview.dark)
            : null;

        document.querySelectorAll('.appearance-swatch').forEach(function (sw) {
            if (preview && preview.canvas && preview.brand) {
                sw.style.background = 'linear-gradient(135deg, ' +
                    preview.canvas + ' 0 50%, ' + preview.brand + ' 50% 100%)';
                sw.style.display = 'inline-block';
            } else {
                sw.style.display = 'none';
            }
        });
        document.querySelectorAll('.appearance-btn').forEach(function (btn) {
            const base = btn.getAttribute('data-base-title') || '';
            const parts = [];
            if (palette) parts.push(palette.name);
            if (mode) parts.push(mode);
            btn.title = parts.length ? base + ' — ' + parts.join(' · ') : base;
        });
    }

    function scheduleIndicatorUpdate() {
        if (indicatorUpdateScheduled) return;
        indicatorUpdateScheduled = true;
        // Defer so ThemeManager has finished updating <html> attributes.
        setTimeout(updateAppearanceIndicators, 0);
    }

    function installThemeListeners(options) {
        const TM = global.ThemeManager;
        if (!TM) return;
        if (!themeListenersInstalled) {
            themeListenersInstalled = true;
            if (typeof TM.onPaletteChange === 'function') TM.onPaletteChange(scheduleIndicatorUpdate);
            if (typeof TM.onChange === 'function') TM.onChange(scheduleIndicatorUpdate);
            if (typeof global.matchMedia === 'function') {
                const mq = global.matchMedia('(prefers-color-scheme: dark)');
                if (mq.addEventListener) mq.addEventListener('change', scheduleIndicatorUpdate);
                else if (mq.addListener) mq.addListener(scheduleIndicatorUpdate);
            }
        }
        if (options.syncThemeAcrossWindows !== false && !storageSyncInstalled) {
            storageSyncInstalled = true;
            global.addEventListener('storage', function (e) {
                const tm = global.ThemeManager;
                if (!tm || !e.newValue) return;
                if (e.key === tm.PALETTE_STORAGE_KEY && typeof tm.setPalette === 'function' &&
                    e.newValue !== tm.getCurrentPalette()) {
                    tm.setPalette(e.newValue);
                }
                if (e.key === tm.STORAGE_KEY && typeof tm.setTheme === 'function' &&
                    e.newValue !== tm.getCurrentTheme()) {
                    tm.setTheme(e.newValue);
                }
            });
        }
    }

    function removeAppearanceControl(target, el) {
        if (!el) return;
        const field = el.closest('.appearance-field');
        (field || el).remove();
        const menu = target.querySelector('.appearance-menu');
        if (menu && !menu.querySelector('select')) {
            if (typeof target.__menubarCleanup === 'function') {
                target.__menubarCleanup();
                target.__menubarCleanup = null;
            }
            menu.remove();
        }
    }

    function bindThemeControls(target, options) {
        const TM = global.ThemeManager;
        const tsel = target.querySelector('#theme-selector');
        const psel = target.querySelector('#palette-selector');

        if (tsel) {
            if (TM && typeof TM.bindSelector === 'function') {
                if (!tsel.__themeBound) {
                    TM.bindSelector(tsel);
                    tsel.__themeBound = true;
                }
            } else {
                removeAppearanceControl(target, tsel);
            }
        }
        if (psel) {
            if (TM && hasPaletteManifest() && typeof TM.bindPaletteSelector === 'function') {
                if (!psel.__themeBound) {
                    TM.bindPaletteSelector(psel);
                    psel.__themeBound = true;
                }
            } else {
                removeAppearanceControl(target, psel);
            }
        }
        installThemeListeners(options);
        updateAppearanceIndicators();
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    function renderButton(btn) {
        const label = btn.label || '';
        const icon = btn.icon || '';
        const idAttr = btn.id ? ' id="' + escapeHtml(btn.id) + '"' : '';
        const titleAttr = btn.title ? ' title="' + escapeHtml(btn.title) + '"' : '';
        const ariaAttr = btn.ariaLabel ? ' aria-label="' + escapeHtml(btn.ariaLabel) + '"' : '';
        const iconHtml = icon
            ? '<span class="btn-icon" aria-hidden="true">' + escapeHtml(icon) + '</span> '
            : '';
        const labelSpan = btn.labelId
            ? '<span id="' + escapeHtml(btn.labelId) + '">' + escapeHtml(label) + '</span>'
            : escapeHtml(label);
        return '<button class="top-bar-btn" type="button"' + idAttr + titleAttr + ariaAttr + '>' +
            iconHtml + labelSpan + '</button>';
    }

    function renderLayoutSelector(options) {
        const opts = (options.layoutOptions || []).map(function (o) {
            return '<option value="' + escapeHtml(o.value) + '">' + escapeHtml(o.label) + '</option>';
        }).join('');
        return '<select class="theme-selector" id="layout-selector" aria-label="Select layout" title="Select layout style">' +
            opts + '</select>';
    }

    function renderThemeSelector() {
        return '<select class="theme-selector" id="theme-selector" aria-label="Select theme" title="Select theme">' +
            '<option value="auto">🖥️ Auto</option>' +
            '<option value="light">☀️ Light</option>' +
            '<option value="dark">🌙 Dark</option>' +
            '</select>';
    }

    function renderPaletteSelector() {
        // Options are populated by ThemeManager.bindPaletteSelector from the manifest.
        return '<select class="theme-selector" id="palette-selector" aria-label="Select colour palette"' +
            ' title="Select colour palette"></select>';
    }

    function renderAppearanceField(labelText, controlHtml) {
        if (!controlHtml) return '';
        return '<label class="appearance-field">' +
            '<span class="appearance-field-label">' + escapeHtml(labelText) + '</span>' +
            controlHtml +
            '</label>';
    }

    function renderSwatch(options) {
        if (options.showPaletteSwatch === false) return '';
        return '<span class="appearance-swatch" aria-hidden="true" style="display:none;' +
            'width:0.9em;height:0.9em;border-radius:50%;vertical-align:middle;margin-left:0.35em;' +
            'border:1px solid var(--color-border-strong, #999);"></span>';
    }

    function renderAppearanceMenu(options, paletteHtml, themeHtml, layoutHtml) {
        return '<div class="appearance-menu">' +
            '<button class="top-bar-btn appearance-btn" type="button" id="appearance-btn"' +
                ' aria-haspopup="dialog" aria-expanded="false" aria-controls="appearance-popup"' +
                ' data-base-title="' + escapeHtml(options.appearanceTitle) + '"' +
                ' title="' + escapeHtml(options.appearanceTitle) + '">' +
                '<span class="btn-icon" aria-hidden="true">' + escapeHtml(options.appearanceIcon) + '</span> ' +
                '<span class="appearance-btn-label">' + escapeHtml(options.appearanceLabel) + '</span>' +
                renderSwatch(options) +
            '</button>' +
            '<div class="appearance-popup" id="appearance-popup" role="dialog"' +
                ' aria-label="' + escapeHtml(options.appearanceTitle) + '" hidden>' +
                '<div class="appearance-popup-header">' + escapeHtml(options.appearanceTitle) + '</div>' +
                renderAppearanceField('Colour palette', paletteHtml) +
                renderAppearanceField('Theme', themeHtml) +
                renderAppearanceField('Layout', layoutHtml) +
            '</div>' +
            '</div>';
    }

    function bindAppearanceMenu(target) {
        const btn = target.querySelector('#appearance-btn');
        const popup = target.querySelector('#appearance-popup');
        if (!btn || !popup) return null;
        const open = function () {
            popup.hidden = false;
            btn.setAttribute('aria-expanded', 'true');
            const first = popup.querySelector('select:not([disabled])');
            if (first) first.focus();
        };
        const close = function (returnFocus) {
            if (popup.hidden) return;
            popup.hidden = true;
            btn.setAttribute('aria-expanded', 'false');
            if (returnFocus) btn.focus();
        };
        btn.addEventListener('click', function () {
            if (popup.hidden) open(); else close(false);
        });
        const onDocClick = function (e) {
            if (!popup.contains(e.target) && !btn.contains(e.target)) close(false);
        };
        const onKeyDown = function (e) {
            if (e.key === 'Escape' && !popup.hidden) close(true);
        };
        document.addEventListener('click', onDocClick);
        document.addEventListener('keydown', onKeyDown);
        return function cleanup() {
            document.removeEventListener('click', onDocClick);
            document.removeEventListener('keydown', onKeyDown);
        };
    }

    function renderLogo(options) {
        const titleHtml = options.titleClickable
            ? '<span class="logo-text logo-about-trigger" id="logo-about-trigger" role="button" tabindex="0"' +
                ' title="' + escapeHtml(options.titleAriaLabel || options.title) + '"' +
                ' aria-label="' + escapeHtml(options.titleAriaLabel || options.title) + '"' +
                ' style="cursor:pointer;">' + escapeHtml(options.title) + '</span>'
            : '<span class="logo-text">' + escapeHtml(options.title) + '</span>';

        return '<div class="logo-container">' +
            '<img alt="' + escapeHtml(options.logoAlt) + '" class="logo" src="' + escapeHtml(options.logoSrc) + '">' +
            titleHtml +
            '</div>';
    }

    function render(container, userOptions) {
        const options = Object.assign({}, DEFAULT_OPTIONS, userOptions || {});
        const target = (typeof container === 'string')
            ? document.querySelector(container)
            : container;
        if (!target) {
            console.warn('[Menubar] Container not found:', container);
            return null;
        }
        // Remove document-level listeners from any previous render.
        if (typeof target.__menubarCleanup === 'function') {
            target.__menubarCleanup();
            target.__menubarCleanup = null;
        }
        const renderId = (target.__menubarRenderId || 0) + 1;
        target.__menubarRenderId = renderId;

        const themeReadyNow = !!(global.ThemeManager && hasPaletteManifest());
        const canLoadTheme = options.autoLoadTheme !== false;

        const buttonsHtml = (options.buttons || []).map(renderButton).join('');
        const layoutHtml = options.showLayoutSelector ? renderLayoutSelector(options) : '';
        const themeHtml = options.showThemeSelector &&
            (global.ThemeManager || canLoadTheme) ? renderThemeSelector() : '';
        // Render the palette selector optimistically when the theme system
        // can still be loaded; it is removed again if the manifest never arrives.
        const showPalette = options.showPaletteSelector && (themeReadyNow || canLoadTheme);
        const paletteHtml = showPalette ? renderPaletteSelector() : '';
        const useAppearanceMenu = options.appearanceMenu !== false &&
            !!(layoutHtml || themeHtml || paletteHtml);
        const appearanceHtml = useAppearanceMenu
            ? renderAppearanceMenu(options, paletteHtml, themeHtml, layoutHtml)
            : layoutHtml + paletteHtml + themeHtml;

        target.innerHTML =
            '<div class="top-bar">' +
                renderLogo(options) +
                '<div class="top-bar-spacer"></div>' +
                appearanceHtml +
                buttonsHtml +
            '</div>';
        // Wire up the appearance popup (open/close behaviour)
        if (useAppearanceMenu) {
            target.__menubarCleanup = bindAppearanceMenu(target);
        }

        // Wire up button click handlers
        (options.buttons || []).forEach(function (btn) {
            if (btn.id && typeof btn.onClick === 'function') {
                const el = document.getElementById(btn.id);
                if (el) el.addEventListener('click', btn.onClick);
            }
        });

        // Wire up theme + palette selectors via the central theme system
        if (themeHtml || paletteHtml) {
            if (themeReadyNow) {
                bindThemeControls(target, options);
            } else {
                ensureThemeSystem(options).then(function () {
                    // Ignore if the menubar was re-rendered in the meantime.
                    if (target.__menubarRenderId !== renderId) return;
                    bindThemeControls(target, options);
                });
            }
        }

        // Wire up layout selector (uses localStorage 'cognotik-layout')
        if (options.showLayoutSelector) {
            const sel = target.querySelector('#layout-selector');
            if (sel) {
                bindLayoutSelector(sel, options.onLayoutChange);
            }
        }

        // Wire up logo click handler
        const isHomepage = window.location.pathname === '/' || window.location.pathname === '';
        const homeNavigateHandler = function () {
            window.location.href = '/';
        };
        const effectiveTitleClick = (typeof options.onTitleClick === 'function')
            ? options.onTitleClick
            : (!isHomepage ? homeNavigateHandler : null);
        // When not on homepage, the logo image always navigates home,
        // regardless of any custom onTitleClick for the title text.
        const logoImgClick = !isHomepage ? homeNavigateHandler : effectiveTitleClick;

        if (options.titleClickable && typeof effectiveTitleClick === 'function') {
            const trigger = target.querySelector('#logo-about-trigger');
            if (trigger) {
                trigger.addEventListener('click', effectiveTitleClick);
                trigger.addEventListener('keydown', function (e) {
                    if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        effectiveTitleClick(e);
                    }
                });
            }
        } else if (options.titleHref) {
            const trigger = target.querySelector('#logo-about-trigger');
            if (trigger) {
                trigger.addEventListener('click', function () {
                    window.location.href = options.titleHref;
                });
            }
        }
        // Always wire up the logo image click: navigate home when not on homepage,
        // otherwise fall back to the configured title click handler (if any).
        const logoImg = target.querySelector('.logo-container .logo');
        if (logoImg && typeof logoImgClick === 'function') {
            logoImg.style.cursor = 'pointer';
            logoImg.setAttribute('title', !isHomepage ? 'Go to homepage' : (options.titleAriaLabel || options.title));
            logoImg.addEventListener('click', logoImgClick);
        }
        // When not on homepage, ensure the title text also navigates home,
        // even if titleClickable was false or a custom onTitleClick wasn't set.
        if (!isHomepage) {
            const trigger = target.querySelector('#logo-about-trigger');
            if (trigger && !options.titleClickable) {
                trigger.style.cursor = 'pointer';
                trigger.addEventListener('click', homeNavigateHandler);
            }
        }

        return target.querySelector('.top-bar');
    }

    function bindLayoutSelector(sel, onChange) {
        const STORAGE_KEY = 'cognotik-layout';
        const URL_PARAM = 'layout';
        let initial = null;
        try {
            const params = new URLSearchParams(window.location.search);
            const urlVal = params.get(URL_PARAM);
            if (urlVal) {
                initial = urlVal;
                localStorage.setItem(STORAGE_KEY, urlVal);
            }
        } catch (e) { /* ignore */ }
        if (!initial) {
            try { initial = localStorage.getItem(STORAGE_KEY); } catch (e) { /* ignore */ }
        }
        initial = initial || 'grid';
        sel.value = initial;
        if (typeof onChange === 'function') onChange(initial);

        sel.addEventListener('change', function () {
            const value = sel.value;
            try { localStorage.setItem(STORAGE_KEY, value); } catch (e) { /* ignore */ }
            if (typeof onChange === 'function') onChange(value);
        });
    }

    const Menubar = {
        render: render,
        /** Resolves with ThemeManager once the central theme system is loaded. */
        ensureTheme: function (opts) {
            return ensureThemeSystem(Object.assign({}, DEFAULT_OPTIONS, opts || {}));
        }
    };

    global.Menubar = Menubar;
})(window);