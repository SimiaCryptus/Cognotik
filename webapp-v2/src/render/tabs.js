import {debounce} from '../util/debounce.js';
import {directChildren, nextFrame} from '../util/dom.js';
import {createLogger} from '../util/logger.js';
import {initCollapsibles} from './collapsible.js';

/** reverse-spec §8 — dynamic, arbitrarily nested tabs with sticky per-container state. */

const log = createLogger('TabSystem');

/** containerId -> { containerId, activeTab } */
const tabStates = new Map();
/** containerId -> last 10 distinct activations */
const tabStateHistory = new Map();
let stateVersion = 0;
let mutationInProgress = false;

const diagnostics = {
    saveCount: 0,
    restoreCount: 0,
    restoreSuccess: 0,
    restoreFail: 0,
    setupErrors: 0,
    restoreErrors: 0,
    saveErrors: 0,
    updateErrors: 0
};

/** Set by the pipeline: newly revealed content must be re-processed (§8.7). */
let onTabActivated = () => {
};

export function setTabActivationHook(fn) {
    onTabActivated = typeof fn === 'function' ? fn : () => {
    };
}

export function getTabDiagnostics() {
    return {...diagnostics, stateVersion, containers: tabStates.size};
}

export function getAllTabStates() {
    return new Map(tabStates);
}

/* --------------------------------------------------------------- selectors */

/** Panes are matched only among DIRECT children so nested panes are never stolen (§8.1). */
function contentPanes(container) {
    return directChildren(container, '.tab-content');
}

function ownTabsGroup(container) {
    return directChildren(container, '.tabs')[0] || null;
}

function ownButtons(container) {
    const group = ownTabsGroup(container);
    if (group) return directChildren(group, '.tab-button');
    return Array.from(container.querySelectorAll('.tabs > .tab-button'))
        .filter((button) => button.closest('.tabs-container') === container);
}

function groupButtons(group) {
    return Array.from(group.querySelectorAll('.tab-button'))
        .filter((button) => button.closest('.tabs') === group);
}

function ensureContainerId(container) {
    if (container.id) return container.id;
    const generated = `tab-container-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`;
    container.id = generated;
    log.warn('Tab container had no id; generated one', {id: generated});
    return generated;
}

/* ------------------------------------------------------------------- state */

function saveTabState(containerId, activeTab) {
    try {
        tabStates.set(containerId, {containerId, activeTab});
        const history = tabStateHistory.get(containerId) || [];
        if (history[history.length - 1] !== activeTab) {
            history.push(activeTab);
            while (history.length > 10) history.shift();
            tabStateHistory.set(containerId, history);
        }
        stateVersion += 1;
        diagnostics.saveCount += 1;
    } catch (err) {
        diagnostics.saveErrors += 1;
        log.error('Failed to save tab state', {containerId, activeTab, error: err});
    }
}

/* -------------------------------------------------------------- activation */

export function setActiveTab(button, container) {
    try {
        const forTab = button?.dataset?.forTab;
        if (!forTab) {
            log.warn('Tab button is missing data-for-tab', {containerId: container?.id});
            return false;
        }
        if (!container?.id) {
            log.warn('Cannot activate tab: container has no id', {forTab});
            return false;
        }

        const group = button.closest('.tabs');
        if (!group) {
            log.warn('Tab button is not inside a .tabs group', {forTab, containerId: container.id});
            return false;
        }

        const panes = contentPanes(container);
        const targetPane = panes.find((pane) => pane.dataset.tab === forTab) || null;
        const current = tabStates.get(container.id);
        if (current?.activeTab === forTab && targetPane?.classList.contains('active')) {
            return true; // already active — no-op (§8.3.3)
        }

        saveTabState(container.id, forTab);

        // Re-initialise nested containers inside the owning group (§8.3.5).
        group.querySelectorAll('.tabs-container').forEach((nested) => setupTabContainer(nested));

        for (const candidate of groupButtons(group)) {
            const match = candidate.dataset.forTab === forTab;
            candidate.classList.toggle('active', match);
            candidate.setAttribute('aria-selected', match ? 'true' : 'false');
            if (match) void candidate.offsetWidth; // force reflow (§8.3.6)
        }

        for (const pane of panes) {
            const match = pane.dataset.tab === forTab;
            pane.classList.toggle('active', match);
            pane.style.display = match ? '' : 'none';
        }

        if (targetPane) {
            nextFrame(() => {
                targetPane.querySelectorAll('.tabs-container').forEach((nested) => {
                    setupTabContainer(nested);
                    restoreTabState(nested);
                });
                onTabActivated(targetPane); // §8.7 — re-run §6 steps 4–6
            });
        }
        return true;
    } catch (err) {
        diagnostics.setupErrors += 1;
        log.error('setActiveTab failed', {containerId: container?.id, error: err});
        return false;
    }
}

/* ------------------------------------------------------------- restoration */

export function restoreTabState(container) {
    try {
        if (!container?.id) return false;
        diagnostics.restoreCount += 1;

        const saved = tabStates.get(container.id);
        const buttons = ownButtons(container);

        if (saved) {
            const match = buttons.find((button) => button.dataset.forTab === saved.activeTab);
            if (match) {
                diagnostics.restoreSuccess += 1;
                return setActiveTab(match, container);
            }
            log.warn('Saved tab has no matching button; falling back to first', {
                containerId: container.id,
                savedTab: saved.activeTab
            });
        } else {
            log.debug('No saved state for container; falling back to first', {containerId: container.id});
        }

        diagnostics.restoreFail += 1;
        const first = buttons[0];
        if (!first) {
            log.warn('Tab container has no tab buttons', {containerId: container.id});
            return false;
        }
        return setActiveTab(first, container);
    } catch (err) {
        diagnostics.restoreErrors += 1;
        log.error('restoreTabState failed', {containerId: container?.id, error: err});
        return false;
    }
}

/** Pipeline step 1 (§6.1): reapply remembered active tabs from a snapshot. */
export function restoreTabStates(snapshot = tabStates) {
    for (const [containerId, state] of snapshot) {
        const container = document.getElementById(containerId);
        if (!container || !container.classList.contains('tabs-container')) continue;
        if (!tabStates.has(containerId)) tabStates.set(containerId, state);
        restoreTabState(container);
    }
}

/* ------------------------------------------------------------------- setup */

export function setupTabContainer(container) {
    if (!container) return;
    try {
        ensureContainerId(container);
        if (container.dataset.tabSystemInitialized === 'true') return;
        container.dataset.tabSystemInitialized = 'true';

        const buttons = ownButtons(container);
        const panes = contentPanes(container);
        const remembered = tabStates.get(container.id)?.activeTab;
        const domActive = buttons.find((button) => button.classList.contains('active'))?.dataset.forTab;
        const resolved = remembered || domActive || buttons[0]?.dataset.forTab || null;

        for (const button of buttons) {
            const match = !!resolved && button.dataset.forTab === resolved;
            button.classList.toggle('active', match);
            button.setAttribute('role', button.getAttribute('role') || 'tab');
            button.setAttribute('aria-selected', match ? 'true' : 'false');
        }
        for (const pane of panes) {
            const match = !!resolved && pane.dataset.tab === resolved;
            pane.classList.toggle('active', match);
            pane.style.display = match ? '' : 'none';
        }
        if (resolved) saveTabState(container.id, resolved);

        // A SINGLE delegated listener per container. stopPropagation prevents an
        // outer container from also handling a nested container's click (§8.6).
        container.addEventListener('click', (event) => {
            const button = event.target.closest?.('.tab-button');
            if (!button || !container.contains(button)) return;
            if (button.classList.contains('active')) return;
            if (!button.closest('.tabs')) return;
            setActiveTab(button, container);
            updateTabs();
            event.stopPropagation();
            event.preventDefault();
        });
    } catch (err) {
        diagnostics.setupErrors += 1;
        log.error('setupTabContainer failed', {containerId: container?.id, error: err});
    }
}

/* --------------------------------------------------------------- discovery */

export function updateTabsNow() {
    if (mutationInProgress) return;
    mutationInProgress = true;
    try {
        initCollapsibles(document);
        const snapshot = getAllTabStates();
        const seen = new Set();

        document.querySelectorAll('.tabs-container').forEach((container) => {
            setupTabContainer(container);
            if (seen.has(container.id)) return;
            seen.add(container.id);

            const active =
                tabStates.get(container.id)?.activeTab ??
                snapshot.get(container.id)?.activeTab ??
                ownButtons(container).find((b) => b.classList.contains('active'))?.dataset.forTab;

            if (active) {
                saveTabState(container.id, active);
                restoreTabState(container);
                return;
            }
            const first = ownButtons(container)[0];
            if (first) setActiveTab(first, container);
            else log.warn('Tab container with no buttons', {containerId: container.id});
        });
    } catch (err) {
        diagnostics.updateErrors += 1;
        log.error('updateTabs failed', err);
    } finally {
        mutationInProgress = false;
    }
}

/** Debounced at 250ms (§8.5). */
export const updateTabs = debounce(updateTabsNow, 250);

/**
 * §6.2 — catches `.tabs-container` nodes injected by server HTML after paint.
 */
export function observeForTabs(root) {
    if (!root || typeof MutationObserver === 'undefined') return () => {
    };
    const observer = new MutationObserver((records) => {
        for (const record of records) {
            for (const node of record.addedNodes) {
                if (node.nodeType !== 1) continue;
                if (node.matches?.('.tabs-container') || node.querySelector?.('.tabs-container')) {
                    updateTabs();
                    return;
                }
            }
        }
    });
    observer.observe(root, {childList: true, subtree: true});
    return () => observer.disconnect();
}