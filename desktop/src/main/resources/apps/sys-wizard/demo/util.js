// =============================================================================
// Philosophical Calculator — Demo Utilities
// =============================================================================
// Shared helpers for screen recording, narration, highlighting, session
// monitoring, and comprehensive diagnostics (logging, screenshots, page dumps,
// console capture) used by the Playwright demo script.
// =============================================================================

const fs = require('fs');
const path = require('path');
const os = require('os');
const { spawn, execSync } = require('child_process');

// ---------------------------------------------------------------------------
// Narration / credentials paths (set by consumer via setNarrationPath)
// ---------------------------------------------------------------------------
let NARRATION_PATH = '';
/**
 * Allow the demo script to tell util where narration.json lives so that
 * audio-file paths inside `say()` can be resolved relative to it.
 */
function setNarrationPath(p) {
    NARRATION_PATH = p;
}

// ---------------------------------------------------------------------------
// Configuration
// ---------------------------------------------------------------------------
const SHORT_PAUSE = 1500;
const MEDIUM_PAUSE = 3000;
const TYPING_DELAY = 35;
// ---------------------------------------------------------------------------
// Mode Configuration
// ---------------------------------------------------------------------------
// Modes:
//   'demo'        — Full experience: recording, narration, highlights, pauses, diagnostics
//   'interactive'  — Like demo but no recording; narration + highlights + diagnostics
//   'headless'     — No recording, no narration, no audio, minimal pauses; logs + screenshots + data only
//                    Ideal for CI/CD, background runs, parallel execution.
//
// Auto-detection: if CI env vars are set, defaults to 'headless'.
// Override via DEMO_MODE env var or programmatically via setMode() / initDiagnostics({ mode }).
// ---------------------------------------------------------------------------
const CI_ENV_VARS = ['CI', 'CONTINUOUS_INTEGRATION', 'GITHUB_ACTIONS', 'GITLAB_CI',
     'JENKINS_URL', 'CIRCLECI', 'TRAVIS', 'BUILDKITE', 'TF_BUILD', 'CODEBUILD_BUILD_ID'];
function _detectCI() {
     return CI_ENV_VARS.some(v => !!process.env[v]);
}
/**
  * Detect if Playwright is configured for headless browsing.
  * The playwright.config.js sets DEMO_MODE=headless when headless: true.
  * This function also checks for the absence of a DISPLAY on Linux (common in
  * headless server environments) as an additional heuristic.
  */
function _detectHeadlessBrowser() {
     // Playwright config propagates headless: true via DEMO_MODE env var
     if ((process.env.DEMO_MODE || '').toLowerCase().trim() === 'headless') return true;
     // On Linux, no DISPLAY usually means headless environment
     if (process.platform === 'linux' && !process.env.DISPLAY) return true;
     return false;
}
function _resolveDefaultMode() {
     const envMode = (process.env.DEMO_MODE || '').toLowerCase().trim();
     if (['headless', 'interactive', 'demo'].includes(envMode)) return envMode;
     if (_detectCI()) return 'headless';
      if (_detectHeadlessBrowser()) return 'headless';
     return 'demo';
}
/** @type {'demo'|'interactive'|'headless'} */
let currentMode = _resolveDefaultMode();
/**
  * Set the demo mode programmatically.
  * @param {'demo'|'interactive'|'headless'} mode
  */
function setMode(mode) {
     const valid = ['demo', 'interactive', 'headless'];
     if (!valid.includes(mode)) {
         throw new Error(`Invalid mode "${mode}". Must be one of: ${valid.join(', ')}`);
     }
     currentMode = mode;
     logInfo(`Demo mode set to: ${mode}`);
}
/**
  * Get the current demo mode.
  * @returns {'demo'|'interactive'|'headless'}
  */
function getMode() {
     return currentMode;
}
/**
  * Check if the current mode is headless (no audio, no recording, minimal pauses).
  * @returns {boolean}
  */
function isHeadless() {
     return currentMode === 'headless';
}
/**
  * Check if narration/audio should be active in the current mode.
  * @returns {boolean}
  */
function isNarrationEnabled() {
     return currentMode === 'demo' || currentMode === 'interactive';
}
/**
  * Check if screen recording should be active in the current mode.
  * @returns {boolean}
  */
function isRecordingEnabled() {
     return currentMode === 'demo';
}
// Headless mode uses much shorter pauses to speed up execution
const HEADLESS_PAUSE_FACTOR = 0.05; // 5% of normal pause durations
const HEADLESS_MIN_PAUSE = 50;      // minimum pause in headless mode (ms)
/**
  * Return an adjusted pause duration based on the current mode.
  * In headless mode, pauses are dramatically reduced for fast execution.
  * @param {number} ms - The original pause duration in milliseconds
  * @returns {number}
  */
function adjustedPause(ms) {
     if (currentMode === 'headless') {
         return Math.max(HEADLESS_MIN_PAUSE, Math.round(ms * HEADLESS_PAUSE_FACTOR));
     }
     return ms;
}

// ---------------------------------------------------------------------------
// Diagnostics / Logging Infrastructure
// ---------------------------------------------------------------------------

/**
 * @typedef {Object} DiagnosticsConfig
 * @property {string}  logDir            - Directory for all diagnostic output
 * @property {string}  logFile           - Path to the main log file
 * @property {boolean} captureConsole    - Whether to capture browser console messages
 * @property {boolean} captureNetwork    - Whether to capture network requests/responses
 * @property {boolean} captureScreenshots - Whether to capture periodic/event screenshots
 * @property {boolean} capturePageDumps  - Whether to capture HTML page dumps
 * @property {boolean} capturePerformance - Whether to capture performance metrics
 * @property {string}  logLevel          - Minimum log level: 'debug' | 'info' | 'warn' | 'error'
 * @property {number}  maxScreenshots    - Maximum number of screenshots to retain
 * @property {number}  maxPageDumps      - Maximum number of page dumps to retain
 * @property {boolean} logToStdout       - Whether to also echo log lines to stdout
  * @property {'demo'|'interactive'|'headless'} mode - Operating mode
 */

/** @type {DiagnosticsConfig} */
let diagConfig = {
    logDir: '',
    logFile: '',
    captureConsole: true,
    captureNetwork: true,
    captureScreenshots: true,
    capturePageDumps: true,
    capturePerformance: true,
    logLevel: 'debug',
    maxScreenshots: 500,
    maxPageDumps: 200,
    logToStdout: true,
     mode: '',  // resolved at init time if not set
};

// Counters for filenames
let screenshotCounter = 0;
let pageDumpCounter = 0;
let networkLogCounter = 0;

// In-memory buffers for structured data
const consoleLogs = [];
const networkLogs = [];
const performanceLogs = [];
const eventTimeline = [];

// Log level ordering
const LOG_LEVELS = { debug: 0, info: 1, warn: 2, error: 3 };

// File stream for the main log
let logStream = null;
// Track pages we've already attached diagnostics to (WeakSet to avoid leaks)
const attachedPages = new WeakSet();
// Whether diagnostics have been auto-initialised
let diagInitialised = false;
// Whether we've registered the process-exit flush handler
let exitHandlerRegistered = false;


/**
 * Initialise the diagnostics subsystem. Call once at the start of the demo.
 *
 * @param {object} [options] - Override any DiagnosticsConfig fields
 * @returns {DiagnosticsConfig} The resolved config
 */
function initDiagnostics(options = {}) {
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-').replace('T', '_').slice(0, 19);
    const defaultLogDir = path.join(process.cwd(), 'demo-diagnostics', `run-${timestamp}`);

    diagConfig = { ...diagConfig, ...options };
    if (!diagConfig.logDir) diagConfig.logDir = defaultLogDir;
    if (!diagConfig.logFile) diagConfig.logFile = path.join(diagConfig.logDir, 'demo.log');
     // Resolve mode: explicit option > env var > auto-detect
     if (diagConfig.mode && ['demo', 'interactive', 'headless'].includes(diagConfig.mode)) {
         currentMode = diagConfig.mode;
     } else {
         diagConfig.mode = currentMode; // use already-resolved default
     }


    // Create directory structure
    const dirs = [
        diagConfig.logDir,
        path.join(diagConfig.logDir, 'screenshots'),
        path.join(diagConfig.logDir, 'page-dumps'),
        path.join(diagConfig.logDir, 'network'),
        path.join(diagConfig.logDir, 'console'),
        path.join(diagConfig.logDir, 'performance'),
    ];
    for (const d of dirs) {
        if (!fs.existsSync(d)) fs.mkdirSync(d, { recursive: true });
    }

    // Open log file stream
    logStream = fs.createWriteStream(diagConfig.logFile, { flags: 'a', encoding: 'utf-8' });

    // Write header
    const header = [
        '='.repeat(80),
        `Demo Diagnostics Log — ${new Date().toISOString()}`,
         `Mode: ${currentMode}`,
         `CI detected: ${_detectCI()}`,
        `Platform: ${process.platform} (${os.type()} ${os.release()})`,
        `Node: ${process.version}`,
        `CWD: ${process.cwd()}`,
        `Log dir: ${diagConfig.logDir}`,
        '='.repeat(80),
        '',
    ].join('\n');
    logStream.write(header);

     logInfo('Diagnostics initialised', { config: diagConfig, mode: currentMode, ci: _detectCI() });

    // Write system info
    _writeSystemInfo();
     diagInitialised = true;
     // Register process-exit handler to flush diagnostics automatically
     _registerExitHandler();
     // Log mode-specific behaviour
     if (currentMode === 'headless') {
         logInfo('Headless mode: recording disabled, narration disabled, pauses minimised.');
         logInfo('Headless mode: logs, screenshots, page dumps, and diagnostics data are active.');
     } else if (currentMode === 'interactive') {
         logInfo('Interactive mode: recording disabled, narration enabled.');
     } else {
         logInfo('Demo mode: full experience — recording, narration, highlights, diagnostics.');
     }


    return diagConfig;
}

/**
  * Ensure diagnostics are initialised. Called lazily by functions that need
  * the diagnostics subsystem. This allows demos that don't explicitly call
  * initDiagnostics() to still get full logging, screenshots, and HTML dumps.
  */
function _ensureDiagnosticsInitialised() {
     if (!diagInitialised) {
         initDiagnostics();
     }
}
/**
  * Register a process-exit handler that flushes all diagnostics buffers.
  * Safe to call multiple times — only registers once.
  */
function _registerExitHandler() {
     if (exitHandlerRegistered) return;
     exitHandlerRegistered = true;
     const doFlush = () => {
         try { flushDiagnostics(); } catch (e) {
             console.error(`Failed to flush diagnostics on exit: ${e.message}`);
         }
     };
     process.on('exit', doFlush);
     process.on('SIGINT', () => { doFlush(); process.exit(130); });
     process.on('SIGTERM', () => { doFlush(); process.exit(143); });
     process.on('uncaughtException', (err) => {
         try { logError('Uncaught exception', { message: err.message, stack: err.stack }); } catch {}
         doFlush();
         process.exit(1);
     });
}
/**
  * Auto-attach page diagnostics if not already attached to this page.
  * Called internally by functions that receive a Playwright page object.
  *
  * @param {import('@playwright/test').Page} page
  */
function _autoAttachPageDiagnostics(page) {
     if (!page || typeof page.on !== 'function') return;
     if (attachedPages.has(page)) return;
     _ensureDiagnosticsInitialised();
     attachPageDiagnostics(page, `page-${Date.now()}`);
}
/**
 * Write system information to a separate file for reference.
 */
function _writeSystemInfo() {
    try {
        const info = {
            timestamp: new Date().toISOString(),
             mode: currentMode,
             ciDetected: _detectCI(),
            platform: process.platform,
            arch: process.arch,
            osType: os.type(),
            osRelease: os.release(),
            hostname: os.hostname(),
            cpus: os.cpus().length,
            totalMemory: `${(os.totalmem() / 1024 / 1024 / 1024).toFixed(2)} GB`,
            freeMemory: `${(os.freemem() / 1024 / 1024 / 1024).toFixed(2)} GB`,
            nodeVersion: process.version,
            env: {
                DISPLAY: process.env.DISPLAY || '(not set)',
                 DEMO_MODE: process.env.DEMO_MODE || '(not set)',
                PIPER_MODEL: process.env.PIPER_MODEL || '(not set)',
                PIPER_DOWNLOAD_DIR: process.env.PIPER_DOWNLOAD_DIR || '(not set)',
                CI: process.env.CI || '(not set)',
                 GITHUB_ACTIONS: process.env.GITHUB_ACTIONS || '(not set)',
                 GITLAB_CI: process.env.GITLAB_CI || '(not set)',
                TERM: process.env.TERM || '(not set)',
            },
            cwd: process.cwd(),
            pid: process.pid,
        };
        const sysInfoPath = path.join(diagConfig.logDir, 'system-info.json');
        fs.writeFileSync(sysInfoPath, JSON.stringify(info, null, 2), 'utf-8');
    } catch (e) {
        logWarn('Failed to write system info', { error: e.message });
    }
}

/**
 * Core logging function. Writes to file and optionally stdout.
 *
 * @param {'debug'|'info'|'warn'|'error'} level
 * @param {string} message
 * @param {object} [meta] - Additional structured data
 */
function _log(level, message, meta = {}) {
    if (LOG_LEVELS[level] < LOG_LEVELS[diagConfig.logLevel]) return;

    const ts = new Date().toISOString();
    const prefix = `[${ts}] [${level.toUpperCase().padEnd(5)}]`;
    const metaStr = Object.keys(meta).length > 0 ? ` ${JSON.stringify(meta)}` : '';
    const line = `${prefix} ${message}${metaStr}\n`;

    if (logStream && !logStream.destroyed) {
        logStream.write(line);
    }

    if (diagConfig.logToStdout) {
        const icons = { debug: '🔍', info: 'ℹ️ ', warn: '⚠️ ', error: '❌' };
        const icon = icons[level] || '  ';
        process.stdout.write(`${icon} ${line}`);
    }

    // Also push to event timeline
    eventTimeline.push({ ts, level, message, meta });
}

function logDebug(message, meta) { _log('debug', message, meta); }
function logInfo(message, meta) { _log('info', message, meta); }
function logWarn(message, meta) { _log('warn', message, meta); }
function logError(message, meta) { _log('error', message, meta); }

/**
 * Attach browser console and network listeners to a Playwright page.
 * Call this for every page/tab you open.
 *
 * @param {import('@playwright/test').Page} page
 * @param {string} [label] - A label for this page (e.g. 'main', 'session-monitor')
 */
function attachPageDiagnostics(page, label = 'main') {
    if (!diagConfig.logDir) {
         _ensureDiagnosticsInitialised();
    }
     // Mark this page as attached to avoid double-attaching
     attachedPages.add(page);


    // --- Browser console capture ---
    if (diagConfig.captureConsole) {
        page.on('console', (msg) => {
            const entry = {
                timestamp: new Date().toISOString(),
                page: label,
                type: msg.type(),
                text: msg.text(),
                location: msg.location(),
            };
            consoleLogs.push(entry);
            const levelMap = { log: 'debug', info: 'info', warning: 'warn', error: 'error' };
            const logLevel = levelMap[msg.type()] || 'debug';
            _log(logLevel, `[CONSOLE:${label}] [${msg.type()}] ${msg.text()}`, { location: msg.location() });
        });

        page.on('pageerror', (error) => {
            const entry = {
                timestamp: new Date().toISOString(),
                page: label,
                type: 'pageerror',
                message: error.message,
                stack: error.stack,
            };
            consoleLogs.push(entry);
            logError(`[PAGE_ERROR:${label}] ${error.message}`, { stack: error.stack });
        });
    }

    // --- Network capture ---
    if (diagConfig.captureNetwork) {
        page.on('request', (request) => {
            const entry = {
                timestamp: new Date().toISOString(),
                page: label,
                direction: 'request',
                method: request.method(),
                url: request.url(),
                resourceType: request.resourceType(),
                headers: request.headers(),
                postData: request.postData() || null,
            };
            networkLogs.push(entry);
            logDebug(`[NET:${label}] → ${request.method()} ${request.url()}`, {
                resourceType: request.resourceType(),
            });
        });

        page.on('response', (response) => {
            const entry = {
                timestamp: new Date().toISOString(),
                page: label,
                direction: 'response',
                status: response.status(),
                url: response.url(),
                headers: response.headers(),
            };
            networkLogs.push(entry);
            const level = response.status() >= 400 ? 'warn' : 'debug';
            _log(level, `[NET:${label}] ← ${response.status()} ${response.url()}`);
        });

        page.on('requestfailed', (request) => {
            const failure = request.failure();
            const entry = {
                timestamp: new Date().toISOString(),
                page: label,
                direction: 'request_failed',
                method: request.method(),
                url: request.url(),
                errorText: failure ? failure.errorText : 'unknown',
            };
            networkLogs.push(entry);
            logWarn(`[NET:${label}] ✗ ${request.method()} ${request.url()} — ${entry.errorText}`);
        });
    }

    // --- Crash / close detection ---
    page.on('crash', () => {
        logError(`[CRASH:${label}] Page crashed!`);
        _recordEvent('page_crash', { page: label });
    });

    page.on('close', () => {
        logInfo(`[CLOSE:${label}] Page closed.`);
        _recordEvent('page_close', { page: label });
    });

    // --- Dialog (alert/confirm/prompt) capture ---
    page.on('dialog', async (dialog) => {
        logInfo(`[DIALOG:${label}] ${dialog.type()}: ${dialog.message()}`);
        _recordEvent('dialog', { page: label, type: dialog.type(), message: dialog.message() });
        try { await dialog.dismiss(); } catch {}
    });

    logInfo(`Diagnostics attached to page: ${label}`);
}

/**
 * Record a named event in the timeline.
 */
function _recordEvent(eventName, data = {}) {
    eventTimeline.push({
        ts: new Date().toISOString(),
        event: eventName,
        ...data,
    });
}

/**
 * Take a screenshot and save it to the diagnostics directory.
 *
 * @param {import('@playwright/test').Page} page
 * @param {string} name - Descriptive name (used in filename)
 * @param {object} [options]
 * @param {boolean} [options.fullPage] - Capture full scrollable page (default true)
 * @returns {Promise<string|null>} Path to the saved screenshot, or null on failure
 */
async function takeScreenshot(page, name, options = {}) {
     _ensureDiagnosticsInitialised();
     _autoAttachPageDiagnostics(page);

    if (!diagConfig.captureScreenshots) return null;
    if (screenshotCounter >= diagConfig.maxScreenshots) {
        logWarn(`Screenshot limit reached (${diagConfig.maxScreenshots}). Skipping: ${name}`);
        return null;
    }

    const fullPage = options.fullPage !== undefined ? options.fullPage : true;
    const counter = String(++screenshotCounter).padStart(4, '0');
    const safeName = name.replace(/[^a-zA-Z0-9_-]/g, '_').substring(0, 80);
    const filename = `${counter}-${safeName}.png`;
    const filepath = path.join(diagConfig.logDir, 'screenshots', filename);

    try {
        await page.screenshot({ path: filepath, fullPage });
        logDebug(`Screenshot saved: ${filename}`, { path: filepath });
        _recordEvent('screenshot', { name, path: filepath });
         // Automatically dump page HTML alongside every screenshot for postmortem debugging
         try {
             await _dumpPageHTMLForScreenshot(page, name, counter);
         } catch (e) {
             logDebug(`Companion HTML dump failed for screenshot "${name}": ${e.message}`);
         }
        return filepath;
    } catch (e) {
        logWarn(`Failed to take screenshot "${name}": ${e.message}`);
        return null;
    }
}

/**
  * Internal: dump page HTML as a companion to a screenshot.
  * Uses the same counter prefix so screenshot and dump are paired by filename.
  * This does NOT increment pageDumpCounter or respect maxPageDumps — it is
  * tied to the screenshot lifecycle.
  */
async function _dumpPageHTMLForScreenshot(page, name, counter) {
     const safeName = name.replace(/[^a-zA-Z0-9_-]/g, '_').substring(0, 80);
     const filename = `${counter}-${safeName}.html`;
     const filepath = path.join(diagConfig.logDir, 'page-dumps', filename);
     const html = await page.content();
     const url = page.url();
     const title = await page.title().catch(() => '(unknown)');
     const header = [
         `<!-- Page Dump (companion to screenshot ${counter}-${safeName}.png) -->`,
         `<!-- URL: ${url} -->`,
         `<!-- Title: ${title} -->`,
         `<!-- Timestamp: ${new Date().toISOString()} -->`,
         '',
     ].join('\n');
     fs.writeFileSync(filepath, header + html, 'utf-8');
     logDebug(`Companion HTML dump saved: ${filename}`, { path: filepath, url, title });
}
/**
 * Dump the current page HTML to the diagnostics directory.
 *
 * @param {import('@playwright/test').Page} page
 * @param {string} name - Descriptive name (used in filename)
 * @returns {Promise<string|null>} Path to the saved dump, or null on failure
 */
async function dumpPageHTML(page, name) {
     _ensureDiagnosticsInitialised();
     _autoAttachPageDiagnostics(page);

    if (!diagConfig.capturePageDumps) return null;
    if (pageDumpCounter >= diagConfig.maxPageDumps) {
        logWarn(`Page dump limit reached (${diagConfig.maxPageDumps}). Skipping: ${name}`);
        return null;
    }

    const counter = String(++pageDumpCounter).padStart(4, '0');
    const safeName = name.replace(/[^a-zA-Z0-9_-]/g, '_').substring(0, 80);
    const filename = `${counter}-${safeName}.html`;
    const filepath = path.join(diagConfig.logDir, 'page-dumps', filename);

    try {
        const html = await page.content();
        const url = page.url();
        const title = await page.title();

        const header = [
            `<!-- Page Dump: ${name} -->`,
            `<!-- URL: ${url} -->`,
            `<!-- Title: ${title} -->`,
            `<!-- Timestamp: ${new Date().toISOString()} -->`,
            `<!-- Dump #${counter} -->`,
            '',
        ].join('\n');

        fs.writeFileSync(filepath, header + html, 'utf-8');
        logDebug(`Page dump saved: ${filename}`, { path: filepath, url, title });
        _recordEvent('page_dump', { name, path: filepath, url, title });
        return filepath;
    } catch (e) {
        logWarn(`Failed to dump page "${name}": ${e.message}`);
        return null;
    }
}

/**
 * Dump the current page's accessibility tree (useful for debugging selectors).
 *
 * @param {import('@playwright/test').Page} page
 * @param {string} name
 * @returns {Promise<string|null>}
 */
async function dumpAccessibilityTree(page, name) {
     _ensureDiagnosticsInitialised();
    if (!diagConfig.capturePageDumps) return null;

    const safeName = name.replace(/[^a-zA-Z0-9_-]/g, '_').substring(0, 80);
    const filename = `a11y-${safeName}.json`;
    const filepath = path.join(diagConfig.logDir, 'page-dumps', filename);

    try {
        const snapshot = await page.accessibility.snapshot();
        fs.writeFileSync(filepath, JSON.stringify(snapshot, null, 2), 'utf-8');
        logDebug(`Accessibility tree saved: ${filename}`);
        return filepath;
    } catch (e) {
        logDebug(`Failed to dump accessibility tree "${name}": ${e.message}`);
        return null;
    }
}

/**
 * Capture browser performance metrics and save them.
 *
 * @param {import('@playwright/test').Page} page
 * @param {string} name
 * @returns {Promise<object|null>}
 */
async function capturePerformanceMetrics(page, name) {
     _ensureDiagnosticsInitialised();
    if (!diagConfig.capturePerformance) return null;

    try {
        const metrics = await page.evaluate(() => {
            const perf = window.performance;
            const navigation = perf.getEntriesByType('navigation')[0] || {};
            const paint = perf.getEntriesByType('paint') || [];
            const resources = perf.getEntriesByType('resource').map(r => ({
                name: r.name,
                type: r.initiatorType,
                duration: Math.round(r.duration),
                size: r.transferSize || 0,
            }));

            return {
                timing: {
                    domContentLoaded: Math.round(navigation.domContentLoadedEventEnd || 0),
                    loadComplete: Math.round(navigation.loadEventEnd || 0),
                    domInteractive: Math.round(navigation.domInteractive || 0),
                    responseEnd: Math.round(navigation.responseEnd || 0),
                },
                paint: paint.map(p => ({ name: p.name, startTime: Math.round(p.startTime) })),
                resourceCount: resources.length,
                totalTransferSize: resources.reduce((sum, r) => sum + r.size, 0),
                resources: resources.slice(0, 50), // cap to avoid huge dumps
                memory: (performance).memory ? {
                    usedJSHeapSize: (performance).memory.usedJSHeapSize,
                    totalJSHeapSize: (performance).memory.totalJSHeapSize,
                    jsHeapSizeLimit: (performance).memory.jsHeapSizeLimit,
                } : null,
            };
        });

        const entry = {
            timestamp: new Date().toISOString(),
            name,
            url: page.url(),
            ...metrics,
        };
        performanceLogs.push(entry);

        const safeName = name.replace(/[^a-zA-Z0-9_-]/g, '_').substring(0, 80);
        const filename = `perf-${safeName}-${Date.now()}.json`;
        const filepath = path.join(diagConfig.logDir, 'performance', filename);
        fs.writeFileSync(filepath, JSON.stringify(entry, null, 2), 'utf-8');

        logDebug(`Performance metrics captured: ${name}`, {
            domContentLoaded: metrics.timing.domContentLoaded,
            loadComplete: metrics.timing.loadComplete,
            resourceCount: metrics.resourceCount,
        });

        return entry;
    } catch (e) {
        logDebug(`Failed to capture performance metrics "${name}": ${e.message}`);
        return null;
    }
}

/**
 * Capture the current state of all visible elements matching a selector.
 * Useful for debugging form state, button states, etc.
 *
 * @param {import('@playwright/test').Page} page
 * @param {string} selector
 * @param {string} name
 * @returns {Promise<object[]|null>}
 */
async function captureElementStates(page, selector, name) {
    try {
        const states = await page.evaluate((sel) => {
            const elements = Array.from(document.querySelectorAll(sel));
            return elements.map((el, i) => ({
                index: i,
                tagName: el.tagName,
                id: el.id || null,
                className: el.className || null,
                textContent: (el.textContent || '').trim().substring(0, 200),
                value: el.value !== undefined ? el.value : null,
                checked: el.checked !== undefined ? el.checked : null,
                disabled: el.disabled || false,
                visible: el.offsetParent !== null,
                boundingBox: el.getBoundingClientRect().toJSON(),
                attributes: Array.from(el.attributes).reduce((acc, attr) => {
                    acc[attr.name] = attr.value;
                    return acc;
                }, {}),
            }));
        }, selector);

        logDebug(`Element states captured for "${selector}" (${name}): ${states.length} elements`);
        return states;
    } catch (e) {
        logDebug(`Failed to capture element states "${name}": ${e.message}`);
        return null;
    }
}

/**
 * Take a comprehensive diagnostic snapshot: screenshot + page dump + performance.
 * Useful at key moments in the demo flow.
 *
 * @param {import('@playwright/test').Page} page
 * @param {string} name
 * @returns {Promise<{screenshot: string|null, pageDump: string|null, performance: object|null}>}
 */
async function diagnosticSnapshot(page, name) {
     _ensureDiagnosticsInitialised();
     _autoAttachPageDiagnostics(page);
    logInfo(`Taking diagnostic snapshot: ${name}`);
    _recordEvent('diagnostic_snapshot', { name });

    const [screenshot, pageDump, perf] = await Promise.all([
        takeScreenshot(page, name),
        dumpPageHTML(page, name),
        capturePerformanceMetrics(page, name),
    ]);

    return { screenshot, pageDump, performance: perf };
}

/**
 * Flush all in-memory diagnostic buffers to disk.
 * Call at the end of the demo or on error.
 */
function flushDiagnostics() {
    if (!diagConfig.logDir) return;

    try {
        // Console logs
        if (consoleLogs.length > 0) {
            const consolePath = path.join(diagConfig.logDir, 'console', 'browser-console.json');
            fs.writeFileSync(consolePath, JSON.stringify(consoleLogs, null, 2), 'utf-8');
            logInfo(`Flushed ${consoleLogs.length} console log entries.`);
             // Also write a human-readable console log file
             const consoleReadablePath = path.join(diagConfig.logDir, 'console', 'browser-console.log');
             const consoleLines = consoleLogs.map(e => {
                 const ts = e.timestamp || '';
                 const pg = e.page || '';
                 const tp = (e.type || '').toUpperCase().padEnd(8);
                 const text = e.text || e.message || '';
                 const loc = e.location ? ` (${e.location.url || ''}:${e.location.lineNumber || ''})` : '';
                 const stack = e.stack ? `\n    Stack: ${e.stack}` : '';
                 return `[${ts}] [${pg}] [${tp}] ${text}${loc}${stack}`;
             });
             fs.writeFileSync(consoleReadablePath, consoleLines.join('\n') + '\n', 'utf-8');
             logInfo(`Flushed human-readable console log: ${consoleReadablePath}`);
        }

        // Network logs
        if (networkLogs.length > 0) {
            const networkPath = path.join(diagConfig.logDir, 'network', 'network-log.json');
            fs.writeFileSync(networkPath, JSON.stringify(networkLogs, null, 2), 'utf-8');
            logInfo(`Flushed ${networkLogs.length} network log entries.`);

            // Also write a summary
            const summary = _buildNetworkSummary();
            const summaryPath = path.join(diagConfig.logDir, 'network', 'network-summary.json');
            fs.writeFileSync(summaryPath, JSON.stringify(summary, null, 2), 'utf-8');
        }

        // Performance logs
        if (performanceLogs.length > 0) {
            const perfPath = path.join(diagConfig.logDir, 'performance', 'all-metrics.json');
            fs.writeFileSync(perfPath, JSON.stringify(performanceLogs, null, 2), 'utf-8');
        }

        // Event timeline
        if (eventTimeline.length > 0) {
            const timelinePath = path.join(diagConfig.logDir, 'event-timeline.json');
            fs.writeFileSync(timelinePath, JSON.stringify(eventTimeline, null, 2), 'utf-8');
            logInfo(`Flushed ${eventTimeline.length} timeline events.`);
        }

        // Write a final summary report
        _writeSummaryReport();
         // Write a dedicated run progress log with timing
         _writeRunProgressLog();


    } catch (e) {
        console.error(`Failed to flush diagnostics: ${e.message}`);
    }

    // Close log stream
    if (logStream && !logStream.destroyed) {
        logStream.write(`\n${'='.repeat(80)}\nDiagnostics session ended: ${new Date().toISOString()}\n${'='.repeat(80)}\n`);
        logStream.end();
    }
}

/**
 * Build a network request summary (counts by status, domain, type, errors).
 */
function _buildNetworkSummary() {
    const requests = networkLogs.filter(e => e.direction === 'request');
    const responses = networkLogs.filter(e => e.direction === 'response');
    const failures = networkLogs.filter(e => e.direction === 'request_failed');

    const statusCounts = {};
    for (const r of responses) {
        const bucket = `${Math.floor(r.status / 100)}xx`;
        statusCounts[bucket] = (statusCounts[bucket] || 0) + 1;
    }

    const domainCounts = {};
    for (const r of requests) {
        try {
            const domain = new URL(r.url).hostname;
            domainCounts[domain] = (domainCounts[domain] || 0) + 1;
        } catch {}
    }

    const typeCounts = {};
    for (const r of requests) {
        typeCounts[r.resourceType] = (typeCounts[r.resourceType] || 0) + 1;
    }

    return {
        totalRequests: requests.length,
        totalResponses: responses.length,
        totalFailures: failures.length,
        statusCounts,
        domainCounts,
        typeCounts,
        failures: failures.map(f => ({ url: f.url, error: f.errorText })),
    };
}

/**
 * Write a human-readable summary report.
 */
function _writeSummaryReport() {
    const reportPath = path.join(diagConfig.logDir, 'SUMMARY.md');
    const errorCount = eventTimeline.filter(e => e.level === 'error').length;
    const warnCount = eventTimeline.filter(e => e.level === 'warn').length;
    const consoleErrors = consoleLogs.filter(e => e.type === 'error' || e.type === 'pageerror');
    const networkFailures = networkLogs.filter(e => e.direction === 'request_failed');
    const crashes = eventTimeline.filter(e => e.event === 'page_crash');
     // Compute run duration from event timeline
     let runDuration = 'N/A';
     if (eventTimeline.length >= 2) {
         const first = new Date(eventTimeline[0].ts).getTime();
         const last = new Date(eventTimeline[eventTimeline.length - 1].ts).getTime();
         const durationSec = ((last - first) / 1000).toFixed(1);
         const durationMin = ((last - first) / 60000).toFixed(1);
         runDuration = `${durationSec}s (${durationMin} min)`;
     }


    const lines = [
        '# Demo Diagnostics Summary',
        '',
        `**Run time:** ${new Date().toISOString()}`,
         `**Total duration:** ${runDuration}`,
         `**Mode:** ${currentMode}`,
         `**CI detected:** ${_detectCI()}`,
        `**Platform:** ${process.platform} (${os.type()} ${os.release()})`,
        `**Log directory:** \`${diagConfig.logDir}\``,
        '',
        '## Overview',
        '',
        `| Metric | Count |`,
        `|--------|-------|`,
        `| Screenshots | ${screenshotCounter} |`,
        `| Page dumps | ${pageDumpCounter} |`,
        `| Console messages | ${consoleLogs.length} |`,
        `| Network requests | ${networkLogs.filter(e => e.direction === 'request').length} |`,
        `| Timeline events | ${eventTimeline.length} |`,
        `| Performance snapshots | ${performanceLogs.length} |`,
        '',
        '## Issues',
        '',
        `| Issue Type | Count |`,
        `|------------|-------|`,
        `| Log errors | ${errorCount} |`,
        `| Log warnings | ${warnCount} |`,
        `| Browser console errors | ${consoleErrors.length} |`,
        `| Network failures | ${networkFailures.length} |`,
        `| Page crashes | ${crashes.length} |`,
        '',
    ];

    if (consoleErrors.length > 0) {
        lines.push('### Browser Console Errors', '');
        for (const e of consoleErrors.slice(0, 20)) {
            lines.push(`- **[${e.type}]** ${e.text || e.message || '(unknown)'}`);
        }
        if (consoleErrors.length > 20) lines.push(`- ... and ${consoleErrors.length - 20} more`);
        lines.push('');
    }

    if (networkFailures.length > 0) {
        lines.push('### Network Failures', '');
        for (const f of networkFailures.slice(0, 20)) {
            lines.push(`- \`${f.method || 'GET'} ${f.url}\` — ${f.errorText}`);
        }
        if (networkFailures.length > 20) lines.push(`- ... and ${networkFailures.length - 20} more`);
        lines.push('');
    }
     // Timeline / progress section with timing
     lines.push('## Progress Timeline', '');
     lines.push('| Timestamp | Event | Details |');
     lines.push('|-----------|-------|---------|');
     const keyEvents = eventTimeline.filter(e =>
         e.event || e.level === 'info' || e.level === 'error'
     );
     for (const e of keyEvents.slice(0, 200)) {
         const ts = e.ts || '';
         const event = e.event || `[${(e.level || '').toUpperCase()}]`;
         const msg = e.message || '';
         const details = e.event
             ? Object.keys(e).filter(k => !['ts', 'event'].includes(k)).map(k => `${k}=${JSON.stringify(e[k])}`).join(', ')
             : msg.substring(0, 120);
         lines.push(`| ${ts} | ${event} | ${details.replace(/\|/g, '\\|')} |`);
     }
     if (keyEvents.length > 200) lines.push(`| ... | ... | ${keyEvents.length - 200} more events |`);
     lines.push('');


    lines.push('## File Listing', '');
    lines.push('```');
    try {
        const listing = _listDirRecursive(diagConfig.logDir, '', 0);
        lines.push(listing);
    } catch { lines.push('(failed to list directory)'); }
    lines.push('```');
    lines.push('');

    fs.writeFileSync(reportPath, lines.join('\n'), 'utf-8');
    logInfo(`Summary report written: ${reportPath}`);
}

/**
  * Write a dedicated run-progress.log file with timing and progress information.
  * This is a clean, human-readable log focused on what happened and when.
  */
function _writeRunProgressLog() {
     const progressPath = path.join(diagConfig.logDir, 'run-progress.log');
     const lines = [];
     lines.push('='.repeat(80));
     lines.push('DEMO RUN PROGRESS LOG');
     lines.push('='.repeat(80));
     lines.push(`Generated: ${new Date().toISOString()}`);
      lines.push(`Mode:      ${currentMode}`);
      lines.push(`CI:        ${_detectCI()}`);
     lines.push(`Platform:  ${process.platform} (${os.type()} ${os.release()})`);
     lines.push(`Node:      ${process.version}`);
     lines.push('');
     // Compute overall timing
     if (eventTimeline.length >= 2) {
         const first = new Date(eventTimeline[0].ts);
         const last = new Date(eventTimeline[eventTimeline.length - 1].ts);
         const durationMs = last.getTime() - first.getTime();
         lines.push(`Start:     ${first.toISOString()}`);
         lines.push(`End:       ${last.toISOString()}`);
         lines.push(`Duration:  ${(durationMs / 1000).toFixed(1)}s (${(durationMs / 60000).toFixed(1)} min)`);
     }
     lines.push('');
     // Statistics
     lines.push('-'.repeat(40));
     lines.push('STATISTICS');
     lines.push('-'.repeat(40));
     lines.push(`Screenshots taken:     ${screenshotCounter}`);
     lines.push(`Page dumps saved:      ${pageDumpCounter}`);
     lines.push(`Console messages:      ${consoleLogs.length}`);
     lines.push(`  - errors:            ${consoleLogs.filter(e => e.type === 'error' || e.type === 'pageerror').length}`);
     lines.push(`  - warnings:          ${consoleLogs.filter(e => e.type === 'warning').length}`);
     lines.push(`Network requests:      ${networkLogs.filter(e => e.direction === 'request').length}`);
     lines.push(`Network failures:      ${networkLogs.filter(e => e.direction === 'request_failed').length}`);
     lines.push(`Performance snapshots: ${performanceLogs.length}`);
     lines.push(`Timeline events:       ${eventTimeline.length}`);
     lines.push('');
     // Chronological progress
     lines.push('-'.repeat(40));
     lines.push('CHRONOLOGICAL PROGRESS');
     lines.push('-'.repeat(40));
     let prevTime = null;
     for (const entry of eventTimeline) {
         const ts = entry.ts || '';
         const elapsed = prevTime
             ? `+${((new Date(ts).getTime() - new Date(prevTime).getTime()) / 1000).toFixed(1)}s`
             : '       ';
         prevTime = ts;
         if (entry.event) {
             // Named event
             const details = Object.keys(entry)
                 .filter(k => !['ts', 'event'].includes(k))
                 .map(k => `${k}=${typeof entry[k] === 'string' ? entry[k] : JSON.stringify(entry[k])}`)
                 .join(', ');
             lines.push(`[${ts}] ${elapsed.padStart(8)} | EVENT: ${entry.event} ${details ? '— ' + details : ''}`);
         } else if (entry.level === 'info' || entry.level === 'error') {
             // Info/error log entries (skip debug/warn for cleanliness)
             const icon = entry.level === 'error' ? 'ERROR' : 'INFO ';
             lines.push(`[${ts}] ${elapsed.padStart(8)} | ${icon}: ${entry.message || ''}`);
         }
     }
     lines.push('');
     lines.push('='.repeat(80));
     lines.push('END OF PROGRESS LOG');
     lines.push('='.repeat(80));
     fs.writeFileSync(progressPath, lines.join('\n') + '\n', 'utf-8');
     logInfo(`Run progress log written: ${progressPath}`);
}
/**
   * Recursively list directory contents for the summary report.
   */
  function _listDirRecursive(dir, prefix, depth) {
    if (depth > 4) return prefix + '...\n';
    let result = '';
    try {
        const entries = fs.readdirSync(dir, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name));
        for (const entry of entries) {
            const full = path.join(dir, entry.name);
            if (entry.isDirectory()) {
                result += `${prefix}${entry.name}/\n`;
                result += _listDirRecursive(full, prefix + '  ', depth + 1);
            } else {
                const size = fs.statSync(full).size;
                const sizeStr = size > 1024 * 1024 ? `${(size / 1024 / 1024).toFixed(1)}MB`
                    : size > 1024 ? `${(size / 1024).toFixed(1)}KB`
                        : `${size}B`;
                result += `${prefix}${entry.name} (${sizeStr})\n`;
            }
        }
    } catch {}
    return result;
}

/**
 * Capture a diagnostic snapshot on error — screenshot, page dump, and error details.
 *
 * @param {import('@playwright/test').Page} page
 * @param {Error} error
 * @param {string} context - Description of what was happening when the error occurred
 */
async function captureErrorDiagnostics(page, error, context) {
     _ensureDiagnosticsInitialised();
     _autoAttachPageDiagnostics(page);
    logError(`Error during "${context}": ${error.message}`, { stack: error.stack });
    _recordEvent('error', { context, message: error.message, stack: error.stack });

    try {
        await takeScreenshot(page, `error-${context}`);
        await dumpPageHTML(page, `error-${context}`);

        // Capture current URL and title
        const url = page.url();
        const title = await page.title().catch(() => '(unknown)');
        logError(`Error page state — URL: ${url}, Title: ${title}`);

        // Capture any visible error messages on the page
        const visibleErrors = await page.evaluate(() => {
            const errorSelectors = [
                '.error', '.error-message', '.alert-danger', '.alert-error',
                '[role="alert"]', '.notification-error', '.toast-error',
                '.session-error-link', '.error-container',
            ];
            const errors = [];
            for (const sel of errorSelectors) {
                for (const el of document.querySelectorAll(sel)) {
                    if (el.offsetParent !== null) { // visible
                        errors.push({
                            selector: sel,
                            text: (el.textContent || '').trim().substring(0, 500),
                        });
                    }
                }
            }
            return errors;
        }).catch(() => []);

        if (visibleErrors.length > 0) {
            logError('Visible error elements on page:', { errors: visibleErrors });
        }
    } catch (e) {
        logWarn(`Failed to capture full error diagnostics: ${e.message}`);
    }
}

// ---------------------------------------------------------------------------
// Local TTS engine detection (piper-tts via pip > espeak-ng > none)
// ---------------------------------------------------------------------------
let TTS_ENGINE = null;
let _ttsDetectionDone = false;

/**
  * Lazily detect and return the TTS engine. In headless mode, always returns null.
  * @returns {string|null}
  */
function getTTSEngine() {
     if (currentMode === 'headless') return null;
     if (!_ttsDetectionDone) {
         TTS_ENGINE = detectTTSEngine();
         _ttsDetectionDone = true;
     }
     return TTS_ENGINE;
}

function detectTTSEngine() {
     if (currentMode === 'headless') {
         console.log('🗣️  TTS engine: disabled (headless mode)');
         return null;
     }
    // Check for piper first (high-quality neural TTS via pip install piper-tts)
    try {
        execSync('piper --help 2>/dev/null', { encoding: 'utf-8', stdio: 'pipe' });
        console.log('🗣️  TTS engine: piper-tts (neural, high quality — pip package)');
        return 'piper';
    } catch {}
    // Check for espeak-ng (lightweight fallback)
    try {
        execSync('espeak-ng --version 2>/dev/null', { encoding: 'utf-8', stdio: 'pipe' });
        console.log('🗣️  TTS engine: espeak-ng (lightweight fallback)');
        return 'espeak-ng';
    } catch {}
    // Check for espeak (older systems)
    try {
        execSync('espeak --version 2>/dev/null', { encoding: 'utf-8', stdio: 'pipe' });
        console.log('🗣️  TTS engine: espeak (legacy fallback)');
        return 'espeak';
    } catch {}
    console.warn('⚠️  No local TTS engine found. Install piper-tts (recommended) or espeak-ng for narration.');
    console.warn('    piper-tts: pip install piper-tts');
    console.warn('    espeak-ng: sudo apt install espeak-ng');
    return null;
}

// ---------------------------------------------------------------------------
// Piper model configuration
// ---------------------------------------------------------------------------
let PIPER_MODEL = process.env.PIPER_MODEL || '';
let PIPER_MODEL_CONFIG = '';
const PIPER_DOWNLOAD_DIR = process.env.PIPER_DOWNLOAD_DIR || path.join(os.homedir(), '.local', 'share', 'piper-voices');
const PIPER_DEFAULT_MODEL_NAME = 'en_US-lessac-medium';
let _piperModelResolved = false;

/**
  * Lazily resolve the Piper model. Skipped entirely in headless mode.
  */
function _ensurePiperModel() {
     if (_piperModelResolved) return;
     _piperModelResolved = true;
     if (currentMode === 'headless') return;
     if (getTTSEngine() !== 'piper') return;
     if (PIPER_MODEL) return;

    // Ensure the download directory exists
    if (!fs.existsSync(PIPER_DOWNLOAD_DIR)) {
        fs.mkdirSync(PIPER_DOWNLOAD_DIR, { recursive: true });
    }
    {
        // Recursively find first .onnx file
        const findOnnx = (dir) => {
            try {
                for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
                    const full = path.join(dir, entry.name);
                    if (entry.isDirectory()) {
                        const found = findOnnx(full);
                        if (found) return found;
                    } else if (entry.name.endsWith('.onnx')) {
                        return full;
                    }
                }
            } catch {}
            return null;
        };
        let found = findOnnx(PIPER_DOWNLOAD_DIR);
        // If no local model found, try to download one
        if (!found) {
            console.log(`   No local piper model found — downloading ${PIPER_DEFAULT_MODEL_NAME}...`);
            try {
                execSync(
                    `echo "test" | piper --model ${PIPER_DEFAULT_MODEL_NAME} --download-dir "${PIPER_DOWNLOAD_DIR}" --data-dir "${PIPER_DOWNLOAD_DIR}" --output_file /dev/null 2>/dev/null`,
                    { encoding: 'utf-8', stdio: 'pipe', timeout: 120000 }
                );
                found = findOnnx(PIPER_DOWNLOAD_DIR);
            } catch (e) {
                try {
                    execSync(
                        `python3 -c "
from piper.download import get_voices, ensure_voice_exists
import json, os
data_dir = '${PIPER_DOWNLOAD_DIR}'
os.makedirs(data_dir, exist_ok=True)
ensure_voice_exists('${PIPER_DEFAULT_MODEL_NAME}', [data_dir], data_dir)
"`,
                        { encoding: 'utf-8', stdio: 'pipe', timeout: 120000 }
                    );
                    found = findOnnx(PIPER_DOWNLOAD_DIR);
                } catch (e2) {
                    try {
                        const voiceBase = PIPER_DEFAULT_MODEL_NAME;
                        const parts = voiceBase.split('-');
                        const langCode = parts[0];
                        const langShort = langCode.split('_')[0];
                        const voiceName = parts[1];
                        const quality = parts[2];
                        const onnxFileName = `${voiceBase}.onnx`;
                        const configFileName = `${voiceBase}.onnx.json`;
                        const baseUrl = `https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/${langShort}/${langCode}/${voiceName}/${quality}`;
                        const destDir = path.join(PIPER_DOWNLOAD_DIR, `${langShort}_${langCode}-${voiceName}-${quality}`);
                        fs.mkdirSync(destDir, { recursive: true });
                        const dlCmd = (url, dest) => {
                            try {
                                execSync(`curl -fSL -o "${dest}" "${url}"`, { stdio: 'pipe', timeout: 120000 });
                                return true;
                            } catch {
                                try {
                                    execSync(`wget -q -O "${dest}" "${url}"`, { stdio: 'pipe', timeout: 120000 });
                                    return true;
                                } catch { return false; }
                            }
                        };
                        console.log(`   Attempting direct download from Hugging Face...`);
                        const onnxDest = path.join(destDir, onnxFileName);
                        const configDest = path.join(destDir, configFileName);
                        if (dlCmd(`${baseUrl}/${onnxFileName}`, onnxDest)) {
                            dlCmd(`${baseUrl}/${configFileName}`, configDest);
                            found = findOnnx(PIPER_DOWNLOAD_DIR);
                        }
                        if (!found) {
                            console.warn(`   ⚠️  Direct download did not produce a usable model.`);
                        }
                    } catch (e3) {
                        console.warn(`   ⚠️  Failed to auto-download piper voice: ${e3.message}`);
                    }
                }
            }
        }
        if (found) {
            PIPER_MODEL = found;
            const configPath = found + '.json';
            if (fs.existsSync(configPath)) PIPER_MODEL_CONFIG = configPath;
            console.log(`   Piper model: ${path.basename(found)}`);
        }
    }
    if (!PIPER_MODEL) {
        console.warn(`   ⚠️  No piper model available. TTS will be unavailable.`);
    }
}
// Eagerly detect TTS only in non-headless mode at module load time
// (preserves original behaviour for demo/interactive modes)
if (!_detectCI() && !['headless'].includes((process.env.DEMO_MODE || '').toLowerCase().trim())) {
     getTTSEngine();
     _ensurePiperModel();
}

/**
 * Synthesise text to a temporary WAV file using the detected local TTS engine.
 * Returns the path to the WAV file, or null if TTS is unavailable.
 */
function synthesiseTTS(text) {
     if (currentMode === 'headless') return null;
     _ensurePiperModel(); // ensure model is resolved on first use
     const engine = getTTSEngine();
     if (!engine) return null;
    const tmpFile = path.join(os.tmpdir(), `demo-tts-${Date.now()}.wav`);
    try {
         if (engine === 'piper' && PIPER_MODEL) {
            const args = ['--model', PIPER_MODEL, '--output_file', tmpFile];
            if (PIPER_MODEL_CONFIG) args.push('--config', PIPER_MODEL_CONFIG);
            execSync(`echo ${JSON.stringify(text)} | piper ${args.join(' ')}`, {
                encoding: 'utf-8',
                stdio: 'pipe',
                timeout: 30000,
            });
         } else if (engine === 'espeak-ng') {
            execSync(`espeak-ng -w "${tmpFile}" ${JSON.stringify(text)}`, {
                encoding: 'utf-8',
                stdio: 'pipe',
                timeout: 15000,
            });
         } else if (engine === 'espeak') {
            execSync(`espeak -w "${tmpFile}" ${JSON.stringify(text)}`, {
                encoding: 'utf-8',
                stdio: 'pipe',
                timeout: 15000,
            });
        } else {
            return null;
        }
        if (fs.existsSync(tmpFile) && fs.statSync(tmpFile).size > 0) {
            return tmpFile;
        }
    } catch (e) {
        logWarn(`TTS synthesis failed: ${e.message}`);
    }
    try { fs.unlinkSync(tmpFile); } catch {}
    return null;
}

/**
 * Play a WAV/audio file in the browser page via an injected Audio element.
 * Waits for playback to finish before resolving.
 */
async function playAudioInPage(page, filePath) {
     if (currentMode === 'headless') {
         logDebug(`Skipping audio playback (headless mode): ${path.basename(filePath)}`);
         return;
     }
    const audioData = fs.readFileSync(filePath).toString('base64');
    const ext = path.extname(filePath).replace('.', '');
    const mime = ext === 'mp3' ? 'audio/mpeg'
        : ext === 'ogg' ? 'audio/ogg'
            : ext === 'wav' ? 'audio/wav'
                : `audio/${ext}`;
    await page.evaluate(({ dataUri }) => {
        return new Promise((resolve) => {
            const audio = new Audio(dataUri);
            audio.addEventListener('ended', resolve);
            audio.addEventListener('error', resolve);
            audio.play().catch(resolve);
        });
    }, { dataUri: `data:${mime};base64,${audioData}` });
}

// ---------------------------------------------------------------------------
// WSL detection
// ---------------------------------------------------------------------------
const RUNNING_IN_WSL = isWSL();
if (RUNNING_IN_WSL) {
    console.log('🐧 WSL environment detected — will use ffmpeg.exe with Windows capture devices (gdigrab/dshow).');
}

/**
 * Handle login if a login form is visible on the current page.
 *
 * @param {import('@playwright/test').Page} page
 * @param {string} credentialsPath - Path to credentials.json
 * @param {object} narration - The full NARRATION object (needs LOGIN_PROMPT & LOGIN_SUCCESS keys)
 * @param {object} [options]
 * @param {number} [options.typingDelay] - ms between keystrokes (default 35)
 * @param {number} [options.pauseAfter] - ms to wait after successful login (default SHORT_PAUSE)
 */
async function handleLogin(page, credentialsPath, narration, options = {}) {
    const typingDelay = options.typingDelay ?? TYPING_DELAY;
     const pauseAfter = adjustedPause(options.pauseAfter ?? SHORT_PAUSE);
     _ensureDiagnosticsInitialised();
     _autoAttachPageDiagnostics(page);


    logInfo('Checking for login form...');
    await takeScreenshot(page, 'login-check');

    const loginForm = page.locator('.login-container');
    if (!(await loginForm.isVisible({ timeout: 5000 }).catch(() => false))) {
        logInfo('No login form detected — skipping login.');
        return false;
    }

    logInfo('Login form detected — proceeding with authentication.');
    await diagnosticSnapshot(page, 'login-form-visible');

    if (narration['LOGIN_PROMPT']) {
        await say(page, narration['LOGIN_PROMPT'], SHORT_PAUSE);
    }

    let credentials;
    try {
        const raw = fs.readFileSync(credentialsPath, 'utf-8');
        credentials = JSON.parse(raw);
        logDebug('Credentials loaded successfully.', { path: credentialsPath });
    } catch (e) {
        logError(`Failed to load credentials from ${credentialsPath}`, { error: e.message });
        await captureErrorDiagnostics(page, e, 'loading-credentials');
        throw new Error(`Failed to load credentials from ${credentialsPath}: ${e.message}`);
    }

    await highlight(page, '#username');
    await page.locator('#username').click();
    await page.locator('#username').type(credentials.username, { delay: typingDelay });

    await highlight(page, '#password');
    await page.locator('#password').click();
    await page.locator('#password').type(credentials.password, { delay: typingDelay });

    await takeScreenshot(page, 'login-credentials-entered');

    await highlight(page, 'button[type="submit"]');
    await page.click('button[type="submit"]');

    logInfo('Login form submitted — waiting for navigation...');
    await page.waitForNavigation({ waitUntil: 'networkidle', timeout: 15000 }).catch((e) => {
        logWarn(`Navigation after login did not reach networkidle: ${e.message}`);
    });
    await page.waitForTimeout(pauseAfter);

    await diagnosticSnapshot(page, 'after-login');

    if (narration['LOGIN_SUCCESS']) {
        await say(page, narration['LOGIN_SUCCESS'], SHORT_PAUSE);
    }

    logInfo('Login completed successfully.');
    return true;
}

/**
 * Speak a narration entry by key. If the entry has a pre-rendered audio file,
 * it will be played; otherwise falls back to local TTS or silent pause.
 *
 * @param {import('@playwright/test').Page} page
 * @param {string} key  - Key into NARRATION (e.g. "INTRO")
 * @param {number} [pauseAfter] - Extra ms to wait after speech finishes
 */
async function say(page, entry, pauseAfter = SHORT_PAUSE) {
     _ensureDiagnosticsInitialised();
     _autoAttachPageDiagnostics(page);
     const text = entry.text || (typeof entry === 'string' ? entry : '');
     // In headless mode: log the narration text but skip all audio
     if (currentMode === 'headless') {
         if (text) {
             logInfo(`Narration (headless, skipped audio): ${text}`);
         }
         await page.waitForTimeout(adjustedPause(pauseAfter));
         return;
     }


    if (entry.audio) {
        const audioPath = path.resolve(path.dirname(NARRATION_PATH), entry.audio);
        if (fs.existsSync(audioPath)) {
            logDebug(`Playing pre-rendered audio: ${entry.audio}`);
            await playAudioInPage(page, audioPath);
            if (pauseAfter > 0) await page.waitForTimeout(pauseAfter);
            return;
        } else {
            logWarn(`Audio file "${entry.audio}" not found — falling back to local TTS.`);
        }
    }

    if (!text) return;

    console.log(`\n🎙️  ${text}`);
    logInfo(`Narration: ${text}`);

    const wavFile = synthesiseTTS(text);
    if (wavFile) {
        try {
            await playAudioInPage(page, wavFile);
        } finally {
            try { fs.unlinkSync(wavFile); } catch {}
        }
        if (pauseAfter > 0) await page.waitForTimeout(pauseAfter);
    } else {
        logWarn('No TTS available — silent pause only.');
        await page.waitForTimeout(pauseAfter > 0 ? pauseAfter : SHORT_PAUSE);
    }
}

/**
 * Detect if we are running inside Windows Subsystem for Linux (WSL).
 */
function isWSL() {
    if (process.platform !== 'linux') return false;
    try {
        const release = fs.readFileSync('/proc/version', 'utf-8').toLowerCase();
        return release.includes('microsoft') || release.includes('wsl');
    } catch {
        return false;
    }
}

// ---------------------------------------------------------------------------
// FFmpeg screen + audio recording helpers
// ---------------------------------------------------------------------------

/**
 * Detect the platform and return the appropriate ffmpeg args for
 * screen capture WITH system audio (loopback).
 */
function getFFmpegArgs(outputPath) {
    const platform = process.platform;

    if (platform === 'linux' && !RUNNING_IN_WSL) {
        let pulseMonitor = 'default.monitor';
        try {
            const sources = execSync('pactl list short sources 2>/dev/null', { encoding: 'utf-8' });
            const monitorLine = sources.split('\n').find(l => l.includes('.monitor'));
            if (monitorLine) {
                pulseMonitor = monitorLine.split('\t')[1] || 'default.monitor';
            }
        } catch {}

        let screenSize = '1920x1080';
        try {
            const xdpyinfo = execSync('xdpyinfo 2>/dev/null | grep dimensions', { encoding: 'utf-8' });
            const match = xdpyinfo.match(/(\d+x\d+)/);
            if (match) screenSize = match[1];
        } catch {}

        return [
            '-y',
            '-f', 'x11grab', '-framerate', '30', '-video_size', screenSize,
            '-i', process.env.DISPLAY || ':0',
            '-f', 'pulse', '-i', pulseMonitor,
            '-c:v', 'libx264', '-preset', 'ultrafast', '-crf', '23', '-pix_fmt', 'yuv420p',
            '-c:a', 'aac', '-b:a', '128k', '-ac', '2',
            outputPath
        ];
    } else if (RUNNING_IN_WSL) {
        let winOutputPath = outputPath;
        try {
            winOutputPath = execSync(`wslpath -w "${outputPath}"`, { encoding: 'utf-8' }).trim();
        } catch {
            const mntMatch = outputPath.match(/^\/mnt\/([a-zA-Z])\/(.*)/);
            if (mntMatch) {
                winOutputPath = `${mntMatch[1].toUpperCase()}:\\${mntMatch[2].replace(/\//g, '\\')}`;
            }
        }

        let audioDevice = null;
        try {
            const devices = execSync(
                'ffmpeg.exe -f dshow -list_devices true -i dummy 2>&1 || echo ""',
                { encoding: 'utf-8', shell: true }
            );
            const loopbackMatch = devices.match(/"((?:Stereo Mix|CABLE Output|Loopback|What U Hear|Internal AUX|Virtual Audio)[^"]*)"/i);
            if (loopbackMatch) audioDevice = loopbackMatch[1];
            if (!audioDevice) {
                const lines = devices.split('\n');
                for (const line of lines) {
                    if (line.includes('Alternative name')) continue;
                    const audioLineMatch = line.match(/"([^"]+)"\s*\(audio\)/i);
                    if (audioLineMatch) {
                        audioDevice = audioLineMatch[1];
                        break;
                    }
                }
                if (!audioDevice) {
                    let inAudioSection = false;
                    for (const line of lines) {
                        if (line.includes('DirectShow audio devices')) inAudioSection = true;
                        else if (line.includes('DirectShow video devices')) inAudioSection = false;
                        if (inAudioSection) {
                            const nameMatch = line.match(/"([^"]+)"/);
                            if (nameMatch && !line.includes('Alternative name')) {
                                audioDevice = nameMatch[1];
                                break;
                            }
                        }
                    }
                }
            }
        } catch (e) {
            logWarn(`Failed to enumerate dshow devices: ${e.message}`);
        }
        if (!audioDevice) {
            logWarn(
                'No audio capture device found (WSL/Windows). ' +
                'Enable "Stereo Mix" in Windows Sound settings, or install virtual audio cable (e.g. VB-CABLE). ' +
                'Recording will proceed WITHOUT audio.'
            );
            return [
                '-y',
                '-f', 'gdigrab', '-framerate', '30', '-i', 'desktop',
                '-c:v', 'libx264', '-preset', 'ultrafast', '-crf', '23', '-pix_fmt', 'yuv420p',
                '-an',
                winOutputPath
            ];
        }
        logInfo(`Audio device: "${audioDevice}"`);

        return [
            '-y',
            '-f', 'gdigrab', '-framerate', '30', '-i', 'desktop',
            '-f', 'dshow', '-i', `audio=${audioDevice}`,
            '-c:v', 'libx264', '-preset', 'ultrafast', '-crf', '23', '-pix_fmt', 'yuv420p',
            '-c:a', 'aac', '-b:a', '128k', '-ac', '2',
            winOutputPath
        ];
    } else if (platform === 'darwin') {
        let audioDevice = '0';
        try {
            const devices = execSync(
                'ffmpeg -f avfoundation -list_devices true -i "" 2>&1 || true',
                { encoding: 'utf-8' }
            );
            const lines = devices.split('\n');
            let inAudioSection = false;
            for (const line of lines) {
                if (line.includes('audio devices')) inAudioSection = true;
                if (inAudioSection) {
                    const bhMatch = line.match(/\[(\d+)\].*(?:BlackHole|Soundflower|Loopback)/i);
                    if (bhMatch) { audioDevice = bhMatch[1]; break; }
                }
            }
        } catch {}

        return [
            '-y',
            '-f', 'avfoundation', '-framerate', '30', '-capture_cursor', '1',
            '-i', `1:${audioDevice}`,
            '-c:v', 'libx264', '-preset', 'ultrafast', '-crf', '23', '-pix_fmt', 'yuv420p',
            '-c:a', 'aac', '-b:a', '128k', '-ac', '2',
            outputPath
        ];
    } else if (platform === 'win32') {
        let audioDevice = null;
        try {
            const devices = execSync(
                'ffmpeg -f dshow -list_devices true -i dummy 2>&1 || echo ""',
                { encoding: 'utf-8', shell: true }
            );
            const loopbackMatch = devices.match(/"((?:Stereo Mix|CABLE Output|Loopback|What U Hear|Internal AUX|Virtual Audio)[^"]*)"/i);
            if (loopbackMatch) audioDevice = loopbackMatch[1];
            if (!audioDevice) {
                const lines = devices.split('\n');
                for (const line of lines) {
                    if (line.includes('Alternative name')) continue;
                    const audioLineMatch = line.match(/"([^"]+)"\s*\(audio\)/i);
                    if (audioLineMatch) {
                        audioDevice = audioLineMatch[1];
                        break;
                    }
                }
                if (!audioDevice) {
                    let inAudioSection = false;
                    for (const line of lines) {
                        if (line.includes('DirectShow audio devices')) inAudioSection = true;
                        else if (line.includes('DirectShow video devices')) inAudioSection = false;
                        if (inAudioSection) {
                            const nameMatch = line.match(/"([^"]+)"/);
                            if (nameMatch && !line.includes('Alternative name')) {
                                audioDevice = nameMatch[1];
                                break;
                            }
                        }
                    }
                }
            }
        } catch (e) {
            logWarn(`Failed to enumerate dshow devices: ${e.message}`);
        }
        if (!audioDevice) {
            logWarn(
                'No audio capture device found on Windows. ' +
                'Enable "Stereo Mix" in Windows Sound settings, or install virtual audio cable (e.g. VB-CABLE). ' +
                'Recording will proceed WITHOUT audio.'
            );
            return [
                '-y',
                '-f', 'gdigrab', '-framerate', '30', '-i', 'desktop',
                '-c:v', 'libx264', '-preset', 'ultrafast', '-crf', '23', '-pix_fmt', 'yuv420p',
                '-an',
                outputPath
            ];
        }
        logInfo(`Audio device: "${audioDevice}"`);

        return [
            '-y',
            '-f', 'gdigrab', '-framerate', '30', '-i', 'desktop',
            '-f', 'dshow', '-i', `audio=${audioDevice}`,
            '-c:v', 'libx264', '-preset', 'ultrafast', '-crf', '23', '-pix_fmt', 'yuv420p',
            '-c:a', 'aac', '-b:a', '128k', '-ac', '2',
            outputPath
        ];
    }

    logWarn(`Unsupported platform (${platform}). Recording video only.`);
    return [
        '-y',
        '-f', 'x11grab', '-framerate', '30', '-video_size', '1920x1080',
        '-i', process.env.DISPLAY || ':0',
        '-c:v', 'libx264', '-preset', 'ultrafast', '-crf', '23', '-pix_fmt', 'yuv420p',
        '-an',
        outputPath
    ];
}

/**
 * Start ffmpeg recording. Returns an object with stop() and isRunning().
 */
function startRecording(outputPath) {
     _ensureDiagnosticsInitialised();
     // In headless or interactive mode, return a no-op recorder
     if (!isRecordingEnabled()) {
         logInfo(`Recording skipped (mode: ${currentMode}). Output would have been: ${outputPath}`);
         _recordEvent('recording_skipped', { mode: currentMode, outputPath });
         return {
             stop: () => {
                 logInfo('Recording stop called (no-op — recording was not started).');
                 return Promise.resolve(null);
             },
             process: null,
             isRunning: () => false,
         };
     }


    const dir = path.dirname(outputPath);
    if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true });
    }

    const args = getFFmpegArgs(outputPath);
    const ffmpegBin = RUNNING_IN_WSL ? 'ffmpeg.exe' : 'ffmpeg';

    logInfo(`Starting screen + audio recording with ${ffmpegBin}`, { outputPath });
    console.log(`\n🎬 Starting screen + audio recording with ${ffmpegBin}...`);
    console.log(`   Output: ${outputPath}`);
    console.log(`   Command: ${ffmpegBin} ${args.join(' ')}`);

    _recordEvent('recording_start', { outputPath, ffmpegBin, args: args.join(' ') });

    const ffmpeg = spawn(ffmpegBin, args, {
        stdio: ['pipe', 'pipe', 'pipe'],
    });

    let ffmpegRunning = true;
    let stderrLog = '';

    // Write ffmpeg stderr to a dedicated log file
    const ffmpegLogPath = diagConfig.logDir
        ? path.join(diagConfig.logDir, 'ffmpeg-stderr.log')
        : path.join(dir, 'ffmpeg-stderr.log');
    const ffmpegLogStream = fs.createWriteStream(ffmpegLogPath, { flags: 'a', encoding: 'utf-8' });
    ffmpegLogStream.write(`# ffmpeg stderr log — ${new Date().toISOString()}\n`);
    ffmpegLogStream.write(`# Command: ${ffmpegBin} ${args.join(' ')}\n\n`);

    ffmpeg.stderr.on('data', (data) => {
        const chunk = data.toString();
        stderrLog += chunk;
        ffmpegLogStream.write(chunk);
    });

    ffmpeg.stdout.on('data', (data) => {
        ffmpegLogStream.write(`[stdout] ${data.toString()}`);
    });

    ffmpeg.on('error', (err) => {
        ffmpegRunning = false;
        if (err.code === 'ENOENT') {
            logError(`${ffmpegBin} not found! Please install ffmpeg.`);
            if (RUNNING_IN_WSL) {
                logError('WSL detected: you need ffmpeg.exe (Windows) on your PATH.');
            }
        } else {
            logError(`${ffmpegBin} error: ${err.message}`);
        }
        _recordEvent('recording_error', { error: err.message, code: err.code });
    });

    ffmpeg.on('close', (code) => {
        ffmpegRunning = false;
        ffmpegLogStream.end();
        if (code !== 0 && code !== 255) {
            logError(`ffmpeg exited with code ${code}`);
            const lines = stderrLog.trim().split('\n');
            const tail = lines.slice(-10).join('\n');
            if (tail) logError(`Last ffmpeg output:\n${tail}`);
        }
        _recordEvent('recording_close', { exitCode: code });
    });

    const startTime = Date.now();

    return {
        stop: () => {
            return new Promise((resolve) => {
                if (!ffmpegRunning) { resolve(outputPath); return; }
                logInfo('Stopping recording...');
                console.log('\n🎬 Stopping recording...');
                try {
                    ffmpeg.stdin.write('q');
                    ffmpeg.stdin.end();
                } catch {
                    try { ffmpeg.kill('SIGINT'); } catch {}
                }
                const timeout = setTimeout(() => {
                    try { ffmpeg.kill('SIGKILL'); } catch {}
                    resolve(outputPath);
                }, 10000);
                ffmpeg.on('close', () => {
                    clearTimeout(timeout);
                    const duration = ((Date.now() - startTime) / 1000).toFixed(1);
                    logInfo(`Recording saved (${duration}s): ${outputPath}`);
                    console.log(`🎬 Recording saved (${duration}s): ${outputPath}`);
                    _recordEvent('recording_saved', { duration, outputPath });
                    resolve(outputPath);
                });
            });
        },
        process: ffmpeg,
        isRunning: () => ffmpegRunning,
    };
}

// ---------------------------------------------------------------------------
// Narration helper — uses local TTS engine
// ---------------------------------------------------------------------------
async function narrate(page, text, pauseMs = MEDIUM_PAUSE) {
     _ensureDiagnosticsInitialised();
     _autoAttachPageDiagnostics(page);

     if (currentMode === 'headless') {
         logInfo(`Narration (headless, skipped audio): ${text}`);
         await page.waitForTimeout(adjustedPause(pauseMs));
         return;
     }

     console.log(`\n🎙️  ${text}`);
     logInfo(`Narrate: ${text}`);

     const wavFile = synthesiseTTS(text);
     if (wavFile) {
         try {
             await playAudioInPage(page, wavFile);
         } finally {
             try { fs.unlinkSync(wavFile); } catch {}
        }
    } else {
        logWarn('No audio — TTS unavailable');
    }
    await page.waitForTimeout(Math.min(pauseMs, SHORT_PAUSE));
}

// ---------------------------------------------------------------------------
// Highlight an element briefly
// ---------------------------------------------------------------------------
async function highlight(page, selector, durationMs = 1200) {
     _ensureDiagnosticsInitialised();
     _autoAttachPageDiagnostics(page);

     const effectiveDuration = adjustedPause(durationMs);
     logDebug(`Highlighting: ${selector}`, { duration: effectiveDuration, mode: currentMode });

     // In headless mode, skip the visual highlight entirely but still log it
     if (currentMode === 'headless') {
         await page.waitForTimeout(effectiveDuration);
         return;
     }

     await page.evaluate(({ sel, dur }) => {
         const el = document.querySelector(sel);
         if (!el) return;
         const prev = el.style.outline;
         el.style.outline = '3px solid #ff6600';
         el.style.outlineOffset = '3px';
         setTimeout(() => { el.style.outline = prev; el.style.outlineOffset = ''; }, dur);
     }, { sel: selector, dur: effectiveDuration });
     await page.waitForTimeout(effectiveDuration);
}

// ---------------------------------------------------------------------------
// Open session monitor link in new tab, wait for task completion
// ---------------------------------------------------------------------------
async function openSessionLinkAndWaitForCompletion(page, mainPage, taskDescription, timeoutMs = 120000) {
    let sessionTab = null;
     _ensureDiagnosticsInitialised();
     _autoAttachPageDiagnostics(page);


    logInfo(`Waiting for session monitor link: ${taskDescription}`);
    _recordEvent('session_monitor_start', { task: taskDescription });

    try {
        await page.waitForSelector('.session-monitor-link a.monitor-link', { timeout: 15000 });
         await page.waitForTimeout(adjustedPause(1000));

        const monitorLink = page.locator('.session-monitor-link a.monitor-link').last();
        if (await monitorLink.isVisible()) {
            const href = await monitorLink.getAttribute('href');
            logInfo(`Session monitor link found: ${href}`, { task: taskDescription });

            await takeScreenshot(page, `session-link-${taskDescription}`);

             if (isNarrationEnabled()) {
                 await narrate(page,
                     `A live session monitor appeared for ${taskDescription}. Let's watch the AI work in real-time.`,
                     SHORT_PAUSE);
             }

            const context = page.context();
            sessionTab = await context.newPage();

            // Attach diagnostics to the session tab
            attachPageDiagnostics(sessionTab, `session-${taskDescription}`);

            const fullUrl = new URL(href, page.url()).toString();
            logInfo(`Opening session monitor tab: ${fullUrl}`);
            await sessionTab.goto(fullUrl, { waitUntil: 'domcontentloaded', timeout: 15000 }).catch((e) => {
                logWarn(`Session tab navigation issue: ${e.message}`);
            });

            await diagnosticSnapshot(sessionTab, `session-monitor-${taskDescription}-opened`);

             if (isNarrationEnabled()) {
                 await narrate(sessionTab,
                     `This is the live session view for ${taskDescription}. We can see prompts, responses, and progress as they happen.`,
                     MEDIUM_PAUSE);
             }

            await sessionTab.evaluate(() => {
                window.scrollTo({ top: 300, behavior: 'smooth' });
            }).catch(() => {});
             await sessionTab.waitForTimeout(adjustedPause(MEDIUM_PAUSE));

            await takeScreenshot(sessionTab, `session-monitor-${taskDescription}-scrolled`);
        }
    } catch (e) {
        logInfo(`No session monitor link detected for ${taskDescription}: ${e.message}`);
    }

    // Wait for task completion on the main page
    logInfo(`Waiting for task completion: ${taskDescription} (timeout: ${timeoutMs}ms)`);
    try {
        await page.waitForFunction(() => {
            const completed = document.querySelector('.session-completed-link');
            const errored = document.querySelector('.session-error-link');
            return completed || errored;
        }, { timeout: timeoutMs });

        // Check if it was an error
        const hasError = await page.evaluate(() => !!document.querySelector('.session-error-link'));
        if (hasError) {
            logWarn(`Task ${taskDescription} completed with error.`);
            await captureErrorDiagnostics(page, new Error('Task completed with error'), `task-${taskDescription}`);
        } else {
            logInfo(`Task ${taskDescription} completed successfully.`);
        }
        await diagnosticSnapshot(page, `task-complete-${taskDescription}`);
    } catch (e) {
        logWarn(`Task ${taskDescription} did not complete within timeout: ${e.message}`);
        await diagnosticSnapshot(page, `task-timeout-${taskDescription}`);
    }

    // Close session tab if opened
    if (sessionTab) {
        try {
            await sessionTab.reload({ waitUntil: 'domcontentloaded', timeout: 10000 }).catch(() => {});
             await sessionTab.waitForTimeout(adjustedPause(SHORT_PAUSE));

            await takeScreenshot(sessionTab, `session-monitor-${taskDescription}-final`);

            await sessionTab.evaluate(() => {
                window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' });
            }).catch(() => {});

            await takeScreenshot(sessionTab, `session-monitor-${taskDescription}-bottom`);

             if (isNarrationEnabled()) {
                 await narrate(sessionTab,
                     `${taskDescription} is complete. Let's go back to the main app.`,
                     SHORT_PAUSE);
             }
            await sessionTab.close();
        } catch {
            await sessionTab.close().catch(() => {});
        }
        await page.bringToFront();
         await page.waitForTimeout(adjustedPause(800));
    }

    _recordEvent('session_monitor_end', { task: taskDescription });
}

// ---------------------------------------------------------------------------
// Exports
// ---------------------------------------------------------------------------
module.exports = {
    // Recording
    startRecording,
     // Mode management
     setMode,
     getMode,
     isHeadless,
     isNarrationEnabled,
     isRecordingEnabled,
     adjustedPause,


    // UI helpers
    highlight,
    openSessionLinkAndWaitForCompletion,
    say,
    handleLogin,
    setNarrationPath,

    // Diagnostics — initialisation & teardown
    initDiagnostics,
    flushDiagnostics,
    attachPageDiagnostics,

    // Diagnostics — snapshots & captures
    takeScreenshot,
    dumpPageHTML,
    dumpAccessibilityTree,
    capturePerformanceMetrics,
    captureElementStates,
    diagnosticSnapshot,
    captureErrorDiagnostics,

    // Diagnostics — logging
    logDebug,
    logInfo,
    logWarn,
    logError,

    // Constants
    SHORT_PAUSE,
    MEDIUM_PAUSE,
    TYPING_DELAY,
     HEADLESS_PAUSE_FACTOR,
     HEADLESS_MIN_PAUSE,
};