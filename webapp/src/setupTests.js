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
    info: console.info,
};
 // Custom formatter for test output
const formatTestOutput = (type, ...args) => {
    const timestamp = new Date().toISOString();
    return `[TEST ${type.toUpperCase()}][${timestamp}] ${args.join(' ')}`;
};
// Define log levels
const LOG_LEVELS = {
    ERROR: 3,
    WARN: 2,
    INFO: 1,
    DEBUG: 0
};
const CURRENT_LOG_LEVEL = process.env.LOG_LEVEL ? LOG_LEVELS[process.env.LOG_LEVEL.toUpperCase()] : LOG_LEVELS.INFO;

// Override console methods for tests
beforeAll(() => {
    console.error = (...args) => {
        originalConsole.error(formatTestOutput('error', ...args));
    };
    console.warn = (...args) => {
        if (CURRENT_LOG_LEVEL <= LOG_LEVELS.WARN) {
            originalConsole.warn(formatTestOutput('warn', ...args));
        }
    };
    console.log = console.info = (...args) => {
        if (CURRENT_LOG_LEVEL <= LOG_LEVELS.INFO && args.length > 0 && args[0] !== undefined) {
            originalConsole.log(formatTestOutput('info', ...args));
        }
    };
    console.debug = (...args) => {
        if (CURRENT_LOG_LEVEL <= LOG_LEVELS.DEBUG) {
            originalConsole.debug(formatTestOutput('debug', ...args));
        }
    };
});
// Restore original console methods after tests
afterAll(() => {
    console.log = originalConsole.log;
    console.error = originalConsole.error;
    console.warn = originalConsole.warn;
    console.info = originalConsole.info;
    console.debug = originalConsole.debug;
});