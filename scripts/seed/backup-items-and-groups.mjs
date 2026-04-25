#!/usr/bin/env node

import { copyFile, mkdir, rename, stat, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const DEFAULT_ERP_BASE_URL = 'http://localhost:8080';
const ERP_BASE_URL = (process.env.ERP_BASE_URL || process.env.ERPNEXT_BASE_URL || DEFAULT_ERP_BASE_URL).replace(/\/$/, '');
const USERNAME = process.env.ERP_USERNAME || process.env.MW_USERNAME || process.env.ERP_USER || 'Administrator';
const PASSWORD = process.env.ERP_PASSWORD || process.env.MW_PASSWORD || 'admin';
const PAGE_SIZE = Number(process.env.PAGE_SIZE || 500);

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const ITEMS_SNAPSHOT_PATH = path.join(SCRIPT_DIR, 'items.snapshot.json');
const GROUPS_SNAPSHOT_PATH = path.join(SCRIPT_DIR, 'item-groups.snapshot.json');

function isoTimestamp() {
  return new Date().toISOString().replace(/[:.]/g, '-');
}

async function fileExists(filePath) {
  try {
    const info = await stat(filePath);
    return info.isFile();
  } catch {
    return false;
  }
}

async function writeJsonAtomic(targetPath, value) {
  const tmpPath = `${targetPath}.tmp-${process.pid}-${Date.now()}`;
  await writeFile(tmpPath, JSON.stringify(value, null, 2) + '\n', 'utf8');
  await rename(tmpPath, targetPath);
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

async function erpJson(pathname, cookie) {
  const res = await fetch(`${ERP_BASE_URL}${pathname}`, { headers: { Cookie: cookie } });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`ERP request failed: ${res.status} ${text}`);
  }
  return res.json();
}

async function listAll(doctype, fields, cookie) {
  const rows = [];
  let start = 0;
  while (true) {
    const params = new URLSearchParams({
      fields: JSON.stringify(fields),
      limit_page_length: String(PAGE_SIZE),
      limit_start: String(start)
    });
    const data = await erpJson(`/api/resource/${encodeURIComponent(doctype)}?${params}`, cookie);
    const page = data.data || [];
    rows.push(...page);
    if (page.length < PAGE_SIZE) {
      break;
    }
    start += PAGE_SIZE;
  }
  return rows;
}

async function getResource(doctype, name, fields, cookie) {
  const params = new URLSearchParams({ fields: JSON.stringify(fields) });
  const encoded = encodeURIComponent(name);
  const data = await erpJson(`/api/resource/${encodeURIComponent(doctype)}/${encoded}?${params}`, cookie);
  return data.data || null;
}

function toNumber(value, fallback = 0) {
  if (value === null || value === undefined) {
    return fallback;
  }
  const num = Number(value);
  return Number.isFinite(num) ? num : fallback;
}

function normalizeDisabled(value) {
  if (value === 1 || value === '1' || value === true || value === 'true') {
    return 1;
  }
  return 0;
}

function pickHsn(item) {
  const preferred = String(item.aas_vendor_hsn_code || '').trim();
  if (preferred) {
    return preferred;
  }
  return String(item.gst_hsn_code || '').trim();
}

async function backupExistingSnapshots() {
  const ts = isoTimestamp();
  const backupDir = path.join(SCRIPT_DIR, '../../temp/seed-backups', ts);
  await mkdir(backupDir, { recursive: true });

  const archived = [];
  for (const source of [ITEMS_SNAPSHOT_PATH, GROUPS_SNAPSHOT_PATH]) {
    if (!(await fileExists(source))) {
      continue;
    }
    const target = path.join(backupDir, path.basename(source));
    await copyFile(source, target);
    archived.push({ source, target });
  }
  return { backupDir, archived };
}

async function mapWithConcurrency(values, concurrency, mapper) {
  const results = new Array(values.length);
  let index = 0;
  const workers = new Array(Math.max(1, concurrency)).fill(null).map(async () => {
    while (true) {
      const current = index;
      index += 1;
      if (current >= values.length) {
        break;
      }
      results[current] = await mapper(values[current], current);
    }
  });
  await Promise.all(workers);
  return results;
}

async function exportItems(cookie) {
  const concurrency = Math.max(1, Number(process.env.CONCURRENCY || 8));

  const listFields = ['name', 'item_code', 'item_name', 'item_group', 'stock_uom', 'disabled'];
  const listed = await listAll('Item', listFields, cookie);
  const names = listed
    .map(row => String(row.name || row.item_code || '').trim())
    .filter(Boolean);

  const getFields = [
    'name',
    'item_code',
    'item_name',
    'item_group',
    'stock_uom',
    'disabled',
    'gst_hsn_code',
    'aas_vendor_hsn_code',
    'aas_margin_percent',
    'aas_vendor_rate',
    'aas_gst_percent',
    'aas_vendor',
    'aas_packaging_unit'
  ];

  const fetched = await mapWithConcurrency(names, concurrency, async name => {
    try {
      return await getResource('Item', name, getFields, cookie);
    } catch {
      return null;
    }
  });

  const snapshot = fetched
    .filter(Boolean)
    .map(row => {
      const code = String(row.item_code || row.name || '').trim();
      if (!code) {
        return null;
      }
      const name = String(row.item_name || row.item_code || row.name || '').trim();
      const itemGroup = String(row.item_group || 'Raw Material').trim();
      const stockUom = String(row.stock_uom || 'Nos').trim();
      const hsn = pickHsn(row);
      const vendor = String(row.aas_vendor || '').trim();
      const packagingUnit = String(row.aas_packaging_unit || '').trim();

      const result = {
        code,
        name,
        item_group: itemGroup,
        stock_uom: stockUom,
        hsn,
        margin_percent: toNumber(row.aas_margin_percent, 0),
        vendor_rate: toNumber(row.aas_vendor_rate, 0),
        gst_percent: toNumber(row.aas_gst_percent, 0),
        vendor,
        disabled: normalizeDisabled(row.disabled)
      };
      if (packagingUnit) {
        result.packaging_unit = packagingUnit;
      }
      return result;
    })
    .filter(Boolean)
    .sort((a, b) => a.code.localeCompare(b.code));

  await writeJsonAtomic(ITEMS_SNAPSHOT_PATH, snapshot);
  return snapshot;
}

async function exportItemGroups(itemsSnapshot, cookie) {
  const required = new Set();
  for (const item of itemsSnapshot) {
    if (item.item_group) {
      required.add(item.item_group);
    }
  }
  required.add('All Item Groups');

  const fields = ['name', 'item_group_name', 'parent_item_group', 'is_group', 'aas_category_code'];
  const byName = new Map();
  const queue = [...required];
  while (queue.length) {
    const name = queue.shift();
    if (!name || byName.has(name)) {
      continue;
    }
    let record = null;
    try {
      record = await getResource('Item Group', name, fields, cookie);
    } catch {
      record = null;
    }
    if (!record) {
      continue;
    }
    const groupName = String(record.name || record.item_group_name || name).trim();
    const parent = String(record.parent_item_group || '').trim();
    byName.set(groupName, {
      item_group_name: String(record.item_group_name || record.name || groupName).trim(),
      parent_item_group: parent,
      is_group: normalizeDisabled(record.is_group) ? 1 : toNumber(record.is_group, 0),
      aas_category_code: String(record.aas_category_code || '').trim()
    });
    if (parent && !byName.has(parent)) {
      queue.push(parent);
    }
  }

  const ordered = [];
  const remaining = new Set(byName.keys());
  while (remaining.size) {
    const ready = [...remaining].filter(name => {
      const parent = byName.get(name)?.parent_item_group || '';
      return !parent || !remaining.has(parent);
    });
    if (!ready.length) {
      // Cycle or missing parent: fall back to stable lexical order.
      ready.push(...[...remaining].sort((a, b) => a.localeCompare(b)));
    } else {
      ready.sort((a, b) => a.localeCompare(b));
    }
    for (const name of ready) {
      remaining.delete(name);
      ordered.push(byName.get(name));
    }
  }

  await writeJsonAtomic(GROUPS_SNAPSHOT_PATH, ordered);
  return ordered;
}

async function run() {
  const cookie = await loginErp();
  const { backupDir, archived } = await backupExistingSnapshots();
  const itemsSnapshot = await exportItems(cookie);
  const groupsSnapshot = await exportItemGroups(itemsSnapshot, cookie);

  console.log(`Backed up snapshots to: ${backupDir}`);
  if (archived.length) {
    for (const entry of archived) {
      console.log(`Archived: ${entry.source} -> ${entry.target}`);
    }
  } else {
    console.log('No existing snapshots to archive.');
  }
  console.log(`Wrote: ${ITEMS_SNAPSHOT_PATH} (${itemsSnapshot.length} items)`);
  console.log(`Wrote: ${GROUPS_SNAPSHOT_PATH} (${groupsSnapshot.length} item groups)`);
}

run().catch(err => {
  console.error(err);
  process.exit(1);
});
