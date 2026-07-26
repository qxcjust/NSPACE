#!/usr/bin/env node
/**
 * Generate a per-VIN encrypted allowlist file (<VIN>.enc).
 *
 * One file per authorized vehicle, hosted on GitHub Pages. The Android app
 * (VinRemoteChecker) reads the live car VIN, fetches "<base><VIN>.enc",
 * decrypts it locally with the same key, and compares the plaintext to the
 * live VIN. To authorize a vehicle, publish its <VIN>.enc; to revoke it,
 * delete the file from the Pages repo.
 *
 * Each file holds a SINGLE VIN, AES-256-GCM encrypted, wire format:
 *   [12-byte IV][ciphertext][16-byte GCM tag].
 *
 * Key source of truth: nspace.vin_remote.key in local.properties (gitignored).
 * Default VIN: bound_vin from config/region_apps_config.json.
 *
 * Usage:
 *   node scripts/gen_vin_enc.js [VIN] [OUTPUT_DIR]
 *     VIN        – the authorized VIN (default: config bound_vin)
 *     OUTPUT_DIR – directory to write "<VIN>.enc"
 *                   (default: D:/Project/homepage/cyaispace/vin)
 *
 * Examples:
 *   node scripts/gen_vin_enc.js                       # bound VIN -> vin/<bound>.enc
 *   node scripts/gen_vin_enc.js HACRA1B30S1092845    # explicit VIN
 *   node scripts/gen_vin_enc.js HACRA1B30S1092845 Z:/other/vin   # custom dir
 */

'use strict';
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const ROOT = path.resolve(__dirname, '..');
const LOCAL_PROPS = path.join(ROOT, 'local.properties');
const CONFIG_JSON = path.join(ROOT, 'config', 'region_apps_config.json');
const DEFAULT_DIR = 'D:/Project/homepage/cyaispace/vin';

function readLocalProp(key) {
  const text = fs.readFileSync(LOCAL_PROPS, 'utf8');
  const m = text.match(new RegExp(key + '\\s*=\\s*([0-9a-fA-F]+)'));
  return m ? m[1] : null;
}

function readConfigBoundVin() {
  const cfg = JSON.parse(fs.readFileSync(CONFIG_JSON, 'utf8'));
  return cfg && cfg.bound_vin ? cfg.bound_vin : null;
}

function main() {
  const vinArg = process.argv[2];
  const dirArg = process.argv[3];

  const hexKey = readLocalProp('nspace.vin_remote.key');
  if (!hexKey || hexKey.length !== 64) {
    console.error('ERROR: nspace.vin_remote.key (64 hex chars) missing in local.properties');
    process.exit(1);
  }
  const key = Buffer.from(hexKey, 'hex');

  const vin = vinArg || readConfigBoundVin();
  if (!vin) {
    console.error('ERROR: no VIN given and no bound_vin in config/region_apps_config.json');
    process.exit(1);
  }

  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
  const ct = Buffer.concat([cipher.update(Buffer.from(vin, 'utf8')), cipher.final()]);
  const tag = cipher.getAuthTag();
  const out = Buffer.concat([iv, ct, tag]);

  const dir = path.resolve(dirArg || DEFAULT_DIR);
  fs.mkdirSync(dir, { recursive: true });
  const outPath = path.join(dir, vin + '.enc');
  fs.writeFileSync(outPath, out);

  console.log(`Wrote ${vin}.enc (${out.length} bytes) for VIN=${vin}`);
  console.log(`  -> ${outPath}`);
}

main();
