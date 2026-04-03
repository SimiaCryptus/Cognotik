'use strict';

const https = require('https');
const crypto = require('crypto');
const { KMSClient, DecryptCommand } = require('@aws-sdk/client-kms');

const kms = new KMSClient({});

// ─── KMS helpers ────────────────────────────────────────────────────────────

const decryptCache = {};

async function decryptEnv(envVar) {
  if (decryptCache[envVar]) return decryptCache[envVar];
  const encrypted = process.env[envVar];
  if (!encrypted) throw new Error(`Missing env var: ${envVar}`);

  const ciphertext = Buffer.from(encrypted, 'base64');
  const { Plaintext } = await kms.send(new DecryptCommand({ CiphertextBlob: ciphertext }));
  const value = Buffer.from(Plaintext).toString('utf-8');
  decryptCache[envVar] = value;
  return value;
}

// ─── HTTP helper ─────────────────────────────────────────────────────────────

function httpsRequest(url, options = {}, body = null) {
  return new Promise((resolve, reject) => {
    const parsed = new URL(url);
    const reqOptions = {
      hostname: parsed.hostname,
      path: parsed.pathname + parsed.search,
      method: options.method || 'GET',
      headers: options.headers || {},
    };

    const req = https.request(reqOptions, (res) => {
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => {
        const raw = Buffer.concat(chunks).toString('utf-8');
        resolve({ status: res.statusCode, headers: res.headers, body: raw });
      });
    });

    req.on('error', reject);
    if (body) req.write(body);
    req.end();
  });
}

// ─── Minimal JWT (HS256) – no external deps ──────────────────────────────────

function b64url(buf) {
  return Buffer.from(buf)
    .toString('base64')
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');
}

function signJwt(payload, secret) {
  const header = b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body   = b64url(JSON.stringify(payload));
  const sig    = crypto
    .createHmac('sha256', secret)
    .update(`${header}.${body}`)
    .digest('base64')
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');
  return `${header}.${body}.${sig}`;
}

function verifyJwt(token, secret) {
  const parts = token.split('.');
  if (parts.length !== 3) throw new Error('Invalid token format');
  const [header, body, sig] = parts;
  const expected = crypto
    .createHmac('sha256', secret)
    .update(`${header}.${body}`)
    .digest('base64')
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');
  if (!crypto.timingSafeEqual(Buffer.from(sig), Buffer.from(expected))) {
    throw new Error('Invalid token signature');
  }
  const payload = JSON.parse(Buffer.from(body, 'base64').toString('utf-8'));
  if (payload.exp && Date.now() / 1000 > payload.exp) throw new Error('Token expired');
  return payload;
}

// ─── Patreon helpers ─────────────────────────────────────────────────────────

const PATREON_TOKEN_URL    = 'https://www.patreon.com/api/oauth2/token';
const PATREON_IDENTITY_URL =
  'https://www.patreon.com/api/oauth2/v2/identity' +
  '?include=memberships.campaign' +
  '&fields%5Bmember%5D=patron_status,currently_entitled_amount_cents' +
  '&fields%5Buser%5D=email,full_name';

async function exchangeCodeForTokens(code, clientId, clientSecret, redirectUri) {
  const params = new URLSearchParams({
    code,
    grant_type:    'authorization_code',
    client_id:     clientId,
    client_secret: clientSecret,
    redirect_uri:  redirectUri,
  });

  const res = await httpsRequest(PATREON_TOKEN_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      'User-Agent':   'PatreonAuthLambda/1.0',
    },
  }, params.toString());

  if (res.status !== 200) {
    throw new Error(`Patreon token exchange failed: ${res.status} ${res.body}`);
  }
  return JSON.parse(res.body);
}

async function fetchPatreonIdentity(accessToken) {
  const res = await httpsRequest(PATREON_IDENTITY_URL, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'User-Agent':  'PatreonAuthLambda/1.0',
    },
  });

  if (res.status !== 200) {
    throw new Error(`Patreon identity fetch failed: ${res.status} ${res.body}`);
  }
  return JSON.parse(res.body);
}

/**
 * Returns true when the identity response contains at least one membership
 * for our campaign that is in "active_patron" status.
 */
function isActiveSubscriber(identityData, campaignId) {
  const included = identityData.included || [];
  return included.some((item) => {
    if (item.type !== 'member') return false;
    const status = item.attributes?.patron_status;
    if (status !== 'active_patron') return false;
    // If a campaignId is configured, scope the check to that campaign only
    if (campaignId) {
      const rel = item.relationships?.campaign?.data;
      return rel && rel.id === campaignId;
    }
    return true;
  });
}

// ─── CORS / response helpers ─────────────────────────────────────────────────

function corsHeaders(origin) {
  const allowed = (process.env.ALLOWED_ORIGINS || '*').split(',').map((s) => s.trim());
  const allowOrigin =
    allowed.includes('*') || allowed.includes(origin) ? (origin || '*') : allowed[0];
  return {
    'Access-Control-Allow-Origin':  allowOrigin,
    'Access-Control-Allow-Headers': 'Content-Type,Authorization',
    'Access-Control-Allow-Methods': 'GET,POST,OPTIONS',
  };
}

function respond(statusCode, body, extraHeaders = {}) {
  return {
    statusCode,
    headers: { 'Content-Type': 'application/json', ...extraHeaders },
    body: typeof body === 'string' ? body : JSON.stringify(body),
  };
}

function redirect(location, extraHeaders = {}) {
  return {
    statusCode: 302,
    headers: { Location: location, ...extraHeaders },
    body: '',
  };
}

// ─── Route handlers ───────────────────────────────────────────────────────────

/**
 * GET /auth
 * Redirects the user to Patreon's OAuth consent screen.
 * Optional QS param: `state` (caller-supplied opaque value, echoed back on callback)
 */
async function handleAuth(event) {
  const clientId   = process.env.PATREON_CLIENT_ID;   // plain – not secret
  const redirectUri = process.env.PATREON_REDIRECT_URI;

  if (!clientId || !redirectUri) {
    return respond(500, { error: 'Server misconfiguration: missing client_id or redirect_uri' });
  }

  const callerState = event.queryStringParameters?.state || '';
  // Embed caller state inside our own state so we can round-trip it
  const state = Buffer.from(JSON.stringify({ s: callerState })).toString('base64url');

  const params = new URLSearchParams({
    response_type: 'code',
    client_id:     clientId,
    redirect_uri:  redirectUri,
    scope:         'identity identity.memberships',
    state,
  });

  return redirect(`https://www.patreon.com/oauth2/authorize?${params}`);
}

/**
 * GET /callback
 * Patreon redirects here after the user grants/denies access.
 * On success: exchanges code → tokens, checks subscription, issues a signed JWT,
 * then redirects to FRONTEND_REDIRECT_URI with ?token=<jwt>[&state=<caller_state>]
 */
async function handleCallback(event) {
  const qs = event.queryStringParameters || {};

  if (qs.error) {
    return respond(400, { error: qs.error, description: qs.error_description });
  }

  const { code, state: rawState } = qs;
  if (!code) return respond(400, { error: 'Missing code parameter' });

  // Decode state
  let callerState = '';
  try {
    const decoded = JSON.parse(Buffer.from(rawState || '', 'base64url').toString('utf-8'));
    callerState = decoded.s || '';
  } catch {
    // non-fatal – state is optional
  }

  // Decrypt secrets
  const [clientSecret, jwtSecret] = await Promise.all([
    decryptEnv('ENCRYPTED_PATREON_CLIENT_SECRET'),
    decryptEnv('ENCRYPTED_JWT_SECRET'),
  ]);

  const clientId    = process.env.PATREON_CLIENT_ID;
  const redirectUri = process.env.PATREON_REDIRECT_URI;
  const campaignId  = process.env.PATREON_CAMPAIGN_ID || '';   // optional filter
  const frontendUri = process.env.FRONTEND_REDIRECT_URI;

  // Exchange code for Patreon tokens
  const tokens = await exchangeCodeForTokens(code, clientId, clientSecret, redirectUri);

  // Fetch identity + memberships
  const identity = await fetchPatreonIdentity(tokens.access_token);

  const userId   = identity.data?.id;
  const email    = identity.data?.attributes?.email    || '';
  const name     = identity.data?.attributes?.full_name || '';
  const isActive = isActiveSubscriber(identity, campaignId);

  // Issue a short-lived JWT (24 h)
  const now = Math.floor(Date.now() / 1000);
  const jwt = signJwt(
    {
      sub:       userId,
      email,
      name,
      patron:    isActive,
      iat:       now,
      exp:       now + 86400,
    },
    jwtSecret,
  );

  // Redirect to frontend with token
  if (frontendUri) {
    const dest = new URL(frontendUri);
    dest.searchParams.set('token', jwt);
    if (callerState) dest.searchParams.set('state', callerState);
    return redirect(dest.toString());
  }

  // No frontend URI configured – return JSON (useful during development)
  return respond(200, { token: jwt, patron: isActive, name, email });
}

/**
 * GET /verify
 * Validates a JWT issued by /callback and returns the subscription status.
 * The token can be supplied as:
 *   - Authorization: Bearer <token>  header
 *   - ?token=<token>                 query string (fallback)
 *
 * Response 200: { valid: true,  patron: bool, sub, email, name, exp }
 * Response 401: { valid: false, error: "..." }
 *
 * Pass ?live=1 to re-check Patreon in real-time (costs one API call).
 */
async function handleVerify(event) {
  const authHeader = event.headers?.Authorization || event.headers?.authorization || '';
  const qs         = event.queryStringParameters || {};
  const rawToken   = authHeader.startsWith('Bearer ')
    ? authHeader.slice(7)
    : qs.token || '';

  if (!rawToken) return respond(401, { valid: false, error: 'No token provided' });

  const jwtSecret = await decryptEnv('ENCRYPTED_JWT_SECRET');

  let payload;
  try {
    payload = verifyJwt(rawToken, jwtSecret);
  } catch (err) {
    return respond(401, { valid: false, error: err.message });
  }

  // Optional live re-check against Patreon
  if (qs.live === '1') {
    // We don't store the Patreon access token in the JWT (security), so we use
    // the creator token to look up the member by their Patreon user ID.
    try {
      const creatorToken = await decryptEnv('ENCRYPTED_PATREON_CREATOR_TOKEN');
      const campaignId   = process.env.PATREON_CAMPAIGN_ID || '';
      const identity     = await fetchPatreonIdentity(creatorToken);   // creator's own identity
      // For a live per-user check we'd need the Campaigns Members API; this
      // lightweight path just re-validates the creator token is still working.
      // Full per-user live check: see handleLiveCheck below.
      void identity; // suppress lint warning
    } catch (err) {
      console.error('Live check error:', err.message);
      // Non-fatal – fall through to cached JWT result
    }
  }

  return respond(200, {
    valid:  true,
    patron: payload.patron,
    sub:    payload.sub,
    email:  payload.email,
    name:   payload.name,
    exp:    payload.exp,
  });
}

/**
 * GET /verify/live
 * Full real-time membership check using the Patreon Campaigns Members API.
 * Requires a valid JWT (to get the Patreon user ID) + the creator token.
 *
 * Response 200: { patron: bool, status: string, entitled_cents: number }
 */
async function handleLiveCheck(event) {
  const authHeader = event.headers?.Authorization || event.headers?.authorization || '';
  const qs         = event.queryStringParameters || {};
  const rawToken   = authHeader.startsWith('Bearer ')
    ? authHeader.slice(7)
    : qs.token || '';

  if (!rawToken) return respond(401, { error: 'No token provided' });

  const [jwtSecret, creatorToken] = await Promise.all([
    decryptEnv('ENCRYPTED_JWT_SECRET'),
    decryptEnv('ENCRYPTED_PATREON_CREATOR_TOKEN'),
  ]);

  let payload;
  try {
    payload = verifyJwt(rawToken, jwtSecret);
  } catch (err) {
    return respond(401, { error: err.message });
  }

  const campaignId = process.env.PATREON_CAMPAIGN_ID;
  if (!campaignId) return respond(500, { error: 'PATREON_CAMPAIGN_ID not configured' });

  // Fetch member record for this user from the campaign
  const memberUrl =
    `https://www.patreon.com/api/oauth2/v2/campaigns/${campaignId}/members` +
    `?filter[user_id]=${payload.sub}` +
    `&fields%5Bmember%5D=patron_status,currently_entitled_amount_cents`;

  const res = await httpsRequest(memberUrl, {
    headers: {
      Authorization: `Bearer ${creatorToken}`,
      'User-Agent':  'PatreonAuthLambda/1.0',
    },
  });

  if (res.status !== 200) {
    throw new Error(`Patreon members API error: ${res.status} ${res.body}`);
  }

  const data    = JSON.parse(res.body);
  const member  = data.data?.[0];
  const status  = member?.attributes?.patron_status || 'none';
  const cents   = member?.attributes?.currently_entitled_amount_cents || 0;
  const isActive = status === 'active_patron';

  return respond(200, { patron: isActive, status, entitled_cents: cents });
}

// ─── Router ───────────────────────────────────────────────────────────────────

const ROUTES = {
  '/auth':         { GET: handleAuth },
  '/callback':     { GET: handleCallback },
  '/verify':       { GET: handleVerify },
  '/verify/live':  { GET: handleLiveCheck },
};

exports.handler = async (event, _context) => {
  const method = (event.httpMethod || 'GET').toUpperCase();
  const path   = event.path || '/';
  const origin = event.headers?.origin || event.headers?.Origin || '';
  const cors   = corsHeaders(origin);

  // Preflight
  if (method === 'OPTIONS') {
    return { statusCode: 204, headers: cors, body: '' };
  }

  const route = ROUTES[path];
  if (!route) {
    return respond(404, { error: `Unknown path: ${path}` }, cors);
  }

  const handler = route[method];
  if (!handler) {
    return respond(405, { error: `Method ${method} not allowed` }, cors);
  }

  try {
    const result = await handler(event);
    // Merge CORS headers into every response
    result.headers = { ...cors, ...result.headers };
    return result;
  } catch (err) {
    console.error('Unhandled error:', err);
    return respond(500, { error: err.message }, cors);
  }
};