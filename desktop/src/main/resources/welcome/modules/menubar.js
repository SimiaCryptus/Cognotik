/**
 * Site-wide Menubar Component
 *
 * Renders a reusable top-bar with logo, optional layout selector,
 * theme selector, and configurable action buttons.
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

        const buttonsHtml = (options.buttons || []).map(renderButton).join('');
        const layoutHtml = options.showLayoutSelector ? renderLayoutSelector(options) : '';
        const themeHtml = options.showThemeSelector ? renderThemeSelector() : '';

        target.innerHTML =
            '<div class="top-bar">' +
                renderLogo(options) +
                '<div class="top-bar-spacer"></div>' +
                layoutHtml +
                themeHtml +
                buttonsHtml +
            '</div>';

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

        // Wire up layout selector (uses localStorage 'cognotik-layout')
        if (options.showLayoutSelector) {
            const sel = target.querySelector('#layout-selector');
            if (sel) {
                bindLayoutSelector(sel, options.onLayoutChange);
            }
        }

        // Wire up logo click handler
        if (options.titleClickable && typeof options.onTitleClick === 'function') {
            const trigger = target.querySelector('#logo-about-trigger');
            const logoImg = target.querySelector('.logo-container .logo');
            if (trigger) {
                trigger.addEventListener('click', options.onTitleClick);
                trigger.addEventListener('keydown', function (e) {
                    if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        options.onTitleClick(e);
                    }
                });
            }
            if (logoImg) {
                logoImg.style.cursor = 'pointer';
                logoImg.setAttribute('title', options.titleAriaLabel || options.title);
                logoImg.addEventListener('click', options.onTitleClick);
            }
        } else if (options.titleHref) {
            const trigger = target.querySelector('#logo-about-trigger');
            if (trigger) {
                trigger.addEventListener('click', function () {
                    window.location.href = options.titleHref;
                });
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