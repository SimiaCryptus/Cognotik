# Hosted Client Support — Client/Server Token Login Protocol

This document describes the protocol implemented by `HostedClientSupport.kt` (and mirrored by
`cognotik-login.mjs`). A command-line or headless client uses it to obtain a session token from a
hosted Cognotik server **without handling the user's credentials**. The user approves the
login in a browser where they are already signed in. The token is then used as a session cookie.

The flow is a variant of the OAuth 2.0 Device Authorization Grant (RFC 8628). It is adapted to a
form-encoded `/login/` endpoint and cookie-based sessions. Use this document as a reference when
you implement compatible clients or servers.

> **Scope note:** This document covers the client side as implemented in `HostedClientSupport.kt`.
> It also lists the server behaviour that the client depends on. Some server-side details are not
> visible in the client code, such as how tokens are generated, stored and checked. Those parts
> are marked **(server requirement)** or **(recommended)**. They describe what a compatible server
> must or should do. They do not describe a specific server implementation.

---

## 1. Actors

| Actor | Description |
|-------|-------------|
| **Client** | The CLI (`cognotik-cli`). It has no browser session and no credentials. |
| **Server** | The hosted Cognotik web application at `baseUrl`. |
| **Approver** | The user's browser, already authenticated to the server. It opens the verification URL. |

---

## 2. Overview

```
Client                                Server                         Approver (browser)
  |  POST /login/  qrAction=device       |                                   |
  |------------------------------------->|  create request: rid, pollSecret, |
  |  {rid, pollSecret, verificationUrl,  |  displayCode, expiry              |
  |   displayCode, tokenEndpoint,        |                                   |
  |   interval, expiresIn}               |                                   |
  |<-------------------------------------|                                   |
  |  show URL + displayCode, open browser|                                   |
  |                                      |<---- GET verificationUrl ---------|
  |  POST tokenEndpoint qrAction=token   |      (status -> "scanned")        |
  |  rid, poll=pollSecret   (every N s)  |                                   |
  |------------------------------------->|                                   |
  |  {status:"pending"|"scanned"|...}    |<---- user checks code, approves --|
  |<-------------------------------------|      (status -> "approved")       |
  |  POST tokenEndpoint qrAction=token   |                                   |
  |------------------------------------->|  mint session token for user     |
  |  {status:"approved", token, user,    |                                   |
  |   expiresIn, cookieName}             |                                   |
  |<-------------------------------------|                                   |
  |  save {userId, sessionKey}           |                                   |
  |  GET /userSettings/  Cookie: <AUTH_COOKIE>=token                         |
  |------------------------------------->|  verify token -> user             |
```

---

## 3. Transport conventions

Every login call uses the same request shape. See `postLoginForm`.

- **Method:** `POST`
- **Content-Type:** `application/x-www-form-urlencoded` (UTF-8, URL-encoded keys and values)
- **Accept:** `application/json`
- **User-Agent:** `cognotik-qr-login/1.0 (java <version>)`
- **Redirects:** the client never follows them (`HttpClient.Redirect.NEVER`).
- **Timeouts:** 15 s to connect, 30 s per request.

The client always adds these fields to the form body:

| Field | Value |
|-------|-------|
| `formAction` | `login` |
| `loginMethod` | `qr` |

These fields route the request to the QR/device handler on the same `/login/` endpoint that the
interactive web login uses.

**Response validation:** the response `Content-Type` must contain `application/json`. Otherwise
the client fails with this message:

> Unexpected response from server (HTTP *status*[, redirect to *location*]). Is the QR login method enabled on this server?

An HTML page or a redirect means that the server does not support QR login, or has it disabled.

**(server requirement)** The server must answer QR-login requests with JSON. It must not redirect
them, and this applies to errors too.

### Response object

All responses map onto a single JSON object (`LoginResponse`). Unknown fields are ignored. Every
field is optional.

| Field | Type | Used in | Meaning |
|-------|------|---------|---------|
| `rid` | string | start | Request ID. It is public and appears in the verification URL. |
| `pollSecret` | string | start | Secret that only the client knows. The client must send it when polling. |
| `verificationUrl` | string | start | URL that the approver opens. It can also be shown as a QR code. |
| `displayCode` | string | start | Short code that the user compares between the CLI and the approval page. |
| `tokenEndpoint` | string | start | URL to poll. The default is the login endpoint. |
| `cookieName` | string | start/approved | Name of the session cookie. The client currently uses `AuthenticationInterface.AUTH_COOKIE`. |
| `interval` | number (s) | start, slow_down | Minimum polling interval. |
| `expiresIn` | number (s) | start / approved | In the start response: lifetime of the request. In the approved response: lifetime of the token. |
| `status` | string | token | See §5. |
| `token` | string | approved | The session token (session key). |
| `user` | `{id, email}` | approved | The user who approved the login. |
| `error` | string | any | Error message that a person can read. |

---

## 4. Step 1 — Start a device request

**Request:** `POST {baseUrl}/login/`

```
formAction=login&loginMethod=qr&qrAction=device&client=cognotik-cli%40<hostname>
```

The `client` field is a label that a person can read. The approval page can show it so the user
knows which device is asking ("cognotik-cli@my-laptop"). The client uses `localhost` as the
hostname if it cannot find the real one.

**Success:** HTTP `200` with a body like this:

```json
{
  "rid": "b7Q…",
  "pollSecret": "x9…",
  "verificationUrl": "https://host/login/?qr=b7Q…",
  "displayCode": "WXYZ-1234",
  "tokenEndpoint": "https://host/login/",
  "interval": 2,
  "expiresIn": 180
}
```

The client treats any status other than `200`, or a missing `rid`, as failure:
`Failed to start login (HTTP n): <error>`.

**Client defaults:**

| Setting | Default | Rule |
|---------|---------|------|
| Token endpoint | `{baseUrl}/login/` | Used when `tokenEndpoint` is missing. |
| Polling interval | 2 s | Never less than 1 s. |
| Request lifetime | 180 s | Used when `expiresIn` is missing or ≤ 0. |

The client then prints `verificationUrl` and `displayCode` to stderr. If `openBrowser` is enabled,
it also tries to open the URL with `open`, `cmd /c start` or `xdg-open`, depending on the
operating system.

### (server requirement) Creating a request

The server must create the following values:

- **`rid`** — a random, unguessable value of at least 128 bits. It is safe to expose in URLs and
  QR codes.
- **`pollSecret`** — a separate random value of at least 128 bits. It is returned **only** to
  the client that started the request.
  - Store only a hash of it.
  - Compare it in constant time.
  - It proves that a poller is the device that started the request. Someone who only sees the
    QR code or URL therefore cannot collect the token.
- **`displayCode`** — a short code that a person can read. The approval page shows the same code,
  so the user can confirm they are approving their own terminal. This protects against phishing
  with a forwarded link.
- **Request lifetime** — a short expiry (about 3 minutes), stored with these fields:
  - `status = pending`
  - the `client` label
  - **(recommended)** the requester's IP address and user agent, so they can be shown to the
    approver
- **Rate limits (recommended)** — limit device-request creation per IP address.

---

## 5. Step 2 — Poll for the token

**Request:** `POST {tokenEndpoint}`

```
formAction=login&loginMethod=qr&qrAction=token&rid=<rid>&poll=<pollSecret>
```

**Loop behaviour:**

- The client sleeps for `interval` seconds before each poll.
- The loop runs until the request's deadline.
- A transport error or a non-JSON response is logged ("Polling error … retrying…"). The client
  waits one extra interval and then continues.
- An `InterruptedException` cancels the login, and the client returns `null`.

### Status values

| `status` | Client action |
|----------|---------------|
| `pending` | Keep polling. |
| `scanned` | The verification page has been opened. The client prints "waiting for you to approve…" once, then keeps polling. |
| `slow_down` | Polling is too fast. The new interval is `max(interval + 1, data.interval ?: interval + 1)`. |
| `approved` | Read `token` (required) and `user`. The login succeeds. |
| `denied` | Fail with "The login request was denied." |
| `expired` | Fail with "The login request expired." |
| anything else | Fail with "Unexpected response". |

If the deadline passes, the client fails with "Timed out waiting for approval."

An approved response looks like this:

```json
{
  "status": "approved",
  "token": "<session key>",
  "cookieName": "sessionId",
  "expiresIn": 2592000,
  "user": { "id": "1234", "email": "alice@example.com" }
}
```

Here `expiresIn` is the lifetime of the token. The client prints it rounded to whole days.

### (server requirement) Polling semantics

The server must handle polls as follows:

- **Wrong secret:** if `rid` is unknown, or `poll` does not match, return an error. Use JSON with
  `status` = `expired` or an `error` field, and a non-2xx status code. The response must **not**
  show whether the `rid` exists.
- **Fast polling:** enforce the interval. If polls come faster than `interval`, return
  `slow_down`, optionally with a larger `interval`.
- **Expiry:** when the request lifetime passes, return `expired` and delete the request.
- **One-time delivery:** the token is delivered **at most once**.
  - The first poll after approval mints the token, or takes a token that was minted at approval.
  - That poll marks the request as consumed and deletes it.
  - Any later poll gets `expired`.

---

## 6. Approver side (browser)

This step is entirely server-side, because the client only polls. It is listed here because a
compatible server must support it.

1. The approver opens `verificationUrl`.
   - If the browser is not logged in, the normal login flow runs first, and the server then
     returns to the URL.
   - Opening the URL changes the request status to `scanned`.
2. The page shows these values, all supplied by the server:
   - the `displayCode`
   - the `client` label
   - **(recommended)** the requester's IP address and location, and the request age
3. The page offers **Approve** and **Deny** actions.
   - **(server requirement)** These must be `POST` requests.
   - **(server requirement)** They must be CSRF-protected.
   - **(server requirement)** They must be bound to the approver's existing authenticated session.
4. The result depends on the action:
   - **Approve** binds the request to the approver's user identity and sets the status to
     `approved`.
   - **Deny** sets the status to `denied`.

---

## 7. Token coining (server requirement / recommended)

When an approved request is collected, the server creates ("coins") a **new** session token for
the approving user. It must not copy the browser's own session cookie. This lets the CLI session
be revoked on its own.

### Requirements

- **Randomness:** the token comes from a CSPRNG, with at least 256 bits of entropy, encoded in
  base64url or hex.
- **Storage:** the server stores a record with these fields:
  - `{token-hash → userId, email, createdAt, expiresAt, client label, origin=device-login}`
  - Store a hash of the token, such as SHA-256, rather than the raw token. A database leak then
    does not reveal tokens that can be used.
- **Lifetime:** long-lived but bounded, typically 30 days. The lifetime is returned as
  `expiresIn`, in seconds.
- **Token type:** the token is a *session key*. The server checks it the same way as a browser
  session cookie (see `AuthenticationInterface`). The CLI then needs no special authorization path.

### Recommended

- Show device sessions in the user's account page with a "revoke" control.
- Revoke all tokens when the user logs out of all sessions or changes their password.

---

## 8. Using the token (authorization)

The client stores the result as `FileServer.SessionKeyFile`:

```json
{ "userId": "alice@example.com", "sessionKey": "<token>" }
```

- `userId` is `user.email`, or `user.id` if there is no email, or `""` if neither is present.
- `saveSessionFile` writes the file and sets POSIX permissions to `rw-------` where the operating
  system supports it. The token is a bearer credential.

### Authenticated requests

Each request sends the token as a cookie:

```
Cookie: <AuthenticationInterface.AUTH_COOKIE>=<sessionKey>
Accept: application/json
User-Agent: cognotik-cli/1.0 (java <version>)
```

The server looks up the token, or its hash, and checks that the token has not expired. It then
resolves the user and applies that user's normal authorization rules.

### Validating a saved session (`fetchHostedUserSettings`)

The client validates a saved session with `GET {baseUrl}/userSettings/`, without following
redirects. It reads the response as follows:

| Response | Result |
|----------|--------|
| 401 or 403 | `Unauthorized` |
| 3xx with a `Location` that contains "login" | `Unauthorized` (the server wants an interactive login) |
| other 3xx | `Failed("unexpected redirect …")` |
| non-200 | `Failed` |
| 200 with non-JSON | `Failed` |
| 200 JSON | `Ok(UserSettings)` |

For a 200 JSON response, the client first parses the body as `UserSettings`. If that fails, it
searches the JSON depth-first, up to 8 levels deep, for an `email` string that contains `@`
(`findEmail`). It then builds a minimal `UserSettings(user = User(email))` from that value.

A blank session key gives `Unauthorized` immediately, without a request.

On `Unauthorized`, callers should run `clearSessionFile` and start a new device login.

---

## 9. Security checklist for compatible implementations

- [ ] `rid` and `pollSecret` are independent CSPRNG values. Only `rid` appears in URLs or QR codes.
- [ ] The poll secret is checked in constant time. The server stores only a hash of it.
- [ ] Device requests expire in a few minutes. They are single-use and deleted after consumption.
- [ ] The approval page shows `displayCode` and the client label. Approval uses POST and is
  CSRF-protected.
- [ ] The token is delivered at most once, and only over HTTPS.
- [ ] The token has ≥256 bits of entropy. It is stored hashed, has an expiry, and can be revoked
  on its own.
- [ ] QR-login endpoints always reply in JSON. Protected API endpoints reply 401/403 or redirect
  to login when the session is missing or invalid.
- [ ] Request creation and polling are rate-limited. `slow_down` is enforced.
- [ ] The client keeps the token in a file that only the owner can read. It deletes the file when
  the server rejects the token.

---

## 10. Writing another client

A minimal client needs to do the following:

1. Send `POST /login/` with `formAction=login, loginMethod=qr, qrAction=device, client=<label>`.
2. Show `verificationUrl` (optionally as a QR code) and `displayCode`.
3. Every `interval` seconds, send `POST tokenEndpoint` with
   `formAction=login, loginMethod=qr, qrAction=token, rid, poll=pollSecret`.
   - Handle `pending`, `scanned` and `slow_down` by continuing to poll.
   - Stop on `approved`, `denied` or `expired`, or when `expiresIn` runs out.
4. Store `token` securely.
5. Send `Cookie: <cookieName or AUTH_COOKIE>=<token>` on every API call.
6. If the server responds with 401, 403 or a redirect to login, discard the token and start again
   from step 1.