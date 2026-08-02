# Static Secrets — Encrypted Browser Proxy PWA

## 1. Overview

Static Secrets is a Progressive Web App (PWA) built with plain HTML and
modular ES6 (no build step required) that acts as an **encrypted browser
proxy**. It fetches content blobs (from URLs), attempts to decrypt them
using a locally-stored keychain of passwords, and renders the decrypted
result as HTML, Markdown, or a downloadable binary file.

The core idea: publish encrypted static files anywhere (GitHub Pages, S3,
any static host). The app becomes a client-side gateway that transparently
decrypts and renders them, keeping all secrets and keys on the client.

## 2. Goals

- Load remote or local files and treat their contents as encrypted blobs.
- Maintain a **local keychain** of passwords in `localStorage`.
- Automatically try every stored key against each blob; use the first that
  yields a valid decryption.
- Prompt the user for a new password when no stored key works.
- Render decrypted output based on detected content type:
  - **HTML** — rendered in a sandboxed container.
  - **Markdown** — rendered via `marked.min.js`.
  - **Binary** — offered as a wrapped dynamic download.
- Track the currently-browsed URL in the page URL as a query parameter to
  support deep-linking and normal browser back/forward navigation.
- Provide a **Node.js CLI script** for encrypting files for publication.
- Work offline as an installable PWA.

## 3. Non-Goals

- No server-side decryption or key storage (client-only trust model).
- No user account system or cloud key sync (v1).
- Not intended to hide the _existence_ of content, only its contents.

## 4. Threat Model & Security Notes

- **Trust boundary:** All decryption happens client-side. The host serving
  the encrypted blobs never sees plaintext or keys.
- **Keys at rest:** Passwords live in `localStorage`, unencrypted by
  default. This is convenient but vulnerable to XSS and local access.
  A future enhancement may add a master passphrase to encrypt the keychain.
- **Crypto primitives:** Use the Web Crypto API (`SubtleCrypto`).
  - Key derivation: PBKDF2 (configurable iterations) or Argon2 (stretch
    goal) from password + per-blob salt.
  - Encryption: AES-GCM (256-bit), providing authenticated encryption.
  - The GCM authentication tag is used to detect a **valid decryption**
    (correct key) vs. failure — no plaintext heuristics needed.
- **Blob format** must be self-describing enough to decrypt but must not
  leak the key or plaintext content type before decryption.

## 5. Encrypted Blob Format

The container uses **envelope encryption** (format v2). A random 256-bit
Content Encryption Key (CEK) encrypts the payload exactly once. The CEK is
then _wrapped_ (encrypted) once per recipient/password in a recipients
section, so a single blob can be decrypted by any of several passwords —
enabling key rotation and multi-user sharing.

Fixed outer header:

| Field           | Size     | Description                      |
| --------------- | -------- | -------------------------------- |
| Magic           | 4 bytes  | Identifier, e.g. `SSEC`          |
| Version         | 1 byte   | Format version (2 = envelope)    |
| KDF id          | 1 byte   | 0 = PBKDF2-SHA256                |
| Iterations      | 4 bytes  | KDF iteration count (big-endian) |
| Recipient count | 2 bytes  | Number of recipient records (BE) |
| Content IV      | 12 bytes | AES-GCM nonce for the content    |

Then `Recipient count` recipient records, each fixed-size:

| Field           | Size     | Description                         |
| --------------- | -------- | ----------------------------------- |
| Salt            | 16 bytes | Per-recipient KDF salt              |
| Wrap IV         | 12 bytes | AES-GCM nonce for the wrapped key   |
| Wrapped CEK+Tag | 48 bytes | AES-GCM(CEK) = 32-byte key + 16 tag |

Followed by:

| Field            | Size     | Description                    |
| ---------------- | -------- | ------------------------------ |
| Ciphertext + Tag | variable | AES-GCM(payload) under the CEK |

Decryption tries each recipient slot: on a GCM tag match the CEK is
recovered, then used to decrypt the content. Legacy v1 (single-key,
direct) blobs are still parseable for backward compatibility.

declares the content type (`html`, `markdown`, `binary`) and an optional
filename/MIME, so type detection happens _after_ decryption and is never
exposed in the encrypted container.

- Inner payload sketch:
  - 1 byte content-type enum
  - 2 bytes filename length + filename (UTF-8)
  - remaining bytes: actual content

## 6. Application Architecture

### 6.1 Module Layout (ES6 modules)

- `index.html` — app shell, PWA meta, root containers.
- `js/app.js` — bootstrap, wiring, top-level state.
- `js/router.js` — reads/writes the `?url=` query param, hooks
  `popstate` for back/forward navigation.
- `js/fetcher.js` — retrieves blobs (fetch, handles CORS, errors).
- `js/crypto.js` — Web Crypto wrappers: parse header, derive key,
  decrypt, try-all-keys loop.
- `js/keychain.js` — CRUD over the `localStorage` keychain; add/remove
  keys; ordering / most-recently-successful-first optimization.
- `js/renderer.js` — content-type dispatch: HTML, Markdown (marked),
  binary download wrapper.
- `js/ui.js` — password prompt modal, keychain manager UI, status/errors.
- `js/sw-register.js` — service worker registration.
- `sw.js` — service worker: cache app shell for offline use.
- `manifest.webmanifest` — PWA manifest (name, icons, display).
- `vendor/marked.min.js` — bundled Markdown renderer.

### 6.2 Decryption Flow

1. Router resolves the target `?url=` on load / navigation.
2. Fetcher downloads the blob as an `ArrayBuffer`.
3. Crypto parses the header (magic, version, KDF params, salt, IV).
4. Try-all-keys loop:
   - For each stored key (recently-successful first):
     - Derive AES-GCM key from password + salt + iterations.
     - Attempt `decrypt`; AES-GCM tag failure → try next key.
   - On success: promote key to front of keychain; pass plaintext on.
   - On exhaustion: prompt user for a new password, add to keychain,
     retry once. Loop until success or user cancels.
5. Parse the inner header to determine content type + filename.
6. Renderer displays or downloads accordingly.

### 6.3 Rendering Rules

- **HTML:** inject into a sandboxed `<iframe srcdoc>` (no same-origin,
  controlled `sandbox` attributes) to limit script capability.
- **Markdown:** convert with `marked`, then insert into a display area.
  Consider sanitizing output (e.g. DOMPurify as a stretch goal).
- **Binary:** create an object URL from a `Blob`, present a download
  button using the inner-header filename and MIME; revoke URL after use.

### 6.4 Navigation & Linking

- Current location stored as `?url=<encoded-target>`.
- Navigating updates history via `pushState`.
- `popstate` re-triggers the fetch/decrypt/render pipeline.
- Relative links inside rendered HTML/Markdown are rewritten to route
  back through the proxy (`?url=` rebasing).

## 7. Keychain Management (UI)

- View list of stored key labels (never show raw passwords by default).
- Add key (label + password).
- Remove key.
- Reorder / mark trusted.
- Import / export keychain as JSON (stretch goal, with warning).

## 8. Node.js Encryption Script

A CLI tool (`tools/encrypt.js`) to produce publishable blobs.

- **Inputs:** source file path, password (prompt or `--password`),
  content-type override (auto-detect by extension otherwise), output path.
- **Behavior:**
  - Detect content type (html / markdown / binary).
  - Build inner payload header + content.
  - Generate a random CEK + content IV; AES-GCM encrypt the payload once.
  - For each supplied password/recipient: generate salt + wrap IV, derive
    a wrapping key via PBKDF2 (matching client), AES-GCM wrap the CEK.
  - Assemble outer header + recipient records + ciphertext; write blob.
- **CLI examples (planned):**
  - `node tools/encrypt.js input.md --out secret.ssec`
  - `node tools/encrypt.js photo.png --type binary --out img.ssec`
  - `node tools/encrypt.js note.md -p alice -p bob --out shared.ssec`
  - `node tools/decrypt.js shared.ssec -p alice --add-recipient "Dave:dpw" --out shared.ssec`
  - `node tools/decrypt.js shared.ssec -p alice --remove-recipient 0 --out shared.ssec`
- Must use the same format constants as `js/crypto.js` (share a small
  spec/constants doc to keep them in sync).

## 9. PWA Requirements

- `manifest.webmanifest` with name, short_name, icons, theme/background
  colors, `display: standalone`, start_url with no `?url=`.
- Service worker caches the app shell (HTML, JS modules, vendor lib,
  manifest, icons) for offline launch.
- Fetched _content_ blobs are **not** cached by default (may be sensitive),
  or cached only with explicit opt-in (stretch goal).
- Installable prompt handling.

## 10. Error Handling & UX

- Clear states: loading, decrypting, prompting, rendering, error.
- Distinguish network errors vs. decryption failures vs. malformed blob.
- Cancelable password prompt.
- Non-blocking status/toast messages.

## 11. Implementation Plan (Phased)

### Phase 0 — Project Scaffold

- Create directory structure, `index.html` shell, empty ES6 modules,
  bundle `marked.min.js`, add manifest + service worker skeleton.

### Phase 1 — Crypto Core (shared spec)

- Define blob format constants.
- Implement `js/crypto.js`: header parse, PBKDF2 derive, AES-GCM decrypt.
- Implement `tools/encrypt.js` to produce matching blobs.
- Verify round-trip: encrypt with Node, decrypt in browser console.

### Phase 2 — Keychain

- `js/keychain.js` localStorage CRUD + ordering.
- Try-all-keys loop with promotion of successful key.

### Phase 3 — Fetch + Render

- `js/fetcher.js` blob retrieval.
- `js/renderer.js` HTML/Markdown/binary dispatch.
- Wire pipeline end-to-end for a hardcoded URL.

### Phase 4 — Routing & Navigation

- `js/router.js` `?url=` param, pushState/popstate.
- Link rewriting for in-content navigation.

### Phase 5 — UI Polish

- Password prompt modal, keychain manager, status/error UI.

### Phase 6 — PWA

- Service worker caching of app shell, install prompt, offline test.

### Phase 7 — Hardening (Stretch)

- Optional keychain encryption with master passphrase.
- Output sanitization (DOMPurify).
- Argon2 KDF option.
- Opt-in content caching.

## 12. Testing Strategy

- **Unit:** crypto round-trip (Node encrypt ↔ browser decrypt), header
  parsing, keychain CRUD, content-type detection.
- **Integration:** full pipeline for each content type.
- **Manual:** navigation (back/forward, deep links), offline launch,
  wrong-password prompting flow.

## 13. Open Questions

- PBKDF2 iteration count vs. per-blob decrypt latency when trying many keys.
- Should content type live only in the encrypted inner header (chosen) or
  be hinted externally for UX? (Currently: inner header only.)
- How aggressively to sandbox rendered HTML while keeping it useful?
- Keychain export security warnings and format.

## 14. Directory Structure (Target)

```
experiments/static-secrets/
  index.html
  manifest.webmanifest
  sw.js
  js/
    app.js
    router.js
    fetcher.js
    crypto.js
    keychain.js
    renderer.js
    ui.js
    sw-register.js
  vendor/
    marked.min.js
  tools/
    encrypt.js
  icons/
    icon-192.png
    icon-512.png
```
