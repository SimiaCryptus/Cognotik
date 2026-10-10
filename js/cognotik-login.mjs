#!/usr/bin/env node
/**
 * Obtain a Cognotik session token from the command line using the QR / device login flow.
 *
 * Usage:
 *   node qr-login.mjs [baseUrl] [--client <name>] [--no-open] [--out <file>] [--check <path>]
 *
 * Examples:
 *   node qr-login.mjs http://localhost:8080
 *   TOKEN=$(node qr-login.mjs https://cognotik.example.com --no-open)
 *   node qr-login.mjs https://cognotik.example.com --out ~/.cognotik-token --check /
 *
 * Using the token afterwards (sent as the auth cookie):
 *   curl -H "Cookie: <cookieName>=$TOKEN" https://cognotik.example.com/...
 *
 * Progress messages go to stderr; only the token is written to stdout, so it can be captured.
 * Requires Node 18+ (global fetch). Optional: `npm i qrcode-terminal` to show a QR in the terminal.
 */
import { spawn } from 'node:child_process';
import { writeFile } from 'node:fs/promises';
import os from 'node:os';

// ─── Arguments ────────────────────────────────────────────────────────────────
const args = process.argv.slice(2);
let baseUrl = process.env.COGNOTIK_URL || 'https://hosted.cognotik.com';
let clientName = `node-cli@${os.hostname()}`;
let openBrowser = true;
let outFile = null;
let checkPath = null;

for (let i = 0; i < args.length; i++) {
  const a = args[i];
  if (a === '--client') clientName = args[++i];
  else if (a === '--no-open') openBrowser = false;
  else if (a === '--out') outFile = args[++i];
  else if (a === '--check') checkPath = args[++i];
  else if (a === '-h' || a === '--help') {
    console.error('Usage: node qr-login.mjs [baseUrl] [--client <name>] [--no-open] [--out <file>] [--check <path>]');
    process.exit(0);
  } else if (!a.startsWith('--')) baseUrl = a;
  else { console.error(`Unknown option: ${a}`); process.exit(2); }
}
baseUrl = baseUrl.replace(/\/+$/, '');
const loginEndpoint = `${baseUrl}/login/`;

const info = (...m) => console.error(...m);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// ─── HTTP helper ──────────────────────────────────────────────────────────────
async function postForm(url, params) {
  const body = new URLSearchParams({ formAction: 'login', loginMethod: 'qr', ...params });
  const res = await fetch(url, {
    method: 'POST',
    redirect: 'manual', // a redirect means the server did not handle this as a JSON API call
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      'Accept': 'application/json',
      'User-Agent': `cognotik-qr-login/1.0 (node ${process.version}; ${os.platform()})`,
    },
    body: body.toString(),
  });
  const type = res.headers.get('content-type') || '';
  if (!type.includes('application/json')) {
    const location = res.headers.get('location');
    throw new Error(
      `Unexpected response from server (HTTP ${res.status}${location ? `, redirect to ${location}` : ''}). ` +
      'Is the QR login method enabled on this server?'
    );
  }
  return { httpStatus: res.status, data: await res.json() };
}

function tryOpenBrowser(url) {
  try {
    const platform = process.platform;
    const [cmd, cmdArgs] =
      platform === 'darwin' ? ['open', [url]] :
      platform === 'win32' ? ['cmd', ['/c', 'start', '""', url]] :
      ['xdg-open', [url]];
    const child = spawn(cmd, cmdArgs, { stdio: 'ignore', detached: true });
    child.on('error', () => {}); // no browser available, the URL is printed anyway
    child.unref();
  } catch { /* ignore */ }
}

async function tryShowQr(url) {
  try {
    const mod = await import('qrcode-terminal');
    const qrcode = mod.default || mod;
    await new Promise((resolve) =>
      qrcode.generate(url, { small: true }, (q) => { info(q); resolve(); }));
  } catch { /* optional dependency not installed */ }
}

// ─── Flow ─────────────────────────────────────────────────────────────────────
async function main() {
  // 1. Start the request
  const { httpStatus, data: start } = await postForm(loginEndpoint, { qrAction: 'device', client: clientName });
  if (httpStatus !== 200 || !start.rid) {
    throw new Error(`Failed to start login (HTTP ${httpStatus}): ${start.error || JSON.stringify(start)}`);
  }
  const tokenEndpoint = start.tokenEndpoint || loginEndpoint;
  let interval = Math.max(1, Number(start.interval) || 2);
  const deadline = Date.now() + (Number(start.expiresIn) || 180) * 1000;

  // 2. Send the user to the browser
  info('');
  info('To sign in, open this URL in a browser where you are already logged in:');
  info(`\n    ${start.verificationUrl}\n`);
  info(`and check that it shows the confirmation code:  ${start.displayCode}`);
  info('');
  await tryShowQr(start.verificationUrl);
  if (openBrowser) tryOpenBrowser(start.verificationUrl);

  // 3. Poll for the token
  let lastStatus = null;
  while (Date.now() < deadline) {
    await sleep(interval * 1000);
    let result;
    try {
      result = await postForm(tokenEndpoint, { qrAction: 'token', rid: start.rid, poll: start.pollSecret });
    } catch (e) {
      info(`Polling error (${e.message}); retrying…`);
      await sleep(interval * 1000);
      continue;
    }
    const { data } = result;
    switch (data.status) {
      case 'pending':
        break;
      case 'scanned':
        if (lastStatus !== 'scanned') info('Approval page opened, waiting for you to approve…');
        break;
      case 'slow_down':
        interval = Math.max(interval + 1, Number(data.interval) || interval + 1);
        break;
      case 'approved':
        return { ...data, cookieName: data.cookieName || start.cookieName };
      case 'denied':
        throw new Error('The login request was denied.');
      case 'expired':
        throw new Error('The login request expired. Please run the command again.');
      default:
        throw new Error(`Unexpected response: ${JSON.stringify(data)}`);
    }
    lastStatus = data.status;
  }
  throw new Error('Timed out waiting for approval.');
}

try {
  const result = await main();
  const who = result.user?.email || 'unknown user';
  info(`✅ Logged in as ${who}. Token valid for ~${Math.round((result.expiresIn || 0) / 86400)} day(s).`);
  info(`   Send it as a cookie:  Cookie: ${result.cookieName}=<token>`);

  if (outFile) {
    const contents = JSON.stringify({
      baseUrl,
      cookieName: result.cookieName,
      token: result.token,
      user: result.user,
      obtainedAt: new Date().toISOString(),
      expiresIn: result.expiresIn,
    }, null, 2);
    await writeFile(outFile, contents, { mode: 0o600 });
    info(`   Saved to ${outFile}`);
  }

  if (checkPath) {
    const res = await fetch(baseUrl + checkPath, {
      headers: { Cookie: `${result.cookieName}=${result.token}` },
      redirect: 'manual',
    });
    const loc = res.headers.get('location') || '';
    const ok = res.status < 300 || (res.status < 400 && !loc.includes('/login'));
    info(`   Check ${checkPath}: HTTP ${res.status}${loc ? ` -> ${loc}` : ''} ${ok ? '(authenticated)' : '(NOT authenticated)'}`);
  }

  // Only the token goes to stdout so it can be captured with $(...)
  process.stdout.write(result.token + '\n');
} catch (e) {
  info(`❌ ${e.message}`);
  process.exit(1);
}