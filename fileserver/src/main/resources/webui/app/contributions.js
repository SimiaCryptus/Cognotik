import {registerCommand, executeCommand, getCommand, isEnabled} from '../core/commands.js';
import {registerAction, registerGroup, presentationFor, allActions} from '../core/actions.js';
import {registerPanel} from '../core/registry.js';
import {registerEditor} from '../components/editors/EditorRegistry.js';
import {MonacoEditor} from '../components/editors/MonacoEditor.js';
import {PlainTextEditor} from '../components/editors/PlainTextEditor.js';
import {ImageViewer, BinaryPlaceholder} from '../components/editors/SimpleEditors.js';
import {HtmlPreview} from '../components/editors/HtmlPreview.js';
import {MarkdownPreview} from '../components/editors/MarkdownPreview.js';
import {TablePreview} from '../components/editors/TablePreview.js';
import {DocOpsStatusView} from '../components/editors/DocOpsStatusView.js';
import {openQuickPick} from '../components/overlays/QuickPick.js';
import {TerminalPanel} from '../components/terminal/TerminalPanel.js';
import {ChatSessionEditor, openChatTab, requestChatSession, withTheme} from '../components/chat/ChatSession.js';
import {tabs} from '../components/tabs/TabModel.js';
import {fs} from '../core/fsclient.js';
import {caps} from '../core/capabilities.js';
import {ui} from '../core/ui.js';
import {bus} from '../core/bus.js';
import {store} from '../core/store.js';
import {persist} from '../core/persist.js';
import {announce} from '../core/a11y.js';
import {raise} from '../core/errors.js';
import {copyText} from '../core/clipboard.js';
import {keysFor} from '../core/keymap.js';
import {basename, dirname, extname, join, validateName} from '../core/paths.js';
import {classicBase, publicUrl} from '../core/urls.js';
import {isDocOpsStatus} from '../core/sessions.js';
import {buildContext} from '../core/context.js';
import config from '../config.js';

/** Registered once from main.js; the shell knows none of this. */
export function registerContributions({shell}) {
    registerEditors();
    registerCoreCommands(shell);
    registerEditorActions();
    registerFileActions();
    registerTerminalActions(shell);
     registerChatActions();
    registerServerActions();
}

function registerEditors() {
     /* A hosted chat session. Highest priority, but only ever matched by a tab
        whose stat carries `chatUrl`, so nothing else changes (note #4). */
     registerEditor({
         id: ChatSessionEditor.id,
         priority: 300,
         canOpen: ChatSessionEditor.canOpen,
         create: (ctx) => new ChatSessionEditor(ctx),
     });
     /* Highest priority, but only ever selected by an explicit preview tab
        (its stat carries `previewUrl`), so editing is unaffected. */
     registerEditor({
         id: HtmlPreview.id,
         priority: 200,
         canOpen: HtmlPreview.canOpen,
         create: (ctx) => new HtmlPreview(ctx)
     });
     /* docops.status.json rendered as a navigable index of agent sessions. Like
        the previews below it is only ever matched by a preview tab (its stat
        carries `previewKind`), so the raw JSON still opens in Monaco. */
     registerEditor({
         id: DocOpsStatusView.id,
         priority: 270,
         canOpen: DocOpsStatusView.canOpen,
         create: (ctx) => new DocOpsStatusView(ctx),
     });
     /* Rendered Markdown / delimited text. Like HtmlPreview these are only ever
        matched by a preview tab (their stat carries `previewKind`), so the same
        document still opens in Monaco for editing. */
     registerEditor({
         id: MarkdownPreview.id,
         priority: 260,
         canOpen: MarkdownPreview.canOpen,
         create: (ctx) => new MarkdownPreview(ctx),
     });
     registerEditor({
         id: TablePreview.id,
         priority: 250,
         canOpen: TablePreview.canOpen,
         create: (ctx) => new TablePreview(ctx),
     });
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
         id: 'tree.revealActiveFile', title: 'Explorer: Reveal Active File', keys: ['Mod+Shift+J'],
         when: () => {
             const tab = tabs.activeTab();
             return !!tab && !tab.virtual;
         },
         run: async () => {
             const tab = tabs.activeTab();
             if (!tab || tab.virtual) {
                 ui.toast({severity: 'info', message: 'No file is open'});
                 return;
             }
             shell.showPanel('explorer');
             await ui.revealPath(tab.path);
             announce(`Revealed ${tab.name}`);
         },
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
             /* The keybinding preventDefault()s Escape, which suppresses the
                native <dialog> cancel: dismiss action/parameter dialogs here. */
             const {closeTopModal} = await import('../components/overlays/Modal.js');
             let closed = false;
             closed = closeContextMenu() || closed;
             closed = close() || closed;
             closed = closeTopModal() || closed;
             /* Nothing was stacked above: Escape dismisses the mobile drawer. */
             if (!closed) shell.closeDrawer?.();
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
         commandId: 'tree.revealActiveFile', title: 'Reveal Active File in Explorer', icon: '🎯',
         disabledReason: 'No file is open',
         menus: [
             {anchor: 'main/go', group: '1_open', order: 30},
             {anchor: 'tab/context', group: '1_open', order: 20},
         ],
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


/* ------------------------------------------------------------------ preview */
/** Documents the browser renders itself, inside a sandboxed iframe. */
const IFRAME_PREVIEW = new Set(['html', 'htm', 'xhtml', 'svg', 'pdf']);
/** Rendered by MarkdownPreview (marked + mermaid + MathJax, all from /lib/). */
const MARKDOWN_PREVIEW = new Set(['md', 'markdown', 'mdown', 'mkd', 'mkdn']);
/** Rendered by TablePreview. */
const TABLE_PREVIEW = new Set(['csv', 'tsv', 'tab']);
/** Rendered by ImageViewer (the real editor, not a virtual tab). */
const IMAGE_PREVIEW = new Set([
     'png', 'apng', 'jpg', 'jpeg', 'jfif', 'gif', 'webp', 'avif', 'bmp', 'ico', 'tif', 'tiff',
]);
/** Extensions worth rendering rather than editing (see 'file.view'). */
const PREVIEWABLE = new Set([
     ...IFRAME_PREVIEW, ...MARKDOWN_PREVIEW, ...TABLE_PREVIEW, ...IMAGE_PREVIEW,
]);

function previewKindFor(path) {
     const ext = extname(path);
     /* A status document is an index of sessions, not source. */
     if (isDocOpsStatus(path)) return 'docops-status';
     if (MARKDOWN_PREVIEW.has(ext)) return 'markdown';
     if (TABLE_PREVIEW.has(ext)) return 'table';
     if (IMAGE_PREVIEW.has(ext)) return 'image';
     return 'iframe';
}
/** Extensions for which "Run…" is meaningful; '' covers launchers (gradlew). */
const RUNNABLE = new Set([
     '', 'sh', 'bash', 'zsh', 'py', 'js', 'mjs', 'cjs', 'ts', 'rb', 'pl', 'php',
     'ps1', 'bat', 'cmd', 'jar', 'exe', 'kts', 'groovy', 'gradle',
]);

/**
  * Opens a read-only preview tab for `path`.
  *
  * HTML/SVG/PDF are hosted in a sandboxed iframe, Markdown and CSV/TSV are
  * rendered by their own editors, and an image simply opens in the image
  * viewer — it needs no second representation.
  */
function openPreviewTab(path) {
     const kind = previewKindFor(path);
     if (kind === 'image') return ui.openPath(path, {pinned: true});
     const previewPath = `fs-preview:${path}`;
     const stat = {
         path: previewPath, type: 'file', size: 0, readOnly: true,
         mimeType: 'text/html', sourcePath: path, title: basename(path),
     };
     if (kind === 'iframe') stat.previewUrl = publicUrl(path);
     else stat.previewKind = kind;
     return tabs.open(previewPath, {pinned: true, virtual: true, stat});
}

/** File/folder actions: one registration serves menubar, context menu and palette. */
function registerFileActions() {
     ui.openPreview = (path) => openPreviewTab(path);
    registerGroup({id: 'group:copy', title: 'Copy', anchor: 'explorer/context', group: '3_clipboard', order: 10});

    registerAction({
         id: 'file.open', title: 'Edit', icon: '✎',
         menus: [{anchor: 'explorer/context', group: '1_open', order: 10}],
        selection: {min: 1, max: 50, kinds: ['file', 'dir']},
         update: (ctx, p) => {
             /* "Edit" is meaningless for a folder — the same entry reveals it. */
             if (!ctx.files.length && ctx.dirs.length) p.text = 'Open';
             else if (ctx.files.length === 1 && !ctx.dirs.length) p.text = `Edit ${ctx.files[0].name}`;
         },
        run: async (ctx) => {
            for (const resource of ctx.resources) {
                if (resource.type !== 'file') {
                    await ui.revealPath(resource.path);
                } else {
                    /* "Edit" always means the source document — even for a
                       docops.status.json, whose session index is reached through
                       'docops.openSessions' instead (it would otherwise be
                       impossible to edit the raw JSON). */
                    await ui.openPath(resource.path, {pinned: true});
                }
            }
        },
    });
     registerAction({
         id: 'file.view', title: 'View', icon: '🌐',
         description: 'Render the document (Markdown, HTML, table, image) in a preview tab',
         menus: [
             {anchor: 'explorer/context', group: '1_open', order: 5},
             {anchor: 'tab/context', group: '1_open', order: 5},
         ],
         selection: {min: 1, max: 1, kinds: ['file']},
         /* Only documents a browser can render; everything else stays "Edit". */
         hideWhenDisabled: true,
         enablement: (ctx) => {
             const path = ctx.resources[0]?.path || '';
             /* A status document has its own first-class entry
                ('docops.openSessions'); claiming it here too would list
                "Open Sessions in …" twice in the same menu group. */
             return !isDocOpsStatus(path) && PREVIEWABLE.has(extname(path));
         },
         update: (ctx, p) => {
             const kind = previewKindFor(ctx.resources[0].path);
             if (kind === 'markdown' || kind === 'table') p.text = `Preview ${ctx.resources[0].name}`;
             else p.text = `View ${ctx.resources[0].name}`;
         },
         run: async (ctx) => {
             await ui.openPreview(ctx.resources[0].path);
             return {kind: 'none'};
         },
     });
     /**
      * A docops.status.json is a directory of live agent sessions, so it gets a
      * first-class entry of its own rather than hiding behind "View".
      */
     registerAction({
         id: 'docops.openSessions', title: 'Open Sessions…', icon: '🗂', category: 'Cognotik',
         description: 'List the agent sessions recorded in this status document',
         menus: [
             {anchor: 'explorer/context', group: '1_open', order: 4},
             {anchor: 'tab/context', group: '1_open', order: 4},
             {anchor: 'main/tools', group: '7_run', order: 26},
         ],
         selection: {min: 1, max: 1, kinds: ['file']},
         hideWhenDisabled: true,
         enablement: (ctx) => isDocOpsStatus(ctx.resources[0]?.path || ''),
         disabledReason: 'Select a docops.status.json file',
         update: (ctx, p) => {
             p.text = `Open Sessions in ${ctx.resources[0].name}`;
         },
         run: async (ctx) => {
             await ui.openPreview(ctx.resources[0].path);
             return {kind: 'none'};
         },
     });
     registerAction({
         id: 'file.openInNewTab', title: 'Open in New Browser Tab', icon: '↗',
         description: 'Open through the v2 FS API in a separate browser tab',
         menus: [
             {anchor: 'explorer/context', group: '8_export', order: 5},
             {anchor: 'tab/context', group: '8_export', order: 5},
             {anchor: 'main/file', group: '8_export', order: 5},
         ],
         selection: {min: 0, max: 1, kinds: ['file', 'dir']},
         run: (ctx) => {
             const resource = ctx.resources[0];
             const path = resource?.path || '/';
             /* A file gets the clean legacy path (real content type, no download);
                a folder has no such representation, so open this same SPA on the
                corresponding deep link instead. */
             const href = (!resource || resource.type === 'dir')
                 ? `${location.pathname}#${path.endsWith('/') ? path : `${path}/`}`
                 : publicUrl(path);
             window.open(href, '_blank', 'noopener');
             return {kind: 'none'};
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
        enablement: (ctx) => !caps.readOnly && writable(ctx.resources[0]),
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
        enablement: (ctx) => !caps.readOnly && ctx.resources.every(writable),
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
             /* navigator.clipboard rejects while the closing menu still owns
                focus, so go through the helper with its legacy fallback (#2). */
             const ok = await copyText(ctx.paths.join('\n'));
             return {
                 kind: 'toast', severity: ok ? 'info' : 'error',
                 message: ok ? `Copied ${ctx.paths.length} path(s)` : 'Could not access the clipboard',
             };
        },
    });

    registerAction({
        id: 'file.copyName', title: 'Copy Name',
        menus: [{anchor: 'group:copy', group: '3_clipboard', order: 20}],
        selection: {min: 1, kinds: ['file', 'dir']},
        run: async (ctx) => {
             const ok = await copyText(ctx.resources.map((r) => r.name).join('\n'));
             return {
                 kind: 'toast', severity: ok ? 'info' : 'error',
                 message: ok ? `Copied ${ctx.resources.length} name(s)` : 'Could not access the clipboard',
             };
        },
    });

    registerAction({
        id: 'file.download', title: 'Download',
        menus: [{anchor: 'explorer/context', group: '8_export', order: 10}, {anchor: 'tab/context', group: '8_export'}],
        selection: {min: 1, max: 1, kinds: ['file']},
         /* A folder has nothing to download: hide rather than grey out (#5). */
         hideWhenDisabled: true,
        run: (ctx) => download(fs.fileUrl(ctx.resources[0].path), ctx.resources[0].name),
    });

    registerAction({
        id: 'folder.downloadZip', title: 'Download as ZIP',
        requires: ['snapshot'],
        menus: [{anchor: 'explorer/context', group: '8_export', order: 20}, {anchor: 'main/file', group: '8_export'}],
        selection: {min: 0, max: 1, kinds: ['dir']},
         hideWhenDisabled: true,
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
            const base = classicBase();
            if (!base) return;
            const path = ctx.resources[0]?.path || '/';
            window.open(publicUrl(path), '_blank', 'noopener');
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
/**
* Whether the UI should let the user *attempt* to modify this resource.
*
* A folder we cannot write to is *not* a veto: individual files inside it may
* be whitelisted (`.writeable`), and creating/renaming/deleting those needs no
* permission on the parent. The server owns that decision and answers EACCES
* when it really means it, which surfaces as a toast — far better than an
* action greyed out on a guess.
*/
function writable(resource) {
    return !resource || resource.type === 'dir' || !resource.readOnly;
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
    /**
     * Mounts the panel *collapsed* and only reveals the dock once a session
     * actually exists, so a failed start never leaves an empty resizable strip
     * above the status bar (#1).
     */
    const openTerminal = async ({cwd = '/', command, label} = {}) => {
        const panel = shell.showBottomPanel('terminal', {reveal: false});
        if (!panel) return null;
        const session = await panel.openSession({cwd, command, label});
        if (session) {
            shell.setBottomVisible(true);
            panel.focus();
        }
        return session;
    };
    ui.openTerminal = openTerminal;
    registerCommand({
        id: 'view.toggleTerminal', title: 'View: Toggle Terminal', keys: ['Ctrl+`', 'Mod+`'],
         run: async () => {
              /* Collapsing must always work — even if the capability check below
                 would fail — otherwise the dock's own ▾ button is a no-op. */
              if (shell.isBottomVisible() && store.get().panels.bottom === 'terminal') {
                  shell.setBottomVisible(false);
                  return;
              }
             /* Never open the dock for a capability the server does not have:
                that is the empty strip above the status bar (#1/#7). */
             if (!caps.has('terminal')) {
                 ui.toast({severity: 'info', message: 'This server does not provide terminal sessions'});
                 return;
             }
             /* Reveal only once there is something to show, and let the panel
                collapse it again when the last session is closed (#7). */
             const panel = shell.showBottomPanel('terminal', {reveal: false});
             const session = panel && await panel.ensureSession();
             if (session) {
                 shell.setBottomVisible(true);
                 panel.focus();
             }
         },
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
         /* Folder-only: right-clicking a file must not offer it (#5). */
         selection: {min: 0, max: 1, kinds: ['dir']},
         hideWhenDisabled: true,
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
         hideWhenDisabled: true,
         /* Offering "Run" for a .txt is noise: restrict it to scripts,
            archives/binaries and extension-less launchers (#4). */
         enablement: (ctx) => RUNNABLE.has(extname(ctx.resources[0]?.path || '')),
         disabledReason: 'This file is not runnable',
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
/*
  * ------------------------------------------------------------------
  * Code chat (#4) and selection-edit prompts (#8)
  * ------------------------------------------------------------------
  */
/** Suggestions offered the first time, before any history exists. */
const EDIT_PROMPTS = [
     'Refactor for clarity',
     'Add documentation comments',
     'Add error handling',
     'Write unit tests for this',
     'Explain what this does',
];
function recentPrompts() {
     return persist.get('editPrompts', []) || [];
}
function rememberPrompt(prompt) {
     const list = [prompt, ...recentPrompts().filter((p) => p !== prompt)].slice(0, 20);
     persist.set('editPrompts', list);
     return list;
}
/** Selection first, otherwise whatever the editor is showing. */
function chatTargets(ctx) {
     if (ctx.paths?.length) return ctx.paths;
     const tab = ctx.activeTab || tabs.activeTab();
     return tab && !tab.virtual ? [tab.path] : [];
}
/**
  * Chat sessions are *documents*, so they open as tabs in the main editor area:
  * a second "Modify files" run no longer destroys the first session's UI, and
  * the bottom dock stays reserved for consoles (note #4).
  *
  * `ui.openChat` is also what server actions answering with a session URL use,
  * instead of a (blockable) pop-up.
  */
function registerChatActions() {
      const openChat = async ({paths = [], name, prompt, url} = {}) => {
          /* `url` short-circuits the round trip: a server action already made one. */
          const target = url || await requestChatSession({paths, name: name || prompt});
          return openChatTab({url: target, paths, name, prompt});
      };
      ui.openChat = openChat;
     registerAction({
         id: 'chat.openForSelection', title: 'Code Chat…', icon: '💬', category: 'Cognotik',
         description: 'Open a patch chat over the selected files',
         menus: [
             {anchor: 'explorer/context', group: '7_run', order: 25},
             {anchor: 'main/tools', group: '7_run', order: 25},
             {anchor: 'editor/context', group: '7_run', order: 25},
             {anchor: 'tab/context', group: '7_run', order: 25},
         ],
         selection: {min: 0, kinds: ['file', 'dir']},
         enablement: (ctx) => !caps.readOnly && chatTargets(ctx).length > 0,
         disabledReason: 'Open or select a file first',
         update: (ctx, p) => {
             const targets = chatTargets(ctx);
             if (targets.length === 1) p.text = `Code Chat about ${basename(targets[0])}…`;
             else if (targets.length > 1) p.text = `Code Chat about ${targets.length} items…`;
         },
         run: async (ctx) => {
             await openChat({paths: chatTargets(ctx)});
             return {kind: 'none'};
         },
     });
     registerEditAction(openChat);
}
/**
  * §19.8 — selection-edit: pick an instruction (recently used first), then open
  * a chat over the active file with the selection quoted into the prompt.
  */
function registerEditAction(openChat) {
     registerCommand({
         id: 'editor.editSelection', title: 'Edit Selection with AI…', keys: ['Mod+Alt+E'],
         when: (ctx) => !caps.readOnly && !!(ctx.activeTab && !ctx.activeTab.virtual),
         run: async (ctx) => {
             const recent = recentPrompts();
             const items = [
                 ...recent.map((p) => ({id: `recent:${p}`, label: p, description: 'recent'})),
                 ...EDIT_PROMPTS.filter((p) => !recent.includes(p))
                     .map((p) => ({id: `preset:${p}`, label: p, description: 'suggested'})),
                 {id: '__other', label: 'Other…', description: 'type a new instruction'},
             ];
             const choice = await openQuickPick({
                 title: 'Edit code with', placeholder: 'Pick or type an instruction', items,
             });
             if (!choice) return;
             let prompt = choice.label;
             if (choice.id === '__other') {
                 prompt = await ui.prompt({
                     title: 'Edit code', label: 'Instruction',
                     validate: (value) => (value && value.trim() ? null : 'An instruction is required'),
                 });
                 if (!prompt) return;
             }
             rememberPrompt(prompt);
             const tab = ctx.activeTab || tabs.activeTab();
             const selection = ctx.editorSelection;
             const ranged = selection && !selection.isEmpty;
             const scope = ranged
                 ? `lines ${selection.ranges[0].startLine}-${selection.ranges[0].endLine}`
                 : 'the whole file';
             await openChat({
                 paths: [tab.path],
                 name: prompt,
                 prompt: `${prompt}\n\nApply this to ${basename(tab.path)} (${scope}).`
                     + (ranged ? `\n\n\`\`\`\n${selection.text}\n\`\`\`` : ''),
             });
         },
     });
     commandAction({
         commandId: 'editor.editSelection', title: 'Edit Selection with AI…', icon: '✨',
         disabledReason: 'Open an editable file first',
         menus: [
             {anchor: 'editor/selection', group: '7_run', order: 10},
             {anchor: 'editor/context', group: '7_run', order: 10},
             {anchor: 'main/selection', group: '7_run', order: 10},
         ],
     });
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
            resolveParam: (paramId, ctx) => resolveServerParamOptions(descriptor, paramId, ctx),
             run: (ctx, params) => runServerAction(descriptor, ctx, params),
         });
     }
}
/**
* Live options for a "checkbox list" parameter (server-side `ActionParam.dynamic`):
* calls the action's own endpoint again with `resolveParam=<id>` plus the current
* selection, so a server-side callback (e.g. DocOps enumerating targets for the
* selected documents) can answer with no bespoke client-side integration.
*/
async function resolveServerParamOptions(descriptor, paramId, ctx) {
     const endpoint = descriptor.endpoint || {};
     const method = (endpoint.method || 'POST').toUpperCase();
     const query = new URLSearchParams();
     query.set('resolveParam', paramId);
     const key = endpoint.selectionParam || 'path';
     if (endpoint.sendSelection === 'paths') {
         for (const path of ctx.paths || []) query.append(key, relativePath(path));
     } else if (endpoint.sendSelection === 'first' && ctx.paths?.length) {
         query.append(key, relativePath(ctx.paths[0]));
     } else if (endpoint.sendSelection === 'folder') {
         const folder = relativePath(targetFolder(ctx));
         if (folder) query.append(key, folder);
     }
     const url = `${store.get().base}/${endpoint.op || ''}?${query.toString()}`;
     const response = await fetch(url, {method, headers: {'X-Fs-Api': '1'}, signal: ctx.signal});
     const payload = await response.json().catch(() => ({}));
     if (!response.ok) {
         throw Object.assign(new Error(payload?.error?.message || `Could not load options for ${paramId}`), {
             code: payload?.error?.code || 'EACTION',
         });
     }
     return payload?.options || [];
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
         /* A 'checklist' parameter (e.g. DocOps targets) is a repeated query parameter. */
         if (Array.isArray(value)) {
             for (const item of value) {
                 if (item === undefined || item === null || item === '') continue;
                 query.append(key, String(item));
             }
             continue;
         }
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
         /* Chat sessions (modify) are hosted as a tab of their own: the
            window.open() below happens after an await, which is exactly what a
            strict pop-up blocker refuses (#4). */
         if (typeof ui.openChat === 'function') {
             const hosted = await ui.openChat({url: payload.url, name: descriptor.title});
             if (hosted) {
                 return {
                     kind: 'toast', severity: 'info',
                     message: `${descriptor.title}: session opened in a new tab`,
                 };
             }
         }
         const opened = window.open(payload.url, '_blank', 'noopener');
         if (opened) {
             return {
                 kind: 'toast', severity: 'info',
                 message: `${descriptor.title}: session opened in a new tab`,
             };
         }
         return {
             kind: 'toast', severity: 'warn',
             message: `${descriptor.title}: session ready — allow pop-ups to open it automatically`,
             /* Carry the workspace theme across, exactly as the hosted tab does (#3). */
             actions: [{label: 'Open', run: () => window.open(withTheme(payload.url), '_blank', 'noopener')}],
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