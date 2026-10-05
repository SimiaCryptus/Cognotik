/**
 * Site-wide Menubar Component
 *
* Renders a reusable top-bar with logo, a consolidated "Appearance"
* (look & feel) popup containing the palette, theme and layout selectors,
* and configurable action buttons.
 *
 * Usage:
 *   <div id="menubar-container"></div>
 *   <script src="modules/theme.js"></script>
 *   <script src="modules/menubar.js"></script>
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
         // Only rendered when the themes.js manifest is available.
         showPaletteSelector: true,
        // Group layout / palette / theme selectors into a single
        // "Appearance" popup. Set to false to render them inline.
        appearanceMenu: true,
        appearanceLabel: 'Appearance',
        appearanceIcon: '🎨',
        appearanceTitle: 'Look & Feel',
        buttons: []
    };

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
     function hasPaletteManifest() {
         return !!(global.ThemeManager &&
             typeof global.ThemeManager.getManifest === 'function' &&
             global.ThemeManager.getManifest());
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
    function renderAppearanceMenu(options, paletteHtml, themeHtml, layoutHtml) {
        return '<div class="appearance-menu">' +
            '<button class="top-bar-btn appearance-btn" type="button" id="appearance-btn"' +
                ' aria-haspopup="dialog" aria-expanded="false" aria-controls="appearance-popup"' +
                ' title="' + escapeHtml(options.appearanceTitle) + '">' +
                '<span class="btn-icon" aria-hidden="true">' + escapeHtml(options.appearanceIcon) + '</span> ' +
                '<span class="appearance-btn-label">' + escapeHtml(options.appearanceLabel) + '</span>' +
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

        const buttonsHtml = (options.buttons || []).map(renderButton).join('');
        const layoutHtml = options.showLayoutSelector ? renderLayoutSelector(options) : '';
        const themeHtml = options.showThemeSelector ? renderThemeSelector() : '';
         const showPalette = options.showPaletteSelector && hasPaletteManifest();
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

        // Wire up theme selector
        if (options.showThemeSelector && global.ThemeManager) {
            const sel = target.querySelector('#theme-selector');
            global.ThemeManager.bindSelector(sel);
        }
         // Wire up palette selector
         if (showPalette) {
             const psel = target.querySelector('#palette-selector');
             global.ThemeManager.bindPaletteSelector(psel);
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
        render: render
    };

    global.Menubar = Menubar;
})(window);