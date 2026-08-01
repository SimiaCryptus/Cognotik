/** Panels, status items and tree decorators contributed by features/hosts. */
const panels = new Map();
const statusItems = [];
const treeDecorators = [];

export function registerPanel(spec) {
    if (!spec?.id || !spec.title) throw new Error('a panel requires id and title');
    panels.set(spec.id, {location: 'sidebar', order: 100, ...spec});
    return panels.get(spec.id);
}

export function getPanel(id) {
    return panels.get(id);
}

export function allPanels(location) {
    return Array.from(panels.values())
        .filter((panel) => !location || panel.location === location)
        .sort((a, b) => a.order - b.order);
}

export function registerStatusItem(spec) {
    statusItems.push({order: 100, ...spec});
    return spec;
}

export function allStatusItems() {
    return statusItems.slice().sort((a, b) => a.order - b.order);
}

export function registerTreeDecorator(fn) {
    treeDecorators.push(fn);
    return fn;
}

export function decorate(node) {
    const out = [];
    for (const decorator of treeDecorators) {
        try {
            const value = decorator(node);
            if (value) out.push(value);
        } catch (e) {
            console.warn('tree decorator failed', e);
        }
    }
    return out;
}
