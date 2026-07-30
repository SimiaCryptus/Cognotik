import {debounce} from '../util/debounce.js';
import {idle} from '../util/dom.js';
import {createLogger} from '../util/logger.js';
import {getAllTabStates, restoreTabStates, setTabActivationHook, updateTabsNow} from './tabs.js';
import {initCollapsibles, setCollapsibleExpandHook} from './collapsible.js';
import {highlightWithin} from './prism.js';
import {renderMermaid} from './mermaid.js';
import {typesetMath} from './mathjax.js';
import {bus, Events} from '../core/bus.js';

/**
 * reverse-spec §6 — DOM post-processing pipeline.
 * Debounced at 250ms, idempotent, ordered: later steps depend on earlier ones.
 */

const log = createLogger('Pipeline');

/** Steps 4–6 only: used when a tab/section reveals already-wired content (§6.3, §8.7). */
export function processRevealed(container) {
    if (!container) return;
    idle(() => highlightWithin(container));
    renderMermaid(container);
    setTimeout(() => typesetMath(container), 100);
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
        // 4. highlight visible, unprocessed code
        idle(() => highlightWithin(scope));
        // 5. mermaid
        renderMermaid(scope);
        // 6. MathJax, one macrotask behind mermaid (it measures the layout mermaid mutates)
        setTimeout(() => typesetMath(scope === document ? document.body : scope), 100);
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