import {LOG_LEVEL} from '../config/env.js';

    /** reverse-spec §16 — bracketed subsystem prefixes, dev-gated debug, always-on warn/error. */

    const LEVELS = {debug: 10, info: 20, warn: 30, error: 40};
    const threshold = LEVELS[LOG_LEVEL] ?? LEVELS.warn;

    /** subsystem -> logger. One logger per subsystem so counters aggregate (§16). */
    const registry = new Map();

    /** Errors do not survive structured-clone/`console` object expansion; flatten them. */
    export function normalizeError(value) {
        if (value instanceof Error) {
            const out = {name: value.name, message: value.message, stack: value.stack};
            if (value.cause !== undefined) out.cause = normalizeError(value.cause);
            return out;
        }
        if (value && typeof value === 'object') return value;
        return {message: String(value)};
    }

    function flatten(args) {
        return args.map((arg) => {
            if (arg instanceof Error) return normalizeError(arg);
            if (arg && typeof arg === 'object' && arg.error instanceof Error) {
                return {...arg, error: normalizeError(arg.error)};
            }
            return arg;
        });
    }

    function stamp() {
        return new Date().toISOString().slice(11, 23);
    }

    export function createLogger(subsystem) {
        const cached = registry.get(subsystem);
        if (cached) return cached;

        const tag = `[${subsystem}]`;
        /** Error/diagnostic counters — the only observability into tab-state bugs (§16). */
        const counters = Object.create(null);

        const bump = (name) => {
            counters[name] = (counters[name] || 0) + 1;
        };

        const write = (method, args) => {
            try {
                console[method](`${stamp()} ${tag}`, ...flatten(args));
            } catch {
                /* console itself must never break the app */
            }
        };

        const logger = {
            tag,
            subsystem,
            debug(...args) {
                if (threshold <= LEVELS.debug) write('debug', args);
            },
            info(...args) {
                if (threshold <= LEVELS.info) write('info', args);
            },
            warn(...args) {
                bump('warn');
                write('warn', args);
            },
            error(...args) {
                bump('error');
                write('error', args);
            },
            /** Increment a named counter and return the new value. */
            count(name, delta = 1) {
                counters[name] = (counters[name] || 0) + delta;
                return counters[name];
            },
            counters() {
                return {...counters};
            }
        };

        registry.set(subsystem, logger);
        return logger;
    }

    /** Snapshot of every subsystem's counters — cheap first stop when triaging (§16). */
    export function dumpDiagnostics() {
        const out = {logLevel: LOG_LEVEL, subsystems: {}};
        for (const [subsystem, logger] of registry) out.subsystems[subsystem] = logger.counters();
        return out;
    }

    /**
     * Last-resort traps. Without these, a throw inside a `setTimeout`/`requestAnimationFrame`
     * callback (i.e. most of the §6 pipeline) vanishes with no subsystem attribution.
     */
    export function installGlobalErrorHandlers(target = window) {
        const log = createLogger('Global');

        const onError = (event) => {
            log.count('window-error');
            log.error('Uncaught error', {
                message: event.message,
                source: event.filename,
                line: event.lineno,
                column: event.colno,
                ...normalizeError(event.error ?? event.message)
            });
        };
        const onRejection = (event) => {
            log.count('unhandled-rejection');
            log.error('Unhandled promise rejection', normalizeError(event.reason));
        };

        target.addEventListener('error', onError);
        target.addEventListener('unhandledrejection', onRejection);
        return () => {
            target.removeEventListener('error', onError);
            target.removeEventListener('unhandledrejection', onRejection);
        };
    }

    /** Renders a fatal error panel instead of a blank page (§16). */
    export function renderFatalError(error, mount = document.getElementById('root') || document.body) {
        const panel = document.createElement('div');
        panel.className = 'fatal-error';
        panel.setAttribute('role', 'alert');
        const message = document.createElement('div');
        message.className = 'fatal-error-message';
        message.textContent = `Something went wrong: ${error?.message || String(error)}`;
        panel.appendChild(message);
        if (threshold <= LEVELS.debug && error?.stack) {
            const pre = document.createElement('pre');
            pre.className = 'fatal-error-stack';
            pre.textContent = error.stack;
            panel.appendChild(pre);
        }
        mount.appendChild(panel);
    }