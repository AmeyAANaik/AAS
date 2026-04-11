#!/usr/bin/env node

import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const DEFAULT_MW_BASE_URL = 'http://localhost:8083';
const DEFAULT_ERP_BASE_URL = 'http://localhost:8080';
const MW_BASE_URL = (process.env.MW_BASE_URL || DEFAULT_MW_BASE_URL).replace(/\/$/, '');
const ERP_BASE_URL = (process.env.ERP_BASE_URL || process.env.ERPNEXT_BASE_URL || DEFAULT_ERP_BASE_URL).replace(/\/$/, '');
const USERNAME = process.env.MW_USERNAME || process.env.ERP_USERNAME || 'Administrator';
const PASSWORD = process.env.MW_PASSWORD || process.env.ERP_PASSWORD || 'admin';
const DEFAULT_MARGIN = Number(process.env.DEFAULT_MARGIN ?? 7);
const DRY_RUN = process.env.DRY_RUN === '1';
const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const SNAPSHOT_PATH = path.join(SCRIPT_DIR, 'items.snapshot.json');

async function buildItems() {
  const raw = await readFile(SNAPSHOT_PATH, 'utf8');
  const parsed = JSON.parse(raw);
  if (!Array.isArray(parsed)) {
    throw new Error(`Invalid item seed snapshot: ${SNAPSHOT_PATH}`);
  }
  return parsed.map(item => ({
    code: String(item.code || '').trim(),
    hsn: String(item.hsn || '').trim(),
    name: String(item.name || item.code || '').trim(),
    item_group: String(item.item_group || 'Raw Material').trim(),
    stock_uom: String(item.stock_uom || 'Nos').trim(),
    margin_percent: Number.isFinite(Number(item.margin_percent)) ? Number(item.margin_percent) : DEFAULT_MARGIN,
    vendor_rate: Number.isFinite(Number(item.vendor_rate)) ? Number(item.vendor_rate) : 0,
    gst_percent: Number.isFinite(Number(item.gst_percent)) ? Number(item.gst_percent) : 0,
    vendor: String(item.vendor || '').trim(),
    disabled: Number(item.disabled) === 1 ? 1 : 0
  })).filter(item => item.code && item.name);
}

function pickSessionCookie(response) {
  const raw = response.headers.get('set-cookie') || '';
  const match = raw.match(/sid=[^;]+/);
  return match ? match[0] : '';
}

async function loginErp() {
  const form = new URLSearchParams({ usr: USERNAME, pwd: PASSWORD });
  const res = await fetch(`${ERP_BASE_URL}/api/method/login`, {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: form
  });
  if (!res.ok) {
    throw new Error(`ERP login failed: ${res.status}`);
  }
  const cookie = pickSessionCookie(res);
  if (!cookie) {
    throw new Error('ERP login did not return a session cookie.');
  }
  return cookie;
}

async function erpRequest(path, cookie, options = {}) {
  const res = await fetch(`${ERP_BASE_URL}${path}`, {
    ...options,
    headers: {
      ...(options.headers || {}),
      Cookie: cookie
    }
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`ERP request failed: ${res.status} ${text}`);
  }
  return res.json();
}

async function listAllItems(cookie) {
  const params = new URLSearchParams({
    fields: JSON.stringify(['name', 'item_code', 'disabled']),
    limit_page_length: '2000'
  });
  const data = await erpRequest(`/api/resource/Item?${params}`, cookie);
  return data.data || [];
}

async function upsertItem(cookie, payload, exists) {
  if (DRY_RUN) {
    return;
  }
  if (exists) {
    await erpRequest(`/api/resource/Item/${encodeURIComponent(payload.item_code)}`, cookie, {
      method: 'PUT',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(payload)
    });
    return;
  }
  await erpRequest('/api/resource/Item', cookie, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(payload)
  });
}

async function disableItem(cookie, name) {
  if (DRY_RUN) {
    return;
  }
  await erpRequest(`/api/resource/Item/${encodeURIComponent(name)}`, cookie, {
    method: 'PUT',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ disabled: 1 })
  });
}

async function run() {
  const cookie = await loginErp();
  const items = await buildItems();
  const targetCodes = new Set(items.map(item => item.code));
  const existing = await listAllItems(cookie);
  const existingNames = new Set(existing.map(row => row.name));

  let disabled = 0;
  for (const row of existing) {
    if (!targetCodes.has(row.name) && row.disabled !== 1) {
      await disableItem(cookie, row.name);
      disabled += 1;
    }
  }

  let created = 0;
  let updated = 0;
  for (const item of items) {
    const exists = existingNames.has(item.code);
    const payload = {
      item_code: item.code,
      item_name: item.name,
      item_group: item.item_group,
      stock_uom: item.stock_uom,
      is_stock_item: 1,
      disabled: item.disabled,
      aas_margin_percent: item.margin_percent,
      aas_vendor_rate: item.vendor_rate,
      aas_gst_percent: item.gst_percent
    };
    if (item.hsn) {
      payload.gst_hsn_code = item.hsn;
      payload.aas_vendor_hsn_code = item.hsn;
    }
    if (item.vendor) {
      payload.aas_vendor = item.vendor;
    }
    await upsertItem(cookie, payload, exists);
    if (exists) {
      updated += 1;
    } else {
      created += 1;
    }
  }

  const finalItems = await listAllItems(cookie);
  const active = finalItems.filter(row => row.disabled !== 1 && targetCodes.has(row.name));
  const missing = items.filter(item => !active.some(row => row.name === item.code));

  console.log(`Items disabled: ${disabled}`);
  console.log(`Items created: ${created}`);
  console.log(`Items updated: ${updated}`);
  console.log(`Active items from list: ${active.length}`);
  if (missing.length) {
    console.log('Missing items:', missing.map(item => item.code).join(', '));
  }
}

run().catch(err => {
  console.error(err);
  process.exit(1);
});
