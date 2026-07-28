import {registerCommand, executeCommand, getCommand, isEnabled} from '../core/commands.js';
import {registerAction, registerGroup, presentationFor, allActions} from '../core/actions.js';
import {registerPanel} from '../core/registry.js';
import {registerEditor} from '../components/editors/EditorRegistry.js';
import {MonacoEditor} from '../components/editors/MonacoEditor.js';
import {PlainTextEditor} from '../components/editors/PlainTextEditor.js';
import {ImageViewer, BinaryPlaceholder} from '../components/editors/SimpleEditors.js';
import {openQuickPick} from '../components/overlays/QuickPick.js';
import {TerminalPanel} from '../components/terminal/TerminalPanel.js';
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
    registerEditorActions();
    registerFileActions();
    registerTerminalActions(shell);
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
        when: (ctx) => {
            const tab = ctx.activeTab || tabs.activeTab();
            return !!tab && !tab.virtual && !caps.readOnly;
        },
        run: (ctx) => {
            const tab = ctx.activeTab || tabs.activeTab();
            return tab ? shell.editorArea.save(tab) : false;
        },
    });
    registerCommand({
        id: 'file.saveAll', title: 'File: Save All', keys: ['Mod+Alt+S'],
        when: () => tabs.dirtyTabs().length > 0 && !caps.readOnly,
        run: async () => {
            for (const tab of tabs.dirtyTabs()) await shell.editorArea.save(tab);
            announce('All files saved');
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

/**
 * A menu entry that delegates to an existing command: the command stays the
 * single source of truth for `keys` and `when`, so no chord is registered twice.
 */
function commandAction({commandId, title, icon, menus, update, disabledReason}) {
    const command = getCommand(commandId);
    if (!command) {
        console.error(`commandAction: unknown command ${commandId}`);
        return null;
    }
    return registerAction({
        id: `${commandId}.menu`,
        title: title || command.title,
        icon,
        commandId,
        paletteHidden: true,
        selection: {min: 0, max: Infinity, kinds: ['file', 'dir']},
        menus,
        enablement: (ctx) => isEnabled(command, ctx),
        disabledReason: disabledReason || 'Not available right now',
        update,
        run: () => executeCommand(commandId),
    });
}

/** File/View menu entries for the editor lifecycle (§19.1 `main/*` anchors). */
function registerEditorActions() {
    commandAction({
        commandId: 'file.save', title: 'Save', icon: '💾',
        disabledReason: 'No editable file is open',
        menus: [
            {anchor: 'main/file', group: '3_save', order: 10},
            {anchor: 'tab/context', group: '3_save', order: 10},
        ],
        update: (ctx, p) => {
            const tab = ctx.activeTab;
            if (tab) p.text = `Save ${tab.name}${tab.dirty ? ' •' : ''}`;
        },
    });
    commandAction({
        commandId: 'file.saveAll', title: 'Save All',
        disabledReason: 'There are no unsaved changes',
        menus: [{anchor: 'main/file', group: '3_save', order: 20}],
        update: (ctx, p) => {
            const count = tabs.dirtyTabs().length;
            if (count > 1) p.text = `Save All (${count})`;
        },
    });
    commandAction({
        commandId: 'tab.close', title: 'Close Tab',
        disabledReason: 'No tab is open',
        menus: [
            {anchor: 'main/file', group: '4_close', order: 10},
            {anchor: 'tab/context', group: '4_close', order: 10},
        ],
        update: (ctx, p) => {
            const tab = ctx.activeTab;
            if (tab) p.text = `Close ${tab.name}`;
        },
    });
    commandAction({
        commandId: 'tree.refresh', title: 'Refresh Explorer', icon: '⟳',
        menus: [{anchor: 'main/view', group: '2_refresh', order: 10}],
    });
    commandAction({
        commandId: 'view.focusEditor', title: 'Focus Editor',
        disabledReason: 'No file is open',
        menus: [{anchor: 'main/go', group: '1_open', order: 20}],
    });
    commandAction({
        commandId: 'view.resetWorkspace', title: 'Reset Workspace State…',
        menus: [{anchor: 'main/view', group: '9_danger', order: 90}],
    });
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

/** POSIX-ish quoting so a file name with spaces survives the shell. */
function shellQuote(value) {
    const text = String(value ?? '');
    return /^[\w@%+=:,./-]+$/.test(text) ? text : `'${text.replace(/'/g, `'\\''`)}'`;
}

function splitArgs(text) {
    return (String(text || '').match(/(?:[^\s"']+|"[^"]*"|'[^']*')+/g) || [])
        .map((part) => part.replace(/^["']|["']$/g, ''));
}

/**
 * §19 — terminals. One registration serves the explorer context menu, the
 * Tools menu, the palette and the keyboard, and the panel is contributed
 * through the registry so the shell knows nothing about it.
 */
function registerTerminalActions(shell) {
    registerPanel({
        id: 'terminal', title: 'Terminal', icon: '⌨', location: 'bottom', order: 10,
        create: () => new TerminalPanel(),
    });
    const openTerminal = async ({cwd = '/', command, label} = {}) => {
        const panel = shell.showBottomPanel('terminal', {focus: true});
        if (!panel) return null;
        return panel.openSession({cwd, command, label});
    };
    ui.openTerminal = openTerminal;
    registerCommand({
        id: 'view.toggleTerminal', title: 'View: Toggle Terminal', keys: ['Ctrl+`', 'Mod+`'],
        run: () => shell.showBottomPanel('terminal', {toggle: true, focus: true}),
    });
    registerCommand({
        id: 'terminal.new', title: 'Terminal: New Terminal', keys: ['Mod+Shift+`'],
        requires: ['terminal'],
        run: () => openTerminal({cwd: '/'}),
    });
    commandAction({
        commandId: 'view.toggleTerminal', title: 'Terminal', icon: '⌨',
        menus: [{anchor: 'main/view', group: '1_open', order: 20}],
    });
    registerAction({
        id: 'terminal.openHere', title: 'Open Terminal Here', icon: '⌨',
        requires: ['terminal'],
        menus: [
            {anchor: 'explorer/context', group: '7_run', order: 10},
            {anchor: 'explorer/empty', group: '7_run', order: 10},
            {anchor: 'main/tools', group: '7_run', order: 10},
        ],
        selection: {min: 0, max: 1, kinds: ['file', 'dir']},
        update: (ctx, p) => {
            const folder = targetFolder(ctx);
            p.text = `Open Terminal in ${basename(folder) || '/'}`;
        },
        run: (ctx) => openTerminal({cwd: targetFolder(ctx), label: basename(targetFolder(ctx)) || '/'}),
    });
    registerAction({
        id: 'file.run', title: 'Run…', icon: '▶',
        requires: ['terminal'],
        menus: [
            {anchor: 'explorer/context', group: '7_run', order: 20},
            {anchor: 'main/tools', group: '7_run', order: 20},
        ],
        selection: {min: 1, max: 1, kinds: ['file']},
        update: (ctx, p) => {
            if (ctx.resources[0]) p.text = `Run ${ctx.resources[0].name}…`;
        },
        params: [
            {
                id: 'interpreter', type: 'enum', label: 'Run with', default: 'auto',
                options: ['auto', 'sh', 'bash', 'node', 'python3', 'java -jar'],
                help: '"auto" executes the file directly (./name)',
            },
            {id: 'args', label: 'Arguments', placeholder: '--help'},
        ],
        run: async (ctx, params) => {
            const file = ctx.resources[0];
            const prefix = params.interpreter && params.interpreter !== 'auto' ? `${params.interpreter} ` : './';
            const command = `${prefix}${shellQuote(file.name)}${params.args ? ` ${params.args}` : ''}`;
            await openTerminal({cwd: dirname(file.path), command, label: file.name});
            return {kind: 'none'};
        },
    });
    registerAction({
        id: 'tools.exec', title: 'Run Command…', icon: '⌘',
        requires: ['exec'],
        menus: [{anchor: 'main/tools', group: '7_run', order: 30}],
        selection: {min: 0, kinds: ['file', 'dir']},
        params: [
            {id: 'cmd', label: 'Command', required: true, help: 'Must be allowlisted by the server'},
            {id: 'args', label: 'Arguments', placeholder: 'status --porcelain'},
        ],
        /* Captured (non-interactive) execution: the transcript opens read-only. */
        run: async (ctx, params) => {
            const cwd = targetFolder(ctx);
            const args = splitArgs(params.args);
            const result = await fs.exec(params.cmd, args, {cwd, signal: ctx.signal});
            const header = `$ ${params.cmd} ${args.join(' ')}`.trim();
            return {
                kind: 'document',
                title: `${params.cmd}.log`,
                languageId: 'plaintext',
                content: [
                    header,
                    `cwd ${cwd} · exit ${result?.code}`,
                    '',
                    result?.stdout || '',
                    result?.stderr ? `\n[stderr]\n${result.stderr}` : '',
                ].join('\n'),
            };
        },
    });
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
const serverActions = new Set();

function registerServerActions() {
     bus.on('actions:descriptors', (payload) => ingestDescriptors(payload?.actions));
     /* The /actions round trip may land before *or* after this module runs, so
        ask again rather than depend on the ordering. */
     fs.actions().then((payload) => ingestDescriptors(payload?.actions)).catch(() => { /* additive */
     });
}

function ingestDescriptors(descriptors) {
     for (const descriptor of descriptors || []) {
         if (!descriptor?.id || !descriptor.title) continue;
         if (serverActions.has(descriptor.id)) continue;
         serverActions.add(descriptor.id);
         registerAction({
             ...descriptor,
             run: (ctx, params) => runServerAction(descriptor, ctx, params),
         });
     }
}

/** Server paths are root-relative: '/src/a.kt' would be read as absolute. */
function relativePath(path) {
     return String(path ?? '').replace(/^\/+/, '');
}

async function runServerAction(descriptor, ctx, params) {
     const endpoint = descriptor.endpoint || {};
     const method = (endpoint.method || 'POST').toUpperCase();
     const query = new URLSearchParams();
     for (const [key, value] of Object.entries(params || {})) {
         if (value === undefined || value === null || value === '' || value === false) continue;
         query.append(key, value === true ? 'true' : String(value));
     }
     const key = endpoint.selectionParam || 'path';
     if (endpoint.sendSelection === 'paths') {
         for (const path of ctx.paths || []) query.append(key, relativePath(path));
     } else if (endpoint.sendSelection === 'first' && ctx.paths?.length) {
         query.append(key, relativePath(ctx.paths[0]));
     } else if (endpoint.sendSelection === 'folder') {
         const folder = relativePath(targetFolder(ctx));
         if (folder) query.append(key, folder);
     }
     const search = query.toString();
     const url = `${store.get().base}/${endpoint.op || ''}${search ? `?${search}` : ''}`;
     const response = await fetch(url, {method, headers: {'X-Fs-Api': '1'}, signal: ctx.signal});
     const payload = await response.json().catch(() => ({}));
     if (!response.ok) {
         throw Object.assign(new Error(payload?.error?.message || `${descriptor.title} failed`), {
             code: payload?.error?.code || 'EACTION',
         });
     }
     return serverActionResult(descriptor, payload, ctx);
}

/** Maps the CLI payload shapes onto §19.8 action results. */
async function serverActionResult(descriptor, payload, ctx) {
     if (!payload || typeof payload !== 'object') return {kind: 'none'};
     if (payload.kind) return payload;
     if (payload.url) {
         /* A popup opened from an awaited promise is usually blocked; offer it instead. */
         return {
             kind: 'toast', severity: 'info', message: `${descriptor.title}: session ready`,
             actions: [{label: 'Open', run: () => window.open(payload.url, '_blank', 'noopener')}],
         };
     }
     if (payload.task) {
         const task = await pollServerTask(payload.task, ctx);
         return {
             kind: 'document',
             title: `${task.kind}-${task.id}.log`,
             languageId: 'plaintext',
             content: [
                 `${task.kind} ${task.label}`,
                 `state ${task.state}${task.exitCode === null || task.exitCode === undefined ? '' : ` · exit ${task.exitCode}`}`,
                 '',
                 task.output || '(no output)',
             ].join('\n'),
         };
     }
     if (Array.isArray(payload.tasks)) {
         return {
             kind: 'document', title: 'tasks.log', languageId: 'plaintext',
             content: payload.tasks.map((t) => `${t.id}  ${t.state}  ${t.kind}  ${t.label}`).join('\n')
                 || '(no tasks yet)',
         };
     }
     return {
         kind: 'document', title: `${descriptor.id}.json`, languageId: 'json',
         content: JSON.stringify(payload, null, 2),
     };
}

async function pollServerTask(task, ctx) {
     let current = task;
     while (current?.state === 'running' && !ctx?.signal?.aborted) {
         ctx?.progress?.(`${current.kind}: ${current.state}`);
         // eslint-disable-next-line no-await-in-loop
         await new Promise((resolve) => setTimeout(resolve, 1500));
         // eslint-disable-next-line no-await-in-loop
         const response = await fetch(`${store.get().base}/tasks?id=${encodeURIComponent(current.id)}`, {
             headers: {'X-Fs-Api': '1'},
         }).catch(() => null);
         if (!response?.ok) break;
         // eslint-disable-next-line no-await-in-loop
         const payload = await response.json().catch(() => null);
         if (!payload?.task) break;
         current = payload.task;
     }
     return current;
}