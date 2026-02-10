#!/usr/bin/env node

const DEFAULT_BASE_URL = 'http://localhost:8083';
const BASE_URL = (process.env.MW_BASE_URL || DEFAULT_BASE_URL).replace(/\/$/, '');
const USERNAME = process.env.MW_USERNAME || 'Administrator';
const PASSWORD = process.env.MW_PASSWORD || 'admin';
const DRY_RUN = process.env.DRY_RUN === '1';

const data = {
  vendors: [
    {
      supplier_name: 'Green Valley Farms',
      address: '12 Orchard Road, Albany, NY 12207',
      phone: '518-555-0132',
      gst: 'GST-10GVF',
      pan: 'PAN-GVF-001',
      food_license_no: 'FL-1001',
      aas_priority: 1,
      disabled: 0
    },
    {
      supplier_name: 'Sunrise Produce Co',
      address: '88 Market St, Newark, NJ 07102',
      phone: '973-555-0119',
      gst: 'GST-20SPC',
      pan: 'PAN-SPC-002',
      food_license_no: 'FL-1002',
      aas_priority: 2,
      disabled: 0
    },
    {
      supplier_name: 'Harvest Ridge',
      address: '245 County Rd, Lancaster, PA 17602',
      phone: '717-555-0168',
      gst: 'GST-30HR',
      pan: 'PAN-HR-003',
      food_license_no: 'FL-1003',
      aas_priority: 3,
      disabled: 0
    },
    {
      supplier_name: 'FreshBridge Traders',
      address: '51 Warehouse Way, Edison, NJ 08817',
      phone: '732-555-0125',
      gst: 'GST-40FBT',
      pan: 'PAN-FBT-004',
      food_license_no: 'FL-1004',
      aas_priority: 4,
      disabled: 0
    },
    {
      supplier_name: 'Lakeside Organics',
      address: '7 Bay St, Cleveland, OH 44114',
      phone: '216-555-0142',
      gst: 'GST-50LO',
      pan: 'PAN-LO-005',
      food_license_no: 'FL-1005',
      aas_priority: 5,
      disabled: 0
    },
    {
      supplier_name: 'Blue Meadow Dairy',
      address: '403 Meadow Ln, Madison, WI 53703',
      phone: '608-555-0194',
      gst: 'GST-60BMD',
      pan: 'PAN-BMD-006',
      food_license_no: 'FL-1006',
      aas_priority: 6,
      disabled: 0
    },
    {
      supplier_name: 'Riverstone Grains',
      address: '91 Mill St, Des Moines, IA 50309',
      phone: '515-555-0177',
      gst: 'GST-70RG',
      pan: 'PAN-RG-007',
      food_license_no: 'FL-1007',
      aas_priority: 7,
      disabled: 0
    },
    {
      supplier_name: 'Orchard Hill Fruits',
      address: '19 Ridge Rd, Portland, ME 04101',
      phone: '207-555-0139',
      gst: 'GST-80OHF',
      pan: 'PAN-OHF-008',
      food_license_no: 'FL-1008',
      aas_priority: 8,
      disabled: 0
    },
    {
      supplier_name: 'Prairie Roots Produce',
      address: '210 Field Ave, Fargo, ND 58102',
      phone: '701-555-0128',
      gst: 'GST-90PRP',
      pan: 'PAN-PRP-009',
      food_license_no: 'FL-1009',
      aas_priority: 9,
      disabled: 0
    }
  ],
  branches: [
    {
      customer_name: 'Downtown Market Branch',
      address: '101 Main St, Albany, NY 12207',
      aas_whatsapp_group_name: 'AAS-Downtown-Market'
    },
    {
      customer_name: 'Uptown Bistro Branch',
      address: '255 State St, Albany, NY 12207',
      aas_whatsapp_group_name: 'AAS-Uptown-Bistro'
    },
    {
      customer_name: 'Riverfront Cafe Branch',
      address: '14 River Rd, Troy, NY 12180',
      aas_whatsapp_group_name: 'AAS-Riverfront-Cafe'
    },
    {
      customer_name: 'Hillview Restaurant Branch',
      address: '770 Hillview Dr, Schenectady, NY 12305',
      aas_whatsapp_group_name: 'AAS-Hillview-Restaurant'
    },
    {
      customer_name: 'Airport Lounge Branch',
      address: '1 Terminal Way, Albany, NY 12211',
      aas_whatsapp_group_name: 'AAS-Airport-Lounge'
    },
    {
      customer_name: 'Lakeside Grill Branch',
      address: '560 Lakeside Ave, Cleveland, OH 44114',
      aas_whatsapp_group_name: 'AAS-Lakeside-Grill'
    },
    {
      customer_name: 'Capitol Deli Branch',
      address: '77 Capitol Ave, Madison, WI 53703',
      aas_whatsapp_group_name: 'AAS-Capitol-Deli'
    }
  ],
  categories: [
    { item_group_name: 'Leafy Greens' },
    { item_group_name: 'Root Vegetables' },
    { item_group_name: 'Fruits' },
    { item_group_name: 'Grains & Pulses' },
    { item_group_name: 'Dairy & Eggs' }
  ],
  items: [
    {
      item_code: 'ITM-LETTUCE',
      item_name: 'Romaine Lettuce',
      item_group: 'Leafy Greens',
      stock_uom: 'Nos',
      aas_packaging_unit: 'Crate',
      aas_vendor_rate: 24,
      aas_margin_percent: 12
    },
    {
      item_code: 'ITM-SPINACH',
      item_name: 'Baby Spinach',
      item_group: 'Leafy Greens',
      stock_uom: 'Nos',
      aas_packaging_unit: 'Box',
      aas_vendor_rate: 18,
      aas_margin_percent: 10
    },
    {
      item_code: 'ITM-CARROT',
      item_name: 'Carrot Bunch',
      item_group: 'Root Vegetables',
      stock_uom: 'Nos',
      aas_packaging_unit: 'Bag',
      aas_vendor_rate: 15,
      aas_margin_percent: 8
    },
    {
      item_code: 'ITM-POTATO',
      item_name: 'Yukon Potato',
      item_group: 'Root Vegetables',
      stock_uom: 'Nos',
      aas_packaging_unit: 'Sack',
      aas_vendor_rate: 12,
      aas_margin_percent: 6
    },
    {
      item_code: 'ITM-APPLE',
      item_name: 'Honeycrisp Apple',
      item_group: 'Fruits',
      stock_uom: 'Nos',
      aas_packaging_unit: 'Crate',
      aas_vendor_rate: 30,
      aas_margin_percent: 15
    },
    {
      item_code: 'ITM-BANANA',
      item_name: 'Cavendish Banana',
      item_group: 'Fruits',
      stock_uom: 'Nos',
      aas_packaging_unit: 'Bunch',
      aas_vendor_rate: 14,
      aas_margin_percent: 10
    },
    {
      item_code: 'ITM-RICE',
      item_name: 'Basmati Rice',
      item_group: 'Grains & Pulses',
      stock_uom: 'Nos',
      aas_packaging_unit: 'Bag',
      aas_vendor_rate: 55,
      aas_margin_percent: 9
    },
    {
      item_code: 'ITM-LENTIL',
      item_name: 'Red Lentil',
      item_group: 'Grains & Pulses',
      stock_uom: 'Nos',
      aas_packaging_unit: 'Bag',
      aas_vendor_rate: 48,
      aas_margin_percent: 7
    },
    {
      item_code: 'ITM-EGGS',
      item_name: 'Free Range Eggs',
      item_group: 'Dairy & Eggs',
      stock_uom: 'Nos',
      aas_packaging_unit: 'Tray',
      aas_vendor_rate: 36,
      aas_margin_percent: 11
    },
    {
      item_code: 'ITM-MILK',
      item_name: 'Whole Milk 1L',
      item_group: 'Dairy & Eggs',
      stock_uom: 'Nos',
      aas_packaging_unit: 'Carton',
      aas_vendor_rate: 22,
      aas_margin_percent: 8
    },
    {
      item_code: 'ITM-KALE',
      item_name: 'Curly Kale',
      item_group: 'Leafy Greens',
      stock_uom: 'Nos',
      aas_packaging_unit: 'Box',
      aas_vendor_rate: 20,
      aas_margin_percent: 9
    },
    {
      item_code: 'ITM-BEET',
      item_name: 'Beetroot',
      item_group: 'Root Vegetables',
      stock_uom: 'Nos',
      aas_packaging_unit: 'Bag',
      aas_vendor_rate: 16,
      aas_margin_percent: 7
    },
    {
      item_code: 'ITM-ORANGE',
      item_name: 'Navel Orange',
      item_group: 'Fruits',
      stock_uom: 'Nos',
      aas_packaging_unit: 'Crate',
      aas_vendor_rate: 28,
      aas_margin_percent: 12
    },
    {
      item_code: 'ITM-CHICKPEA',
      item_name: 'Chickpeas',
      item_group: 'Grains & Pulses',
      stock_uom: 'Nos',
      aas_packaging_unit: 'Bag',
      aas_vendor_rate: 44,
      aas_margin_percent: 6
    },
    {
      item_code: 'ITM-YOGURT',
      item_name: 'Plain Yogurt 500g',
      item_group: 'Dairy & Eggs',
      stock_uom: 'Nos',
      aas_packaging_unit: 'Cup',
      aas_vendor_rate: 26,
      aas_margin_percent: 10
    },
    {
      item_code: 'ITM-CABBAGE',
      item_name: 'Green Cabbage',
      item_group: 'Leafy Greens',
      stock_uom: 'Nos',
      aas_packaging_unit: 'Crate',
      aas_vendor_rate: 17,
      aas_margin_percent: 8
    },
    {
      item_code: 'ITM-ARUGULA',
      item_name: 'Arugula',
      item_group: 'Leafy Greens',
      stock_uom: 'Nos',
      aas_packaging_unit: 'Box',
      aas_vendor_rate: 21,
      aas_margin_percent: 9
    },
    {
      item_code: 'ITM-SWEETPOTATO',
      item_name: 'Sweet Potato',
      item_group: 'Root Vegetables',
      stock_uom: 'Nos',
      aas_packaging_unit: 'Sack',
      aas_vendor_rate: 19,
      aas_margin_percent: 7
    },
    {
      item_code: 'ITM-ONION',
      item_name: 'Yellow Onion',
      item_group: 'Root Vegetables',
      stock_uom: 'Nos',
      aas_packaging_unit: 'Bag',
      aas_vendor_rate: 13,
      aas_margin_percent: 6
    },
    {
      item_code: 'ITM-PEAR',
      item_name: 'Bartlett Pear',
      item_group: 'Fruits',
      stock_uom: 'Nos',
      aas_packaging_unit: 'Crate',
      aas_vendor_rate: 27,
      aas_margin_percent: 12
    },
    {
      item_code: 'ITM-GRAPES',
      item_name: 'Red Grapes',
      item_group: 'Fruits',
      stock_uom: 'Nos',
      aas_packaging_unit: 'Punnet',
      aas_vendor_rate: 32,
      aas_margin_percent: 14
    },
    {
      item_code: 'ITM-QUINOA',
      item_name: 'Quinoa',
      item_group: 'Grains & Pulses',
      stock_uom: 'Nos',
      aas_packaging_unit: 'Bag',
      aas_vendor_rate: 62,
      aas_margin_percent: 10
    },
    {
      item_code: 'ITM-BARLEY',
      item_name: 'Pearl Barley',
      item_group: 'Grains & Pulses',
      stock_uom: 'Nos',
      aas_packaging_unit: 'Bag',
      aas_vendor_rate: 40,
      aas_margin_percent: 7
    },
    {
      item_code: 'ITM-CHEESE',
      item_name: 'Cheddar Cheese 500g',
      item_group: 'Dairy & Eggs',
      stock_uom: 'Nos',
      aas_packaging_unit: 'Block',
      aas_vendor_rate: 48,
      aas_margin_percent: 11
    },
    {
      item_code: 'ITM-BUTTER',
      item_name: 'Unsalted Butter 500g',
      item_group: 'Dairy & Eggs',
      stock_uom: 'Nos',
      aas_packaging_unit: 'Block',
      aas_vendor_rate: 42,
      aas_margin_percent: 9
    }
  ]
};

async function request(path, options = {}) {
  let response;
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      headers: {
        'Content-Type': 'application/json',
        ...(options.headers || {})
      },
      ...options
    });
  } catch (err) {
    const detail = err?.cause?.message || err?.message || String(err);
    throw new Error(`Network error calling ${BASE_URL}${path}: ${detail}`);
  }
  if (!response.ok) {
    const text = await response.text();
    throw new Error(`${options.method || 'GET'} ${path} failed: ${response.status} ${response.statusText} - ${text}`);
  }
  if (response.status === 204) {
    return null;
  }
  return response.json();
}

async function login() {
  const payload = { username: USERNAME, password: PASSWORD };
  const result = await request('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
  if (!result?.accessToken) {
    throw new Error('Login failed: accessToken missing');
  }
  return { token: result.accessToken, role: result.role };
}

function authHeaders(token) {
  return { Authorization: `Bearer ${token}` };
}

async function ensureSetup(token) {
  try {
    await request('/api/setup/ensure', {
      method: 'POST',
      headers: authHeaders(token),
      body: JSON.stringify({})
    });
  } catch (err) {
    const message = err?.message || String(err);
    if (message.includes('403') || message.includes('401')) {
      console.warn('Setup ensure skipped: unauthorized. Ensure you are using an ADMIN account.');
      return;
    }
    throw err;
  }
}

function pickName(entry, keys) {
  for (const key of keys) {
    const value = entry?.[key];
    if (value) {
      return String(value).trim();
    }
  }
  return '';
}

async function listResources(path, token) {
  const result = await request(path, { headers: authHeaders(token) });
  return Array.isArray(result) ? result : [];
}

async function createResource(path, fields, token) {
  if (DRY_RUN) {
    return { dryRun: true, fields };
  }
  return request(path, {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify({ fields })
  });
}

async function ensureList({ label, path, existingKeys, entries, nameKeys }) {
  const existing = new Set(existingKeys.map(key => key.toLowerCase()));
  const created = [];
  const skipped = [];
  for (const entry of entries) {
    const name = pickName(entry, nameKeys).toLowerCase();
    if (!name) {
      skipped.push({ reason: 'missing name', entry });
      continue;
    }
    if (existing.has(name)) {
      skipped.push({ reason: 'already exists', entry });
      continue;
    }
    created.push(entry);
  }
  return { label, created, skipped };
}

async function fetchProfile(token) {
  try {
    return await request('/api/me', { headers: authHeaders(token) });
  } catch {
    return null;
  }
}

function ensureAdminForWrites(role, label) {
  if (role !== 'admin') {
    throw new Error(
      `Cannot create ${label}: logged in as role "${role}". ` +
        'Use an ADMIN account (e.g., MW_USERNAME=Administrator MW_PASSWORD=admin).'
    );
  }
}

async function seed() {
  console.log(`Seeding mock data into ${BASE_URL}`);
  if (DRY_RUN) {
    console.log('DRY_RUN=1 enabled; no records will be created.');
  }
  const { token, role: loginRole } = await login();
  const profile = await fetchProfile(token);
  const role = String(profile?.role ?? loginRole ?? '').toLowerCase() || 'unknown';
  const username = profile?.email || profile?.name || USERNAME;
  console.log(`Authenticated as ${username} (role: ${role}).`);

  await ensureSetup(token);

  const vendorsExisting = await listResources('/api/vendors', token);
  const branchesExisting = await listResources('/api/shops', token);
  const categoriesExisting = await listResources('/api/categories', token);
  const itemsExisting = await listResources('/api/items', token);

  const vendorsPlan = await ensureList({
    label: 'vendors',
    path: '/api/vendors',
    existingKeys: vendorsExisting.map(entry => pickName(entry, ['supplier_name', 'name'])),
    entries: data.vendors,
    nameKeys: ['supplier_name']
  });

  const branchesPlan = await ensureList({
    label: 'branches',
    path: '/api/shops',
    existingKeys: branchesExisting.map(entry => pickName(entry, ['customer_name', 'name'])),
    entries: data.branches,
    nameKeys: ['customer_name']
  });

  const categoriesPlan = await ensureList({
    label: 'categories',
    path: '/api/categories',
    existingKeys: categoriesExisting.map(entry => pickName(entry, ['item_group_name', 'name'])),
    entries: data.categories,
    nameKeys: ['item_group_name']
  });

  const itemsPlan = await ensureList({
    label: 'items',
    path: '/api/items',
    existingKeys: itemsExisting.map(entry => pickName(entry, ['item_code', 'name'])),
    entries: data.items,
    nameKeys: ['item_code']
  });

  const plans = [vendorsPlan, branchesPlan, categoriesPlan, itemsPlan];

  for (const plan of plans) {
    if (!plan.created.length) {
      console.log(`No new ${plan.label} to create.`);
      continue;
    }
    if (plan.label === 'vendors' || plan.label === 'branches') {
      ensureAdminForWrites(role, plan.label);
    }
    console.log(`Creating ${plan.created.length} ${plan.label}...`);
    for (const entry of plan.created) {
      const path = plan.label === 'vendors'
        ? '/api/vendors'
        : plan.label === 'branches'
          ? '/api/shops'
          : plan.label === 'categories'
            ? '/api/categories'
            : '/api/items';
      await createResource(path, entry, token);
    }
  }

  console.log('Seed complete.');
}

seed().catch(err => {
  console.error(err?.stack || err?.message || err);
  process.exitCode = 1;
});
