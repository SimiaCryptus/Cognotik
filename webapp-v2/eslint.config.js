export default [
        {
            files: ['src/**/*.js'],
            languageOptions: {
                ecmaVersion: 2022,
                sourceType: 'module',
                globals: {
                    window: 'readonly',
                    document: 'readonly',
                    localStorage: 'readonly',
                    location: 'readonly',
                    fetch: 'readonly',
                    WebSocket: 'readonly',
                    CustomEvent: 'readonly',
                    Event: 'readonly',
                    EventTarget: 'readonly',
                    IntersectionObserver: 'readonly',
                    MutationObserver: 'readonly',
                    requestAnimationFrame: 'readonly',
                    requestIdleCallback: 'readonly',
                    setTimeout: 'readonly',
                    clearTimeout: 'readonly',
                    setInterval: 'readonly',
                    clearInterval: 'readonly',
                    console: 'readonly',
                    btoa: 'readonly',
                    crypto: 'readonly'
                }
            },
            rules: {
                'no-unused-vars': ['warn', {argsIgnorePattern: '^_'}],
                'no-debugger': 'warn'
            }
        }
    ];