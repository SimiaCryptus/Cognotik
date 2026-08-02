#!/usr/bin/env node
// CLI to produce publishable .ssec blobs, matching js/crypto.js.
//
// v2 envelope format: a random Content Encryption Key (CEK) encrypts the
// payload once; the CEK is then wrapped once per password ("recipient"),
// so any of several passwords can decrypt the blob. Great for key rotation
// and multiple users.
//
// Usage:
//   node tools/encrypt.js input.md --out secret.ssec
//   node tools/encrypt.js photo.png --type binary --out img.ssec
//   node tools/encrypt.js page.html --password hunter2 --out page.ssec
//   node tools/encrypt.js note.md -p alice -p bob -p carol --out shared.ssec
//   node tools/encrypt.js note.md --recipient "Alice:alicepw" \
//         --recipient "Bob:bobpw" --out shared.ssec

import { readFile, writeFile } from 'node:fs/promises';
import { pbkdf2Sync, randomBytes, createCipheriv } from 'node:crypto';
import { basename, extname } from 'node:path';
import { createInterface } from 'node:readline';
import {
  assembleBlob,
  buildInnerPayload,
  CONTENT_TYPE,
  KDF_PBKDF2_SHA256,
  DEFAULT_ITERATIONS,
  SALT_LEN,
  IV_LEN,
  CEK_LEN,
} from '../js/format.js';

function parseArgs(argv) {
  const args = { _: [], passwords: [], recipients: [] };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--out' || a === '-o') args.out = argv[++i];
    else if (a === '--password' || a === '-p') args.passwords.push(argv[++i]);
    else if (a === '--recipient' || a === '-r') args.recipients.push(argv[++i]);
    else if (a === '--type' || a === '-t') args.type = argv[++i];
    else if (a === '--iterations') args.iterations = parseInt(argv[++i], 10);
    else if (a === '--help' || a === '-h') args.help = true;
    else args._.push(a);
  }
  return args;
}

function usage() {
  console.log(`Static Secrets encryptor (v2 envelope)

        Usage:
          node tools/encrypt.js <input> [options]

        Options:
          -o, --out <path>          Output blob path (default: <input>.ssec)
          -p, --password <pw>       Add a recipient by password (repeatable)
          -r, --recipient <lbl:pw>  Add a labeled recipient "label:password"
                                    (repeatable; label is informational only)
          -t, --type <type>         html | markdown | binary (auto-detected otherwise)
              --iterations <n>      PBKDF2 iterations (default: ${DEFAULT_ITERATIONS})
          -h, --help                Show this help

        Multiple passwords/recipients produce a blob decryptable by ANY of them,
        enabling key rotation and multi-user sharing.
        `);
}

function detectType(path) {
  const ext = extname(path).toLowerCase();
  if (ext === '.html' || ext === '.htm') return 'html';
  if (ext === '.md' || ext === '.markdown') return 'markdown';
  return 'binary';
}

function promptHidden(question) {
  return new Promise((resolve) => {
    const rl = createInterface({ input: process.stdin, output: process.stdout });
    const stdout = process.stdout;
    rl.question(question, (answer) => {
      rl.close();
      stdout.write('\n');
      resolve(answer);
    });
    rl._writeToOutput = function () {
      stdout.write('*');
    };
  });
}

// Collect the list of {label, password} recipients from CLI args, or prompt.
async function collectRecipients(args) {
  const recips = [];

  for (const pw of args.passwords) {
    if (pw) recips.push({ label: '', password: pw });
  }
  for (const spec of args.recipients) {
    if (!spec) continue;
    const idx = spec.indexOf(':');
    if (idx === -1) {
      recips.push({ label: '', password: spec });
    } else {
      recips.push({
        label: spec.slice(0, idx),
        password: spec.slice(idx + 1),
      });
    }
  }

  if (recips.length === 0) {
    const pw = await promptHidden('Password: ');
    if (pw) recips.push({ label: '', password: pw });
  }

  // De-duplicate identical passwords (they'd produce redundant slots).
  const seen = new Set();
  const unique = [];
  for (const r of recips) {
    if (!r.password || seen.has(r.password)) continue;
    seen.add(r.password);
    unique.push(r);
  }
  return unique;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help || args._.length === 0) {
    usage();
    process.exit(args.help ? 0 : 1);
  }

  const inputPath = args._[0];
  const typeName = args.type || detectType(inputPath);
  if (!(typeName in CONTENT_TYPE)) {
    console.error(`Unknown type "${typeName}". Use html | markdown | binary.`);
    process.exit(1);
  }
  const contentType = CONTENT_TYPE[typeName];
  const outPath = args.out || inputPath + '.ssec';
  const iterations = args.iterations || DEFAULT_ITERATIONS;

  const recipients = await collectRecipients(args);
  if (recipients.length === 0) {
    console.error('At least one password/recipient is required.');
    process.exit(1);
  }

  const content = await readFile(inputPath); // Buffer
  const filename = typeName === 'binary' ? basename(inputPath) : '';

  // Inner payload.
  const inner = buildInnerPayload({
    contentType,
    filename,
    content: new Uint8Array(content),
  });

  // 1. Generate a random Content Encryption Key (CEK) and encrypt payload.
  const cek = randomBytes(CEK_LEN);
  const contentIv = randomBytes(IV_LEN);
  const contentCipher = createCipheriv('aes-256-gcm', cek, contentIv);
  const contentEnc = Buffer.concat([
    contentCipher.update(Buffer.from(inner)),
    contentCipher.final(),
  ]);
  const contentTag = contentCipher.getAuthTag(); // 16 bytes
  // Web Crypto expects ciphertext || tag concatenated.
  const ciphertext = Buffer.concat([contentEnc, contentTag]);

  // 2. Wrap the CEK once per recipient using PBKDF2-derived key.
  const recipientRecords = recipients.map((r) => {
    const salt = randomBytes(SALT_LEN);
    const wrapIv = randomBytes(IV_LEN);
    const wrapKey = pbkdf2Sync(Buffer.from(r.password, 'utf8'), salt, iterations, 32, 'sha256');
    const wrapCipher = createCipheriv('aes-256-gcm', wrapKey, wrapIv);
    const wrappedBody = Buffer.concat([wrapCipher.update(cek), wrapCipher.final()]);
    const wrapTag = wrapCipher.getAuthTag(); // 16 bytes
    const wrappedKey = Buffer.concat([wrappedBody, wrapTag]); // 48 bytes
    return {
      salt: new Uint8Array(salt),
      wrapIv: new Uint8Array(wrapIv),
      wrappedKey: new Uint8Array(wrappedKey),
    };
  });

  // 3. Assemble the v2 blob.
  const blob = assembleBlob({
    kdfId: KDF_PBKDF2_SHA256,
    iterations,
    contentIv: new Uint8Array(contentIv),
    recipients: recipientRecords,
    ciphertext: new Uint8Array(ciphertext),
  });

  await writeFile(outPath, Buffer.from(blob));

  console.log(`Wrote ${blob.length} bytes to ${outPath}`);
  console.log(
    `  type=${typeName} iterations=${iterations} ` +
      `recipients=${recipients.length}` +
      (filename ? ` filename=${filename}` : '')
  );
  recipients.forEach((r, i) => {
    console.log(`    [${i}] ${r.label || '(unlabeled)'}`);
  });
}

main().catch((err) => {
  console.error('Error:', err.message);
  process.exit(1);
});
