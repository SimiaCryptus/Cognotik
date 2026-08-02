#!/usr/bin/env node
// CLI to decrypt / inspect / manage a .ssec blob (v2 envelope format).
//
// Usage:
//   node tools/decrypt.js secret.ssec --password hunter2 [--out out.txt]
//   node tools/decrypt.js secret.ssec --info
//   node tools/decrypt.js secret.ssec -p oldpw --add-recipient "Bob:bobpw" \
//         --out secret.ssec
//   node tools/decrypt.js secret.ssec -p mypw --remove-recipient 0 \
//         --out secret.ssec
//
// Managing recipients (add/remove) requires a password that can already
// unwrap the CEK, so you can rotate/add/revoke keys without the plaintext.

import { readFile, writeFile } from 'node:fs/promises';
import { pbkdf2Sync, randomBytes, createCipheriv, createDecipheriv } from 'node:crypto';
import { createInterface } from 'node:readline';
import {
  parseOuterHeader,
  parseInnerPayload,
  assembleBlob,
  KDF_PBKDF2_SHA256,
  VERSION_V1,
  VERSION,
  SALT_LEN,
  IV_LEN,
  CEK_LEN,
  TAG_LEN,
} from '../js/format.js';

function parseArgs(argv) {
  const args = { _: [], addRecipients: [] };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--out' || a === '-o') args.out = argv[++i];
    else if (a === '--password' || a === '-p') args.password = argv[++i];
    else if (a === '--info' || a === '-i') args.info = true;
    else if (a === '--add-recipient') args.addRecipients.push(argv[++i]);
    else if (a === '--remove-recipient') {
      args.removeRecipient = parseInt(argv[++i], 10);
    } else if (a === '--iterations') args.iterations = parseInt(argv[++i], 10);
    else if (a === '--help' || a === '-h') args.help = true;
    else args._.push(a);
  }
  return args;
}

function usage() {
  console.log(`Static Secrets decryptor / manager (v2 envelope)

        Usage:
          node tools/decrypt.js <blob.ssec> [options]

        Options:
          -p, --password <pw>          Password to unwrap the content key
          -o, --out <path>             Output path (decrypted content, or the
                                       rewritten blob for add/remove operations)
          -i, --info                   Print header/recipient info and exit
              --add-recipient <l:pw>   Wrap the CEK for a new recipient "label:pw"
                                       (repeatable); requires a working --password
              --remove-recipient <n>   Remove recipient slot index n; requires a
                                       working --password (cannot remove the slot
                                       you authenticated with if it's the last)
              --iterations <n>         PBKDF2 iterations for newly added recipients
          -h, --help                   Show this help
        `);
}

function promptHidden(question) {
  return new Promise((resolve) => {
    const rl = createInterface({ input: process.stdin, output: process.stdout });
    rl.question(question, (answer) => {
      rl.close();
      process.stdout.write('\n');
      resolve(answer);
    });
    rl._writeToOutput = () => process.stdout.write('*');
  });
}

// Derive a PBKDF2 wrapping key.
function deriveWrapKey(password, salt, iterations) {
  return pbkdf2Sync(Buffer.from(password, 'utf8'), Buffer.from(salt), iterations, 32, 'sha256');
}

// Try to unwrap the CEK from a v2 recipient slot. Returns Buffer or null.
function unwrapRecipient(recipient, password, iterations) {
  const key = deriveWrapKey(password, recipient.salt, iterations);
  const wrapped = Buffer.from(recipient.wrappedKey);
  const tag = wrapped.subarray(wrapped.length - TAG_LEN);
  const body = wrapped.subarray(0, wrapped.length - TAG_LEN);
  const decipher = createDecipheriv('aes-256-gcm', key, Buffer.from(recipient.wrapIv));
  decipher.setAuthTag(tag);
  try {
    return Buffer.concat([decipher.update(body), decipher.final()]);
  } catch {
    return null;
  }
}

// Recover the CEK for a v2 blob from any recipient that matches password.
// Returns { cek, index } or null.
function recoverCek(parsed, password) {
  for (let i = 0; i < parsed.recipients.length; i++) {
    const cek = unwrapRecipient(parsed.recipients[i], password, parsed.iterations);
    if (cek && cek.length === CEK_LEN) {
      return { cek, index: i };
    }
  }
  return null;
}

// Decrypt content given a CEK (v2).
function decryptContentV2(parsed, cek) {
  const ct = Buffer.from(parsed.ciphertext);
  const tag = ct.subarray(ct.length - TAG_LEN);
  const body = ct.subarray(0, ct.length - TAG_LEN);
  const decipher = createDecipheriv('aes-256-gcm', cek, Buffer.from(parsed.contentIv));
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(body), decipher.final()]);
}

// Legacy v1: password directly decrypts the content.
function decryptV1(parsed, password) {
  const key = deriveWrapKey(password, parsed.salt, parsed.iterations);
  const ct = Buffer.from(parsed.ciphertext);
  const tag = ct.subarray(ct.length - TAG_LEN);
  const body = ct.subarray(0, ct.length - TAG_LEN);
  const decipher = createDecipheriv('aes-256-gcm', key, Buffer.from(parsed.iv));
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(body), decipher.final()]);
}

// Wrap a CEK for a new recipient, producing a recipient record.
function wrapForRecipient(cek, password, iterations) {
  const salt = randomBytes(SALT_LEN);
  const wrapIv = randomBytes(IV_LEN);
  const key = deriveWrapKey(password, salt, iterations);
  const cipher = createCipheriv('aes-256-gcm', key, wrapIv);
  const body = Buffer.concat([cipher.update(cek), cipher.final()]);
  const tag = cipher.getAuthTag();
  return {
    salt: new Uint8Array(salt),
    wrapIv: new Uint8Array(wrapIv),
    wrappedKey: new Uint8Array(Buffer.concat([body, tag])),
  };
}

function printInfo(parsed) {
  console.error(
    `version=${parsed.version} kdfId=${parsed.kdfId} ` + `iterations=${parsed.iterations}`
  );
  if (parsed.version === VERSION) {
    console.error(`recipients=${parsed.recipientCount}`);
    parsed.recipients.forEach((r, i) => {
      console.error(`  [${i}] salt=${Buffer.from(r.salt).toString('hex').slice(0, 12)}…`);
    });
    console.error(`ciphertext=${parsed.ciphertext.length} bytes`);
  } else {
    console.error('(legacy v1 single-key blob)');
    console.error(`ciphertext=${parsed.ciphertext.length} bytes`);
  }
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help || args._.length === 0) {
    usage();
    process.exit(args.help ? 0 : args._.length === 0 ? 1 : 0);
  }
  const blobPath = args._[0];
  const blob = await readFile(blobPath);
  const parsed = parseOuterHeader(new Uint8Array(blob));

  if (parsed.kdfId !== KDF_PBKDF2_SHA256) {
    console.error(`Unsupported KDF id: ${parsed.kdfId}`);
    process.exit(1);
  }

  if (args.info) {
    printInfo(parsed);
    return;
  }

  const password = args.password || (await promptHidden('Password: '));
  const isManage = args.addRecipients.length > 0 || typeof args.removeRecipient === 'number';

  // --- v1 legacy path (no recipient management supported) ---
  if (parsed.version === VERSION_V1) {
    if (isManage) {
      console.error(
        'Recipient management requires a v2 blob. ' +
          'Re-encrypt this file with the current encryptor.'
      );
      process.exit(1);
    }
    let plaintext;
    try {
      plaintext = decryptV1(parsed, password);
    } catch {
      console.error('Decryption failed: wrong password or corrupt blob.');
      process.exit(2);
    }
    await emitContent(plaintext, args);
    return;
  }

  // --- v2 path ---
  const recovered = recoverCek(parsed, password);
  if (!recovered) {
    console.error('Decryption failed: password did not unwrap any recipient.');
    process.exit(2);
  }
  const { cek, index } = recovered;
  console.error(`Unwrapped CEK via recipient slot [${index}].`);

  if (isManage) {
    await manageRecipients(parsed, cek, args);
    return;
  }

  // Normal decrypt of content.
  let plaintext;
  try {
    plaintext = decryptContentV2(parsed, cek);
  } catch {
    console.error('Content decryption failed: corrupt blob.');
    process.exit(2);
  }
  await emitContent(plaintext, args);
}

async function emitContent(plaintext, args) {
  const inner = parseInnerPayload(new Uint8Array(plaintext));
  console.error(
    `type=${inner.contentTypeName}` +
      (inner.filename ? ` filename=${inner.filename}` : '') +
      ` bytes=${inner.content.length}`
  );
  if (args.out) {
    await writeFile(args.out, Buffer.from(inner.content));
    console.error(`Wrote decrypted content to ${args.out}`);
  } else {
    process.stdout.write(Buffer.from(inner.content));
  }
}

// Add and/or remove recipients, then rewrite the blob (keeping the same CEK
// and content ciphertext — only the recipients section changes).
async function manageRecipients(parsed, cek, args) {
  const iterations = args.iterations || parsed.iterations;
  let recipients = parsed.recipients.slice();

  // Removal first (index refers to the original ordering).
  if (typeof args.removeRecipient === 'number') {
    const n = args.removeRecipient;
    if (n < 0 || n >= recipients.length) {
      console.error(`Cannot remove: no recipient at index ${n}.`);
      process.exit(1);
    }
    if (recipients.length === 1 && args.addRecipients.length === 0) {
      console.error('Refusing to remove the last recipient ' + '(blob would be undecryptable).');
      process.exit(1);
    }
    recipients.splice(n, 1);
    console.error(`Removed recipient slot [${n}].`);
  }

  // Additions.
  for (const spec of args.addRecipients) {
    if (!spec) continue;
    const idx = spec.indexOf(':');
    const label = idx === -1 ? '' : spec.slice(0, idx);
    const pw = idx === -1 ? spec : spec.slice(idx + 1);
    if (!pw) continue;
    const record = wrapForRecipient(cek, pw, iterations);
    recipients.push(record);
    console.error(`Added recipient ${label || '(unlabeled)'}.`);
  }

  if (recipients.length === 0) {
    console.error('Refusing to write a blob with zero recipients.');
    process.exit(1);
  }

  const rebuilt = assembleBlob({
    kdfId: parsed.kdfId,
    iterations: parsed.iterations,
    contentIv: parsed.contentIv,
    recipients,
    ciphertext: parsed.ciphertext,
  });

  const outPath = args.out || args._[0];
  await writeFile(outPath, Buffer.from(rebuilt));
  console.error(
    `Wrote updated blob (${rebuilt.length} bytes, ` +
      `${recipients.length} recipients) to ${outPath}`
  );
}

main().catch((err) => {
  console.error('Error:', err.message);
  process.exit(1);
});
