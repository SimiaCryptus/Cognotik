import {parseHtml} from '../util/dom.js';
import {isReferenceId} from '../core/store.js';

/** reverse-spec §5 — pointer-based div substitution for `z*` reference messages. */

/**
 * Breadth-first, single pass. Injected children are enqueued as the walk proceeds,
 * so nested references expand transitively; `seen` terminates cycles.
 *
 * @returns {{html: string, refs: Set<string>}} expanded HTML + the refs it consumed.
 */
export function expandReferences(html, store, seen = new Set()) {
    const root = parseHtml(html);
    const refs = new Set();
    const queue = [root];

    while (queue.length) {
        const node = queue.shift();
        const id = node.getAttribute ? node.getAttribute('message-id') : null;

        if (id && !seen.has(id) && isReferenceId(id)) {
            seen.add(id);
            refs.add(id);
            const ref = store.get(id);
            if (ref && ref.content) {
                node.innerHTML = ref.content; // may itself contain placeholders
            } else if (ref) {
                node.innerHTML = '<span class="reference-error">Referenced content unavailable</span>';
            } else {
                node.innerHTML = '<span class="reference-error">Referenced message not found</span>';
            }
        }

        for (const child of node.children) queue.push(child);
    }

    return {html: root.innerHTML, refs};
}