#!/usr/bin/env node
/**
 * cognotik-chat — a command line LLM client for the Cognotik API proxy.
 *
 * Talks to the same endpoints used by ProxyProvider / ProxyChatClient:
 *   GET  {base}/api-proxy/models/{providers}                     -> List<ChatModel>
 *   POST {base}/api-proxy/chat/{providers}?session={id}          -> { token }
 *   GET  {base}/api-proxy/chat/{providers}/result/{token}        -> 200 ChatResponse | 202 pending
 *
 * Authentication uses the session cookie obtained via cognotik-login.mjs
 * (`cognotik-chat login`), optionally plus an X-API-Key header.
 *
  * The protocol / conversation logic lives in ./cognotik-client.mjs (Node + browser);
  * this file only adds the command line interface on top of it.
  *
 * Commands:
 *   chat     Interactive chat session (default when no prompt is given)
 *   ask      One-shot prompt; reads additional input from stdin when piped
 *   models   List available models
 *   login    Sign in via the QR / device flow and store the token
 *   logout   Delete the stored token
 *   status   Show configuration and verify authentication
 *   config   Show / set / unset persistent defaults
 *
 * Requires Node 18+.
 */
import readline from 'node:readline';
import { readFile, writeFile, mkdir, rm, stat } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import os from 'node:os';
import path from 'node:path';
import {
   CognotikClient, CognotikError, Conversation, ModelCache, UsageTracker,
   DEFAULT_URL, DEFAULT_PROVIDERS, DEFAULT_COOKIE_NAME, IMAGE_MIME,
   newSessionId, providerName, matchModels, chooseDefaultModel, formatTokens,
   buildRequest, extractText, messageText, textFilePart, imagePart, mimeForFilename,
   transcriptToJSON, applyTranscript,
} from './cognotik-client.mjs';

// ─── Constants ────────────────────────────────────────────────────────────────
const VERSION = '1.0.0';
const CONFIG_DIR = path.join(os.homedir(), '.cognotik');
const CONFIG_FILE = path.join(CONFIG_DIR, 'cli-config.json');
const DEFAULT_TOKEN_FILE = path.join(CONFIG_DIR, 'token.json');
const USER_AGENT = `cognotik-chat/${VERSION} (node ${process.version}; ${os.platform()})`;
const MAX_TEXT_ATTACHMENT_BYTES = 2 * 1024 * 1024;
const COMMANDS = ['chat', 'ask', 'models', 'login', 'logout', 'status', 'config', 'help'];

// ─── Output helpers ───────────────────────────────────────────────────────────
const colorize = (stream) => (code) => (s) =>
  stream.isTTY && !process.env.NO_COLOR ? `\x1b[${code}m${s}\x1b[0m` : String(s);
const ec = colorize(process.stderr);
const oc = colorize(process.stdout);
const err = { dim: ec('2'), red: ec('31'), yellow: ec('33'), cyan: ec('36'), green: ec('32') };
const out = { dim: oc('2'), bold: oc('1'), green: oc('32'), magenta: oc('35'), cyan: oc('36'), yellow: oc('33') };
const info = (...m) => console.error(...m);
const warn = (m) => console.error(err.yellow(`warning: ${m}`));

// ─── Errors ───────────────────────────────────────────────────────────────────
class UsageError extends Error {
  constructor(message) { super(message); this.exitCode = 2; }
}

function printError(e) {
  if (e?.aborted) { info(err.yellow('⨯ cancelled')); return; }
  info(err.red(`✖ ${e?.message || e}`));
  if (e?.auth) info(err.dim('  Run `cognotik-chat login` to sign in.'));
  if (process.env.COGNOTIK_DEBUG && e?.stack) info(err.dim(e.stack));
}

// ─── Small utilities ──────────────────────────────────────────────────────────
const expandHome = (p) => (p && p.startsWith('~') ? path.join(os.homedir(), p.slice(1)) : p);




async function readJsonFile(file) {
  return JSON.parse(await readFile(file, 'utf8'));
}

async function readStdin() {
  const chunks = [];
  for await (const c of process.stdin) chunks.push(c);
  return Buffer.concat(chunks).toString('utf8');
}


function parseTemperature(v) {
  const n = Number(v);
  if (v === '' || !Number.isFinite(n) || n < 0 || n > 2) throw new UsageError('Temperature must be a number between 0 and 2');
  return n;
}

function parsePositiveInt(v, name) {
  const n = Number(v);
  if (!Number.isInteger(n) || n <= 0) throw new UsageError(`${name} must be a positive integer`);
  return n;
}

const parseList = (v) => String(v).split(',').map((s) => s.trim()).filter(Boolean);

function startSpinner(initial) {
  if (!process.stderr.isTTY) return { update() {}, stop() {} };
  const frames = '⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏';
  const started = Date.now();
  let i = 0;
  let status = initial;
  const render = () => {
    const secs = ((Date.now() - started) / 1000).toFixed(1);
    process.stderr.write(`\r\x1b[2K${err.cyan(frames[i++ % frames.length])} ${err.dim(`${status} ${secs}s`)}`);
  };
  const timer = setInterval(render, 100);
  render();
  return {
    update(s) { if (s) status = s; },
    stop() { clearInterval(timer); process.stderr.write('\r\x1b[2K'); },
  };
}

// ─── Argument parsing ─────────────────────────────────────────────────────────
function parseArgs(argv) {
  const opts = { files: [], images: [], positional: [] };
  for (let i = 0; i < argv.length; i++) {
    let a = argv[i];
    let inline;
    if (a === '--') { opts.positional.push(...argv.slice(i + 1)); break; }
    if (a.startsWith('--') && a.includes('=')) {
      inline = a.slice(a.indexOf('=') + 1);
      a = a.slice(0, a.indexOf('='));
    }
    const take = () => {
      if (inline !== undefined) return inline;
      if (i + 1 >= argv.length) throw new UsageError(`Option ${a} requires a value`);
      return argv[++i];
    };
    switch (a) {
      case '-h': case '--help': opts.help = true; break;
      case '-V': case '--version': opts.version = true; break;
      case '-u': case '--url': opts.url = take(); break;
      case '--token': opts.token = take(); break;
      case '--token-file': opts.tokenFile = take(); break;
      case '--cookie-name': opts.cookieName = take(); break;
      case '--api-key': opts.apiKey = take(); break;
      case '-p': case '--providers': opts.providers = parseList(take()); break;
      case '-m': case '--model': opts.model = take(); break;
      case '-s': case '--system': opts.system = take(); break;
      case '-t': case '--temperature': opts.temperature = parseTemperature(take()); break;
      case '--max-tokens': opts.maxTokens = parsePositiveInt(take(), '--max-tokens'); break;
      case '--session': opts.session = take(); break;
      case '--timeout': opts.timeout = parsePositiveInt(take(), '--timeout'); break;
      case '-f': case '--file': opts.files.push(take()); break;
      case '-i': case '--image': opts.images.push(take()); break;
      case '--load': opts.load = take(); break;
      case '--save': opts.save = take(); break;
      case '--json': opts.json = true; break;
      case '--usage': opts.showUsage = true; break;
      case '--no-open': opts.noOpen = true; break;
      case '--client': opts.client = take(); break;
      case '-v': case '--verbose': opts.verbose = true; break;
      default:
        if (a.startsWith('-') && a !== '-') throw new UsageError(`Unknown option: ${a}`);
        opts.positional.push(a);
    }
  }
  if (opts.positional.length && COMMANDS.includes(opts.positional[0])) {
    opts.command = opts.positional.shift();
  } else {
    opts.command = opts.positional.length ? 'ask' : 'chat';
  }
  return opts;
}

function printHelp() {
  process.stdout.write(`cognotik-chat ${VERSION} — command line LLM client for Cognotik

Usage:
  cognotik-chat [chat] [options]              Interactive chat
  cognotik-chat ask <prompt...> [options]     One-shot prompt (stdin is appended when piped)
  cognotik-chat "<prompt>" [options]          Shorthand for ask
  cognotik-chat models [filter] [--json]      List available models
  cognotik-chat login [--no-open] [--client <name>]
  cognotik-chat logout
  cognotik-chat status
  cognotik-chat config [set <key> <value> | unset <key>]

Options:
  -u, --url <url>            Server base URL (env COGNOTIK_URL, default ${DEFAULT_URL})
  -m, --model <id>           Model id or name (env COGNOTIK_MODEL)
  -s, --system <text>        System prompt
  -t, --temperature <n>      Sampling temperature (0-2)
      --max-tokens <n>       Maximum output tokens
  -p, --providers <a,b,..>   Upstream providers (default ${DEFAULT_PROVIDERS.join(',')})
  -f, --file <path>          Attach a text file (repeatable)
  -i, --image <path>         Attach an image (repeatable)
      --load <file>          Load a saved conversation
      --save <file>          Save the conversation after each exchange
      --json                 Print raw JSON responses
      --usage                Print token usage after each response
      --timeout <seconds>    Overall wait for a response (default 300)
      --session <id>         Proxy session id (default: generated)
      --token <token>        Auth token (env COGNOTIK_TOKEN)
      --token-file <path>    Token file (default ${DEFAULT_TOKEN_FILE})
      --cookie-name <name>   Auth cookie name (default from token file or "${DEFAULT_COOKIE_NAME}")
      --api-key <key>        Value for the X-API-Key header
  -v, --verbose              Verbose diagnostics on stderr
  -h, --help                 Show help
  -V, --version              Show version

Examples:
  cognotik-chat login
  cognotik-chat models claude
  cognotik-chat -m claude-sonnet "Explain monads briefly"
  git diff | cognotik-chat ask "Write a commit message for this diff"
  cognotik-chat chat -m gemini-2.5-pro -s "You are a terse assistant" --save convo.json
`);
}

// ─── Configuration ────────────────────────────────────────────────────────────
const CONFIG_KEYS = {
  url: 'string', model: 'string', providers: 'list', temperature: 'temperature',
  maxTokens: 'int', system: 'string', cookieName: 'string', tokenFile: 'string', timeout: 'int',
};

async function loadConfig() {
  try { return await readJsonFile(CONFIG_FILE); } catch (e) {
    if (e.code !== 'ENOENT') warn(`Ignoring unreadable config ${CONFIG_FILE}: ${e.message}`);
    return {};
  }
}

async function saveConfig(config) {
  await mkdir(CONFIG_DIR, { recursive: true });
  await writeFile(CONFIG_FILE, JSON.stringify(config, null, 2) + '\n', { mode: 0o600 });
}

async function runConfig(config, args) {
  const [action, key, ...rest] = args;
  if (!action) {
    process.stdout.write(`${CONFIG_FILE}\n${JSON.stringify(config, null, 2)}\n`);
    return;
  }
  if (!key || !(key in CONFIG_KEYS)) {
    throw new UsageError(`Config key must be one of: ${Object.keys(CONFIG_KEYS).join(', ')}`);
  }
  if (action === 'unset') {
    delete config[key];
  } else if (action === 'set') {
    const raw = rest.join(' ');
    if (!raw) throw new UsageError(`config set ${key} requires a value`);
    const type = CONFIG_KEYS[key];
    config[key] =
      type === 'list' ? parseList(raw) :
      type === 'temperature' ? parseTemperature(raw) :
      type === 'int' ? parsePositiveInt(raw, key) : raw;
  } else {
    throw new UsageError(`Unknown config action: ${action} (use set / unset)`);
  }
  await saveConfig(config);
  info(err.green(`✔ saved ${CONFIG_FILE}`));
}

async function resolveSettings(opts, config) {
  const tokenFile = expandHome(opts.tokenFile || process.env.COGNOTIK_TOKEN_FILE || config.tokenFile || DEFAULT_TOKEN_FILE);
  let tokenData = null;
  try { tokenData = await readJsonFile(tokenFile); } catch { /* not logged in */ }

  const baseUrl = String(opts.url || process.env.COGNOTIK_URL || config.url || tokenData?.baseUrl || DEFAULT_URL)
    .replace(/\/+$/, '');
  const envTemp = process.env.COGNOTIK_TEMPERATURE;
  const settings = {
    baseUrl,
    tokenFile,
    token: opts.token || process.env.COGNOTIK_TOKEN || tokenData?.token || null,
    cookieName: opts.cookieName || process.env.COGNOTIK_COOKIE || tokenData?.cookieName || config.cookieName || DEFAULT_COOKIE_NAME,
    apiKey: opts.apiKey || process.env.COGNOTIK_API_KEY || null,
    providers: opts.providers || (process.env.COGNOTIK_PROVIDERS && parseList(process.env.COGNOTIK_PROVIDERS)) || config.providers || DEFAULT_PROVIDERS,
    model: opts.model || process.env.COGNOTIK_MODEL || config.model || null,
    system: opts.system ?? config.system ?? null,
    temperature: opts.temperature ?? (envTemp ? parseTemperature(envTemp) : config.temperature ?? null),
    maxTokens: opts.maxTokens ?? config.maxTokens ?? null,
    session: opts.session || newSessionId(),
    chatTimeoutMs: (opts.timeout || Number(process.env.COGNOTIK_TIMEOUT) || config.timeout || 300) * 1000,
    verbose: !!opts.verbose || !!process.env.COGNOTIK_DEBUG,
    json: !!opts.json,
    showUsage: !!opts.showUsage,
  };
  if (!settings.providers.length) throw new UsageError('At least one upstream provider is required');
  if (tokenData?.baseUrl && settings.token === tokenData.token &&
      tokenData.baseUrl.replace(/\/+$/, '') !== baseUrl) {
    warn(`stored token was issued by ${tokenData.baseUrl}, but requests go to ${baseUrl}`);
  }
  return settings;
}











// ─── Models ───────────────────────────────────────────────────────────────────





function printModels(models, { current } = {}) {
  const rows = models.map((m) => [
    (m.modelId === current ? '* ' : '  ') + (m.modelId ?? ''),
    m.name ?? '',
    providerName(m),
    formatTokens(m.maxTotalTokens),
    formatTokens(m.maxOutTokens),
    m.deprecated ? 'deprecated' : '',
  ]);
  const header = ['  MODEL ID', 'NAME', 'PROVIDER', 'CONTEXT', 'MAX OUT', ''];
  const widths = header.map((h, i) => Math.max(h.length, ...rows.map((r) => r[i].length)));
  const fmt = (r) => r.map((c, i) => c.padEnd(widths[i])).join('  ').trimEnd();
  process.stdout.write(out.bold(fmt(header)) + '\n');
  for (const r of rows) process.stdout.write((r[5] ? out.dim(fmt(r)) : fmt(r)) + '\n');
  process.stdout.write(out.dim(`${models.length} model(s)\n`));
}




// ─── Transcripts ──────────────────────────────────────────────────────────────

async function saveTranscript(file, conv, settings) {
   await writeFile(expandHome(file), JSON.stringify(transcriptToJSON(conv, settings), null, 2) + '\n');
}

async function loadTranscript(file, conv, settings) {
  const data = await readJsonFile(expandHome(file));
  if (!Array.isArray(data?.messages)) throw new Error(`${file} is not a saved conversation`);
   return applyTranscript(data, conv, settings);
}

// ─── Attachments ──────────────────────────────────────────────────────────────
async function fileToPart(p) {
  const file = expandHome(p);
  const st = await stat(file);
  if (!st.isFile()) throw new Error(`${p} is not a file`);
  if (st.size > MAX_TEXT_ATTACHMENT_BYTES) throw new Error(`${p} is too large (${st.size} bytes)`);
  const text = await readFile(file, 'utf8');
   return textFilePart(path.basename(file), text);
}

async function imageToPart(p) {
  const file = expandHome(p);
   const mime = mimeForFilename(file);
  if (!mime) throw new Error(`Unsupported image type: ${p} (use ${Object.keys(IMAGE_MIME).join(', ')})`);
   return imagePart(mime, await readFile(file));
}

async function collectAttachments(files, images) {
  const parts = [];
  for (const f of files) parts.push(await fileToPart(f));
  for (const i of images) parts.push(await imageToPart(i));
  return parts;
}


// ─── Model resolution ─────────────────────────────────────────────────────────
/** Resolves a user-supplied model query; falls back to the raw string when listing fails. */
async function resolveModel(cache, query, { quiet = false } = {}) {
  let models;
  try { models = await cache.get(); } catch (e) {
    if (!quiet) warn(`could not list models (${e.message}); using "${query}" as-is`);
    return { modelId: query };
  }
  const matches = matchModels(models, query);
  if (matches.length === 1) return matches[0];
  if (matches.length > 1) {
    const exact = matches.find((m) => m.modelId?.toLowerCase() === query.toLowerCase());
    if (exact) return exact;
    const names = matches.slice(0, 10).map((m) => m.modelId).join(', ');
    throw new UsageError(`"${query}" is ambiguous: ${names}${matches.length > 10 ? ', …' : ''}`);
  }
  if (!quiet) warn(`model "${query}" not found in the proxy model list; using it as-is`);
  return { modelId: query };
}

async function ensureModel(settings, cache) {
  if (settings.model) return;
  const models = await cache.get();
  const m = chooseDefaultModel(models);
  if (!m) throw new CognotikError('No models available from the proxy');
  settings.model = m.modelId;
  info(err.dim(`Using model ${m.modelId} (set one with --model or \`config set model <id>\`)`));
}

// ─── Commands ─────────────────────────────────────────────────────────────────
async function runModels(client, settings, opts) {
  const models = await client.listModels();
  const filter = opts.positional.join(' ');
  const shown = filter ? matchModels(models, filter) : models;
  if (settings.json) { process.stdout.write(JSON.stringify(shown, null, 2) + '\n'); return; }
  if (!shown.length) { info(`No models match "${filter}"`); return; }
  printModels(shown, { current: settings.model });
}

async function runAsk(client, settings, opts) {
  let prompt = opts.positional.join(' ');
  if (!process.stdin.isTTY) {
    const piped = await readStdin();
    if (piped.trim()) prompt = prompt ? `${prompt}\n\n${piped}` : piped;
  }
  if (!prompt.trim()) throw new UsageError('No prompt given');

  const conv = new Conversation({ system: settings.system });
  if (opts.load) await loadTranscript(opts.load, conv, settings);
  if (!settings.model) await ensureModel(settings, new ModelCache(client));

  conv.addUser([...(await collectAttachments(opts.files, opts.images)), { type: 'text', text: prompt }]);

  const ctrl = new AbortController();
  const onSigint = () => ctrl.abort();
  process.once('SIGINT', onSigint);
  const spinner = startSpinner('submitting');
  let resp;
  try {
    resp = await client.chat(buildRequest(conv, settings), { signal: ctrl.signal, onStatus: (s) => spinner.update(s) });
  } finally {
    spinner.stop();
    process.removeListener('SIGINT', onSigint);
  }

  const text = extractText(resp);
  if (settings.json) process.stdout.write(JSON.stringify(resp, null, 2) + '\n');
  else process.stdout.write(text.endsWith('\n') ? text : text + '\n');
  if (!text && !settings.json) warn(`empty response (finish_reason=${resp?.choices?.[0]?.finish_reason ?? 'unknown'})`);
  if (settings.showUsage || settings.verbose) info(err.dim(`${resp?.model || settings.model} · ${UsageTracker.format(resp?.usage)}`));

  if (opts.save) {
    conv.addAssistant(text);
    await saveTranscript(opts.save, conv, settings);
  }
}

const REPL_HELP = [
  ['/help', 'Show this help'],
  ['/models [filter]', 'List available models'],
  ['/model [id]', 'Show or switch the current model'],
  ['/system [text|clear]', 'Show, set or clear the system prompt'],
  ['/temp [n|default]', 'Show or set temperature'],
  ['/max [n|default]', 'Show or set max output tokens'],
  ['/file <path>', 'Attach a text file to the next message'],
  ['/image <path>', 'Attach an image to the next message'],
  ['/attachments [clear]', 'List or clear pending attachments'],
  ['/history', 'Show the conversation so far'],
  ['/undo', 'Remove the last exchange'],
  ['/retry', 'Resend the last (or failed) message'],
  ['/clear', 'Start a new conversation (keeps system prompt)'],
  ['/save <file>', 'Save the conversation to JSON'],
  ['/load <file>', 'Load a conversation from JSON'],
  ['/usage', 'Show token usage for this session'],
  ['/session [new|<id>]', 'Show or change the proxy session id'],
  ['/exit', 'Quit (also Ctrl-D, or Ctrl-C twice)'],
];

async function runChat(client, settings, opts) {
  const cache = new ModelCache(client);
  const usage = new UsageTracker();
  const conv = new Conversation({ system: settings.system });
  let pending = await collectAttachments(opts.files, opts.images);
  let lastFailed = null;
  let autoSave = opts.save || null;

  if (opts.load) {
    const n = await loadTranscript(opts.load, conv, settings);
    info(err.dim(`Loaded ${n} message(s) from ${opts.load}`));
  }
  try {
    if (settings.model) settings.model = (await resolveModel(cache, settings.model)).modelId;
    else await ensureModel(settings, cache);
  } catch (e) {
    if (e instanceof UsageError) throw e;
    printError(e);
    info(err.dim('Select a model later with /model <id>.'));
  }

  const write = (s = '') => process.stdout.write(s + '\n');
  write(`${out.bold('Cognotik chat')} ${out.dim(`— ${settings.baseUrl}`)}`);
  write(out.dim(`model: ${settings.model ?? '(none)'} · session: ${client.session}${settings.token ? '' : ' · not logged in'}`));
  write(out.dim('Type /help for commands. Use """ on its own line for multi-line input, or end a line with \\.'));
  write();

  const commandNames = REPL_HELP.map(([c]) => c.split(' ')[0]);
  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
    terminal: !!process.stdin.isTTY,
    historySize: 500,
    completer: (line) => {
      if (!line.startsWith('/') || line.includes(' ')) return [[], line];
      const hits = commandNames.filter((n) => n.startsWith(line));
      return [hits.length ? hits : commandNames, line];
    },
  });

  let block = null;          // lines collected inside a """ block
  let continuation = [];     // lines joined with trailing backslash
  let current = null;        // AbortController of the in-flight request
  let lastSigint = 0;
  let closed = false;

  const setPrompt = () => rl.setPrompt(block || continuation.length ? out.dim('... ') : out.green('you> '));
  const prompt = () => { if (!closed) { setPrompt(); rl.prompt(); } };

  async function persist() {
    if (!autoSave) return;
    try { await saveTranscript(autoSave, conv, settings); } catch (e) { warn(`autosave failed: ${e.message}`); }
  }

  async function exchange() {
    if (!settings.model) {
      try { await ensureModel(settings, cache); } catch (e) {
        lastFailed = conv.messages.pop();
        throw e;
      }
    }
    const ctrl = new AbortController();
    current = ctrl;
    const spinner = startSpinner('submitting');
    try {
      const resp = await client.chat(buildRequest(conv, settings), { signal: ctrl.signal, onStatus: (s) => spinner.update(s) });
      spinner.stop();
      const text = extractText(resp);
      conv.addAssistant(text);
      usage.add(resp?.usage);
      write(out.magenta('assistant>'));
      write(text || out.dim(`(empty response, finish_reason=${resp?.choices?.[0]?.finish_reason ?? 'unknown'})`));
      if (settings.showUsage) write(out.dim(UsageTracker.format(resp?.usage)));
      write();
      await persist();
    } catch (e) {
      spinner.stop();
      lastFailed = conv.messages.pop();
      printError(e);
      info(err.dim('  Message not added to the conversation; use /retry to resend it.'));
    } finally {
      current = null;
    }
  }

  async function send(text) {
    conv.addUser([...pending, { type: 'text', text }]);
    pending = [];
    lastFailed = null;
    await exchange();
  }

  async function command(line) {
    const [cmd, ...restParts] = line.split(/\s+/);
    const arg = line.slice(cmd.length).trim();
    switch (cmd) {
      case '/help': case '/?':
        for (const [c, d] of REPL_HELP) write(`  ${out.cyan(c.padEnd(22))} ${d}`);
        break;
      case '/exit': case '/quit': case '/q':
        rl.close();
        break;
      case '/models': {
        const models = await cache.refresh();
        const shown = arg ? matchModels(models, arg) : models;
        if (shown.length) printModels(shown, { current: settings.model });
        else write(`No models match "${arg}"`);
        break;
      }
      case '/model':
        if (!arg) { write(`model: ${settings.model ?? '(none)'}`); break; }
        settings.model = (await resolveModel(cache, arg)).modelId;
        write(out.dim(`model set to ${settings.model}`));
        break;
      case '/system':
        if (!arg) write(conv.system ? `system: ${conv.system}` : 'no system prompt');
        else if (arg === 'clear') { conv.system = null; write(out.dim('system prompt cleared')); }
        else { conv.system = arg; write(out.dim('system prompt set')); }
        break;
      case '/temp': case '/temperature':
        if (!arg) write(`temperature: ${settings.temperature ?? 'default'}`);
        else { settings.temperature = arg === 'default' ? null : parseTemperature(arg); write(out.dim(`temperature: ${settings.temperature ?? 'default'}`)); }
        break;
      case '/max':
        if (!arg) write(`max tokens: ${settings.maxTokens ?? 'default'}`);
        else { settings.maxTokens = arg === 'default' ? null : parsePositiveInt(arg, 'max tokens'); write(out.dim(`max tokens: ${settings.maxTokens ?? 'default'}`)); }
        break;
      case '/file':
        if (!arg) throw new UsageError('Usage: /file <path>');
        pending.push(await fileToPart(arg));
        write(out.dim(`attached ${arg} (${pending.length} pending)`));
        break;
      case '/image':
        if (!arg) throw new UsageError('Usage: /image <path>');
        pending.push(await imageToPart(arg));
        write(out.dim(`attached image ${arg} (${pending.length} pending)`));
        break;
      case '/attachments':
        if (arg === 'clear') { pending = []; write(out.dim('attachments cleared')); break; }
        if (!pending.length) { write('no pending attachments'); break; }
        pending.forEach((p, i) => write(`  ${i + 1}. ${p.type === 'image_url' ? '[image]' : p.text.split('\n')[0]}`));
        break;
      case '/history':
        if (conv.system) write(`${out.yellow('system>')} ${conv.system}\n`);
        if (!conv.messages.length) { write('conversation is empty'); break; }
        for (const m of conv.messages) {
          write(`${m.role === 'user' ? out.green('you>') : out.magenta('assistant>')} ${messageText(m)}\n`);
        }
        break;
      case '/undo':
        write(out.dim(conv.undo() ? 'removed last exchange' : 'nothing to undo'));
        await persist();
        break;
      case '/retry':
        if (lastFailed) { conv.messages.push(lastFailed); lastFailed = null; await exchange(); break; }
        if (conv.messages.at(-1)?.role === 'assistant') { conv.messages.pop(); await exchange(); break; }
        write('nothing to retry');
        break;
      case '/clear':
        conv.messages = []; pending = []; lastFailed = null;
        write(out.dim('conversation cleared'));
        break;
      case '/save':
        if (!arg) throw new UsageError('Usage: /save <file>');
        await saveTranscript(arg, conv, settings);
        autoSave = autoSave || null;
        write(out.dim(`saved to ${arg}`));
        break;
      case '/load': {
        if (!arg) throw new UsageError('Usage: /load <file>');
        const n = await loadTranscript(arg, conv, settings);
        write(out.dim(`loaded ${n} message(s) from ${arg}`));
        break;
      }
      case '/usage':
        write(usage.summary());
        break;
      case '/session':
        if (!arg) write(`session: ${client.session}`);
        else { client.session = arg === 'new' ? newSessionId() : restParts[0]; write(out.dim(`session: ${client.session}`)); }
        break;
      default:
        write(`Unknown command ${cmd}; type /help`);
    }
  }

  async function onLine(line) {
    if (block) {
      if (line.trim() === '"""') { const text = block.join('\n'); block = null; if (text.trim()) await send(text); }
      else block.push(line);
      return;
    }
    if (line.trim() === '"""') { block = []; return; }
    if (line.endsWith('\\')) { continuation.push(line.slice(0, -1)); return; }
    if (continuation.length) { continuation.push(line); line = continuation.join('\n'); continuation = []; }
    const trimmed = line.trim();
    if (!trimmed) return;
    if (trimmed.startsWith('/') && !trimmed.includes('\n')) return command(trimmed);
    return send(line);
  }

  rl.on('SIGINT', () => {
    if (current) { current.abort(); return; }
    if (block || continuation.length || rl.line) {
      block = null; continuation = [];
      rl.write(null, { ctrl: true, name: 'u' });
      process.stdout.write('\n');
      prompt();
      return;
    }
    const now = Date.now();
    if (now - lastSigint < 1500) { rl.close(); return; }
    lastSigint = now;
    process.stdout.write(`\n${out.dim('(press Ctrl-C again to exit, or type /exit)')}\n`);
    prompt();
  });

  let queue = Promise.resolve();
  await new Promise((resolve) => {
    rl.on('line', (line) => {
      queue = queue
        .then(() => onLine(line))
        .catch((e) => printError(e))
        .finally(prompt);
    });
    rl.on('close', () => {
      closed = true;
      queue.finally(resolve);
    });
    prompt();
  });

  if (usage.requests) info(err.dim(`\nSession usage: ${usage.summary()}`));
  if (autoSave) info(err.dim(`Conversation saved to ${autoSave}`));
}

async function runLogin(settings, opts) {
  const script = fileURLToPath(new URL('./cognotik-login.mjs', import.meta.url));
  await mkdir(path.dirname(settings.tokenFile), { recursive: true });
  const args = [script, settings.baseUrl, '--out', settings.tokenFile];
  if (opts.noOpen) args.push('--no-open');
  if (opts.client) args.push('--client', opts.client);
  const child = spawn(process.execPath, args, { stdio: ['inherit', 'pipe', 'inherit'] });
  child.stdout.resume(); // the raw token is printed on stdout; we keep it only in the token file
  const code = await new Promise((resolve) => {
    child.on('error', () => resolve(-1));
    child.on('close', resolve);
  });
  if (code !== 0) throw new CognotikError(`Login failed (exit code ${code})`);
  info(err.green(`✔ Token stored in ${settings.tokenFile}`));
}

async function runLogout(settings) {
  await rm(settings.tokenFile, { force: true });
  info(err.green(`✔ Removed ${settings.tokenFile}`));
}

async function runStatus(client, settings) {
  const line = (k, v) => process.stdout.write(`${out.dim(k.padEnd(13))} ${v}\n`);
  line('server', settings.baseUrl);
  line('proxy', client.proxyBase);
  line('providers', settings.providers.join(', '));
  line('token', settings.token ? `present (${settings.tokenFile}, cookie "${settings.cookieName}")` : 'none');
  line('api key', settings.apiKey ? 'set' : 'not set');
  line('model', settings.model ?? '(auto)');
  line('temperature', settings.temperature ?? 'default');
  line('max tokens', settings.maxTokens ?? 'default');
  line('config', CONFIG_FILE);
  const spinner = startSpinner('checking proxy');
  try {
    const models = await client.listModels();
    spinner.stop();
    line('connection', out.green(`ok — ${models.length} model(s) available`));
  } catch (e) {
    spinner.stop();
    line('connection', `failed — ${e.message}`);
    process.exitCode = 1;
  }
}

// ─── Entry point ──────────────────────────────────────────────────────────────
async function main() {
  const opts = parseArgs(process.argv.slice(2));
  if (opts.version) { process.stdout.write(`${VERSION}\n`); return; }
  if (opts.help || opts.command === 'help') { printHelp(); return; }

  const config = await loadConfig();
  if (opts.command === 'config') return runConfig(config, opts.positional);

  const settings = await resolveSettings(opts, config);
  if (opts.command === 'login') return runLogin(settings, opts);
  if (opts.command === 'logout') return runLogout(settings);

   const client = new CognotikClient({
     ...settings,
     userAgent: USER_AGENT,
     sendCookieHeader: true,
     logger: (m) => info(err.dim(m)),
   });
  if (!settings.token && !settings.apiKey && opts.command !== 'status') {
    warn('no auth token found; run `cognotik-chat login` first if requests are rejected');
  }
  switch (opts.command) {
    case 'status': return runStatus(client, settings);
    case 'models': return runModels(client, settings, opts);
    case 'ask': return runAsk(client, settings, opts);
    case 'chat':
      if (opts.positional.length) return runAsk(client, settings, opts);
      return runChat(client, settings, opts);
    default: throw new UsageError(`Unknown command: ${opts.command}`);
  }
}

main().catch((e) => {
  printError(e);
  process.exit(e?.exitCode || (e?.aborted ? 130 : 1));
});