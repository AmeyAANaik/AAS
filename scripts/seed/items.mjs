#!/usr/bin/env node

const DEFAULT_MW_BASE_URL = 'http://localhost:8083';
const DEFAULT_ERP_BASE_URL = 'http://localhost:8080';
const MW_BASE_URL = (process.env.MW_BASE_URL || DEFAULT_MW_BASE_URL).replace(/\/$/, '');
const ERP_BASE_URL = (process.env.ERP_BASE_URL || process.env.ERPNEXT_BASE_URL || DEFAULT_ERP_BASE_URL).replace(/\/$/, '');
const USERNAME = process.env.MW_USERNAME || process.env.ERP_USERNAME || 'Administrator';
const PASSWORD = process.env.MW_PASSWORD || process.env.ERP_PASSWORD || 'admin';
const DEFAULT_MARGIN = Number(process.env.DEFAULT_MARGIN ?? 7);
const DRY_RUN = process.env.DRY_RUN === '1';

const RAW_ITEMS = [
  { hsn: '11010000', name: 'SFK SAMRAT ATTA 50KG', uom: 'KG' },
  { hsn: '11010000', name: 'MAIDA SAMRAT', uom: 'KG' },
  { hsn: '10059000', name: 'SFK MAKKA ATTA', uom: 'KG' },
  { hsn: '21069099', name: 'Talod Dhokla Mix Yellow 1kg', uom: 'KG' },
  { hsn: '07139010', name: 'TOORDAL PRESIDENT', uom: 'KG' },
  { hsn: '07132000', name: 'DOUBLE BEE', uom: 'KG' },
  { hsn: '07132000', name: 'SFK CHAWLI', uom: 'KG' },
  { hsn: '07132000', name: 'SFK GREEN MOONG', uom: 'KG' },
  { hsn: '07132000', name: 'SFK RAJMA BIG', uom: 'KG' },
  { hsn: '07132000', name: 'SFK BROWN CHANA', uom: 'KG' },
  { hsn: '07132000', name: 'SFK MASOOR WHOLE', uom: 'KG' },
  { hsn: '07134000', name: 'SFK MATKI', uom: 'KG' },
  { hsn: '10063010', name: 'BASMATI RICE HEAVEN HARVEST', uom: 'KG' },
  { hsn: '10063020', name: 'STAFF RICE CHOTU', uom: 'KG' },
  { hsn: '10063010', name: 'STAFF RICE YESS BOSS', uom: 'KG' },
  { hsn: '11010000', name: 'AMBEMOHAR RICE', uom: 'KG' },
  { hsn: '17011490', name: 'SFK SUGAR', uom: 'KG' },
  { hsn: '17011310', name: 'GUD BLACK', uom: 'KG' },
  { hsn: '28276090', name: 'TATA SALT 1KG', uom: 'KG' },
  { hsn: '15121910', name: 'SFK KIRTI GOLD SUNFLOWER OIL 13kg', uom: 'LTR' },
  { hsn: '15149120', name: 'SF MAHAVIR MUSTARD OIL 1LTR', uom: 'LTR' },
  { hsn: '1101', name: 'SF TILL OIL', uom: 'LTR' },
  { hsn: '07149010', name: 'SFK SABUDANA', uom: 'KG' },
  { hsn: '12024210', name: 'SFK SHINGDANA', uom: 'KG' },
  { hsn: '11031110', name: 'SUJI RAVA (BADA)', uom: 'KG' },
  { hsn: '09103090', name: 'PRAVIN AMBARI MIRCHI POWDER', uom: 'KG' },
  { hsn: '09103090', name: 'PRAVIN AMBARI HALDI POWDER', uom: 'KG' },
  { hsn: '09103090', name: 'PRAVIN AMBARI DHANIYA POWDER', uom: 'KG' },
  { hsn: '09109100', name: 'EVEREST CHAT MASALA 500GM', uom: 'GMS' },
  { hsn: '13019013', name: 'LG HING 50GM', uom: 'GMS' },
  { hsn: '09109990', name: 'UTSAV KASTURI METHI', uom: 'KG' },
  { hsn: '09093129', name: 'SFK JEERA', uom: 'KG' },
  { hsn: '12075010', name: 'MOHARI', uom: 'KG' },
  { hsn: '09109914', name: 'OVA AJWAIN', uom: 'KG' },
  { hsn: '30049011', name: 'ENO', uom: 'PCS' },
  { hsn: '08011100', name: 'SFK KHOBRA KHIS', uom: 'KG' },
  { hsn: '29181400', name: 'SF CITRIC ACID', uom: 'KG' },
  { hsn: '250100', name: 'SFK BLACK SALT', uom: 'KG' },
  { hsn: '09042110', name: 'KASHMIRI CHILLY WHOLE', uom: 'KG' },
  { hsn: '19059030', name: 'SAMOSA PAPAD 1', uom: 'PCS' },
  { hsn: '19059030', name: 'NACHNI PAPAD', uom: 'PCS' },
  { hsn: '08041020', name: 'SFK KHAJUR SEEDLESS', uom: 'KG' },
  { hsn: '08013210', name: 'KAJUKANI BHARI', uom: 'KG' },
  { hsn: '08042090', name: 'SFK ANJEER', uom: 'KG' },
  { hsn: '12077090', name: 'SFK KALINGAD MAGAJ 1KG', uom: 'KG' },
  { hsn: '09096139', name: 'SFK BADISHEP', uom: 'KG' },
  { hsn: '17019990', name: 'SFK KHADHI SHAKAR RAVALGAON', uom: 'KG' },
  { hsn: '34029011', name: 'SF NIRMA 500GM', uom: 'GMS' },
  { hsn: '19054000', name: 'HABIT PANKO BREAD CRUMB.', uom: 'KG' },
  { hsn: '09023020', name: 'TATA TEA AGNI 1KG', uom: 'KG' },
  { hsn: '20011000', name: 'PRAVIN PICKLE TOOFAN 5KG MANGO', uom: 'KG' },
  { hsn: '21069099', name: 'SF ZERO SEV', uom: 'KG' },
  { hsn: '21069099', name: 'FARSAN', uom: 'KG' },
  { hsn: '21032000', name: 'KISSAN DIP SAUCE POUCH 930GM', uom: 'GMS' },
  { hsn: '21069011', name: 'MANAMA ROSE SYRUP 750ML', uom: 'ML' },
  { hsn: '20089919', name: 'MANAMA KALA KHATTA CORDIAL 750ML', uom: 'ML' },
  { hsn: '20089919', name: 'MANAMA KIWI CRUSH 1LTR', uom: 'LTR' },
  { hsn: '19059040', name: 'SF LIJJAT PAPAD SMALL', uom: 'PCS' },
  { hsn: '09109100', name: 'AAMCHOOR POWDER', uom: 'KG' }
];

const UNIT_REPLACEMENTS = [
  [/\b(\d+)\s*kg\b/gi, '$1 KG'],
  [/\b(\d+)\s*gm\b/gi, '$1 GM'],
  [/\b(\d+)\s*g\b/gi, '$1 G'],
  [/\b(\d+)\s*ltr\b/gi, '$1 LTR'],
  [/\b(\d+)\s*l\b/gi, '$1 L'],
  [/\b(\d+)\s*pcs\b/gi, '$1 PCS'],
  [/\b(\d+)\s*pc\b/gi, '$1 PCS'],
  [/\b(\d+)\s*tin\b/gi, '$1 TIN']
];

function normalizeName(raw) {
  let name = String(raw || '').replace(/\s+/g, ' ').trim();
  name = name.replace(/[.]+$/g, '');
  for (const [pattern, replacement] of UNIT_REPLACEMENTS) {
    name = name.replace(pattern, replacement);
  }
  name = name.replace(/(\d)(KG|GM|G|LTR|L|PCS|TIN)\b/g, '$1 $2');
  return name
    .split(' ')
    .map(token => {
      if (!token) return token;
      if (token.toUpperCase() === token && token.length <= 4) return token;
      if (/\d/.test(token)) return token.toUpperCase();
      return token.charAt(0).toUpperCase() + token.slice(1).toLowerCase();
    })
    .join(' ');
}

function deriveStockUom(name) {
  const text = String(name || '').toUpperCase();
  if (/\bKG\b/.test(text)) return 'KG';
  if (/\bGM\b/.test(text) || /\bG\b/.test(text)) return 'GMS';
  if (/\bLTR\b/.test(text) || /\bL\b/.test(text)) return 'LTR';
  if (/\bML\b/.test(text)) return 'ML';
  if (/\bPCS\b/.test(text)) return 'PCS';
  if (/\bTIN\b/.test(text)) return 'TIN';
  return 'Nos';
}

function buildItems() {
  const counts = new Map();
  return RAW_ITEMS.map(item => {
    const count = (counts.get(item.hsn) || 0) + 1;
    counts.set(item.hsn, count);
    const code = count === 1 ? item.hsn : `${item.hsn}-${count}`;
    const name = normalizeName(item.name);
    return {
      code,
      hsn: item.hsn,
      name,
      stock_uom: item.uom || deriveStockUom(name)
    };
  });
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
  const items = buildItems();
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
      item_group: 'Grocery',
      stock_uom: item.stock_uom,
      is_stock_item: 1,
      gst_hsn_code: item.hsn,
      aas_margin_percent: DEFAULT_MARGIN,
      disabled: 0
    };
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
