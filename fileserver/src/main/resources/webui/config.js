/**
 * Single mutable configuration object. A deployment may overwrite fields from the
 * host page *before* app/main.js runs, e.g.
 *   <script type="module">
 *     import config from '/ui/config.js';
 *     config.monaco.base = '/ui/vendor/monaco/vs';
 *   </script>
 */
const config = {
    /** Air-gapped installs point `base` at /ui/vendor/monaco/vs. */
    monaco: {
        enabled: true,
        base: 'https://cdn.jsdelivr.net/npm/monaco-editor@0.55.1/min/vs',
        maxBytes: 8 * 1024 * 1024,
    },
    /** Files above this open read-only-by-default with an "Edit anyway" affordance. */
    largeFileBytes: 2 * 1024 * 1024,
    /** Virtualise the tree beyond this many visible rows. */
    treeVirtualiseAfter: 500,
    /** Trailing window used to coalesce watch events. */
    watchCoalesceMs: 100,
    /**
     * Terminal panel. `xterm` URLs may be re-pointed at /ui/vendor/... for
     * air-gapped installs; if they cannot be fetched the panel silently falls
     * back to an accessible <pre> + <input> line console.
     */
    terminal: {
        enabled: true,
        scrollback: 5000,
        defaultHeight: 260,
        xterm: {
            enabled: true,
            css: 'https://cdn.jsdelivr.net/npm/xterm@5.3.0/css/xterm.css',
            js: 'https://cdn.jsdelivr.net/npm/xterm@5.3.0/lib/xterm.js',
            fit: 'https://cdn.jsdelivr.net/npm/xterm-addon-fit@0.8.0/lib/xterm-addon-fit.js',
        },
    },
    quickOpen: {maxEntries: 20000},
    features: {git: true, search: true, watch: true},
};
export default config;