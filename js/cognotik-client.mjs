/**
 * cognotik-client — a dependency-free client library for the Cognotik API proxy.
 *
 * Works in Node 18+ and modern browsers (ES modules + fetch + AbortController).
 *
 * Endpoints:
 *   GET  {base}/api-proxy/models/{providers}                     -> List<ChatModel>
 *   POST {base}/api-proxy/chat/{providers}?session={id}          -> { token }
 *   GET  {base}/api-proxy/chat/{providers}/result/{token}        -> 200 ChatResponse | 202 pending
 *
 * Authentication:
 *   - Node: pass `token` (and optionally `cookieName`); it is sent as a Cookie header.
 *   - Browser: the browser's own cookies are used (credentials: 'include'); `token` is ignored
 *     because browsers do not allow setting the Cookie header.
 *   - Either: `apiKey` is sent as an X-API-Key header.
 *
 * Example:
 *   import { CognotikClient } from './cognotik-client.mjs';
 *   const client = new CognotikClient({ baseUrl: 'https://hosted.cognotik.com', token });
 *   const { text } = await client.ask('Hello!', { model: 'claude-sonnet-4' });
 */

// ─── Constants ────────────────────────────────────────────────────────────────
export const VERSION = '1.0.0';
export const DEFAULT_URL = 'https://hosted.cognotik.com';
export const DEFAULT_PROVIDERS = ['Gemini', 'Anthropic', 'ElevenLabs', 'Groq', 'Mistral', 'xAI', 'DeepSeek'];
export const DEFAULT_COOKIE_NAME = 'sessionId';
export const IMAGE_MIME = {
  '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg',
  '.gif': 'image/gif', '.webp': 'image/webp',
};

export const IS_BROWSER = typeof window !== 'undefined' && typeof window.document !== 'undefined';

// ─── Errors ───────────────────────────────────────────────────────────────────
export class CognotikError extends Error {
  constructor(message, { status, body, retryable = false, auth = false, aborted = false, cause } = {}) {
    super(message, cause ? { cause } : undefined);
    this.name = 'CognotikError';
    Object.assign(this, { status, body, retryable, auth, aborted });
  }
}
const abortError = () => new CognotikError('Request cancelled', { aborted: true });

// ─── Utilities ────────────────────────────────────────────────────────────────
export function sleep(ms, signal) {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) return reject(abortError());
    const onAbort = () => { clearTimeout(t); reject(abortError()); };
    const t = setTimeout(() => { signal?.removeEventListener('abort', onAbort); resolve(); }, ms);
    signal?.addEventListener('abort', onAbort, { once: true });
  });
}

/** AbortSignal.any() replacement that also works on Node 18 and older browsers. */
export function combineSignals(...signals) {
  const ctrl = new AbortController();
  for (const s of signals) {
    if (!s) continue;
    if (s.aborted) { ctrl.abort(s.reason); break; }
    s.addEventListener('abort', () => ctrl.abort(s.reason), { once: true });
  }
  return ctrl.signal;
}

export function parseJson(text) {
  try { return JSON.parse(text); } catch { return null; }
}

function randomBytes(n) {
  const bytes = new Uint8Array(n);
  if (globalThis.crypto?.getRandomValues) globalThis.crypto.getRandomValues(bytes);
  else for (let i = 0; i < n; i++) bytes[i] = Math.floor(Math.random() * 256);
  return bytes;
}

export function newSessionId() {
  const d = new Date();
  const ymd = `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`;
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  const rand = Array.from(randomBytes(4), (b) => alphabet[b % alphabet.length]).join('');
  return `U-${ymd}-${rand}`;
}

/** Base64-encodes a Uint8Array / ArrayBuffer / Buffer in Node or the browser. */
export function bytesToBase64(bytes) {
  const u8 = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  if (typeof Buffer !== 'undefined') return Buffer.from(u8.buffer, u8.byteOffset, u8.byteLength).toString('base64');
  let bin = '';
  for (let i = 0; i < u8.length; i += 0x8000) bin += String.fromCharCode.apply(null, u8.subarray(i, i + 0x8000));
  return btoa(bin);
}

// ─── Proxy client ─────────────────────────────────────────────────────────────
export class CognotikClient {
  /**
   * @param {object} options
   * @param {string} [options.baseUrl]       Server base URL; '' means same origin (browser).
   * @param {string[]} [options.providers]   Upstream providers.
   * @param {string} [options.token]         Auth token (sent as cookie; Node only).
   * @param {string} [options.cookieName]    Auth cookie name.
   * @param {string} [options.apiKey]        X-API-Key header value.
   * @param {string} [options.session]       Proxy session id (default generated).
   * @param {number} [options.chatTimeoutMs] Overall wait for a chat response.
   * @param {Function} [options.fetch]       Custom fetch implementation.
   * @param {string} [options.credentials]   fetch credentials mode (browser default 'include').
   * @param {object} [options.headers]       Extra headers for every request.
   * @param {string} [options.userAgent]     User-Agent header (Node only by default).
   * @param {boolean} [options.sendCookieHeader] Force/disable sending the Cookie header.
   * @param {boolean} [options.verbose]      Emit debug messages via logger.
   * @param {Function} [options.logger]      (message) => void, used for debug output.
   */
  constructor(options = {}) {
    const o = options;
    this.baseUrl = String(o.baseUrl ?? DEFAULT_URL).replace(/\/+$/, '');
    this.providers = o.providers?.length ? [...o.providers] : [...DEFAULT_PROVIDERS];
    this.token = o.token ?? null;
    this.cookieName = o.cookieName || DEFAULT_COOKIE_NAME;
    this.apiKey = o.apiKey ?? null;
    this.session = o.session || newSessionId();
    this.verbose = !!o.verbose;
    this.logger = o.logger || ((m) => console.debug(m));
    this.fetchImpl = o.fetch || (typeof globalThis.fetch === 'function' ? globalThis.fetch.bind(globalThis) : null);
    if (!this.fetchImpl) throw new CognotikError('No fetch implementation available; pass options.fetch');
    this.credentials = o.credentials ?? (IS_BROWSER ? 'include' : undefined);
    this.userAgent = o.userAgent ?? (IS_BROWSER ? null : `cognotik-client/${VERSION}`);
    this.sendCookieHeader = o.sendCookieHeader ?? !IS_BROWSER;
    this.extraHeaders = o.headers || {};
    this.chatTimeoutMs = o.chatTimeoutMs ?? 300_000;
    this.connectTimeoutMs = o.connectTimeoutMs ?? 30_000;
    this.modelsTimeoutMs = o.modelsTimeoutMs ?? 120_000;
    this.pollTimeoutMs = o.pollTimeoutMs ?? 30_000;
    this.pollInitialMs = o.pollInitialMs ?? 500;
    this.pollMaxMs = o.pollMaxMs ?? 5_000;
  }

  get proxyBase() { return `${this.baseUrl}/api-proxy`; }
  get providerPath() { return this.providers.map(encodeURIComponent).join(','); }

  debug(msg) { if (this.verbose) this.logger(`[debug] ${msg}`); }

  headers(extra = {}) {
    const h = { Accept: 'application/json', ...this.extraHeaders, ...extra };
    if (this.userAgent) h['User-Agent'] = this.userAgent;
    if (this.token && this.sendCookieHeader) h.Cookie = `${this.cookieName}=${this.token}`;
    if (this.apiKey) h['X-API-Key'] = this.apiKey;
    return h;
  }

  /** Performs a request and reads the full body, bounded by timeoutMs. */
  async request(url, init, { timeoutMs, signal }) {
    const timeoutCtrl = new AbortController();
    const timer = setTimeout(() => timeoutCtrl.abort(), timeoutMs);
    this.debug(`${init.method || 'GET'} ${url}`);
    try {
      const fetchInit = {
        ...init,
        redirect: 'manual',
        headers: this.headers(init.headers),
        signal: combineSignals(signal, timeoutCtrl.signal),
      };
      if (this.credentials) fetchInit.credentials = this.credentials;
      const res = await this.fetchImpl(url, fetchInit);
      const text = await res.text();
      this.debug(`-> HTTP ${res.status} (${text.length} bytes)`);
      return { res, text };
    } catch (e) {
      if (signal?.aborted) throw abortError();
      if (timeoutCtrl.signal.aborted) {
        throw new CognotikError(`Request timed out after ${timeoutMs / 1000}s: ${url}`, { retryable: true });
      }
      throw new CognotikError(`Network error contacting ${this.baseUrl || 'server'}: ${e.cause?.message || e.message}`,
        { retryable: true, cause: e });
    } finally {
      clearTimeout(timer);
    }
  }

  checkAuth(res) {
    // Browsers hide redirect details when redirect: 'manual' is used.
    if (res.type === 'opaqueredirect') {
      throw new CognotikError('Request was redirected (probably to the login page); not authenticated.',
        { status: res.status, auth: true });
    }
    const location = res.headers.get('location') || '';
    if (res.status === 401 || res.status === 403 ||
        (res.status >= 300 && res.status < 400 && /login/i.test(location))) {
      throw new CognotikError(
        this.token ? `Authentication rejected (HTTP ${res.status}); your token may have expired.`
                   : `Not authenticated (HTTP ${res.status}).`,
        { status: res.status, auth: true });
    }
    if (res.status >= 300 && res.status < 400) {
      throw new CognotikError(`Unexpected redirect (HTTP ${res.status}) to ${location || '?'}`, { status: res.status });
    }
  }

  async listModels({ signal } = {}) {
    const { res, text } = await this.request(`${this.proxyBase}/models/${this.providerPath}`,
      { method: 'GET' }, { timeoutMs: this.modelsTimeoutMs, signal });
    this.checkAuth(res);
    if (!res.ok) {
      throw new CognotikError(`Model listing failed (HTTP ${res.status}): ${text.slice(0, 500)}`,
        { status: res.status, body: text });
    }
    const data = parseJson(text);
    if (!Array.isArray(data)) throw new CognotikError('Malformed model list returned by proxy', { body: text });
    return data;
  }

  /** Submits a chat request and waits for the result. */
  async chat(request, { signal, onStatus } = {}) {
    onStatus?.('submitting');
    const token = await this.submit(request, signal);
    onStatus?.('waiting for response');
    return this.poll(token, { signal, onStatus });
  }

  /** Convenience one-shot prompt. Returns { text, response }. */
  async ask(prompt, { model, system, temperature, maxTokens, attachments = [], signal, onStatus } = {}) {
    if (!model) throw new CognotikError('ask() requires a model');
    const conv = new Conversation({ system });
    conv.addUser([...attachments, textPart(prompt)]);
    const response = await this.chat(buildRequest(conv, { model, temperature, maxTokens }), { signal, onStatus });
    return { text: extractText(response), response };
  }

  async submit(request, signal) {
    const url = `${this.proxyBase}/chat/${this.providerPath}?session=${encodeURIComponent(this.session)}`;
    const { res, text } = await this.request(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    }, { timeoutMs: this.connectTimeoutMs, signal });
    this.checkAuth(res);
    const parsed = parseJson(text);
    if (!res.ok) {
      throw new CognotikError(`Chat submission failed (HTTP ${res.status}): ${parsed?.error || text.slice(0, 500)}`,
        { status: res.status, body: text });
    }
    if (!parsed?.token) throw new CognotikError('Proxy returned no job token', { status: res.status, body: text });
    this.debug(`job token ${parsed.token}`);
    return parsed.token;
  }

  async poll(token, { signal, onStatus } = {}) {
    const url = `${this.proxyBase}/chat/${this.providerPath}/result/${encodeURIComponent(token)}`;
    const deadline = Date.now() + this.chatTimeoutMs;
    let delay = this.pollInitialMs;
    while (true) {
      if (Date.now() > deadline) {
        throw new CognotikError(`Timed out after ${this.chatTimeoutMs / 1000}s waiting for response (token=${token})`);
      }
      let status = 'waiting for response';
      try {
        const { res, text } = await this.request(url, { method: 'GET' }, { timeoutMs: this.pollTimeoutMs, signal });
        this.checkAuth(res);
        if (res.status === 200) {
          const data = parseJson(text);
          if (!data) throw new CognotikError('Malformed chat response from proxy', { status: 200, body: text });
          return data;
        }
        const parsed = parseJson(text);
        if (res.status === 202) {
          if (parsed?.status) status = parsed.status;
        } else if (res.status === 404) {
          throw new CognotikError(`Job not found or expired: ${parsed?.error || text}`, { status: 404, body: text });
        } else if (parsed?.status === 'failed') {
          throw new CognotikError(`Chat job failed: ${parsed.error || 'Unknown failure'}`, { status: res.status, body: text });
        } else if (res.status >= 500) {
          this.debug(`server error ${res.status} while polling, retrying: ${text.slice(0, 200)}`);
          status = `server error ${res.status}, retrying`;
        } else {
          throw new CognotikError(`Unexpected poll response (HTTP ${res.status}): ${text.slice(0, 500)}`,
            { status: res.status, body: text });
        }
      } catch (e) {
        if (!e.retryable || e.aborted) throw e;
        this.debug(`transient poll error: ${e.message}`);
        status = 'connection issue, retrying';
      }
      onStatus?.(status);
      await sleep(Math.max(0, Math.min(delay, deadline - Date.now())), signal);
      delay = Math.min(delay * 2, this.pollMaxMs);
    }
  }
}

// ─── Models ───────────────────────────────────────────────────────────────────
export class ModelCache {
  constructor(client) { this.client = client; this.promise = null; }
  get() {
    if (!this.promise) {
      this.promise = this.client.listModels().catch((e) => { this.promise = null; throw e; });
    }
    return this.promise;
  }
  refresh() { this.promise = null; return this.get(); }
}

export const providerName = (m) => (typeof m?.provider === 'string' ? m.provider : m?.provider?.name || '');
export const isTextModel = (m) =>
  !Array.isArray(m?.outputModalities) || !m.outputModalities.length ||
  m.outputModalities.some((x) => /text/i.test(String(x?.name ?? x)));

export function matchModels(models, query) {
  const q = query.toLowerCase();
  const exact = models.filter((m) => m.modelId?.toLowerCase() === q || m.name?.toLowerCase() === q);
  if (exact.length) return exact;
  return models.filter((m) =>
    [m.modelId, m.name, providerName(m)].some((v) => v && String(v).toLowerCase().includes(q)));
}

export function chooseDefaultModel(models) {
  return models.find((m) => !m.deprecated && isTextModel(m)) || models[0] || null;
}

export function formatTokens(n) {
  if (!n) return '';
  return n >= 1_000_000 ? `${+(n / 1_000_000).toFixed(1)}M` : n >= 1000 ? `${Math.round(n / 1000)}k` : String(n);
}

// ─── Conversation ─────────────────────────────────────────────────────────────
export class Conversation {
  constructor({ system = null, messages = [] } = {}) {
    this.system = system;
    this.messages = messages;
  }
  addUser(parts) { this.messages.push({ role: 'user', content: parts }); }
  addAssistant(text) { this.messages.push({ role: 'assistant', content: [{ type: 'text', text }] }); }
  toMessages() {
    const sys = this.system ? [{ role: 'system', content: [{ type: 'text', text: this.system }] }] : [];
    return [...sys, ...this.messages];
  }
  /** Removes the last user message and any reply that followed it. */
  undo() {
    let removed = 0;
    while (this.messages.length && this.messages.at(-1).role === 'assistant') { this.messages.pop(); removed++; }
    if (this.messages.length && this.messages.at(-1).role === 'user') { this.messages.pop(); removed++; }
    return removed;
  }
}

/** Builds a proxy chat request from a conversation and { model, temperature, maxTokens }. */
export function buildRequest(conv, settings) {
  const req = { model: settings.model, messages: conv.toMessages() };
  if (settings.temperature != null) req.temperature = settings.temperature;
  if (settings.maxTokens != null) req.max_tokens = settings.maxTokens;
  return req;
}

export function extractText(resp) {
  const choice = resp?.choices?.[0];
  const content = choice?.message?.content ?? choice?.text;
  if (typeof content === 'string') return content;
  if (Array.isArray(content)) return content.map((p) => (typeof p === 'string' ? p : p?.text ?? '')).join('');
  return '';
}

export function messageText(msg) {
  if (typeof msg.content === 'string') return msg.content;
  return (msg.content || [])
    .map((p) => (p.type === 'image_url' || p.image_url ? '[image]' : p.text ?? ''))
    .join('\n');
}

/** Serializable snapshot of a conversation plus generation settings. */
export function transcriptToJSON(conv, settings = {}) {
  return {
    version: 1,
    savedAt: new Date().toISOString(),
    model: settings.model ?? null,
    temperature: settings.temperature ?? null,
    maxTokens: settings.maxTokens ?? null,
    system: conv.system,
    messages: conv.messages,
  };
}

/**
 * Loads a transcript into `conv`; fills unset fields of `settings` (model, temperature, maxTokens).
 * Returns the number of messages loaded.
 */
export function applyTranscript(data, conv, settings = {}) {
  if (!Array.isArray(data?.messages)) throw new CognotikError('Not a saved conversation');
  conv.messages = data.messages.filter((m) => m?.role === 'user' || m?.role === 'assistant');
  if (data.system !== undefined) conv.system = data.system;
  if (data.model && !settings.model) settings.model = data.model;
  if (data.temperature != null && settings.temperature == null) settings.temperature = data.temperature;
  if (data.maxTokens != null && settings.maxTokens == null) settings.maxTokens = data.maxTokens;
  return conv.messages.length;
}

// ─── Message parts / attachments ──────────────────────────────────────────────
export const textPart = (text) => ({ type: 'text', text });

/** Wraps file contents in a fenced block labelled with the file name. */
export function textFilePart(name, text) {
  const m = /[^./\\]\.([^./\\]+)$/.exec(name);
  const lang = m ? m[1] : '';
  const base = name.split(/[\\/]/).pop();
  return textPart(`File: ${base}\n\`\`\`${lang}\n${text}\n\`\`\``);
}

export function mimeForFilename(name) {
  const m = /(\.[^./\\]+)$/.exec(name);
  return m ? IMAGE_MIME[m[1].toLowerCase()] || null : null;
}

/** Image part from a MIME type and either base64 text or raw bytes. */
export function imagePart(mime, data) {
  const b64 = typeof data === 'string' ? data : bytesToBase64(data);
  return { type: 'image_url', image_url: `data:${mime};base64,${b64}` };
}

/** Image part from a Blob / File (browser, or Node 18+ Blob). */
export async function imagePartFromBlob(blob, mime = blob.type) {
  if (!mime) throw new CognotikError('Image MIME type is unknown');
  return imagePart(mime, new Uint8Array(await blob.arrayBuffer()));
}

// ─── Usage tracking ───────────────────────────────────────────────────────────
export class UsageTracker {
  constructor() { this.requests = 0; this.prompt = 0; this.completion = 0; this.cost = 0; }
  add(u) {
    this.requests++;
    this.prompt += Number(u?.prompt_tokens) || 0;
    this.completion += Number(u?.completion_tokens) || 0;
    this.cost += Number(u?.cost) || 0;
  }
  static format(u) {
    if (!u) return 'usage unavailable';
    const parts = [`in ${u.prompt_tokens ?? '?'}`, `out ${u.completion_tokens ?? '?'}`];
    if (Number(u.cost)) parts.push(`$${Number(u.cost).toFixed(4)}`);
    return `tokens: ${parts.join(' / ')}`;
  }
  summary() {
    const cost = this.cost ? `, cost $${this.cost.toFixed(4)}` : '';
    return `${this.requests} request(s), ${this.prompt} prompt + ${this.completion} completion tokens${cost}`;
  }
}