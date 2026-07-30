import {createLogger} from '../util/logger.js';

/** reverse-spec §9 — collapsible sections. Idempotent; flag lives in dataset (fixes §20.12). */

const log = createLogger('Collapsible');
const BOUND_FLAG = 'collapsibleBound';

/** Set by the pipeline so revealing content re-runs Prism/Mermaid/MathJax (§9). */
let onExpandHook = () => {
};

export function setCollapsibleExpandHook(fn) {
    onExpandHook = typeof fn === 'function' ? fn : () => {
    };
}

function syncIcon(content, icon) {
    if (!icon) return;
    const expanded = !!content && content.classList.contains('expanded');
    icon.classList.toggle('expanded', expanded);
    icon.textContent = expanded ? '▲' : '▼';
}

export function toggleCollapsible(header) {
    const content = header.nextElementSibling;
    const icon = header.querySelector('.expand-icon');
    if (!content) {
        log.warn('Header has no adjacent .expandable-content');
        return;
    }
    content.classList.toggle('expanded');
    syncIcon(content, icon);
    if (content.classList.contains('expanded')) onExpandHook(content);
}

export function initCollapsibles(root = document) {
    const headers = (root || document).querySelectorAll('.expandable-header');
    headers.forEach((header) => {
        const content = header.nextElementSibling;
        const icon = header.querySelector('.expand-icon');

        if (header.dataset[BOUND_FLAG] === 'true') {
            syncIcon(content, icon); // re-encounter: only re-sync the glyph
            return;
        }

        header.dataset[BOUND_FLAG] = 'true';
        header.setAttribute('role', header.getAttribute('role') || 'button');
        header.setAttribute('tabindex', header.getAttribute('tabindex') || '0');
        header.addEventListener('click', () => toggleCollapsible(header));
        header.addEventListener('keydown', (event) => {
            if (event.key !== 'Enter' && event.key !== ' ') return;
            event.preventDefault();
            toggleCollapsible(header);
        });
        syncIcon(content, icon);
    });
}