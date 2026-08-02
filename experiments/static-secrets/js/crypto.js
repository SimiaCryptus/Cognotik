// Browser-side Web Crypto wrappers.
import {
  parseOuterHeader,
  parseInnerPayload,
  KDF_PBKDF2_SHA256,
  VERSION_V1,
  VERSION,
  CEK_LEN,
} from './format.js';

// Derive an AES-GCM CryptoKey from a password + salt + iterations.
// Used to wrap/unwrap the Content Encryption Key (CEK).
async function deriveWrappingKey(password, salt, iterations) {
  const enc = new TextEncoder();
  const baseKey = await crypto.subtle.importKey(
    'raw',
    enc.encode(password),
    { name: 'PBKDF2' },
    false,
    ['deriveKey']
  );
  return crypto.subtle.deriveKey(
    {
      name: 'PBKDF2',
      salt,
      iterations,
      hash: 'SHA-256',
    },
    baseKey,
    { name: 'AES-GCM', length: 256 },
    false,
    ['decrypt', 'encrypt']
  );
}

// Import raw CEK bytes as an AES-GCM key for content decryption.
async function importContentKey(rawCek) {
  return crypto.subtle.importKey('raw', rawCek, { name: 'AES-GCM', length: 256 }, false, [
    'decrypt',
    'encrypt',
  ]);
}

// Attempt to unwrap the CEK for a single recipient using a password.
// Returns the raw CEK (Uint8Array) on success, or null on auth failure.
async function tryUnwrapRecipient(recipient, password, iterations) {
  const wrappingKey = await deriveWrappingKey(password, recipient.salt, iterations);
  try {
    const cek = await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv: recipient.wrapIv },
      wrappingKey,
      recipient.wrappedKey
    );
    return new Uint8Array(cek);
  } catch {
    // Tag mismatch => wrong password for this recipient slot.
    return null;
  }
}

// Decrypt the content ciphertext with a recovered CEK.
async function decryptContent(parsed, rawCek) {
  const contentKey = await importContentKey(rawCek);
  const plaintext = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: parsed.contentIv },
    contentKey,
    parsed.ciphertext
  );
  return parseInnerPayload(new Uint8Array(plaintext));
}

// Attempt to decrypt a parsed blob with a single password.
// Returns the parsed inner payload on success, or null on failure.
//
// Handles both v2 (envelope) and legacy v1 (direct) blobs.
export async function tryDecrypt(parsed, password) {
  if (parsed.kdfId !== KDF_PBKDF2_SHA256) {
    throw new Error(`Unsupported KDF id: ${parsed.kdfId}`);
  }

  if (parsed.version === VERSION_V1) {
    // Legacy: password directly derives the content key.
    const key = await deriveWrappingKey(password, parsed.salt, parsed.iterations);
    let plaintext;
    try {
      plaintext = await crypto.subtle.decrypt(
        { name: 'AES-GCM', iv: parsed.iv },
        key,
        parsed.ciphertext
      );
    } catch {
      return null;
    }
    return parseInnerPayload(new Uint8Array(plaintext));
  }

  // v2: try to unwrap the CEK from any recipient slot, then decrypt.
  for (const recipient of parsed.recipients) {
    const cek = await tryUnwrapRecipient(recipient, password, parsed.iterations);
    if (cek) {
      if (cek.length !== CEK_LEN) {
        // Defensive: unexpected CEK size.
        continue;
      }
      try {
        return await decryptContent(parsed, cek);
      } catch {
        // CEK unwrapped but content failed (corrupt blob).
        return null;
      }
    }
  }
  return null;
}

// Parse an outer blob (ArrayBuffer) into structured header + ciphertext.
export function parseBlob(arrayBuffer) {
  return parseOuterHeader(new Uint8Array(arrayBuffer));
}

// Try every key in order. Calls onProgress(index) as it goes.
// Returns { inner, password, index } on success or null on exhaustion.
export async function tryAllKeys(parsed, passwords, onProgress) {
  for (let i = 0; i < passwords.length; i++) {
    if (onProgress) onProgress(i);
    const inner = await tryDecrypt(parsed, passwords[i]);
    if (inner) {
      return { inner, password: passwords[i], index: i };
    }
  }
  return null;
}
