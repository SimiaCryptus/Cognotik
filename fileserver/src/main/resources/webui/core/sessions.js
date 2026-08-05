/**
   * Agent session links, and the `docops.status.json` documents that reference them.
   *
   * A status document records one entry per target being worked on, each carrying
   * the id of a live chat session. The *server* owns the URL contract
   * (`ModifyFilesFsAction`: `{chatUri}/proxy/?session=<id>`, where `chatUri` comes
   * from `ApplicationDirectory.domainName` / `publicName`), so we ask it through the
   * `session` FS API action and cache the answer for the whole document.
   *
   * Only when that action is absent — the stand-alone CLI file server, say — do we
   * guess, and then the chat UI is assumed to share this origin. A deployment that
   * splits the two can pin it with `config.sessions.base`, `?sessionBase=…`, or a
   * `<meta name="cognotik-session-base">` tag.
   */
  import config from '../config.js';
  import {store} from './store.js';
  import {persist} from './persist.js';
  import {basename} from './paths.js';

  /** `docops.status.json`, optionally namespaced (`api.docops.status.json`). */
  const STATUS_FILE = /^(?:[\w.-]+\.)?docops\.status\.json$/i;
  /** Session ids are echoed into a URL: keep them boring. */
  const SESSION_ID = /^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/;
  const RUNNING = new Set(['RUNNING', 'IN_PROGRESS', 'STARTED', 'WORKING']);
  /** Sort order: what still needs attention first, finished work last. */
  const RANK = {
      RUNNING: 0, IN_PROGRESS: 0, STARTED: 0, WORKING: 0,
      FAILED: 1, ERROR: 1, CANCELLED: 1,
      PENDING: 2, QUEUED: 2,
      COMPLETED: 4, DONE: 4,
  };

  export function isDocOpsStatus(path) {
      return STATUS_FILE.test(basename(String(path ?? '')));
  }

  export function isSessionId(value) {
      return typeof value === 'string' && SESSION_ID.test(value);
  }

  /** Origin of the chat UI when the server does not tell us. */
  export function sessionBase() {
      const configured = config.sessions?.base
          || document.querySelector('meta[name="cognotik-session-base"]')?.content
          || new URLSearchParams(location.search).get('sessionBase')
          || persist.global('sessionBase');
      if (configured) return String(configured).replace(/\/+$/, '');
      try {
          return new URL(store.get().base || location.href, location.href).origin;
      } catch (e) {
          return location.origin;
      }
  }

  export function localSessionUrl(id) {
      const path = config.sessions?.proxyPath || '/proxy/';
      const href = /^[a-z][a-z0-9+.-]*:/i.test(path)
          ? path
          : `${sessionBase()}${path.startsWith('/') ? path : `/${path}`}`;
      const url = new URL(href, location.href);
      url.searchParams.set('session', id);
      return url.href;
  }

  let templatePromise = null;

  async function loadTemplate() {
      if (config.sessions?.template) return config.sessions.template;
      const base = store.get().base;
      if (!base) return null;
      try {
          const response = await fetch(`${base}/session`, {
              headers: {'X-Fs-Api': '1'}, credentials: 'same-origin',
          });
          if (!response.ok) return null;
          const payload = await response.json();
          if (payload?.template) return String(payload.template);
          if (payload?.base) return `${String(payload.base).replace(/\/+$/, '')}/proxy/?session={session}`;
      } catch (e) {
          /* This mount does not publish session links; fall back to the origin. */
      }
      return null;
  }

  /** Cached: one probe serves every id in a document. */
  export function sessionTemplate() {
      if (!templatePromise) templatePromise = loadTemplate().catch(() => null);
      return templatePromise;
  }

  export async function sessionUrl(id) {
      if (!isSessionId(id)) return null;
      const template = await sessionTemplate();
      return template ? template.replace('{session}', encodeURIComponent(id)) : localSessionUrl(id);
  }

  /** Throws when the document is not JSON; answers `{lastUpdated, tasks}` otherwise. */
  export function parseDocOpsStatus(text) {
      let data;
      try {
          data = JSON.parse(String(text ?? ''));
      } catch (e) {
          throw new Error(e.message);
      }
      const raw = (data && typeof data.tasks === 'object' && data.tasks) || {};
      const tasks = Object.entries(raw).map(([key, value]) => {
          const task = (value && typeof value === 'object') ? value : {};
          const status = String(task.status ?? 'UNKNOWN').toUpperCase();
          return {
              key,
              target: String(task.target || key),
              status,
              running: RUNNING.has(status),
              sessionId: isSessionId(task.sessionId) ? task.sessionId : null,
              startedAt: task.startedAt || null,
              completedAt: task.completedAt || null,
              message: task.message || task.error || null,
              url: null,
          };
      });
      tasks.sort((a, b) =>
          ((RANK[a.status] ?? 3) - (RANK[b.status] ?? 3))
          || String(b.startedAt || '').localeCompare(String(a.startedAt || ''))
          || a.target.localeCompare(b.target));
      return {lastUpdated: data?.lastUpdated || null, tasks};
  }