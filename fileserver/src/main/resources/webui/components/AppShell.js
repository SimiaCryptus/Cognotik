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
        this.buildBottom();

        this.statusBar = new StatusBar();
        this.statusBar.mount(this.el);

        const width = persist.get('layout', {})?.sidebar ?? store.get().panels.sidebarWidth;
        this.setSidebarWidth(width);
        this.showPanel(persist.get('layout', {})?.panel || 'explorer');

        this.track(bus.on('caps:ready', () => this.updateReadOnlyBadge()));
        this.updateReadOnlyBadge();

        ui.setSidebar = (visible) => this.setSidebarVisible(visible);
        ui.focusPanel = (id) => (allPanels('bottom').some((panel) => panel.id === id)
            ? this.showBottomPanel(id, {focus: true})
            : this.showPanel(id, {focus: true}));
    }

    buildHeader() {
        this.menuBar = new MenuBar();
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
        /* Accent is orthogonal to light/dark: one hue drives every token that
           derives from --fs-accent (focus rings, selection, tabs, links…). */
        const accent = h('select', {
            class: 'fs-theme', 'aria-label': 'Accent colour',
            onchange: (e) => {
                document.documentElement.setAttribute('data-accent', e.target.value);
                persist.set('accent', e.target.value);
                /* Editors read colours from the tokens, so re-theme them too. */
                bus.emit('theme:changed', persist.get('theme', 'auto'));
            },
        }, [
            h('option', {value: 'indigo', text: '◆ Indigo'}),
            h('option', {value: 'violet', text: '◆ Violet'}),
            h('option', {value: 'blue', text: '◆ Blue'}),
            h('option', {value: 'teal', text: '◆ Teal'}),
            h('option', {value: 'amber', text: '◆ Amber'}),
            h('option', {value: 'rose', text: '◆ Rose'}),
        ]);
        const savedAccent = persist.get('accent', 'indigo');
        accent.value = savedAccent;
        document.documentElement.setAttribute('data-accent', savedAccent);


        const classic = h('a', {
            class: 'fs-header__classic', text: 'Classic view',
            href: store.get().classicBase || '#', rel: 'noopener',
        });

        this.header.append(
            h('span', {class: 'fs-header__title', text: 'Files'}),
            this.menuBar.mount(h('div')),
            h('span', {class: 'fs-header__spacer'}),
            this.readOnlyBadge, theme, accent, classic,
        );

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

    /**
     * The bottom dock lives *inside* .fs-main (below the editor) so the
     * explorer keeps full height and no grid surgery is needed.
     */
    buildBottom() {
        /* Whether a bottom panel is currently mounted: the dock bar (and thus the
           expand affordance) is shown for a *collapsed* panel, but not when there
           is no panel at all. */
        this.bottomMounted = false;
        this.bottomSplitter = h('div', {
            class: 'fs-hsplitter', role: 'separator', tabindex: '0', hidden: true,
             'aria-orientation': 'horizontal', 'aria-label': 'Resize panel', 'aria-controls': 'fs-bottom',
             title: 'Drag to resize · double-click to collapse',
        });
        this.bottomChevron = h('span', {'aria-hidden': 'true', text: '▾'});
        this.bottomToggle = h('button', {
            type: 'button', class: 'fs-bottom__toggle', 'aria-expanded': 'true',
            'aria-controls': 'fs-bottom', title: 'Collapse panel',
            onclick: () => this.setBottomVisible(!this.isBottomVisible()),
        }, [this.bottomChevron, h('span', {class: 'sr-only', text: 'Collapse or expand the panel'})]);
        this.bottomTitle = h('span', {class: 'fs-bottom__title', text: 'Panel'});
        this.bottomBar = h('div', {class: 'fs-bottom__bar', hidden: true}, [
            this.bottomToggle, this.bottomTitle,
            h('span', {style: {flex: '1'}}),
            h('button', {
                type: 'button', class: 'fs-bottom__close', title: 'Close panel',
                onclick: () => this.closeBottomPanel(),
            }, [
                h('span', {'aria-hidden': 'true', text: '✕'}),
                h('span', {class: 'sr-only', text: 'Close panel'}),
            ]),
        ]);
        this.bottom = h('section', {
            class: 'fs-bottom', id: 'fs-bottom', hidden: true, 'aria-label': 'Panel',
        });
        this.main.append(this.bottomSplitter, this.bottomBar, this.bottom);
        /* Double-clicking the bar toggles, exactly like an IDE tool window. */
        this.track(on(this.bottomBar, 'dblclick', (event) => {
            if (event.target instanceof Element && event.target.closest('button')) return;
            this.setBottomVisible(!this.isBottomVisible());
        }));
        this.setBottomHeight(persist.get('layout', {})?.bottom ?? 260);
         /* A panel with nothing left to show collapses the dock rather than
            leaving a blank strip above the status bar (#7). */
         this.track(bus.on('panel:empty', ({id}) => {
             if (store.get().panels.bottom === id) this.setBottomVisible(false);
         }));
         /* The divider is only rendered while the dock is open (#1), so a
            double-click can only mean "collapse". */
         this.track(on(this.bottomSplitter, 'dblclick', () => this.setBottomVisible(false)));
        let dragging = false;
         const endDrag = (event) => {
             if (!dragging) return;
             dragging = false;
             delete this.bottomSplitter.dataset.dragging;
             try {
                 this.bottomSplitter.releasePointerCapture(event.pointerId);
             } catch (e) { /* already released */
             }
         };
        this.track(on(this.bottomSplitter, 'pointerdown', (event) => {
             /* Defence in depth: a collapsed dock has no height to drag. */
             if (this.bottom.hidden) return;
             /* Without this the browser claims the gesture (text selection /
                native drag) and the pointermove stream dies after one event, so
                the split looked unresizable. */
             event.preventDefault();
            dragging = true;
             this.bottomSplitter.dataset.dragging = 'true';
            this.bottomSplitter.setPointerCapture(event.pointerId);
        }));
        this.track(on(this.bottomSplitter, 'pointermove', (event) => {
            if (!dragging) return;
             const height = this.main.getBoundingClientRect().bottom - event.clientY;
             /* Dragging (nearly) shut collapses the dock instead of pinning it
                at its minimum height. */
             if (height < 48) {
                 endDrag(event);
                 this.setBottomVisible(false);
                 return;
             }
             this.setBottomHeight(height);
        }));
         this.track(on(this.bottomSplitter, 'pointerup', endDrag));
         this.track(on(this.bottomSplitter, 'pointercancel', endDrag));
         this.track(on(this.bottomSplitter, 'lostpointercapture', endDrag));
        this.track(on(this.bottomSplitter, 'keydown', (event) => {
            if (this.bottom.hidden) return;
            const step = event.shiftKey ? 64 : 16;
            const height = this.bottom.getBoundingClientRect().height;
            if (event.key === 'ArrowUp') this.setBottomHeight(height + step);
             else if (event.key === 'ArrowDown') {
                 if (height - step < 96) this.setBottomVisible(false);
                 else this.setBottomHeight(height - step);
             } else if (event.key === 'Enter') this.setBottomVisible(false);
            else if (event.key === 'Escape') this.setBottomVisible(false);
            else return;
            event.preventDefault();
        }));
    }

    setBottomHeight(height) {
        const clamped = Math.min(window.innerHeight * 0.8, Math.max(80, Math.round(height)));
        this.bottom.style.height = `${clamped}px`;
        this.bottomSplitter.setAttribute('aria-valuenow', String(clamped));
        persist.patch({layout: {...(persist.get('layout', {}) || {}), bottom: clamped}});
         /* Panels that own a canvas (xterm) have to re-fit after every drag. */
         bus.emit('panel:resized', {id: store.get().panels.bottom, height: clamped});
    }

    setBottomVisible(visible) {
        this.bottom.hidden = !visible;
         /* Belt and braces: `.fs-bottom` carries `display: flex`, which would
            otherwise out-specify the UA's [hidden] { display: none } and leave a
            "collapsed" dock occupying its full height. */
         this.bottom.style.display = visible ? '' : 'none';
        /* The splitter only exists while there is a height to drag; the dock bar
           stays behind so a collapsed panel can always be expanded again. */
        this.bottomSplitter.hidden = !visible;
        this.bottomBar.hidden = !this.bottomMounted;
         this.bottomBar.dataset.collapsed = String(!visible);
        this.bottomToggle.setAttribute('aria-expanded', String(!!visible));
        this.bottomToggle.title = visible ? 'Collapse panel' : 'Expand panel';
        this.bottomChevron.textContent = visible ? '▾' : '▸';
        persist.patch({layout: {...(persist.get('layout', {}) || {}), bottomCollapsed: !visible}});
         if (visible) {
             const id = store.get().panels.bottom;
             bus.emit('panel:resized', {id});
             /* Expanding is a deliberate act: give the panel the keyboard back
                (and let it re-fit its canvas) instead of leaving focus adrift. */
             const instance = id ? this.panelInstances.get(id) : null;
             instance?.focus?.();
         }
        announce(visible ? 'Panel expanded' : 'Panel collapsed');
    }
     isBottomVisible() {
         return !this.bottom.hidden;
     }
     /** Unmounts the dock entirely (the bar disappears with it). */
     closeBottomPanel() {
         const id = store.get().panels.bottom;
         this.bottomMounted = false;
         this.setBottomVisible(false);
         this.bottomBar.hidden = true;
         clear(this.bottom);
         const instance = id ? this.panelInstances.get(id) : null;
         if (typeof instance?.destroy === 'function') {
             try {
                 instance.destroy();
             } catch (e) {
                 console.warn(e);
             }
             this.panelInstances.delete(id);
         }
         store.set({panels: {...store.get().panels, bottom: null}});
         announce('Panel closed');
     }


    /**
     * Mounts (once) a registered `location: 'bottom'` panel.
     *
     * `reveal: false` mounts it while keeping the dock collapsed, so a caller
     * that may fail (or produce nothing) never leaves an empty, resizable strip
     * above the status bar (#1).
     */
    showBottomPanel(id, {toggle = false, focus = false, reveal = true} = {}) {
        const visible = !this.bottom.hidden;
        if (toggle && visible && store.get().panels.bottom === id) {
            this.setBottomVisible(false);
            return this.panelInstances.get(id) || null;
        }
        let instance = this.panelInstances.get(id);
        if (!instance) {
            instance = allPanels('bottom').find((panel) => panel.id === id)?.create?.();
            if (!instance) return null;
            this.panelInstances.set(id, instance);
        }
        clear(this.bottom);
        if (instance.el) this.bottom.appendChild(instance.el);
        else instance.mount(this.bottom);
        const title = allPanels('bottom').find((panel) => panel.id === id)?.title || id;
        this.bottom.setAttribute('aria-label', title);
        this.bottomTitle.textContent = title;
        this.bottomMounted = true;
        store.set({panels: {...store.get().panels, bottom: id}});
        /* `reveal: false` still shows the bar, so the caller's panel can be
           expanded by the user even if it started life collapsed. */
        this.setBottomVisible(reveal ? true : this.isBottomVisible());
        if (focus && this.isBottomVisible()) instance.focus?.();
        return instance;
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