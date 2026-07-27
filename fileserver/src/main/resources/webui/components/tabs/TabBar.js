import {Component} from './../base.js';
import {h, clear, on} from '../../core/dom.js';
import {tabs} from './TabModel.js';
import {executeCommand} from '../../core/commands.js';
import {openContextMenu} from '../overlays/ContextMenu.js';
import {buildContext, setActiveOrigin} from '../../core/context.js';

export class TabBar extends Component {
    render() {
        this.el = h('div', {
            class: 'fs-tabs', role: 'tablist', 'aria-label': 'Open editors',
            'aria-orientation': 'horizontal',
        });
        this.track(on(this.el, 'keydown', (event) => this.onKeydown(event)));
        this.track(on(this.el, 'contextmenu', (event) => {
            const tab = this.tabFor(event.target);
            if (!tab) return;
            event.preventDefault();
            tabs.activate(tab.id);
            openContextMenu({
                anchor: 'tab/context', x: event.clientX, y: event.clientY, label: `Actions for ${tab.name}`,
                ctx: buildContext({origin: 'tab', anchor: 'tab/context'}),
            });
        }));
        return this.el;
    }

    mounted() {
        this.track(tabs.onChange(() => this.update()));
        this.update();
    }

    tabFor(target) {
        const el = target instanceof Element ? target.closest('[role="tab"]') : null;
        return el ? tabs.byId.get(el.dataset.id) : null;
    }

    update() {
        clear(this.el);
        for (const tab of tabs.list()) {
            const selected = tabs.active === tab.id;
            const label = `${tab.path}${tab.dirty ? ', unsaved changes' : ''}${tab.readOnly ? ', read-only' : ''}`;
            this.el.appendChild(h('div', {
                class: 'fs-tab', role: 'tab', id: `tab-${tab.id}`, 'aria-selected': String(selected),
                'aria-controls': `panel-${tab.id}`, 'aria-label': label,
                tabindex: selected ? '0' : '-1', dataset: {id: tab.id, preview: String(!!tab.preview)},
                onclick: () => {
                    setActiveOrigin('editor');
                    tabs.activate(tab.id);
                },
            }, [
                h('span', {class: 'fs-tab__label', text: tab.name}),
                tab.dirty ? h('span', {class: 'fs-tab__dirty', 'aria-hidden': 'true', text: '●'}) : null,
                h('button', {
                    type: 'button', class: 'fs-tab__close', 'aria-label': `Close ${tab.name}`,
                    onclick: (event) => {
                        event.stopPropagation();
                        this.closeTab(tab.id);
                    },
                }, [h('span', {'aria-hidden': 'true', text: '✕'})]),
            ]));
        }
    }

    closeTab(id) {
        const previous = tabs.active;
        tabs.activate(id);
        executeCommand('tab.close').then(() => {
            if (tabs.byId.has(previous)) tabs.activate(previous);
        });
    }

    onKeydown(event) {
        const list = tabs.list();
        const index = list.findIndex((tab) => tab.id === tabs.active);
        switch (event.key) {
            case 'ArrowRight':
                event.preventDefault();
                tabs.move(1);
                break;
            case 'ArrowLeft':
                event.preventDefault();
                tabs.move(-1);
                break;
            case 'Home':
                event.preventDefault();
                if (list[0]) tabs.activate(list[0].id);
                break;
            case 'End':
                event.preventDefault();
                if (list.length) tabs.activate(list[list.length - 1].id);
                break;
            case 'Delete':
                event.preventDefault();
                executeCommand('tab.close');
                break;
            default:
                return;
        }
        void index;
        this.el.querySelector('[role="tab"][aria-selected="true"]')?.focus();
    }
}