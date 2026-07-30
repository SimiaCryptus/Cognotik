import {defineConfig} from 'vite';

/**
 * `base: './'` is mandatory: the client is served under an arbitrary path
 * prefix (/coding/, /chat/, /archive/...) — see reverse-spec §1.1.
 */
export default defineConfig({
    base: './',
    build: {
        outDir: 'build',
        target: 'es2020',
        sourcemap: false,
        assetsDir: 'static'
    },
    server: {
        port: 3000,
        strictPort: false
    }
});