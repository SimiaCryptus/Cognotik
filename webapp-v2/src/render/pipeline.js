import {debounce} from '../util/debounce.js';
import {idle} from '../util/dom.js';
import {createLogger} from '../util/logger.js';
import {getAllTabStates, restoreTabStates, setTabActivationHook, updateTabsNow} from './tabs.js';
import {initCollapsibles, setCollapsibleExpandHook} from './collapsible.js';
import {highlightWithin} from './prism.js';
import {renderIslands} from './islands.js';
import {bus, Events} from '../core/bus.js';

/**
 * reverse-spec §6 — DOM post-processing pipeline.
 * Debounced at 250ms, idempotent, ordered: later steps depend on earlier ones.
*
* Diagrams and maths are no longer wired by hand: Markdown2 renders them
* (render/islands.js, render/markdown2/).
 */

const log = createLogger('Pipeline');

/** Steps 4–5 only: used when a tab/section reveals already-wired content (§6.3, §8.7). */
export function processRevealed(container) {
    if (!container) return;
    renderIslands(container);
    idle(() => highlightWithin(container));
}

export function runPipelineNow(container = document) {
    const scope = container || document;
    try {
        // 1. reapply remembered active tabs
        restoreTabStates(getAllTabStates());
        // 2. discover/initialise new containers
        updateTabsNow();
        // 3. idempotent collapsible wiring
        initCollapsibles(scope);
        // 4. Markdown2 islands (diagrams, maths, Markdown2 source) — claimed before Prism
        renderIslands(scope);
        // 5. highlight visible, unprocessed code
        idle(() => highlightWithin(scope));
    } catch (err) {
        log.error('Pipeline failed', err);
    }
}

export const runPipeline = debounce(runPipelineNow, 250);

/**
 * Wires the hooks that let render/ modules request re-processing without importing
 * the pipeline (avoids a cycle) and subscribes to the §6.2 triggers we own.
 */
export function installPipeline() {
    setTabActivationHook((pane) => processRevealed(pane));
    setCollapsibleExpandHook((content) => processRevealed(content));
    bus.on(Events.VERBOSE_CHANGED, () => runPipeline());
    bus.on(Events.MODAL_CONTENT_READY, (event) => runPipelineNow(event.detail));
    return () => runPipeline.cancel();
}