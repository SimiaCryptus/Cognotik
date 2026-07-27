import {h} from '../../core/dom.js';
import {fs} from '../../core/fsclient.js';
import {formatBytes, basename} from '../../core/paths.js';
import {isImage} from '../../core/mime.js';

/** image/* viewer: alt text is the filename, keyboard zoom included. */
export class ImageViewer {
    static id = 'image';

    static canOpen(stat) {
        return isImage(stat);
    }

    constructor(ctx) {
        this.ctx = ctx;
        this.zoom = 1;
        this.img = h('img', {alt: basename(ctx.tab.path), src: fs.fileUrl(ctx.tab.path)});
        this.el = h('div', {class: 'fs-image', tabindex: '0'}, [
            h('div', {role: 'toolbar', 'aria-label': 'Image'}, [
                h('button', {type: 'button', text: 'Zoom in', onclick: () => this.setZoom(this.zoom * 1.25)}),
                h('button', {type: 'button', text: 'Zoom out', onclick: () => this.setZoom(this.zoom / 1.25)}),
                h('button', {type: 'button', text: 'Fit', onclick: () => this.setZoom(1)}),
            ]),
            this.img,
        ]);
        this.el.addEventListener('keydown', (event) => {
            if (event.key === '+' || event.key === '=') this.setZoom(this.zoom * 1.25);
            else if (event.key === '-') this.setZoom(this.zoom / 1.25);
            else if (event.key === '0') this.setZoom(1);
        });
    }

    setZoom(value) {
        this.zoom = Math.min(8, Math.max(0.1, value));
        this.img.style.transform = `scale(${this.zoom})`;
        this.ctx.onCursor?.({});
    }

    focus() {
        this.el.focus();
    }

    dispose() {
    }
}

/** Explains *why* the file cannot be edited, and offers a download. */
export class BinaryPlaceholder {
    static id = 'binary';

    static canOpen() {
        return true;
    }

    constructor(ctx) {
        const {tab} = ctx;
        this.el = h('div', {class: 'fs-binary', tabindex: '0'}, [
            h('h2', {text: tab.name}),
            h('p', {text: `${formatBytes(tab.size)} · ${tab.mimeType}`}),
            h('p', {text: 'This file is binary or too large to edit in the browser.'}),
            h('a', {href: fs.fileUrl(tab.path), download: tab.name, text: `Download ${tab.name}`}),
        ]);
    }

    focus() {
        this.el.focus();
    }

    dispose() {
    }
}
