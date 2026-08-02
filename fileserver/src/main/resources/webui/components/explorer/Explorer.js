import {Component} from './../base.js';
import {h, on, debounce} from '../../core/dom.js';
import {fs} from '../../core/fsclient.js';
import {bus} from '../../core/bus.js';
import {caps} from '../../core/capabilities.js';
import {ui} from '../../core/ui.js';
import {announce} from '../../core/a11y.js';
import {persist} from '../../core/persist.js';
import {raise} from '../../core/errors.js';
import {registerContextSource, setActiveOrigin, buildContext} from '../../core/context.js';
import {executeCommand} from '../../core/commands.js';
import {openContextMenu} from '../overlays/ContextMenu.js';
import {TreeModel} from './TreeModel.js';
import {TreeView} from './TreeView.js';
import {dirname, basename, join} from '../../core/paths.js';

/** The persistent, lazily-loaded file tree plus its toolbar and filter box. */
export class Explorer extends Component {
    render() {
        this.model = new TreeModel({fs});
        this.view = new TreeView({
            model: this.model,
            onActivate: (node, {preview}) => {
                if (node.type === 'dir') this.model.toggle(node.path);
                else ui.openPath(node.path, {preview, pinned: !preview});
            },
            onContextMenu: ({x, y, node}) => this.openMenu(x, y, node),
            onDropFiles: (files, target) => this.upload(files, target),
            onFocusIn: () => setActiveOrigin('explorer'),
        });

        this.filter = h('input', {
            type: 'search', class: 'fs-explorer__filter', placeholder: 'Filter files',
            'aria-label': 'Filter loaded files',
        });
        this.status = h('p', {class: 'fs-explorer__status', role: 'status'});

        const toolbar = h('div', {class: 'fs-explorer__toolbar', role: 'toolbar', 'aria-label': 'Explorer actions'}, [
            this.button('＋', 'New file', 'file.new'),
            this.button('📁', 'New folder', 'folder.new'),
             /* #10 — jump the tree to whatever the editor is showing. */
             this.button('🎯', 'Reveal active file', 'tree.revealActiveFile'),
            this.button('⟳', 'Refresh', 'tree.refresh'),
            this.button('⤒', 'Collapse all', 'tree.collapseAll'),
            this.filter,
        ]);

        this.el = h('div', {class: 'fs-explorer'}, [
            toolbar,
            h('span', {id: 'fs-readonly-hint', class: 'sr-only', text: 'read-only'}),
            this.status,
        ]);
        this.view.mount(this.el);

        const onFilter = debounce(() => {
            this.model.setQuery(this.filter.value);
            const count = this.model.rows().length;
            if (this.filter.value) announce(`${count} matches`);
        }, 180);
        this.track(on(this.filter, 'input', onFilter));
        return this.el;
    }

    button(icon, label, commandId) {
        return h('button', {
            type: 'button', title: label, onclick: () => executeCommand(commandId),
        }, [h('span', {'aria-hidden': 'true', text: icon}), h('span', {class: 'sr-only', text: label})]);
    }

    async mounted() {
        this.track(registerContextSource('explorer', () => ({
            resources: this.model.selectedResources(),
        })));
        this.track(bus.on('fs:changed', ({dirs}) => this.onExternalChange(dirs)));
        this.track(bus.on('fs:overflow', () => {
            this.model.invalidateAll();
            this.model.load('/', {force: true});
        }));
        this.track(bus.on('explorer:reveal', (path) => this.reveal(path)));

        ui.revealPath = (path) => this.reveal(path);
        ui.refresh = async (path) => {
            const dir = this.model.node(path)?.type === 'dir' ? path : dirname(path);
            await this.model.refresh(dir);
        };
        window.__fsExplorer = this;

        try {
            await this.model.expand('/');
            for (const path of persist.get('expanded', []) || []) {
                if (path !== '/') await this.model.expand(path).catch(() => {
                });
            }
            this.status.textContent = '';
        } catch (error) {
            this.status.textContent = 'Could not list the root folder.';
            raise(error, {operation: 'readdir', path: '/'});
        }
        this.track(this.model.onChange(() => this.persistExpanded()));
    }

    persistExpanded = debounce(() => {
        persist.set('expanded', Array.from(this.model.expanded));
    }, 400);

    focus() {
        this.view.focusPath(this.model.focus || this.model.rows()[0]?.path || '/');
    }
     /** The drawer (or panel) was revealed: it may have been measured at 0 px. */
     onShown() {
         this.view.update();
     }

    async onExternalChange(dirs) {
        for (const dir of dirs) {
            if (this.model.isLoaded(dir)) await this.model.refresh(dir).catch(() => {
            });
        }
    }

    async reveal(path) {
        await this.model.reveal(path).catch((e) => raise(e, {operation: 'reveal', path}));
        this.model.select([path], {focus: path});
        this.view.focusPath(path);
    }

    openMenu(x, y, node) {
        const anchor = node ? 'explorer/context' : 'explorer/empty';
        openContextMenu({
            anchor, x, y,
            label: this.model.selection.length > 1
                ? `Actions for ${this.model.selection.length} selected items`
                : `Actions for ${node ? node.name : 'folder'}`,
            ctx: buildContext({origin: 'explorer', anchor}),
        });
    }

    async upload(files, target) {
        for (const file of files) {
            if (file.size > caps.limits.maxFileSize) {
                ui.toast({severity: 'warn', message: `${file.name} exceeds the server limit`});
                continue;
            }
            const path = join(target, file.name);
            const task = ui.task({label: `Uploading ${file.name}`});
            try {
                const bytes = new Uint8Array(await file.arrayBuffer());
                await fs.writeFile(path, bytes, {ifNoneMatch: '*'});
                announce(`Uploaded ${basename(path)}`);
            } catch (error) {
                if (error.code === 'EEXIST') {
                    const overwrite = await ui.confirm({
                        title: 'File exists',
                        body: `${file.name} already exists. Overwrite?`,
                        confirmLabel: 'Overwrite',
                    });
                    if (overwrite) {
                        const bytes = new Uint8Array(await file.arrayBuffer());
                        await fs.writeFile(path, bytes).catch((e) => raise(e, {operation: 'upload', path}));
                    }
                } else {
                    raise(error, {operation: 'upload', path});
                }
            } finally {
                task.done();
            }
        }
        await this.model.refresh(target).catch(() => {
        });
    }
}