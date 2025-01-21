// jest-dom adds custom jest matchers for asserting on DOM nodes.
// allows you to do things like:
// expect(element).toHaveTextContent(/react/i)
// learn more: https://github.com/testing-library/jest-dom
import '@testing-library/jest-dom';
// Store original console methods
const originalConsole = {
    log: console.log,
    error: console.error,
    warn: console.warn,
    debug: console.debug,
};
// Custom formatter for test console output
const formatTestOutput = (type, ...args) => {
    const timestamp = new Date().toISOString();
    return `[TEST ${type.toUpperCase()}][${timestamp}] ${args.join(' ')}`;
};
// Override console methods for tests
beforeAll(() => {
    console.log = (...args) => {
        // Only log if not empty or undefined
        if (args.length > 0 && args[0] !== undefined) {
            originalConsole.log(formatTestOutput('log', ...args));
        }
    };
    console.error = (...args) => {
        originalConsole.error(formatTestOutput('error', ...args));
    };
    console.warn = (...args) => {
        originalConsole.warn(formatTestOutput('warn', ...args));
    };
    console.debug = (...args) => {
        // Only show debug logs if explicitly enabled
        if (process.env.DEBUG_MODE === 'true') {
            originalConsole.debug(formatTestOutput('debug', ...args));
        }
    };
});
// Restore original console methods after tests
afterAll(() => {
    console.log = originalConsole.log;
    console.error = originalConsole.error;
    console.warn = originalConsole.warn;
    console.debug = originalConsole.debug;
});