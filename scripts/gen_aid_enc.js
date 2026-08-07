#!/usr/bin/env node
/**
 * Generate a per-ANDROID_ID encrypted authorization file (<android_id>.enc).
 *
 * One file per authorized device, hosted on GitHub Pages. The Android app
 * (RemoteAuthorizer) reads this device's ANDROID_ID, fetches
 * "<base><android_id>.enc", decrypts it locally with the shared key, and
 * compares the plaintext to its own ANDROID_ID. To authorize a device, publish
 * its <android_id>.enc; to revoke it, delete the file from the Pages repo.
 *
 * Each file holds a SINGLE android_id, AES-256-GCM encrypted, wire format:
 *   [12-byte IV][ciphertext][16-byte GCM tag].
 *
 * Key source of truth: nspace.aid_remote.key in local.properties (gitignored).
 * The same key also decrypts the legacy /vin per-VIN files.
 *
 * Usage:
 *   node scripts/gen_aid_enc.js [ANDROID_ID] [OUTPUT_DIR]
 *     ANDROID_ID – the device's ANDROID_ID (required)
 *     OUTPUT_DIR – directory to write "<android_id>.enc"
 *                   (default: D:/Project/homepage/cyaispace/aid)
 *
 * Examples:
 *   node scripts/gen_aid_enc.js 9774d56d682e549c
 *   node scripts/gen_aid_enc.js 9774d56d682e549c Z:/other/aid
 */
'use strict';
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const ROOT = path.resolve(__dirname, '..');
const LOCAL_PROPS = path.join(ROOT, 'local.properties');
const DEFAULT_DIR = 'D:/Project/homepage/cyaispace/aid';

function readLocalProp(key) {
  const text = fs.readFileSync(LOCAL_PROPS, 'utf8');
  const m = text.match(new RegExp(key + '\\s*=\\s*([0-9a-fA-F]+)'));
  return m ? m[1] : null;
}

function main() {
  const aidArg = process.argv[2];
  const dirArg = process.argv[3];

  const hexKey = readLocalProp('nspace.aid_remote.key');
  if (!hexKey || hexKey.length !== 64) {
    console.error('ERROR: nspace.aid_remote.key (64 hex chars) missing in local.properties');
    process.exit(1);
  }
  const key = Buffer.from(hexKey, 'hex');

  if (!aidArg) {
    console.error('ERROR: ANDROID_ID argument required, e.g. node scripts/gen_aid_enc.js <android_id>');
    process.exit(1);
  }
  const aid = aidArg;

  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
  const ct = Buffer.concat([cipher.update(Buffer.from(aid, 'utf8')), cipher.final()]);
  const tag = cipher.getAuthTag();
  const out = Buffer.concat([iv, ct, tag]);

  const dir = path.resolve(dirArg || DEFAULT_DIR);
  fs.mkdirSync(dir, { recursive: true });
  const outPath = path.join(dir, aid + '.enc');
  fs.writeFileSync(outPath, out);

  console.log(`Wrote ${aid}.enc (${out.length} bytes) for ANDROID_ID=${aid}`);
  console.log(`  -> ${outPath}`);
}

main();
