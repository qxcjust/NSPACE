#!/usr/bin/env node
'use strict';
// Re-encrypt the region app config into the bundled asset.
//
//   Source (plaintext, NOT shipped):  config/region_apps_config.json
//   Output (ciphertext, shipped)   :  app/src/main/assets/region_apps_config.enc
//
// Format written: [12-byte random IV][ciphertext][16-byte GCM auth tag]
// Algorithm: AES-256-GCM. The key comes from the gitignored local.properties
// (nspace.config.key) and MUST match BuildConfig.NSPACE_CONFIG_KEY injected by Gradle.
//
// Run from the NSpace project root:
//   node scripts/encrypt_config.js
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const ROOT = path.resolve(__dirname, '..');
const LOCAL = path.join(ROOT, 'local.properties');
const SRC = path.join(ROOT, 'config', 'region_apps_config.json');
const OUT = path.join(ROOT, 'app', 'src', 'main', 'assets', 'region_apps_config.enc');

function readLocalProp(key) {
  if (!fs.existsSync(LOCAL)) throw new Error('local.properties not found at ' + LOCAL);
  const text = fs.readFileSync(LOCAL, 'utf8');
  const m = text.match(new RegExp('^\\s*' + key + '\\s*=\\s*(.+?)\\s*$', 'm'));
  if (!m) throw new Error('missing ' + key + ' in local.properties');
  return m[1].trim();
}

function main() {
  const hex = readLocalProp('nspace.config.key');
  const key = Buffer.from(hex, 'hex');
  if (key.length !== 32) {
    throw new Error('nspace.config.key must be 32 bytes (64 hex chars), got ' + key.length);
  }

  const plain = fs.readFileSync(SRC); // raw UTF-8 JSON bytes
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
  const enc = Buffer.concat([cipher.update(plain), cipher.final()]);
  const tag = cipher.getAuthTag();
  const out = Buffer.concat([iv, enc, tag]); // [iv 12][ciphertext][tag 16]

  fs.mkdirSync(path.dirname(OUT), { recursive: true });
  fs.writeFileSync(OUT, out);
  console.log('Encrypted', SRC);
  console.log('  ->', OUT, '(' + out.length + ' bytes, IV+tag included)');
}

main();
