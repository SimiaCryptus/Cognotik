import {registerCommand, executeCommand} from '../core/commands.js';
import {registerAction, registerGroup, presentationFor, allActions} from '../core/actions.js';
import {registerEditor} from '../components/editors/EditorRegistry.js';
import {MonacoEditor} from '../components/editors/MonacoEditor.js';
import {PlainTextEditor} from '../components/editors/PlainTextEditor.js';
import {ImageViewer, BinaryPlaceholder} from '../components/editors/SimpleEditors.js';
import {openQuickPick} from '../components/overlays/QuickPick.js';
import {tabs} from '../components/tabs/TabModel.js';
import {fs} from '../core/fsclient.js';
import {caps} from '../core/capabilities.js';
import {ui} from '../core/ui.js';
import {bus} from '../core/bus.js';
import {store} from '../core/store.js';
import {persist} from '../core/persist.js';
import {announce} from '../core/a11y.js';
import {raise} from '../core/errors.js';
import {keysFor} from '../core/keymap.js';
import {basename, dirname, join, validateName} from '../core/paths.js';
import {buildContext} from '../core/context.js';
import config from '../config.js';

/** Registered once from main.js; the shell knows none of this. */
export function registerContributions({shell}) {
    registerEditors();
    registerCoreCommands(shell);
    registerFileActions();
    registerServerActions();
}

function registerEditors() {
    registerEditor({
        id: MonacoEditor.id,
        priority: 100,
        canOpen: MonacoEditor.canOpen,
        create: (ctx) => new MonacoEditor(ctx)
    });
    registerEditor({
        id: ImageViewer.id,
        priority: 90,
        canOpen: ImageViewer.canOpen,
        create: (ctx) => new ImageViewer(ctx)
    });
    registerEditor({
        id: PlainTextEditor.id,
        priority: 50,
        canOpen: PlainTextEditor.canOpen,
        create: (ctx) => new PlainTextEditor(ctx)
    });
    registerEditor({
        id: BinaryPlaceholder.id,
        priority: 0,
        canOpen: BinaryPlaceholder.canOpen,
        create: (ctx) => new BinaryPlaceholder(ctx)
    });
}

function registerCoreCommands(shell) {
    registerCommand({
        id: 'palette.show', title: 'Show Command Palette', keys: ['Mod+Shift+P', 'F1'], paletteHidden: true,
        run: () => openQuickPick({
            title: 'Command palette', placeholder: 'Type a command',
            items: () => paletteItems({actionsOnly: false}),
        }),
    });
    registerCommand({
        id: 'palette.findAction', title: 'Find Action', keys: ['Mod+Shift+A'], paletteHidden: true,
        run: () => openQuickPick({
            title: 'Find action', placeholder: 'Type an action name',
            items: () => paletteItems({actionsOnly: true}),
        }),
    });
    registerCommand({
        id: 'context.availableActions', title: 'Available Actions for the Current Context',
        keys: ['Alt+Enter'], paletteHidden: true,
        run: (ctx) => {
            const items = allActions().map((action) => {
                const presentation = presentationFor(action, ctx);
                if (!presentation.visible) return null;
                return {
                    id: action.id,
                    label: presentation.text,
                    description: presentation.description || presentation.disabledReason,
                    disabled: !presentation.enabled,
                    disabledReason: presentation.disabledReason,
                    keys: keysFor(action.id),
                    run: () => executeCommand(action.id, ctx),
                };
            }).filter(Boolean);
            return openQuickPick({title: 'Available actions', placeholder: 'Filter actions', items});
        },
    });
    registerCommand({
        id: 'quickopen.file', title: 'Quick Open File', keys: ['Mod+P'], paletteHidden: true,
        run: () => openQuickPick({
            title: 'Go to file', placeholder: 'Type a file name',
            items: () => quickOpenIndex(),
        }),
    });
    registerCommand({
        id: 'menu.focus', title: 'Focus Main Menu', keys: ['F10'], paletteHidden: true,
        run: () => shell.menuBar.focusFirst(),
    });
    registerCommand({
        id: 'view.toggleSidebar', title: 'View: Toggle Sidebar', keys: ['Mod+B'],
        run: () => shell.setSidebarVisible(shell.el.dataset.sidebar === 'hidden'),
    });
    registerCommand({
        id: 'view.focusExplorer', title: 'View: Focus Explorer', keys: ['Mod+Shift+E'],
        run: () => shell.showPanel('explorer', {focus: true}),
    });
    registerCommand({
        id: 'view.focusEditor', title: 'View: Focus Editor', keys: ['Alt+Down'],
        run: () => shell.editorArea.editorFor(tabs.active)?.focus?.(),
    });
    registerCommand({
        id: 'view.resetWorkspace', title: 'View: Reset Workspace State',
        run: async () => {
            if (await ui.confirm({
                title: 'Reset workspace state?',
                body: 'Open tabs, expanded folders and layout will be forgotten.',
                confirmLabel: 'Reset'
            })) {
                persist.reset();
                location.reload();
            }
        },
    });
    registerCommand({
        id: 'tree.refresh', title: 'Explorer: Refresh', keys: ['Mod+Shift+R'],
        run: async () => {
            const explorer = shell.panelInstances.get('explorer');
            explorer?.model.invalidateAll();
            await explorer?.model.load('/', {force: true});
            announce('Tree refreshed');
        },
    });
    registerCommand({
        id: 'tree.collapseAll', title: 'Explorer: Collapse All',
        run: () => shell.panelInstances.get('explorer')?.model.collapseAll(),
    });
    registerCommand({
        id: 'file.save', title: 'File: Save', keys: ['Mod+S'],
        when: (ctx) => !!ctx.activeTab && !caps.readOnly,
        run: (ctx) => shell.editorArea.save(ctx.activeTab),
    });
    registerCommand({
        id: 'file.saveAll', title: 'File: Save All', keys: ['Mod+Alt+S'],
        when: () => tabs.dirtyTabs().length > 0 && !caps.readOnly,
        run: async () => {
            for (const tab of tabs.dirtyTabs()) await shell.editorArea.save(tab);
        },
    });
    registerCommand({
        id: 'tab.close', title: 'File: Close Tab', keys: ['Alt+W'],
        when: () => !!tabs.activeTab(),
        run: async () => {
            const tab = tabs.activeTab();
            if (!tab) return;
            if (tab.dirty) {
                const choice = await ui.quickPick({
                    title: `Save ${tab.name}?`,
                    items: [{id: 'save', label: 'Save'}, {id: 'discard', label: "Don't save"}, {
                        id: 'cancel',
                        label: 'Cancel'
                    }],
                });
                if (!choice || choice.id === 'cancel') return;
                if (choice.id === 'save' && !(await shell.editorArea.save(tab))) return;
            }
            tabs.close(tab.id);
        },
    });
    registerCommand({id: 'tab.next', title: 'File: Next Tab', keys: ['Mod+Alt+Right'], run: () => tabs.move(1)});
    registerCommand({
        id: 'tab.previous',
        title: 'File: Previous Tab',
        keys: ['Mod+Alt+Left'],
        run: () => tabs.move(-1)
    });
    registerCommand({
        id: 'overlay.close', title: 'Close Overlay', keys: ['Escape'], paletteHidden: true,
        run: async () => {
            const {closeContextMenu} = await import('../components/overlays/ContextMenu.js');
            const {close} = await import('../components/overlays/QuickPick.js');
            closeContextMenu();
            close();
        },
    });
}

function paletteItems({actionsOnly}) {
    const ctx = buildContext({origin: 'palette'});
    const {allCommands, isVisible, isEnabled} = window.__fsCommands;
    return allCommands()
        .filter((command) => !command.paletteHidden && isVisible(command) && (!actionsOnly || command.isAction))
        .map((command) => ({
            id: command.id,
            label: command.title,
            description: command.category,
            keys: keysFor(command.id).map((k) => k),
            disabled: !isEnabled(command, ctx),
            run: () => executeCommand(command.id, ctx),
        }))
        .sort((a, b) => a.label.localeCompare(b.label));
}

let quickOpenCache = null;
bus.on('fs:overflow', () => {
    quickOpenCache = null;
});
bus.on('fs:changed', () => {
    quickOpenCache = null;
});

async function quickOpenIndex() {
    if (quickOpenCache) return quickOpenCache;
    const result = await fs.readdir('/', {recursive: true, depth: caps.limits.maxDepth, stat: false});
    const items = (result.entries || [])
        .filter((entry) => entry.type === 'file')
        .slice(0, config.quickOpen.maxEntries)
        .map((entry) => ({
            id: entry.path,
            label: entry.name,
            description: `/${entry.path}`,
            run: () => ui.openPath(`/${entry.path}`, {pinned: true}),
        }));
    if (result.truncated) {
        items.unshift({id: '__truncated', label: 'Showing the first entries only — refine your query', disabled: true});
    }
    quickOpenCache = items;
    return items;
}

/** File/folder actions: one registration serves menubar, context menu and palette. */
function registerFileActions() {
    registerGroup({id: 'group:copy', title: 'Copy', anchor: 'explorer/context', group: '3_clipboard', order: 10});

    registerAction({
        id: 'file.open', title: 'Open', menus: [{anchor: 'explorer/context', group: '1_open', order: 10}],
        selection: {min: 1, max: 50, kinds: ['file', 'dir']},
        run: async (ctx) => {
            for (const resource of ctx.resources) {
                if (resource.type === 'file') await ui.openPath(resource.path, {pinned: true});
                else await ui.revealPath(resource.path);
            }
        },
    });

    registerAction({
        id: 'file.new', title: 'New File…', icon: '＋',
        menus: [
            {anchor: 'main/file', group: '2_new', order: 10},
            {anchor: 'explorer/context', group: '2_new', order: 10},
            {anchor: 'explorer/empty', group: '2_new', order: 10},
        ],
        selection: {min: 0, kinds: ['file', 'dir']},
        enablement: () => !caps.readOnly,
        disabledReason: 'The server is read-only',
        run: async (ctx) => {
            const parent = targetFolder(ctx);
            const name = await ui.prompt({title: 'New file', label: `Name (in ${parent})`, validate: validateName});
            if (!name) return;
            const path = join(parent, name);
            try {
                await fs.writeFile(path, new Uint8Array(0), {ifNoneMatch: '*'});
            } catch (error) {
                raise(error, {operation: 'create', path});
                return;
            }
            await ui.refresh(parent);
            await ui.openPath(path, {pinned: true});
            announce(`Created ${name}`);
        },
    });

    registerAction({
        id: 'folder.new', title: 'New Folder…', icon: '📁',
        menus: [
            {anchor: 'main/file', group: '2_new', order: 20},
            {anchor: 'explorer/context', group: '2_new', order: 20},
            {anchor: 'explorer/empty', group: '2_new', order: 20},
        ],
        selection: {min: 0, kinds: ['file', 'dir']},
        enablement: () => !caps.readOnly,
        disabledReason: 'The server is read-only',
        run: async (ctx) => {
            const parent = targetFolder(ctx);
            const name = await ui.prompt({title: 'New folder', label: `Name (in ${parent})`, validate: validateName});
            if (!name) return;
            const path = join(parent, name);
            try {
                await fs.mkdir(path, {recursive: false});
            } catch (error) {
                raise(error, {operation: 'mkdir', path});
                return;
            }
            await ui.refresh(parent);
            await ui.revealPath(path);
            announce(`Created folder ${name}`);
        },
    });

    registerAction({
        id: 'file.rename', title: 'Rename…', keys: ['F2'],
        menus: [{anchor: 'explorer/context', group: '4_refactor', order: 10}, {
            anchor: 'main/file',
            group: '4_refactor'
        }],
        selection: {min: 1, max: 1, kinds: ['file', 'dir']},
        enablement: (ctx) => !caps.readOnly && !ctx.resources[0]?.readOnly,
        disabledReason: 'This item is read-only',
        update: (ctx, p) => {
            if (ctx.resources[0]) p.text = `Rename ${ctx.resources[0].name}…`;
        },
        run: async (ctx) => {
            const resource = ctx.resources[0];
            const name = await ui.prompt({
                title: 'Rename', label: `New name for ${resource.name}`, value: resource.name, validate: validateName,
            });
            if (!name || name === resource.name) return;
            const to = join(dirname(resource.path), name);
            try {
                await fs.rename(resource.path, to, {overwrite: false});
            } catch (error) {
                if (error.code === 'EEXIST') {
                    const overwrite = await ui.confirm({
                        title: 'Already exists',
                        body: `${name} already exists. Overwrite?`,
                        confirmLabel: 'Overwrite',
                        danger: true
                    });
                    if (!overwrite) return;
                    await fs.rename(resource.path, to, {overwrite: true});
                } else {
                    raise(error, {operation: 'rename', path: resource.path});
                    return;
                }
            }
            const tab = tabs.byPath(resource.path);
            if (tab) {
                tab.path = to;
                tab.name = basename(to);
                tabs.emit();
            }
            await ui.refresh(dirname(resource.path));
            announce(`Renamed to ${name}`);
        },
    });

    registerAction({
        id: 'file.delete', title: 'Delete…', keys: ['Delete'],
        menus: [{anchor: 'explorer/context', group: '9_danger', order: 10}],
        selection: {min: 1, kinds: ['file', 'dir']},
        enablement: (ctx) => !caps.readOnly && !ctx.resources.some((r) => r.readOnly),
        disabledReason: 'One or more items are read-only',
        update: (ctx, p) => {
            p.text = ctx.resources.length > 1 ? `Delete ${ctx.resources.length} items…` : `Delete ${ctx.resources[0]?.name ?? ''}…`;
        },
        run: async (ctx) => {
            const names = ctx.resources.map((r) => r.name).join(', ');
            const ok = await ui.confirm({
                title: ctx.resources.length > 1 ? `Delete ${ctx.resources.length} items?` : `Delete ${names}?`,
                body: ctx.dirs.length ? 'Folders are deleted recursively. This cannot be undone.' : 'This cannot be undone.',
                confirmLabel: 'Delete', danger: true,
            });
            if (!ok) return;
            const chunkSize = Math.max(1, caps.limits.maxBatchOps || 32);
            const failures = [];
            for (let i = 0; i < ctx.resources.length; i += chunkSize) {
                const chunk = ctx.resources.slice(i, i + chunkSize);
                const results = await fs.batch(chunk.map((r) => ({
                    op: 'rm',
                    path: r.path,
                    recursive: true,
                    force: false
                })));
                (results || []).forEach((result, index) => {
                    if (result.ok) {
                        const path = chunk[index].path;
                        tabs.byPath(path) && tabs.close(tabs.byPath(path).id);
                        window.__fsExplorer?.model.remove(path);
                    } else {
                        failures.push(`${chunk[index].name}: ${result.error?.code}`);
                    }
                });
            }
            await ui.refresh(ctx.commonAncestor || '/');
            if (failures.length) ui.toast({severity: 'error', message: `Could not delete: ${failures.join('; ')}`});
            else announce(`Deleted ${ctx.resources.length} item(s)`);
        },
    });

    registerAction({
        id: 'file.copyPath', title: 'Copy Path',
        menus: [{anchor: 'group:copy', group: '3_clipboard', order: 10}],
        selection: {min: 1, kinds: ['file', 'dir']},
        run: async (ctx) => {
            await navigator.clipboard?.writeText(ctx.paths.join('\n'));
            announce('Path copied');
        },
    });

    registerAction({
        id: 'file.copyName', title: 'Copy Name',
        menus: [{anchor: 'group:copy', group: '3_clipboard', order: 20}],
        selection: {min: 1, kinds: ['file', 'dir']},
        run: async (ctx) => {
            await navigator.clipboard?.writeText(ctx.resources.map((r) => r.name).join('\n'));
            announce('Name copied');
        },
    });

    registerAction({
        id: 'file.download', title: 'Download',
        menus: [{anchor: 'explorer/context', group: '8_export', order: 10}, {anchor: 'tab/context', group: '8_export'}],
        selection: {min: 1, max: 1, kinds: ['file']},
        run: (ctx) => download(fs.fileUrl(ctx.resources[0].path), ctx.resources[0].name),
    });

    registerAction({
        id: 'folder.downloadZip', title: 'Download as ZIP',
        requires: ['snapshot'],
        menus: [{anchor: 'explorer/context', group: '8_export', order: 20}, {anchor: 'main/file', group: '8_export'}],
        selection: {min: 0, max: 1, kinds: ['dir']},
        run: (ctx) => {
            const path = ctx.resources[0]?.path || '/';
            download(fs.snapshotUrl(path), `${basename(path) || 'root'}.zip`);
        },
    });

    registerAction({
        id: 'view.openClassic', title: 'Open in Classic View',
        menus: [{anchor: 'main/view', group: '8_export', order: 90}, {
            anchor: 'explorer/context',
            group: '8_export',
            order: 90
        }],
        selection: {min: 0, kinds: ['file', 'dir']},
        run: (ctx) => {
            const base = store.get().classicBase;
            if (!base) return;
            const path = ctx.resources[0]?.path || '/';
            window.open(base.replace(/\/$/, '') + path, '_blank', 'noopener');
        },
    });

    registerAction({
        id: 'help.keyboard', title: 'Keyboard Shortcuts',
        menus: [{anchor: 'main/help', group: '1_open', order: 10}],
        selection: {min: 0},
        run: () => executeCommand('palette.show'),
    });

    registerAction({
        id: 'view.toggleSidebar.action', title: 'Toggle Sidebar',
        menus: [{anchor: 'main/view', group: '1_open', order: 10}],
        selection: {min: 0},
        run: () => executeCommand('view.toggleSidebar'),
    });
}

function targetFolder(ctx) {
    const first = ctx.resources[0];
    if (!first) return '/';
    return first.type === 'dir' ? first.path : dirname(first.path);
}

function download(href, name) {
    const link = document.createElement('a');
    link.href = href;
    link.download = name;
    document.body.appendChild(link);
    link.click();
    link.remove();
}

/**
 * §19.11 — descriptors from GET /actions become first-class actions, so a
 * host-registered Kotlin tool appears in every surface with no JavaScript.
 */
function registerServerActions() {
    bus.on('actions:descriptors', ({actions: descriptors}) => {
        for (const descriptor of descriptors || []) {
            if (!descriptor?.id || !descriptor.title) continue;
            registerAction({
                ...descriptor,
                run: async (ctx, params) => {
                    const response = await fetch(`${store.get().base}/action/${encodeURIComponent(descriptor.id)}`, {
                        method: 'POST',
                        headers: {'Content-Type': 'application/json', 'X-Fs-Api': '1'},
                        body: JSON.stringify({
                            context: {
                                origin: ctx.origin, anchor: ctx.anchor, truncated: ctx.truncated,
                                resources: ctx.resources.map((r) => ({path: r.path, type: r.type})),
                                editorSelection: ctx.editorSelection,
                            },
                            params,
                        }),
                        signal: ctx.signal,
                    });
                    if (!response.ok) {
                        const payload = await response.json().catch(() => ({}));
                        throw Object.assign(new Error(payload?.error?.message || 'Action failed'), {code: payload?.error?.code || 'EACTION'});
                    }
                    return response.json();
                },
            });
        }
    });
}
