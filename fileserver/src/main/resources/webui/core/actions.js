import {registerCommand, executeCommand, missingCapabilities} from './commands.js';
import {caps} from './capabilities.js';
import {ui} from './ui.js';
import {announce} from './a11y.js';
import {FsError} from './errors.js';
import {bus} from './bus.js';

const actions = new Map();
const anchors = new Map();   // anchor -> [placement]
const groups = new Map();    // group id -> {id, title, anchor, group, order}
const knownAnchors = new Set([
    'main/file', 'main/edit', 'main/selection', 'main/view', 'main/go', 'main/tools', 'main/help',
    'main/toolbar', 'explorer/context', 'explorer/toolbar', 'explorer/empty',
    'tab/context', 'editor/context', 'editor/selection', 'editor/gutter',
    'breadcrumb/context', 'statusbar', 'git/context',
]);

export const DEFAULT_GROUP = '5_tools';

export function registerMenuAnchor(id) {
    knownAnchors.add(id);
    return id;
}

export function anchorExists(id) {
    return knownAnchors.has(id);
}

function place(anchor, placement) {
    if (!anchor) return;
    if (!anchors.has(anchor)) anchors.set(anchor, []);
    anchors.get(anchor).push(placement);
}

export function registerGroup(spec) {
    if (!spec?.id || !spec.title) throw new Error('a group requires id and title');
    const group = {group: DEFAULT_GROUP, order: 100, ...spec};
    groups.set(group.id, group);
    registerMenuAnchor(group.id);
    if (group.anchor) place(group.anchor, {submenu: group.id, group: group.group, order: group.order});
    return group;
}

export function registerAction(spec) {
    if (!spec?.id) throw new Error('an action requires an id');
    /* Text is mandatory — icon-only registrations are rejected (§19.13). */
    if (!spec.title || typeof spec.title !== 'string') {
        console.error(`action ${spec.id} has no title; falling back to its id`);
        spec = {...spec, title: spec.id};
    }
    const action = {
        selection: {min: 0, max: Infinity, kinds: ['file', 'dir'], collapseDescendants: true, onTruncated: 'ancestor'},
        preview: true,
        singleton: true,
        modal: false,
        ...spec,
    };
    actions.set(action.id, action);
    registerCommand({
        id: action.id,
        title: action.title,
        category: action.category,
        keys: action.keys || [],
        requires: action.requires || [],
        paletteHidden: action.paletteHidden,
        isAction: true,
        when: (ctx) => presentationFor(action, ctx).enabled,
        run: (ctx) => invoke(action, ctx),
    });
    for (const menu of action.menus || []) {
        place(menu.anchor, {actionId: action.id, group: menu.group || DEFAULT_GROUP, order: menu.order ?? 100});
    }
    return action;
}

export function getAction(id) {
    return actions.get(id);
}

export function allActions() {
    return Array.from(actions.values());
}

/** Mirrors isEnabledAndVisible, tuned for discoverability (§19.5). */
export function presentationFor(action, ctx) {
    const p = {
        visible: true, enabled: true, text: action.title, description: action.description,
        icon: action.icon, checked: false, badge: null, disabledReason: null,
    };
    const missing = missingCapabilities({requires: action.requires || []});
    if (missing.length) {
        p.visible = false;
        p.enabled = false;
        p.disabledReason = `Requires ${missing.join(', ')}`;
        return p;
    }
    const sel = action.selection || {};
    const count = ctx.resources.length;
    const disable = (reason) => {
        p.enabled = false;
        p.disabledReason = reason;
    };

    if (sel.min != null && count < sel.min) {
        disable(sel.min === 1 ? 'Select an item first' : `Select at least ${sel.min} items`);
    } else if (sel.max != null && count > sel.max) {
        disable(`Select at most ${sel.max} items`);
    } else if (sel.kinds && ctx.resources.some((r) => !sel.kinds.includes(r.type))) {
        disable(sel.kinds.includes('dir') ? 'Only folders are supported here' : 'Only files are supported here');
    } else if (sel.homogeneous && ctx.files.length && ctx.dirs.length) {
        disable('Select files or folders, not both');
    } else if (action.requiresEditorSelection && !(ctx.editorSelection && !ctx.editorSelection.isEmpty)) {
        disable('Select some text first');
    } else if (action.requiresEditor && ctx.editor?.kind !== action.requiresEditor) {
        disable(`Requires the ${action.requiresEditor} editor`);
    } else if (ctx.truncated && sel.onTruncated === 'reject') {
        disable(`Too many items selected (limit ${caps.limits.maxContextResources})`);
    } else if (action.enablement) {
        try {
            if (!action.enablement(ctx)) disable(action.disabledReason || 'Not available here');
        } catch (e) {
            console.warn(`enablement() of ${action.id} threw`, e);
            disable('Unavailable');
        }
    }
    if (p.enabled && action.update) {
        try {
            action.update(ctx, p);
        } catch (e) {
            console.warn(`update() of ${action.id} threw`, e);
            disable('Unavailable');
        }
    }
    return p;
}

/** Ordered groups for one anchor; each group is separated by a role=separator. */
export function actionsForAnchor(anchor, ctx) {
    const placements = (anchors.get(anchor) || []).slice();
    const byGroup = new Map();
    for (const placement of placements) {
        const entry = placement.submenu
            ? submenuEntry(placement, ctx)
            : itemEntry(placement, ctx);
        if (!entry) continue;
        const key = placement.group || DEFAULT_GROUP;
        if (!byGroup.has(key)) byGroup.set(key, []);
        byGroup.get(key).push(entry);
    }
    return Array.from(byGroup.entries())
        .sort((a, b) => String(a[0]).localeCompare(String(b[0])))
        .map(([group, items]) => ({
            group,
            items: items.sort((a, b) => (a.order - b.order) || a.presentation.text.localeCompare(b.presentation.text)),
        }))
        .filter((group) => group.items.length);
}

function itemEntry(placement, ctx) {
    const action = actions.get(placement.actionId);
    if (!action) return null;
    const presentation = presentationFor(action, ctx);
    if (!presentation.visible) return null;
    return {kind: 'item', action, presentation, order: placement.order ?? 100};
}

function submenuEntry(placement, ctx) {
    const group = groups.get(placement.submenu);
    if (!group) return null;
    const children = actionsForAnchor(group.id, ctx);
    if (!children.length) return null;
    return {
        kind: 'submenu',
        submenu: group,
        children,
        presentation: {visible: true, enabled: true, text: group.title, icon: group.icon, disabledReason: null},
        order: placement.order ?? 100,
    };
}

const running = new Map();

/** Runs an action: parameter dialog, task/progress, then result handling. */
export async function invoke(action, ctx) {
    if (action.singleton && running.has(action.id)) {
        ui.toast({severity: 'warn', message: `"${action.title}" is already running`});
        return undefined;
    }
    let params = {};
    if (action.params?.length) {
       params = await ui.form({
           title: action.title, params: action.params, ctx, remember: action.id,
           resolveOptions: action.resolveParam ? (param) => action.resolveParam(param.id, ctx) : undefined,
       });
        /* Cancelled (or dismissed) dialogs never run the action. */
        if (params === null || params === undefined) return undefined;
    }
    const controller = new AbortController();
    const task = ui.task({label: action.title, cancel: () => controller.abort()});
    running.set(action.id, task);
    const runCtx = {...ctx, signal: controller.signal, progress: (message, value) => task.progress(message, value)};
    try {
        const result = await action.run(runCtx, params);
        await handleResult(result, action, runCtx);
        return result;
    } finally {
        running.delete(action.id);
        task.done();
    }
}

/** §19.8 — what a tool can put on screen. Unknown kinds are ignored. */
export async function handleResult(result, action, ctx) {
    if (!result || result.kind === 'none') return;
    switch (result.kind) {
        case 'toast':
            ui.toast({severity: result.severity || 'info', message: result.message, actions: result.actions});
            announce(result.message);
            break;
        case 'refresh':
            for (const path of result.paths || []) await ui.refresh(path);
            break;
        case 'open':
            for (const path of result.paths || []) {
                await ui.openPath(path, {pinned: result.pinned !== false, line: result.line, col: result.col});
            }
            break;
        case 'document':
            await ui.openVirtualDocument(result);
            announce(`Opened ${result.title}`);
            break;
        case 'edits': {
            const editor = ctx.editor;
            if (!editor?.applyEdits) throw new FsError('EACTION', {
                syscall: action.id,
                message: 'no editor to apply edits to'
            });
            if (action.preview !== false) {
                const ok = await ui.confirm({
                    title: result.undoLabel || action.title,
                    body: `Apply ${result.edits.length} edit(s) to the open buffer?`,
                    confirmLabel: 'Apply',
                });
                if (!ok) return;
            }
            editor.applyEdits(result.edits, {undoLabel: result.undoLabel || action.title});
            announce(`${result.edits.length} edits applied`);
            break;
        }
        default:
            bus.emit('action:result', {action: action.id, result});
    }
}