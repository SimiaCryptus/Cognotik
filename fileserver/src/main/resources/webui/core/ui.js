import {announce} from './a11y.js';

/**
 * The surface an action/command may touch. Components fill these in at boot,
 * so contributed code never needs the DOM.
 */
export const ui = {
    announce,
    toast() { /* replaced by Toasts */
    },
    confirm: async () => true,
    prompt: async () => null,
    form: async () => null,
    quickPick: async () => null,
    openPath: async () => null,
    revealPath: async () => null,
    openVirtualDocument: async () => null,
    refresh: async () => null,
    /** Replaced by the terminal panel; resolves to the created session. */
    openTerminal: async () => null,
    focusPanel() {
    },
    setSidebar() {
    },
    task() {
        return {
            done() {
            }, progress() {
            }
        };
    },
};