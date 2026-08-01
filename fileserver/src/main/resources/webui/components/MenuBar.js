import {Component} from './base.js';
import {h, on} from '../core/dom.js';
import {openContextMenu} from './overlays/ContextMenu.js';
import {buildContext} from '../core/context.js';

const MENUS = [
    {anchor: 'main/file', title: 'File'},
    {anchor: 'main/edit', title: 'Edit'},
    {anchor: 'main/selection', title: 'Selection'},
    {anchor: 'main/view', title: 'View'},
    {anchor: 'main/go', title: 'Go'},
    {anchor: 'main/tools', title: 'Tools'},
    {anchor: 'main/help', title: 'Help'},
];

/** WAI-ARIA menubar; the same renderer as every other menu surface (§8.9). */
export class MenuBar extends Component {
    render() {
        this.buttons = [];
        this.el = h('div', {class: 'fs-menubar', role: 'menubar', 'aria-label': 'Main menu'});
        MENUS.forEach((menu, index) => {
            const button = h('button', {
                type: 'button', role: 'menuitem', 'aria-haspopup': 'menu', 'aria-expanded': 'false',
                tabindex: index === 0 ? '0' : '-1', text: menu.title, dataset: {anchor: menu.anchor},
                onclick: (event) => this.open(index, event.currentTarget),
            });
            this.buttons.push(button);
            this.el.appendChild(button);
        });
        this.track(on(this.el, 'keydown', (event) => this.onKeydown(event)));
        return this.el;
    }

    focusFirst() {
        this.setFocus(0);
        this.buttons[0].focus();
    }

    setFocus(index) {
        this.buttons.forEach((b, i) => b.setAttribute('tabindex', i === index ? '0' : '-1'));
    }

    onKeydown(event) {
        const index = this.buttons.indexOf(document.activeElement);
        if (index < 0) return;
        let next = index;
        switch (event.key) {
            case 'ArrowRight':
                next = (index + 1) % this.buttons.length;
                break;
            case 'ArrowLeft':
                next = (index - 1 + this.buttons.length) % this.buttons.length;
                break;
            case 'Home':
                next = 0;
                break;
            case 'End':
                next = this.buttons.length - 1;
                break;
            case 'ArrowDown':
            case 'Enter':
            case ' ':
                event.preventDefault();
                this.open(index, this.buttons[index]);
                return;
            default:
                return;
        }
        event.preventDefault();
        this.setFocus(next);
        this.buttons[next].focus();
    }

    open(index, button) {
        const anchor = MENUS[index].anchor;
        const rect = button.getBoundingClientRect();
        button.setAttribute('aria-expanded', 'true');
        openContextMenu({
            anchor,
            x: rect.left,
            y: rect.bottom,
            label: MENUS[index].title,
            ctx: buildContext({origin: 'menu', anchor}),
            onClose: () => button.setAttribute('aria-expanded', 'false'),
            invoker: button,
        });
    }
}
