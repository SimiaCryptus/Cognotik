// .eslintrc.js
module.exports = {
    env: {
        browser: true,
        es2021: true,
        node: true,
        jest: true,
        'cypress/globals': true
    },
    extends: [
        'eslint:recommended'
    ],
    plugins: [
        'cypress'
    ],
    parserOptions: {
        ecmaVersion: 'latest',
        sourceType: 'module'
    },
    rules: {
        'no-unused-vars': ['error', {'argsIgnorePattern': '^_'}],
        'no-console': 'warn',
        'prefer-const': 'error',
        'no-var': 'error'
    },
    overrides: [
        {
            files: ['cypress/**/*.js'],
            rules: {
                'no-unused-expressions': 'off'
            }
        }
    ]
};