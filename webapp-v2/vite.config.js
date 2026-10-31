import { defineConfig } from 'vite';
import { fileURLToPath } from 'node:url';

const m2core = fileURLToPath(new URL('./Markdown2/packages/core/src', import.meta.url));

export default defineConfig({
  resolve: {
    alias: [
      // `import ... from '@markdown2/core'` -> submodule TS source (Vite/esbuild compiles TS)
      { find: /^@markdown2\/core$/, replacement: `${m2core}/index.ts` },
    ],
  },
  optimizeDeps: {
    // Without this, Vite's dep scanner crawls every *.html under the root,
    // including Markdown2/apps/pwa/index.html, and chokes on monaco/plotly/etc.
    entries: ['index.html'],
  },
});
