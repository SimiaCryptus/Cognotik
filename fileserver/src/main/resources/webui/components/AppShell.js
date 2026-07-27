import {Component} from './base.js';
import {h, on, clear} from '../core/dom.js';
import {store} from '../core/store.js';
import {bus} from '../core/bus.js';
import {caps} from '../core/capabilities.js';
import {persist} from '../core/persist.js';
import {ui} from '../core/ui.js';
import {announce} from '../core/a11y.js';
import {allPanels} from '../core/registry.js';
import {executeCommand} from '../core/commands.js';
import {MenuBar} from './MenuBar.js';
import {StatusBar} from './StatusBar.js';
import {TabBar} from './tabs/TabBar.js';
import {EditorArea} from './editors/EditorArea.js';
import {Explorer} from './explorer/Explorer.js';

const MIN_SIDEBAR = 160;
const MAX_SIDEBAR = 720;

export class AppShell extends Component {
    render() {
        this.panelInstances = new Map();

        this.header = h('header', {class: 'fs-header', role: 'banner'});
        this.activity = h('nav', {class: 'fs-activity', 'aria-label': 'Activity'});
        this.sidebar = h('aside', {class: 'fs-sidebar', 'aria-label': 'Explorer', id: 'fs-explorer'});
        this.splitter = h('div', {
            class: 'fs-splitter', role: 'separator', tabindex: '0',
            'aria-orientation': 'vertical', 'aria-label': 'Resize sidebar',
            'aria-controls': 'fs-explorer', 'aria-valuemin': String(MIN_SIDEBAR), 'aria-valuemax': String(MAX_SIDEBAR),
        });
        this.main = h('main', {class: 'fs-main', id: 'fs-main'});

        this.el = h('div', {class: 'fs-shell', dataset: {sidebar: 'visible'}});
        this.el.append(this.header, this.activity, this.sidebar, this.splitter, this.main);

        this.buildHeader();
        this.buildActivityBar();
        this.buildSplitter();
        return this.el;
    }

    mounted() {
        this.tabBar = new TabBar();
        this.tabBar.mount(this.main);
        this.editorArea = new EditorArea();
        this.editorArea.mount(this.main);

        this.statusBar = new StatusBar();
        this.statusBar.mount(this.el);

        const width = persist.get('layout', {})?.sidebar ?? store.get().panels.sidebarWidth;
        this.setSidebarWidth(width);
        this.showPanel(persist.get('layout', {})?.panel || 'explorer');

        this.track(bus.on('caps:ready', () => this.updateReadOnlyBadge()));
        this.updateReadOnlyBadge();

        ui.setSidebar = (visible) => this.setSidebarVisible(visible);
        ui.focusPanel = (id) => this.showPanel(id, {focus: true});
    }

    buildHeader() {
        this.menuBar = new MenuBar();
        this.breadcrumbs = h('div', {class: 'fs-breadcrumbs', 'aria-live': 'off'});
        this.readOnlyBadge = h('span', {class: 'fs-badge', hidden: true, text: 'Read-only'});

        const theme = h('select', {
            class: 'fs-theme', 'aria-label': 'Theme',
            onchange: (e) => {
                const value = e.target.value;
                document.documentElement.setAttribute('data-theme', value);
                persist.set('theme', value);
                bus.emit('theme:changed', value);
            },
        }, [
            h('option', {value: 'auto', text: '🌓 Auto'}),
            h('option', {value: 'light', text: '☀️ Light'}),
            h('option', {value: 'dark', text: '🌙 Dark'}),
            h('option', {value: 'hc', text: '◐ High contrast'}),
        ]);
        const saved = persist.get('theme', 'auto');
        theme.value = saved;
        document.documentElement.setAttribute('data-theme', saved);

        const classic = h('a', {
            class: 'fs-header__classic', text: 'Classic view',
            href: store.get().classicBase || '#', rel: 'noopener',
        });

        this.header.append(
            h('span', {class: 'fs-header__title', text: 'Files'}),
            this.menuBar.mount(h('div')),
            this.breadcrumbs,
            h('span', {class: 'fs-header__spacer'}),
            this.readOnlyBadge, theme, classic,
        );
        this.track(bus.on('tab:activated', (tab) => this.renderBreadcrumbs(tab?.path || '/')));
    }

    renderBreadcrumbs(path) {
        clear(this.breadcrumbs);
        const parts = path.split('/').filter(Boolean);
        const nav = h('ol', {class: 'fs-breadcrumbs__list', 'aria-label': 'Breadcrumbs'});
        let accumulated = '';
        nav.appendChild(h('li', {}, [h('button', {
            text: '/', onclick: () => ui.revealPath('/'),
        })]));
        parts.forEach((part, index) => {
            accumulated += `/${part}`;
            const target = accumulated;
            nav.appendChild(h('li', {}, [
                index ? h('span', {'aria-hidden': 'true', text: '›'}) : null,
                h('button', {text: part, onclick: () => ui.revealPath(target)}),
            ]));
        });
        this.breadcrumbs.appendChild(nav);
    }

    buildActivityBar() {
        this.activityButtons = new Map();
        const panels = [{id: 'explorer', title: 'Explorer', icon: '🗂'}, ...allPanels('sidebar')];
        const seen = new Set();
        for (const panel of panels) {
            if (seen.has(panel.id)) continue;
            seen.add(panel.id);
            const button = h('button', {
                type: 'button', 'aria-pressed': 'false', title: panel.title,
                onclick: () => this.showPanel(panel.id, {toggle: true, focus: true}),
            }, [
                h('span', {'aria-hidden': 'true', text: panel.icon || '▪'}),
                h('span', {class: 'sr-only', text: panel.title}),
            ]);
            this.activityButtons.set(panel.id, button);
            this.activity.appendChild(button);
        }
    }

    showPanel(id, {toggle = false, focus = false} = {}) {
        const current = store.get().panels.sidebar;
        if (toggle && current === id && this.el.dataset.sidebar === 'visible') {
            this.setSidebarVisible(false);
            return;
        }
        store.set({panels: {...store.get().panels, sidebar: id}});
        persist.patch({layout: {...(persist.get('layout', {}) || {}), panel: id}});
        for (const [panelId, button] of this.activityButtons) {
            button.setAttribute('aria-pressed', String(panelId === id));
        }
        clear(this.sidebar);
        let instance = this.panelInstances.get(id);
        if (!instance) {
            instance = id === 'explorer' ? new Explorer() : allPanels('sidebar').find((p) => p.id === id)?.create?.();
            if (instance) this.panelInstances.set(id, instance);
        }
        if (instance) {
            if (instance.el) this.sidebar.appendChild(instance.el);
            else instance.mount(this.sidebar);
            this.sidebar.setAttribute('aria-label', id === 'explorer' ? 'Explorer' : (allPanels('sidebar').find((p) => p.id === id)?.title || id));
            if (focus) instance.focus?.();
        }
        this.setSidebarVisible(true);
    }

    setSidebarVisible(visible) {
        const narrow = window.matchMedia('(max-width: 720px)').matches;
        this.el.dataset.sidebar = visible ? (narrow ? 'drawer' : 'visible') : 'hidden';
        this.splitter.setAttribute('aria-valuenow', String(visible ? store.get().panels.sidebarWidth : 0));
        announce(visible ? 'Sidebar shown' : 'Sidebar hidden');
    }

    setSidebarWidth(width) {
        const clamped = Math.min(MAX_SIDEBAR, Math.max(MIN_SIDEBAR, Math.round(width)));
        this.el.style.setProperty('--fs-sidebar-w', `${clamped}px`);
        store.set({panels: {...store.get().panels, sidebarWidth: clamped}});
        this.splitter.setAttribute('aria-valuenow', String(clamped));
        persist.patch({layout: {...(persist.get('layout', {}) || {}), sidebar: clamped}});
    }

    buildSplitter() {
        let dragging = false;
        this.track(on(this.splitter, 'pointerdown', (event) => {
            dragging = true;
            this.splitter.setPointerCapture(event.pointerId);
        }));
        this.track(on(this.splitter, 'pointermove', (event) => {
            if (!dragging) return;
            const left = this.activity.getBoundingClientRect().right;
            this.setSidebarWidth(event.clientX - left);
        }));
        this.track(on(this.splitter, 'pointerup', () => {
            dragging = false;
        }));
        this.track(on(this.splitter, 'keydown', (event) => {
            const step = event.shiftKey ? 64 : 16;
            const width = store.get().panels.sidebarWidth;
            switch (event.key) {
                case 'ArrowLeft':
                    this.setSidebarWidth(width - step);
                    break;
                case 'ArrowRight':
                    this.setSidebarWidth(width + step);
                    break;
                case 'Home':
                    this.setSidebarWidth(MIN_SIDEBAR);
                    break;
                case 'End':
                    this.setSidebarWidth(MAX_SIDEBAR);
                    break;
                case 'Enter':
                    executeCommand('view.toggleSidebar');
                    break;
                default:
                    return;
            }
            event.preventDefault();
        }));
    }

    updateReadOnlyBadge() {
        this.readOnlyBadge.hidden = !caps.readOnly;
    }
}
