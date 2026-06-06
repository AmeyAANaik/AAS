#!/usr/bin/env node

import { readFile, writeFile } from 'node:fs/promises';
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
const RESET_GROCERY = process.env.RESET_GROCERY === '1';
const PRESERVE_OLD_ITEMS = RESET_GROCERY ? false : (process.env.PRESERVE_OLD_ITEMS ?? '1') !== '0';
const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const SNAPSHOT_PATH = path.join(SCRIPT_DIR, 'items.snapshot.json');
const ITEM_GROUP_SNAPSHOT_PATH = path.join(SCRIPT_DIR, 'item-groups.snapshot.json');
const GROCERY_GROUP = 'Grocery';
const GROCERY_CATEGORY_CODE = 'GROCERY';

function normalizeCatalogCode(code) {
  const normalized = String(code || '').trim();
  if (!normalized || normalized.startsWith('AAS-')) {
    return normalized;
  }
  return normalized.replace(/RAW_MATERIAL/g, GROCERY_CATEGORY_CODE);
}

function normalizeCatalogItemGroup(itemGroup) {
  const normalized = String(itemGroup || '').trim();
  if (!normalized || normalized === 'All Item Groups') {
    return normalized || GROCERY_GROUP;
  }
  return GROCERY_GROUP;
}

async function normalizeSnapshots() {
  const rawItems = await readFile(SNAPSHOT_PATH, 'utf8');
  const parsedItems = JSON.parse(rawItems);
  if (!Array.isArray(parsedItems)) {
    throw new Error(`Invalid item seed snapshot: ${SNAPSHOT_PATH}`);
  }

  const normalizedItems = parsedItems.map(item => {
    const currentCode = String(item?.code || '').trim();
    const currentGroup = String(item?.item_group || '').trim();
    return {
      ...item,
      code: currentCode ? normalizeCatalogCode(currentCode) : currentCode,
      item_group: normalizeCatalogItemGroup(currentGroup || GROCERY_GROUP)
    };
  });

  const rawItemGroups = await readFile(ITEM_GROUP_SNAPSHOT_PATH, 'utf8');
  const parsedItemGroups = JSON.parse(rawItemGroups);
  if (!Array.isArray(parsedItemGroups)) {
    throw new Error(`Invalid item group snapshot: ${ITEM_GROUP_SNAPSHOT_PATH}`);
  }

  const normalizedItemGroups = [
    {
      item_group_name: 'All Item Groups',
      parent_item_group: '',
      is_group: 1,
      aas_category_code: ''
    },
    {
      item_group_name: GROCERY_GROUP,
      parent_item_group: 'All Item Groups',
      is_group: 0,
      aas_category_code: GROCERY_CATEGORY_CODE
    }
  ];

  await writeFile(SNAPSHOT_PATH, JSON.stringify(normalizedItems, null, 2) + '\n', 'utf8');
  await writeFile(ITEM_GROUP_SNAPSHOT_PATH, JSON.stringify(normalizedItemGroups, null, 2) + '\n', 'utf8');
}

async function buildItems() {
  const raw = await readFile(SNAPSHOT_PATH, 'utf8');
  const parsed = JSON.parse(raw);
  if (!Array.isArray(parsed)) {
    throw new Error(`Invalid item seed snapshot: ${SNAPSHOT_PATH}`);
  }
  return parsed.map(item => ({
    code: normalizeCatalogCode(item.code),
    hsn: String(item.hsn || '').trim(),
    name: String(item.name || item.code || '').trim(),
    item_group: normalizeCatalogItemGroup(item.item_group || GROCERY_GROUP),
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

async function erpRequest(pathname, cookie, options = {}) {
  const res = await fetch(`${ERP_BASE_URL}${pathname}`, {
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
  if (res.status === 204) {
    return {};
  }
  const text = await res.text();
  return text ? JSON.parse(text) : {};
}

async function listAllItems(cookie) {
  const params = new URLSearchParams({
    fields: JSON.stringify(['name', 'item_code', 'item_group', 'disabled']),
    limit_page_length: '2000'
  });
  const data = await erpRequest(`/api/resource/Item?${params}`, cookie);
  return data.data || [];
}

async function listItemsByGroup(cookie, itemGroup) {
  const params = new URLSearchParams({
    fields: JSON.stringify(['name', 'item_code', 'item_group', 'disabled']),
    filters: JSON.stringify([['Item', 'item_group', '=', itemGroup]]),
    limit_page_length: '2000'
  });
  const data = await erpRequest(`/api/resource/Item?${params}`, cookie);
  return data.data || [];
}

async function getUom(cookie, uomName) {
  const encoded = encodeURIComponent(uomName);
  const res = await fetch(`${ERP_BASE_URL}/api/resource/UOM/${encoded}`, {
    headers: { Cookie: cookie }
  });

  if (res.status === 404 || res.status === 417) {
    return null;
  }
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`ERP request failed: ${res.status} ${text}`);
  }
  const json = await res.json();
  return json?.data || null;
}

async function createUom(cookie, uomName) {
  const payload = {
    uom_name: uomName,
    must_be_whole_number: uomName.toLowerCase() === 'nos' ? 1 : 0
  };
  try {
    await erpRequest('/api/resource/UOM', cookie, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(payload)
    });
  } catch (err) {
    throw new Error(`Failed to create UOM "${uomName}": ${err?.message || err}`);
  }
}

async function ensureUomsExist(cookie, items) {
  const required = new Set();
  for (const item of items) {
    const uom = String(item.stock_uom || '').trim();
    if (uom) {
      required.add(uom);
    }
  }

  const created = [];
  const wouldCreate = [];
  for (const uomName of [...required].sort((a, b) => a.localeCompare(b))) {
    const existing = await getUom(cookie, uomName);
    if (existing) {
      continue;
    }
    if (DRY_RUN) {
      wouldCreate.push(uomName);
      continue;
    }
    await createUom(cookie, uomName);
    created.push(uomName);
  }

  if (created.length) {
    console.log(`UOMs created: ${created.length} (${created.join(', ')})`);
  } else if (wouldCreate.length) {
    console.log(`UOMs missing (DRY_RUN=1; would create): ${wouldCreate.length} (${wouldCreate.join(', ')})`);
  } else {
    console.log('UOMs created: 0');
  }
}

async function getItemGroup(cookie, groupName) {
  const encoded = encodeURIComponent(groupName);
  const res = await fetch(`${ERP_BASE_URL}/api/resource/Item%20Group/${encoded}`, {
    headers: { Cookie: cookie }
  });

  if (res.status === 404 || res.status === 417) {
    return null;
  }
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`ERP request failed: ${res.status} ${text}`);
  }
  const json = await res.json();
  return json?.data || null;
}

async function createItemGroup(cookie, groupName, parentName) {
  const payload = {
    item_group_name: groupName,
    parent_item_group: parentName || '',
    is_group: parentName ? 0 : 1,
    aas_category_code: groupName === GROCERY_GROUP ? GROCERY_CATEGORY_CODE : ''
  };
  try {
    await erpRequest('/api/resource/Item Group', cookie, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(payload)
    });
  } catch (err) {
    throw new Error(`Failed to create Item Group "${groupName}": ${err?.message || err}`);
  }
}

async function updateItemGroup(cookie, groupName, parentName) {
  const payload = {
    item_group_name: groupName,
    parent_item_group: parentName || '',
    is_group: parentName ? 0 : 1
  };
  if (groupName === GROCERY_GROUP) {
    payload.aas_category_code = GROCERY_CATEGORY_CODE;
  }
  await erpRequest(`/api/resource/Item%20Group/${encodeURIComponent(groupName)}`, cookie, {
    method: 'PUT',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(payload)
  });
}

async function ensureItemGroupsExist(cookie, items) {
  const required = new Set();
  for (const item of items) {
    const group = String(item.item_group || '').trim();
    if (group) {
      required.add(group);
    }
  }
  required.add('All Item Groups');

  const created = [];
  const wouldCreate = [];
  const updated = [];
  const ordered = [...required].sort((a, b) => a.localeCompare(b));

  for (const groupName of ordered) {
    const parent = groupName === 'All Item Groups' ? '' : 'All Item Groups';
    const existing = await getItemGroup(cookie, groupName);
    if (existing) {
      if (!DRY_RUN && groupName === GROCERY_GROUP && existing.aas_category_code !== GROCERY_CATEGORY_CODE) {
        await updateItemGroup(cookie, groupName, parent);
        updated.push(groupName);
      }
      continue;
    }

    if (DRY_RUN) {
      wouldCreate.push(groupName);
      continue;
    }
    if (parent && !(await getItemGroup(cookie, parent))) {
      await createItemGroup(cookie, parent, '');
      created.push(parent);
    }
    await createItemGroup(cookie, groupName, parent);
    created.push(groupName);
  }

  if (created.length) {
    const uniq = [...new Set(created)];
    console.log(`Item Groups created: ${uniq.length} (${uniq.join(', ')})`);
  } else if (wouldCreate.length) {
    console.log(`Item Groups missing (DRY_RUN=1; would create): ${wouldCreate.length} (${wouldCreate.join(', ')})`);
  } else {
    console.log('Item Groups created: 0');
  }

  if (updated.length) {
    console.log(`Item Groups updated: ${updated.length} (${updated.join(', ')})`);
  }
}

async function upsertItem(cookie, payload, exists) {
  if (DRY_RUN) {
    return;
  }
  if (exists && PRESERVE_OLD_ITEMS) {
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

async function deleteItem(cookie, name) {
  if (DRY_RUN) {
    return;
  }
  await erpRequest(`/api/resource/Item/${encodeURIComponent(name)}`, cookie, {
    method: 'DELETE'
  });
}

function isLinkExistsError(message) {
  return String(message || '').includes('You can disable this Item instead of deleting it.');
}

async function resetGroceryItems(cookie) {
  const groceryItems = await listItemsByGroup(cookie, GROCERY_GROUP);
  const failures = [];
  let deleted = 0;
  let disabled = 0;

  console.log(`Grocery items found before reset: ${groceryItems.length}`);

  for (const row of groceryItems) {
    const itemCode = String(row.name || row.item_code || '').trim();
    if (!itemCode) {
      continue;
    }
    if (DRY_RUN) {
      console.log(`[DRY_RUN] Would reset Grocery item: ${itemCode}`);
      continue;
    }
    try {
      await deleteItem(cookie, itemCode);
      deleted += 1;
    } catch (err) {
      const message = err?.message || String(err);
      if (isLinkExistsError(message)) {
        try {
          await disableItem(cookie, itemCode);
          disabled += 1;
        } catch (disableErr) {
          failures.push(`${itemCode}: ${disableErr?.message || disableErr}`);
        }
      } else {
        failures.push(`${itemCode}: ${message}`);
      }
    }
  }

  if (DRY_RUN) {
    console.log('Grocery items deleted: 0');
    console.log('Grocery items disabled: 0');
    console.log('Grocery item reset failures: 0');
    return;
  }

  console.log(`Grocery items deleted: ${deleted}`);
  console.log(`Grocery items disabled: ${disabled}`);
  if (failures.length) {
    console.log(`Grocery item reset failures (${failures.length}):`);
    for (const failure of failures) {
      console.log(`- ${failure}`);
    }
  } else {
    console.log('Grocery item reset failures: 0');
  }
}

async function run() {
  await normalizeSnapshots();

  const cookie = await loginErp();
  const items = await buildItems();
  const targetCodes = new Set(items.map(item => item.code));

  if (RESET_GROCERY) {
    await resetGroceryItems(cookie);
  }

  await ensureUomsExist(cookie, items);
  await ensureItemGroupsExist(cookie, items);

  const existingBefore = await listAllItems(cookie);
  const existingNames = new Set(existingBefore.map(row => row.name));
  const plannedDisable = !PRESERVE_OLD_ITEMS
    ? existingBefore.filter(row => !targetCodes.has(row.name) && row.disabled !== 1).length
    : 0;

  let created = 0;
  let updated = 0;
  let skipped = 0;
  const upsertFailures = [];
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
    try {
      await upsertItem(cookie, payload, exists);
      if (exists) {
        if (PRESERVE_OLD_ITEMS) {
          skipped += 1;
        } else {
          updated += 1;
        }
      } else {
        created += 1;
      }
    } catch (err) {
      upsertFailures.push(`${item.code}: ${err?.message || err}`);
    }
  }

  if (DRY_RUN) {
    const activeTargets = items.filter(item => item.disabled !== 1);
    const activeGroceryTargets = activeTargets.filter(item => item.item_group === GROCERY_GROUP);
    console.log(`Items disabled: ${plannedDisable}`);
    console.log(`Items created: ${created}`);
    console.log(`Items updated: ${updated}`);
    if (PRESERVE_OLD_ITEMS) {
      console.log(`Items skipped (existing): ${skipped}`);
    }
    console.log(`Active items from list: ${activeTargets.length}`);
    console.log(`Final Grocery items total: ${activeGroceryTargets.length}`);
    console.log(`Final active Grocery items: ${activeGroceryTargets.length}`);
    if (upsertFailures.length) {
      console.log(`Item upsert failures (${upsertFailures.length}):`);
      for (const failure of upsertFailures) {
        console.log(`- ${failure}`);
      }
    } else {
      console.log('Item upsert failures: 0');
    }
    return;
  }

  const finalItems = await listAllItems(cookie);

  let disabled = 0;
  if (!PRESERVE_OLD_ITEMS) {
    for (const row of finalItems) {
      if (!targetCodes.has(row.name) && row.disabled !== 1) {
        await disableItem(cookie, row.name);
        disabled += 1;
      }
    }
  }

  const refreshed = await listAllItems(cookie);
  const active = refreshed.filter(row => row.disabled !== 1 && targetCodes.has(row.name));
  const finalGroceryItems = refreshed.filter(row => row.item_group === GROCERY_GROUP);
  const finalActiveGroceryItems = finalGroceryItems.filter(row => row.disabled !== 1);
  const missing = items.filter(item => !active.some(row => row.name === item.code));

  console.log(`Items disabled: ${disabled}`);
  console.log(`Items created: ${created}`);
  console.log(`Items updated: ${updated}`);
  if (PRESERVE_OLD_ITEMS) {
    console.log(`Items skipped (existing): ${skipped}`);
  }
  console.log(`Active items from list: ${active.length}`);
  console.log(`Final Grocery items total: ${finalGroceryItems.length}`);
  console.log(`Final active Grocery items: ${finalActiveGroceryItems.length}`);
  if (upsertFailures.length) {
    console.log(`Item upsert failures (${upsertFailures.length}):`);
    for (const failure of upsertFailures) {
      console.log(`- ${failure}`);
    }
  } else {
    console.log('Item upsert failures: 0');
  }
  if (missing.length) {
    console.log('Missing items:', missing.map(item => item.code).join(', '));
  }
}

console.log(`MW base URL: ${MW_BASE_URL}`);
console.log(`ERP base URL: ${ERP_BASE_URL}`);
console.log(`RESET_GROCERY=${RESET_GROCERY ? '1' : '0'}`);
console.log(`DRY_RUN=${DRY_RUN ? '1' : '0'}`);
console.log(`PRESERVE_OLD_ITEMS=${PRESERVE_OLD_ITEMS ? '1' : '0'}`);

run().catch(err => {
  console.error(err);
  process.exit(1);
});
