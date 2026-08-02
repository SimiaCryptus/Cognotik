// Shared blob format constants & helpers used by both the browser app
// (js/crypto.js) and the Node CLI (tools/encrypt.js).
//
// NOTE: This file must remain dependency-free so it can be imported in
// both a browser (ES module) and Node (ES module) context.
//
// Format v2 uses *envelope encryption*: the payload is encrypted with a
// random 256-bit Content Encryption Key (CEK). The CEK is then wrapped
// (encrypted) once per recipient/password in a "recipients" section, so a
// single blob can be decrypted by any of several passwords. This supports
// key rotation and multiple users.

export const MAGIC = new Uint8Array([0x53, 0x53, 0x45, 0x43]); // "SSEC"

// v1 = single-key direct encryption (legacy).
// v2 = envelope encryption with multiple wrapped-key recipients.
export const VERSION = 2;
export const VERSION_V1 = 1;

export const KDF_PBKDF2_SHA256 = 0;

export const DEFAULT_ITERATIONS = 200000;

export const SALT_LEN = 16;
export const IV_LEN = 12;
export const TAG_LEN = 16;
export const CEK_LEN = 32; // 256-bit content encryption key

// v1 outer header layout (bytes) — kept for backward-compat parsing:
//   Magic(4) Version(1) KdfId(1) Iterations(4 BE) Salt(16) IV(12)
export const V1_OUTER_HEADER_LEN = 4 + 1 + 1 + 4 + SALT_LEN + IV_LEN; // = 38

// v2 outer (fixed) header layout (bytes):
//   Magic(4) Version(1) KdfId(1) Iterations(4 BE)
//   RecipientCount(2 BE) ContentIV(12)
export const V2_FIXED_HEADER_LEN = 4 + 1 + 1 + 4 + 2 + IV_LEN; // = 24

// Each recipient record (fixed size, v2):
//   Salt(16) WrapIV(12) WrappedKey(CEK_LEN + TAG_LEN = 48)
export const WRAPPED_KEY_LEN = CEK_LEN + TAG_LEN; // 48
export const RECIPIENT_LEN = SALT_LEN + IV_LEN + WRAPPED_KEY_LEN; // = 76

// Inner (plaintext) content-type enum.
export const CONTENT_TYPE = {
  html: 0,
  markdown: 1,
  binary: 2,
};

export const CONTENT_TYPE_NAMES = {
  0: 'html',
  1: 'markdown',
  2: 'binary',
};

// ---------------------------------------------------------------------------
// v2 outer header (fixed portion + recipients)
// ---------------------------------------------------------------------------

// Build the v2 outer header (fixed part only, without recipients/ciphertext).
export function buildOuterHeader({ kdfId, iterations, recipientCount, contentIv }) {
  const buf = new Uint8Array(V2_FIXED_HEADER_LEN);
  const view = new DataView(buf.buffer);
  let off = 0;
  buf.set(MAGIC, off);
  off += 4;
  buf[off++] = VERSION;
  buf[off++] = kdfId;
  view.setUint32(off, iterations, false);
  off += 4;
  view.setUint16(off, recipientCount, false);
  off += 2;
  buf.set(contentIv, off);
  off += IV_LEN;
  return buf;
}

// Build a single recipient record: salt(16) wrapIv(12) wrappedKey(48).
export function buildRecipientRecord({ salt, wrapIv, wrappedKey }) {
  const buf = new Uint8Array(RECIPIENT_LEN);
  let off = 0;
  buf.set(salt, off);
  off += SALT_LEN;
  buf.set(wrapIv, off);
  off += IV_LEN;
  if (wrappedKey.length !== WRAPPED_KEY_LEN) {
    throw new Error(`Wrapped key must be ${WRAPPED_KEY_LEN} bytes, got ${wrappedKey.length}`);
  }
  buf.set(wrappedKey, off);
  off += WRAPPED_KEY_LEN;
  return buf;
}

// Assemble a full v2 blob from parts.
export function assembleBlob({ kdfId, iterations, contentIv, recipients, ciphertext }) {
  const header = buildOuterHeader({
    kdfId,
    iterations,
    recipientCount: recipients.length,
    contentIv,
  });
  const recordBufs = recipients.map((r) => buildRecipientRecord(r));
  const totalRecipients = recordBufs.reduce((n, b) => n + b.length, 0);
  const out = new Uint8Array(header.length + totalRecipients + ciphertext.length);
  let off = 0;
  out.set(header, off);
  off += header.length;
  for (const rb of recordBufs) {
    out.set(rb, off);
    off += rb.length;
  }
  out.set(ciphertext, off);
  return out;
}

// Parse the outer header from an ArrayBuffer / Uint8Array.
// Returns a normalized structure with a `version` and (for v2) `recipients`.
export function parseOuterHeader(bytes) {
  const buf = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  if (buf.length < 6) {
    throw new Error('Blob too small to contain header');
  }
  for (let i = 0; i < 4; i++) {
    if (buf[i] !== MAGIC[i]) {
      throw new Error('Bad magic: not a Static Secrets blob');
    }
  }
  const version = buf[4];
  if (version === VERSION_V1) {
    return parseOuterHeaderV1(buf);
  }
  if (version === VERSION) {
    return parseOuterHeaderV2(buf);
  }
  throw new Error(`Unsupported blob version: ${version}`);
}

function parseOuterHeaderV1(buf) {
  if (buf.length < V1_OUTER_HEADER_LEN) {
    throw new Error('Blob too small to contain v1 header');
  }
  const view = new DataView(buf.buffer, buf.byteOffset, buf.byteLength);
  let off = 4;
  const version = buf[off++];
  const kdfId = buf[off++];
  const iterations = view.getUint32(off, false);
  off += 4;
  const salt = buf.slice(off, off + SALT_LEN);
  off += SALT_LEN;
  const iv = buf.slice(off, off + IV_LEN);
  off += IV_LEN;
  const ciphertext = buf.slice(off);
  return { version, kdfId, iterations, salt, iv, ciphertext };
}

function parseOuterHeaderV2(buf) {
  if (buf.length < V2_FIXED_HEADER_LEN) {
    throw new Error('Blob too small to contain v2 header');
  }
  const view = new DataView(buf.buffer, buf.byteOffset, buf.byteLength);
  let off = 4;
  const version = buf[off++];
  const kdfId = buf[off++];
  const iterations = view.getUint32(off, false);
  off += 4;
  const recipientCount = view.getUint16(off, false);
  off += 2;
  const contentIv = buf.slice(off, off + IV_LEN);
  off += IV_LEN;

  const needed = V2_FIXED_HEADER_LEN + recipientCount * RECIPIENT_LEN;
  if (buf.length < needed) {
    throw new Error('Blob truncated: recipients section incomplete');
  }
  const recipients = [];
  for (let i = 0; i < recipientCount; i++) {
    let r = off;
    const salt = buf.slice(r, r + SALT_LEN);
    r += SALT_LEN;
    const wrapIv = buf.slice(r, r + IV_LEN);
    r += IV_LEN;
    const wrappedKey = buf.slice(r, r + WRAPPED_KEY_LEN);
    r += WRAPPED_KEY_LEN;
    recipients.push({ salt, wrapIv, wrappedKey });
    off = r;
  }
  const ciphertext = buf.slice(off);
  return { version, kdfId, iterations, recipientCount, contentIv, recipients, ciphertext };
}

// Build the inner (plaintext) payload: type(1) filenameLen(2 BE) filename content
export function buildInnerPayload({ contentType, filename = '', content }) {
  const enc = new TextEncoder();
  const fnameBytes = enc.encode(filename);
  if (fnameBytes.length > 0xffff) {
    throw new Error('Filename too long');
  }
  const contentBytes = content instanceof Uint8Array ? content : new Uint8Array(content);
  const total = 1 + 2 + fnameBytes.length + contentBytes.length;
  const buf = new Uint8Array(total);
  const view = new DataView(buf.buffer);
  let off = 0;
  buf[off++] = contentType;
  view.setUint16(off, fnameBytes.length, false);
  off += 2;
  buf.set(fnameBytes, off);
  off += fnameBytes.length;
  buf.set(contentBytes, off);
  return buf;
}

// Parse the inner payload back into structured fields.
export function parseInnerPayload(bytes) {
  const buf = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  if (buf.length < 3) throw new Error('Inner payload too small');
  const view = new DataView(buf.buffer, buf.byteOffset, buf.byteLength);
  let off = 0;
  const contentType = buf[off++];
  const fnameLen = view.getUint16(off, false);
  off += 2;
  const fnameBytes = buf.slice(off, off + fnameLen);
  off += fnameLen;
  const filename = new TextDecoder().decode(fnameBytes);
  const content = buf.slice(off);
  return {
    contentType,
    contentTypeName: CONTENT_TYPE_NAMES[contentType] || 'binary',
    filename,
    content,
  };
}
