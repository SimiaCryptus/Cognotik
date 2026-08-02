/**
 * menu.js — common application menu bar
 *
 * Renders a shared menubar into any Cognotik app page:
  *   • Context-aware navigation (host root, app root, new session, session, files)
  *   • Link to the filesystem ("Files") view for the current session
*   • Git status + operations (init / commit / branches / new branch / log /
*     hard reset / clean), destructive actions gated behind a confirmation
 *   • Sessions: currently running tasks and all known sessions, with navigation
 *   • Token usage summary + per-model breakdown
*   • Available budget indicator + "Usage & Credits" (buy credits) dialog
 *
 * Usage:
 *   import { initMenu } from '/lib/app/menu.js';
 *   const menu = initMenu({ appName: 'Resume Customizer' });
 */

import {getProxyUrl as defaultGetProxyUrl} from './session.js';
import {
     getStatus, initRepository, commit, getBranches, checkout, getLog, formatStatus,
     createBranch, resetHard, clean as cleanWorkingTree
} from './git.js';
import {fetchDocopsStatus} from './docops.js';
import {aggregateUsage, createUsageTableHtml, renderUsageSummary} from './usage.js';
import {escapeHtml, showToast} from './ui.js';
import {serverUrl, getConfig} from './config.js';

const STYLE_ID = 'cognotik-menu-style';
const MENU_ID = 'cognotik-menu';
const DEFAULT_BUDGET_REFRESH_MS = 60000;

const MENU_CSS = `
.cog-menu { font: 14px/1.4 system-ui, -apple-system, "Segoe UI", sans-serif; background: #1d2330; color: #e7ecf3; box-shadow: 0 1px 4px rgba(0,0,0,.35); z-index: 900; }
.cog-menu-sticky { position: sticky; top: 0; }
.cog-menu a { color: #cfe0ff; text-decoration: none; }
.cog-menu a:hover { text-decoration: underline; }
.cog-menu-bar { display: flex; align-items: center; gap: .6rem; padding: .4rem .8rem; flex-wrap: wrap; }
.cog-menu-brand { font-weight: 700; color: #fff !important; margin-right: .3rem; }
.cog-menu-context { font-size: .78rem; color: #9fb0c8; border: 1px solid #33405a; border-radius: 10px; padding: .1rem .5rem; white-space: nowrap; }
.cog-menu-links { display: flex; align-items: center; gap: .55rem; flex-wrap: wrap; }
.cog-menu-links a { padding: .15rem .35rem; border-radius: 4px; }
.cog-menu-links a.active { background: #2c3446; }
.cog-menu-grow { flex: 1 1 auto; }
.cog-menu-tabs { display: flex; gap: .35rem; }
.cog-menu button { font: inherit; background: #2c3446; color: #e7ecf3; border: 1px solid #3c4760; border-radius: 5px; padding: .2rem .6rem; cursor: pointer; }
.cog-menu button:hover:not(:disabled) { background: #38425a; }
.cog-menu button[aria-expanded="true"] { background: #4a90d9; border-color: #4a90d9; color: #fff; }
.cog-menu button:disabled { opacity: .45; cursor: not-allowed; }
.cog-menu button.cog-danger { background: #5a2b2b; border-color: #7a3a3a; color: #ffd9d9; }
.cog-menu button.cog-danger:hover:not(:disabled) { background: #7a3a3a; }
.cog-menu .cog-danger-link { color: #ff9b9b !important; }
.cog-menu-panel { border-top: 1px solid #333d52; background: #232a39; padding: .6rem .8rem; max-height: 55vh; overflow: auto; }
.cog-panel-head { display: flex; align-items: center; gap: .6rem; margin-bottom: .5rem; flex-wrap: wrap; }
.cog-panel-head strong { font-size: .95rem; }
.cog-panel-actions { display: flex; gap: .35rem; flex-wrap: wrap; }
.cog-muted { color: #93a2b8; font-size: .85rem; margin: .2rem 0; }
.cog-menu ul.cog-list { list-style: none; margin: .2rem 0 .6rem; padding: 0; }
.cog-menu ul.cog-list li { display: flex; gap: .5rem; align-items: center; padding: .18rem 0; border-bottom: 1px solid #2c3446; font-size: .86rem; }
.cog-tag { font-size: .7rem; text-transform: uppercase; letter-spacing: .03em; border-radius: 8px; padding: .05rem .4rem; background: #3c4760; }
.cog-tag.running { background: #b7791f; color: #fff; }
.cog-tag.completed { background: #2f855a; color: #fff; }
.cog-tag.error { background: #c53030; color: #fff; }
.cog-usage-summary { display: flex; gap: 1.2rem; flex-wrap: wrap; margin-bottom: .5rem; font-size: .85rem; }
.cog-usage-summary span b { display: block; color: #9fb0c8; font-weight: 500; font-size: .72rem; text-transform: uppercase; }
.cog-menu table { border-collapse: collapse; width: 100%; font-size: .82rem; }
.cog-menu table th, .cog-menu table td { border-bottom: 1px solid #2f394d; padding: .25rem .4rem; text-align: left; }
.cog-menu .git-status-box { font-size: .85rem; }
.cog-menu .git-changes-list { list-style: none; padding-left: 0; }
.cog-menu button.cog-budget-btn { display: inline-flex; align-items: center; gap: .3rem; }
.cog-menu button.cog-budget-btn.budget-warning { background: #b7791f; border-color: #b7791f; color: #fff; }
.cog-menu button.cog-budget-btn.budget-critical { background: #c53030; border-color: #c53030; color: #fff; }
.cog-budget-banner { padding: .35rem .8rem; font-size: .82rem; background: #b7791f; color: #fff; }
.cog-budget-banner.critical { background: #c53030; }
.cog-budget-banner a { color: #fff !important; text-decoration: underline; }
.cog-budget-banner[hidden] { display: none; }
.cog-credits-overlay { position: fixed; inset: 0; background: rgba(0,0,0,.55); z-index: 1000; display: flex; align-items: center; justify-content: center; }
.cog-credits-overlay[hidden] { display: none; }
.cog-credits-dialog { font: 14px/1.4 system-ui, -apple-system, "Segoe UI", sans-serif; background: #1d2330; color: #e7ecf3; width: min(1100px, 92vw); height: 85vh; border-radius: 8px; display: flex; flex-direction: column; overflow: hidden; box-shadow: 0 12px 40px rgba(0,0,0,.5); }
.cog-credits-head { display: flex; align-items: center; gap: .6rem; padding: .5rem .8rem; border-bottom: 1px solid #333d52; }
.cog-credits-head strong { flex: 1 1 auto; font-size: .95rem; }
.cog-credits-head button { font: inherit; background: #2c3446; color: #e7ecf3; border: 1px solid #3c4760; border-radius: 5px; padding: .2rem .6rem; cursor: pointer; }
.cog-credits-head button:hover { background: #38425a; }
.cog-credits-dialog iframe { flex: 1 1 auto; width: 100%; border: none; background: #fff; }
@media (max-width: 700px) { .cog-menu-grow { display: none; } }
`;

function injectStyles() {
    if (document.getElementById(STYLE_ID)) return;
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = MENU_CSS;
    document.head.appendChild(style);
}

/** Escape a value for use inside a double-quoted HTML attribute. */
function attr(value) {
    return String(value == null ? '' : value)
        .replace(/&/g, '&amp;')
        .replace(/"/g, '&quot;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');
}

/**
 * Determine the navigation context from the current (or supplied) location.
 * @param {Location|Object} [loc=window.location]
 * @returns {{view:string, pathname:string, appId:string, sessionId:string, appRoot:string, basePath:string}}
 */
export function getMenuContext(loc = window.location) {
    const cfg = getConfig();
    const pathname = loc.pathname || '/';
    const parts = pathname.split('/').filter(Boolean);
    const ctx = {view: 'unknown', pathname, appId: '', sessionId: '', appRoot: '', basePath: ''};

    const fileIndexIdx = parts.indexOf('fileIndex');
    const uiIdx = parts.indexOf('ui');

    if (fileIndexIdx >= 0) {
        ctx.view = 'session';
        ctx.appId = parts[fileIndexIdx - 1] || '';
        ctx.sessionId = parts[fileIndexIdx + 1] || '';
        ctx.appRoot = '/' + parts.slice(0, fileIndexIdx).join('/');
        ctx.basePath = '/' + parts.slice(0, fileIndexIdx + 2).join('/');
    } else if (uiIdx >= 0) {
        ctx.view = 'ide';
        ctx.appId = parts[uiIdx - 1] || '';
        ctx.appRoot = '/' + parts.slice(0, uiIdx).join('/');
        const q = new URLSearchParams(loc.search || '');
        const hashMatch = /session=([^&/#]+)/.exec(loc.hash || '');
        ctx.sessionId = q.get('session') || (hashMatch ? hashMatch[1] : '');
        if (ctx.appRoot && ctx.sessionId) ctx.appRoot = ctx.appRoot.replace(/\/$/, '');
    } else if (parts.length === 0) {
        ctx.view = 'home';
    } else if (parts[parts.length - 1] === 'new' || parts[parts.length - 1] === 'new.html') {
        ctx.view = 'new';
        ctx.appId = parts[parts.length - 2] || '';
        ctx.appRoot = '/' + parts.slice(0, parts.length - 1).join('/');
    } else if (parts[0] === 'proxy') {
        ctx.view = 'proxy';
        const q = new URLSearchParams(loc.search || '');
        ctx.sessionId = q.get('session') || (loc.hash || '').replace(/^#/, '');
    } else {
        ctx.view = 'app';
        ctx.appId = parts[0];
        ctx.appRoot = '/' + parts[0];
    }

    // Explicit overrides from the loading page always win.
    if (cfg.appId) ctx.appId = cfg.appId;
    if (cfg.sessionId) ctx.sessionId = cfg.sessionId;
    if (cfg.basePath) ctx.basePath = cfg.basePath;
    if (!ctx.appRoot && ctx.appId) ctx.appRoot = '/' + ctx.appId;
    if (!ctx.basePath && ctx.appRoot && ctx.sessionId) {
        ctx.basePath = ctx.appRoot + '/fileIndex/' + ctx.sessionId;
    }
    return ctx;
}

/**
 * Build the filesystem IDE URL for a context (e.g. `/my-app/ui/?session=U-123#/`).
 * @param {Object} ctx
 * @returns {string|null}
 */
export function getIdeUrl(ctx) {
    if (!ctx.appRoot || !ctx.sessionId) return null;
    const template = getConfig().ideUrlTemplate || '{appRoot}/ui/?session={sessionId}#/';
    const path = template
        .replace('{appRoot}', ctx.appRoot)
        .replace('{appId}', ctx.appId)
        .replace('{sessionId}', encodeURIComponent(ctx.sessionId));
    return serverUrl(path);
}

async function fetchJson(url) {
    try {
        const resp = await fetch(url, {headers: {'Accept': 'application/json'}, credentials: 'include'});
        if (!resp.ok) return null;
        const ct = resp.headers.get('content-type') || '';
        if (!ct.includes('json')) return null;
        return await resp.json();
    } catch (e) {
        return null;
    }
}

function normalizeSessions(data) {
    let list = [];
    if (Array.isArray(data)) list = data;
    else if (Array.isArray(data.sessions)) list = data.sessions;
    else if (data.sessions && typeof data.sessions === 'object') {
        list = Object.entries(data.sessions).map(([id, v]) =>
            (v && typeof v === 'object') ? {sessionId: id, ...v} : {sessionId: id, name: String(v || '')});
    } else if (Array.isArray(data.entries)) list = data.entries;

    return list
        .map(s => (typeof s === 'string')
            ? {sessionId: s, name: '', active: false}
            : {
                sessionId: s.sessionId || s.id || s.session || s.key || '',
                name: s.name || s.title || s.sessionName || s.description || '',
                active: !!(s.active || s.running || s.alive)
            })
        .filter(s => s.sessionId);
}

/**
 * Fetch the list of known sessions. Endpoint may be overridden via
 * `configure({ sessionsEndpoint })` or the `sessionsEndpoint` menu option.
 * @param {Object} ctx
 * @param {string|null} [endpoint]
 * @returns {Promise<Array<{sessionId:string,name:string,active:boolean}>>}
 */
export async function fetchSessionList(ctx, endpoint = null) {
    const configured = endpoint || getConfig().sessionsEndpoint;
    const candidates = configured ? [configured] : [
        ctx.appRoot ? serverUrl(ctx.appRoot + '/api/sessions?format=json') : null,
        ctx.appRoot ? serverUrl(ctx.appRoot + '/sessions?format=json') : null,
        serverUrl('/api/sessions?format=json')
    ].filter(Boolean);

    for (const url of candidates) {
        const data = await fetchJson(url);
        if (data) {
            const list = normalizeSessions(data);
            if (list.length) return list;
        }
    }
    return [];
}

/**
 * Collect the currently running docops tasks for a session base path.
 * @param {string} basePath
 * @returns {Promise<Array<{target:string,status:string,taskId:string}>>}
 */
export async function fetchRunningTasks(basePath) {
    if (!basePath) return [];
    const status = await fetchDocopsStatus(basePath);
    if (!status || !status.tasks) return [];
    return Object.entries(status.tasks).map(([target, info]) => ({
        target,
        status: (info && info.status) || 'UNKNOWN',
        taskId: (info && (info.taskId || info.sessionId || info.id)) || ''
    }));
}

function statusTagClass(status) {
    const s = String(status || '').toUpperCase();
    if (s === 'RUNNING') return 'running';
    if (s === 'COMPLETED') return 'completed';
    if (s === 'ERROR' || s === 'FAILED') return 'error';
    return '';
}
/**
  * Format a currency amount the same way the homepage menubar does.
  * @param {number|null} amount
  * @returns {string}
  */
export function formatBudget(amount) {
     if (typeof amount !== 'number' || isNaN(amount)) return '\u2014';
     const sign = amount < 0 ? '-' : '';
     return sign + '$' + Math.abs(amount).toFixed(2);
}
/**
  * Fetch the currently available budget from the shared usage JSON endpoint.
  * Endpoint may be overridden via `configure({ usageJsonEndpoint })` or the
  * `usageJsonUrl` menu option.
  * @param {string|null} [endpoint]
  * @returns {Promise<number|null>} available budget, or null if unknown
  */
export async function fetchBudget(endpoint = null) {
     const url = endpoint
         || getConfig().usageJsonEndpoint
         || serverUrl('/usage/?format=json');
     const data = await fetchJson(url);
     if (!data) return null;
     return (typeof data.available_budget === 'number') ? data.available_budget : null;
}

function contextLabel(ctx, opts) {
    const bits = [];
    //if (opts.appName) bits.push(opts.appName);
    if (ctx.sessionId) bits.push(ctx.sessionId);
    if (!bits.length) bits.push(ctx.view);
    return bits.join(' · ');
}

function buildLinksHtml(ctx, opts) {
    const links = [];
    if (ctx.appRoot) {
         const appLabel = opts.appName || ctx.appId || 'App';
         links.push({href: serverUrl(ctx.appRoot + '/'), label: appLabel, active: ctx.view === 'app'});
        links.push({
            href: serverUrl(ctx.appRoot + '/' + opts.newSessionPath),
             label: 'New',
            active: ctx.view === 'new'
        });
    }
    if (opts.showIde) {
        const ide = getIdeUrl(ctx);
         if (ide) links.push({href: ide, label: 'Files', target: '_blank', active: ctx.view === 'ide'});
    }
    (opts.extraLinks || []).forEach(l => links.push(l));

    return links.map(l =>
        `<a href="${attr(l.href)}"${l.target ? ` target="${attr(l.target)}"` : ''}` +
        `${l.active ? ' class="active"' : ''}>${escapeHtml(l.label)}</a>`
    ).join('');
}

/**
 * Initialize (or re-initialize) the menu bar.
 * @param {Object} [options]
 * @param {HTMLElement|string} [options.mount] - Container/selector; defaults to top of <body>
 * @param {string} [options.appName]
 * @param {boolean} [options.showGit=true]
 * @param {boolean} [options.showSessions=true]
 * @param {boolean} [options.showUsage=true]
* @param {boolean} [options.showBudget=true] - Show available budget + credits dialog
* @param {number}  [options.budgetRefreshMs=60000] - 0 disables auto-refresh
* @param {string}  [options.usageUrl] - Page shown in the credits dialog (default `/usage/`)
* @param {string}  [options.usageJsonUrl] - JSON endpoint for the budget amount
 * @param {boolean} [options.showIde=true]
 * @param {boolean} [options.sticky=true]
 * @param {string} [options.newSessionPath='new']
 * @param {Function} [options.getProxyUrl]
 * @param {Array|Function} [options.sessionIds] - Extra session IDs to include in usage totals
 * @param {Array} [options.extraLinks] - [{ href, label, target }]
 * @param {string} [options.sessionsEndpoint]
 * @returns {Object} menu controller
 */
export function initMenu(options = {}) {
    injectStyles();

    const opts = Object.assign({
        mount: null,
        appName: '',
        showGit: true,
        showSessions: true,
        showUsage: true,
         showBudget: true,
         budgetRefreshMs: DEFAULT_BUDGET_REFRESH_MS,
         usageUrl: null,
         usageJsonUrl: null,
        showIde: true,
        sticky: true,
        newSessionPath: 'new',
        getProxyUrl: defaultGetProxyUrl,
        sessionIds: null,
        extraLinks: [],
        sessionsEndpoint: null
    }, options);

    const ctx = getMenuContext();
    if (!opts.appName) opts.appName = ctx.appId || 'Cognotik';

    const existing = document.getElementById(MENU_ID);
    if (existing && existing.parentNode) existing.parentNode.removeChild(existing);

    const nav = document.createElement('nav');
    nav.id = MENU_ID;
    nav.className = 'cog-menu' + (opts.sticky ? ' cog-menu-sticky' : '');
    nav.innerHTML = `
            <div class="cog-menu-bar">
                <a class="cog-menu-brand" href="${attr(serverUrl('/'))}">&#129504; Cognotik</a>
                <div class="cog-menu-links">${buildLinksHtml(ctx, opts)}</div>
                <span class="cog-menu-context" title="${attr(ctx.pathname)}">${escapeHtml(contextLabel(ctx, opts))}</span>
                <div class="cog-menu-grow"></div>
                <div class="cog-menu-tabs">
                     ${opts.showBudget ? '<button type="button" class="cog-budget-btn" data-budget-btn title="Usage and available credits">&#128202; <span data-budget-amount>Budget</span></button>' : ''}
                    ${opts.showGit ? `<button type="button" data-tab="git" aria-expanded="false"${ctx.basePath ? '' : ' disabled title="No session context"'}>Git</button>` : ''}
                    ${opts.showSessions ? '<button type="button" data-tab="sessions" aria-expanded="false">Sessions</button>' : ''}
                    ${opts.showUsage ? '<button type="button" data-tab="usage" aria-expanded="false">Usage</button>' : ''}
                </div>
            </div>
             <div class="cog-budget-banner" data-budget-banner role="alert" aria-live="polite" hidden></div>
            <section class="cog-menu-panel" data-panel="git" hidden>
                <div class="cog-panel-head">
                    <strong>Git</strong>
                    <div class="cog-panel-actions">
                        <button type="button" data-git="status">Status</button>
                        <button type="button" data-git="init">Init</button>
                        <button type="button" data-git="commit">Commit&hellip;</button>
                        <button type="button" data-git="branches">Branches</button>
                        <button type="button" data-git="new-branch">New Branch&hellip;</button>
                        <button type="button" data-git="log">Log</button>
                        <button type="button" class="cog-danger" data-git="reset" title="git reset --hard">Reset&nbsp;--hard</button>
                        <button type="button" class="cog-danger" data-git="clean" title="git clean -fdx">Clean&nbsp;-fdx</button>
                    </div>
                </div>
                <div data-git-output><p class="cog-muted">Open to load status&hellip;</p></div>
            </section>
            <section class="cog-menu-panel" data-panel="sessions" hidden>
                <div class="cog-panel-head">
                    <strong>Sessions</strong>
                    <div class="cog-panel-actions">
                        <button type="button" data-sessions="refresh">Refresh</button>
                    </div>
                </div>
                <div data-sessions-running><p class="cog-muted">Loading&hellip;</p></div>
                <div data-sessions-all></div>
            </section>
            <section class="cog-menu-panel" data-panel="usage" hidden>
                <div class="cog-panel-head">
                    <strong>Token Usage</strong>
                    <div class="cog-panel-actions">
                        <button type="button" data-usage="refresh">Refresh</button>
                    </div>
                </div>
                <div class="cog-usage-summary">
                    <span><b>Prompt</b><span data-usage="prompt">&mdash;</span></span>
                    <span><b>Completion</b><span data-usage="completion">&mdash;</span></span>
                    <span><b>Total</b><span data-usage="total">&mdash;</span></span>
                    <span><b>Cost</b><span data-usage="cost">&mdash;</span></span>
                </div>
                <div data-usage-table><p class="cog-muted">Open to load usage&hellip;</p></div>
            </section>`;

    // Mount
    let mount = opts.mount;
    if (typeof mount === 'string') mount = document.querySelector(mount);
    if (mount) mount.appendChild(nav);
    else document.body.insertBefore(nav, document.body.firstChild);

    const panels = {
        git: nav.querySelector('[data-panel="git"]'),
        sessions: nav.querySelector('[data-panel="sessions"]'),
        usage: nav.querySelector('[data-panel="usage"]')
    };
    const tabs = Array.from(nav.querySelectorAll('[data-tab]'));
    const gitOut = nav.querySelector('[data-git-output]');

    function closeAll() {
        Object.values(panels).forEach(p => {
            if (p) p.hidden = true;
        });
        tabs.forEach(t => t.setAttribute('aria-expanded', 'false'));
    }

    function open(name) {
        const panel = panels[name];
        if (!panel) return;
        const wasOpen = !panel.hidden;
        closeAll();
        if (wasOpen) return;
        panel.hidden = false;
        const tab = tabs.find(t => t.dataset.tab === name);
        if (tab) tab.setAttribute('aria-expanded', 'true');
        if (name === 'git') refreshGit();
        if (name === 'sessions') refreshSessions();
        if (name === 'usage') refreshUsage();
    }

    tabs.forEach(t => t.addEventListener('click', () => open(t.dataset.tab)));
    document.addEventListener('keydown', onKeydown);
    document.addEventListener('click', onDocClick);

    function onKeydown(e) {
         if (e.key !== 'Escape') return;
         if (creditsOverlay && !creditsOverlay.hidden) {
             closeCredits();
             return;
         }
         closeAll();
    }

    function onDocClick(e) {
         if (creditsOverlay && creditsOverlay.contains(e.target)) return;
        if (!nav.contains(e.target)) closeAll();
    }
     // ---- Budget / Credits -------------------------------------------------
     const usageUrl = opts.usageUrl || serverUrl('/usage/');
     let creditsOverlay = null;
     let budgetTimer = null;
     function ensureCreditsOverlay() {
         if (creditsOverlay) return creditsOverlay;
         creditsOverlay = document.createElement('div');
         creditsOverlay.className = 'cog-credits-overlay';
         creditsOverlay.hidden = true;
         creditsOverlay.innerHTML = `
             <div class="cog-credits-dialog" role="dialog" aria-modal="true" aria-label="Usage and Credits">
                 <div class="cog-credits-head">
                     <strong>&#128202; Usage &amp; Credits</strong>
                     <button type="button" data-credits-close>Close</button>
                 </div>
                 <iframe data-credits-frame src="about:blank" title="Usage and Credits"></iframe>
             </div>`;
         creditsOverlay.addEventListener('click', e => {
             if (e.target === creditsOverlay) closeCredits();
         });
         creditsOverlay.querySelector('[data-credits-close]')
             .addEventListener('click', closeCredits);
         document.body.appendChild(creditsOverlay);
         return creditsOverlay;
     }
     function openCredits() {
         const overlay = ensureCreditsOverlay();
         // (Re)load each time so balances/checkout state are fresh.
         overlay.querySelector('[data-credits-frame]').src = usageUrl;
         overlay.hidden = false;
     }
     function closeCredits() {
         if (!creditsOverlay || creditsOverlay.hidden) return;
         creditsOverlay.hidden = true;
         creditsOverlay.querySelector('[data-credits-frame]').src = 'about:blank';
         // Balance may have changed (credits purchased) — refresh immediately.
         refreshBudget();
     }
     function renderBudgetBanner(banner, budget) {
         if (!banner) return;
         if (typeof budget !== 'number' || isNaN(budget) || budget >= 1.00) {
             banner.hidden = true;
             banner.classList.remove('critical');
             banner.innerHTML = '';
             return;
         }
         banner.hidden = false;
         if (budget < 0.01) {
             banner.classList.add('critical');
             banner.innerHTML = `<strong>&#128683; Insufficient credits (${escapeHtml(formatBudget(budget))}).</strong> ` +
                 'You need credits to launch AI sessions. ' +
                 `<a href="${attr(usageUrl)}" data-credits-open>Add credits now &rarr;</a>`;
         } else {
             banner.classList.remove('critical');
             banner.innerHTML = `<strong>&#9888;&#65039; Low balance: ${escapeHtml(formatBudget(budget))}.</strong> ` +
                 'Your available credits are running low. ' +
                 `<a href="${attr(usageUrl)}" data-credits-open>Top up credits &rarr;</a>`;
         }
         const link = banner.querySelector('[data-credits-open]');
         if (link) link.addEventListener('click', e => {
             e.preventDefault();
             openCredits();
         });
     }
     async function refreshBudget() {
         if (!opts.showBudget) return null;
         const btn = nav.querySelector('[data-budget-btn]');
         const amountEl = nav.querySelector('[data-budget-amount]');
         const banner = nav.querySelector('[data-budget-banner]');
         if (!btn || !amountEl) return null;
         const budget = await fetchBudget(opts.usageJsonUrl);
         btn.classList.remove('budget-warning', 'budget-critical');
         if (budget === null) {
             amountEl.textContent = 'Budget';
             btn.removeAttribute('data-budget');
             btn.title = 'Usage and available credits';
             renderBudgetBanner(banner, null);
             return null;
         }
         amountEl.textContent = formatBudget(budget);
         btn.setAttribute('data-budget', String(budget));
         btn.title = 'Available budget: ' + formatBudget(budget) + ' \u2014 click to view usage & buy credits';
         if (budget < 0.01) btn.classList.add('budget-critical');
         else if (budget < 1.00) btn.classList.add('budget-warning');
         renderBudgetBanner(banner, budget);
         return budget;
     }

    // ---- Git -------------------------------------------------------------
    function confirmDestructive(title, detail) {
        return window.confirm(`${title}\n\n${detail}\n\nThis cannot be undone. Continue?`);
    }

    /** Prompt for a name and create (+ check out) a branch at `startPoint` (or HEAD). */
    async function promptNewBranch(startPoint) {
        const short = startPoint ? String(startPoint).slice(0, 7) : '';
        const label = short ? 'commit ' + short : 'current HEAD';
        const raw = window.prompt(`New branch name (from ${label}):`, '');
        if (raw === null) return false;
        const name = raw.trim();
        if (!name || name.startsWith('-') || /[\s~^:?*\[\]\\]/.test(name)) {
            showToast('Invalid branch name', 'error');
            return false;
        }
        await createBranch(ctx.basePath, name, startPoint || null, true);
        showToast(`Created and checked out ${name}` + (short ? ` at ${short}` : ''), 'success');
        return true;
    }

    /** Wire up action links rendered inside the git output area. */
    function bindGitOutputActions() {
        gitOut.querySelectorAll('[data-checkout]').forEach(a => {
            a.addEventListener('click', async ev => {
                ev.preventDefault();
                try {
                    await checkout(ctx.basePath, a.dataset.checkout, false);
                    showToast('Checked out ' + a.dataset.checkout, 'success');
                } catch (e) {
                    showToast('Checkout failed: ' + (e.message || String(e)), 'error');
                }
                gitAction('status');
            });
        });
        gitOut.querySelectorAll('[data-branch-from]').forEach(a => {
            a.addEventListener('click', async ev => {
                ev.preventDefault();
                try {
                    if (await promptNewBranch(a.dataset.branchFrom || '')) await gitAction('status');
                } catch (e) {
                    showToast('Branch failed: ' + (e.message || String(e)), 'error');
                }
            });
        });
        gitOut.querySelectorAll('[data-reset-to]').forEach(a => {
            a.addEventListener('click', ev => {
                ev.preventDefault();
                gitAction('reset', a.dataset.resetTo);
            });
        });
    }

    async function gitAction(action, ref = null) {
        if (!ctx.basePath) return;
        gitOut.innerHTML = '<p class="cog-muted">Working&hellip;</p>';
        try {
            if (action === 'status') {
                gitOut.innerHTML = formatStatus(await getStatus(ctx.basePath));
            } else if (action === 'init') {
                await initRepository(ctx.basePath);
                showToast('Repository initialized', 'success');
                gitOut.innerHTML = formatStatus(await getStatus(ctx.basePath));
            } else if (action === 'commit') {
                const message = window.prompt('Commit message:', 'Update from ' + opts.appName);
                if (!message) {
                    gitOut.innerHTML = formatStatus(await getStatus(ctx.basePath));
                    return;
                }
                await commit(ctx.basePath, message);
                showToast('Committed', 'success');
                gitOut.innerHTML = formatStatus(await getStatus(ctx.basePath));
            } else if (action === 'branches') {
                const data = await getBranches(ctx.basePath);
                const list = data.branches || data.all || [];
                const current = data.current || data.currentBranch || '';
                gitOut.innerHTML = '<ul class="cog-list">' + list.map(b => {
                        const name = typeof b === 'string' ? b : (b.name || '');
                        return `<li><span class="cog-tag${name === current ? ' completed' : ''}">${escapeHtml(name === current ? 'current' : 'branch')}</span>` +
                            `<a href="#" data-checkout="${attr(name)}">${escapeHtml(name)}</a>` +
                            `<a href="#" data-branch-from="${attr(name)}" title="Create a new branch starting at ${attr(name)}">branch from</a></li>`;
                    }).join('') + '</ul>' +
                    '<p class="cog-muted">Click a branch to check it out. ' +
                    '<a href="#" data-branch-from="">New branch from HEAD&hellip;</a></p>';
                bindGitOutputActions();
            } else if (action === 'log') {
                const data = await getLog(ctx.basePath, 20);
                const commits = data.commits || data.log || [];
                gitOut.innerHTML = commits.length
                    ? '<ul class="cog-list">' + commits.map(c => {
                        const hash = String(c.hash || c.id || '');
                        return `<li><span class="cog-tag">${escapeHtml(hash.slice(0, 7))}</span>` +
                            `<span>${escapeHtml(c.message || '')}</span>` +
                            `<span class="cog-muted">${escapeHtml(c.author || '')} ${escapeHtml(c.date || '')}</span>` +
                            (hash
                                ? `<a href="#" data-branch-from="${attr(hash)}" title="Create a new branch at this commit">branch</a>` +
                                  `<a href="#" class="cog-danger-link" data-reset-to="${attr(hash)}" title="git reset --hard to this commit">reset</a>`
                                : '') +
                            '</li>';
                    }).join('') + '</ul>' +
                    '<p class="cog-muted">Use <em>branch</em> to start a new branch at a past commit, ' +
                    '<em>reset</em> to hard-reset onto it.</p>'
                    : '<p class="cog-muted">No commits.</p>';
                bindGitOutputActions();
            } else if (action === 'new-branch') {
                await promptNewBranch(ref || '');
                gitOut.innerHTML = formatStatus(await getStatus(ctx.basePath));
            } else if (action === 'reset') {
                const target = ref || 'HEAD';
                const short = String(target).slice(0, 7);
                const ok = confirmDestructive(
                    `git reset --hard ${short}`,
                    'All uncommitted changes to tracked files will be permanently discarded' +
                    (ref ? `, and the current branch will be moved to ${short}.` : '.'));
                if (ok) {
                    await resetHard(ctx.basePath, target);
                    showToast('Hard reset to ' + short, 'success');
                }
                gitOut.innerHTML = formatStatus(await getStatus(ctx.basePath));
            } else if (action === 'clean') {
                const ok = confirmDestructive(
                    'git clean -fdx',
                    'All untracked and ignored files and directories (including build output and ' +
                    'local-only files) will be deleted from the working tree.');
                if (ok) {
                    const res = await cleanWorkingTree(ctx.basePath, {
                        directories: true, ignored: true, force: true
                    });
                    const removed = (res && (res.removed || res.files || res.paths)) || [];
                    showToast('Working tree cleaned' +
                        (removed.length ? ` (${removed.length} path(s) removed)` : ''), 'success');
                }
                gitOut.innerHTML = formatStatus(await getStatus(ctx.basePath));
            }
        } catch (e) {
            gitOut.innerHTML = `<p class="cog-muted">Git error: ${escapeHtml(e.message || String(e))}</p>`;
        }
    }

    nav.querySelectorAll('[data-git]').forEach(b =>
        b.addEventListener('click', () => gitAction(b.dataset.git)));

    function refreshGit() {
        if (opts.showGit && ctx.basePath) return gitAction('status');
    }

    // ---- Sessions --------------------------------------------------------
    async function refreshSessions() {
        if (!opts.showSessions) return;
        const runningEl = nav.querySelector('[data-sessions-running]');
        const allEl = nav.querySelector('[data-sessions-all]');
        runningEl.innerHTML = '<p class="cog-muted">Loading&hellip;</p>';
        allEl.innerHTML = '';

        const tasks = await fetchRunningTasks(ctx.basePath);
        const running = tasks.filter(t => t.status === 'RUNNING');
        runningEl.innerHTML = '<p class="cog-muted">Running tasks (this session)</p>' + (tasks.length
            ? '<ul class="cog-list">' + tasks.map(t =>
            `<li><span class="cog-tag ${statusTagClass(t.status)}">${escapeHtml(t.status)}</span>` +
            `<span>${escapeHtml(t.target)}</span>` +
            (t.taskId ? `<a href="${attr(opts.getProxyUrl(t.taskId))}" target="_blank">monitor</a>` : '') +
            '</li>').join('') + '</ul>'
            : '<p class="cog-muted">No docops tasks recorded.</p>');

        const sessions = await fetchSessionList(ctx, opts.sessionsEndpoint);
        allEl.innerHTML = '<p class="cog-muted">All sessions</p>' + (sessions.length
            ? '<ul class="cog-list">' + sessions.map(s => {
            const appHref = ctx.appRoot ? serverUrl(`${ctx.appRoot}/fileIndex/${s.sessionId}/`) : null;
            const ide = getIdeUrl({...ctx, sessionId: s.sessionId});
            return `<li>${s.active || s.sessionId === ctx.sessionId ? '<span class="cog-tag running">active</span>' : '<span class="cog-tag">session</span>'}` +
                `<span>${escapeHtml(s.name || s.sessionId)}</span>` +
                (appHref ? `<a href="${attr(appHref)}">files</a>` : '') +
                (ide ? `<a href="${attr(ide)}" target="_blank">ide</a>` : '') +
                `<a href="${attr(opts.getProxyUrl(s.sessionId))}" target="_blank">monitor</a></li>`;
        }).join('') + '</ul>'
            : '<p class="cog-muted">No session list endpoint available. ' +
            'Set <code>sessionsEndpoint</code> via configure() or the menu options.</p>');

        return {tasks, running, sessions};
    }

    // ---- Usage -----------------------------------------------------------
    function collectUsageSessionIds(extraTaskIds) {
        const ids = new Set();
        if (ctx.sessionId) ids.add(ctx.sessionId);
        const provided = typeof opts.sessionIds === 'function' ? opts.sessionIds() : opts.sessionIds;
        (provided || []).forEach(id => {
            if (id) ids.add(id);
        });
        (extraTaskIds || []).forEach(id => {
            if (id) ids.add(id);
        });
        return Array.from(ids);
    }

    async function refreshUsage() {
        if (!opts.showUsage) return;
        const tableEl = nav.querySelector('[data-usage-table]');
        tableEl.innerHTML = '<p class="cog-muted">Loading&hellip;</p>';
        const tasks = await fetchRunningTasks(ctx.basePath);
        const ids = collectUsageSessionIds(tasks.map(t => t.taskId));
        if (!ids.length) {
            tableEl.innerHTML = '<p class="cog-muted">No sessions to report on.</p>';
            return null;
        }
        const {models, totals} = await aggregateUsage(ids);
        renderUsageSummary(totals, {
            prompt: nav.querySelector('[data-usage="prompt"]'),
            completion: nav.querySelector('[data-usage="completion"]'),
            total: nav.querySelector('[data-usage="total"]'),
            cost: nav.querySelector('[data-usage="cost"]')
        });
        tableEl.innerHTML = createUsageTableHtml(models, totals);
        return {models, totals};
    }

    nav.querySelector('[data-sessions="refresh"]')?.addEventListener('click', refreshSessions);
    nav.querySelector('[data-usage="refresh"]')?.addEventListener('click', refreshUsage);
     nav.querySelector('[data-budget-btn]')?.addEventListener('click', openCredits);
     if (opts.showBudget) {
         refreshBudget();
         if (opts.budgetRefreshMs > 0) {
             budgetTimer = setInterval(refreshBudget, opts.budgetRefreshMs);
         }
     }

    function destroy() {
        document.removeEventListener('keydown', onKeydown);
        document.removeEventListener('click', onDocClick);
         if (budgetTimer) {
             clearInterval(budgetTimer);
             budgetTimer = null;
         }
         if (creditsOverlay && creditsOverlay.parentNode) {
             creditsOverlay.parentNode.removeChild(creditsOverlay);
             creditsOverlay = null;
         }
        if (nav.parentNode) nav.parentNode.removeChild(nav);
    }

    return {
        element: nav,
        context: ctx,
        open,
        close: closeAll,
         openCredits,
         closeCredits,
        refresh() {
             return Promise.all([refreshGit(), refreshSessions(), refreshUsage(), refreshBudget()]);
        },
        refreshGit,
        refreshSessions,
        refreshUsage,
         refreshBudget,
        destroy
    };
}

export const MenuUtils = {
    initMenu,
    getMenuContext,
    getIdeUrl,
    fetchSessionList,
     fetchRunningTasks,
     fetchBudget,
     formatBudget
};