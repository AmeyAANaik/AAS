#!/usr/bin/env node

const DEFAULT_ERP_BASE_URL = 'http://localhost:8080';
const ERP_BASE_URL = (process.env.ERP_BASE_URL || process.env.ERPNEXT_BASE_URL || DEFAULT_ERP_BASE_URL).replace(/\/$/, '');
const USERNAME = process.env.MW_USERNAME || process.env.ERP_USERNAME || 'Administrator';
const PASSWORD = process.env.MW_PASSWORD || process.env.ERP_PASSWORD || 'admin';

const KEEP_BRANCHES = new Set([
  'Sukarta Aundh',
  'Sukarta Baner',
  'Sukarta Balewadi',
  'Sukarta Wakad',
  'Sukarta Hinjawadi',
  'Sukarta Kothrud'
]);

const TRANSACTION_DOCTYPES = [
  'Payment Entry',
  'Sales Invoice',
  'Purchase Invoice',
  'Purchase Order',
  'Sales Order'
];

const ATTACHMENT_DOCTYPES = new Set([
  'Payment Entry',
  'Sales Invoice',
  'Purchase Invoice',
  'Purchase Order',
  'Sales Order'
]);

let cookieHeader = '';

async function main() {
  await login();

  const summary = {
    deleted: {},
    disabled: {},
    retained: {}
  };

  await repointDefaultShopUser();

  for (const doctype of TRANSACTION_DOCTYPES) {
    const docs = await listAll(doctype, ['name', 'docstatus']);
    let deleted = 0;
    let retained = 0;
    for (const doc of docs) {
      const name = asText(doc.name);
      const docstatus = Number(doc.docstatus || 0);
      if (!name) {
        continue;
      }
      if (docstatus !== 0) {
        retained += 1;
        continue;
      }
      await deleteResource(doctype, name);
      deleted += 1;
    }
    summary.deleted[doctype] = deleted;
    if (retained > 0) {
      summary.retained[doctype] = retained;
    }
  }

  const files = await listAll('File', ['name', 'attached_to_doctype']);
  let deletedFiles = 0;
  for (const file of files) {
    const doctype = asText(file.attached_to_doctype);
    if (!ATTACHMENT_DOCTYPES.has(doctype)) {
      continue;
    }
    await deleteResource('File', asText(file.name));
    deletedFiles += 1;
  }
  summary.deleted.File = deletedFiles;

  const items = await listAll('Item', ['name', 'item_code', 'disabled']);
  let disabledItems = 0;
  for (const item of items) {
    const itemCode = asText(item.item_code || item.name);
    if (!itemCode || itemCode === 'AAS-SYSTEM-BRANCH-IMAGE' || Number(item.disabled || 0) === 1) {
      continue;
    }
    await updateResource('Item', itemCode, { disabled: 1 });
    disabledItems += 1;
  }
  summary.disabled.Item = disabledItems;

  const suppliers = await listAll('Supplier', ['name', 'supplier_name', 'disabled']);
  let disabledSuppliers = 0;
  for (const supplier of suppliers) {
    const name = asText(supplier.name);
    if (!name || Number(supplier.disabled || 0) === 1) {
      continue;
    }
    await updateResource('Supplier', name, { disabled: 1 });
    disabledSuppliers += 1;
  }
  summary.disabled.Supplier = disabledSuppliers;

  const customers = await listAll('Customer', ['name', 'customer_name', 'disabled']);
  let disabledCustomers = 0;
  let deletedCustomers = 0;
  for (const customer of customers) {
    const name = asText(customer.name);
    const customerName = asText(customer.customer_name || customer.name);
    if (!name) {
      continue;
    }
    if (customerName === 'Shop A' || name === 'Shop A') {
      try {
        await deleteResource('Customer', name);
        deletedCustomers += 1;
      } catch {
        if (Number(customer.disabled || 0) !== 1) {
          await updateResource('Customer', name, { disabled: 1, customer_name: 'Archived Shop A' });
          disabledCustomers += 1;
        }
      }
      continue;
    }
    if (KEEP_BRANCHES.has(customerName) && Number(customer.disabled || 0) !== 1) {
      await updateResource('Customer', name, { disabled: 1 });
      disabledCustomers += 1;
    }
  }
  summary.disabled.Customer = disabledCustomers;
  summary.deleted.Customer = deletedCustomers;

  summary.retained['Item Group'] = (await listAll('Item Group', ['name'])).length;
  summary.retained.Supplier = suppliers.length;
  summary.retained.Customer = (await listAll('Customer', ['name'])).length;

  console.log(JSON.stringify(summary, null, 2));
}

async function repointDefaultShopUser() {
  try {
    await updateResource('User', 'shop@example.com', { customer: 'Sukarta Aundh' });
  } catch (error) {
    // Ignore if the default shop user does not exist.
  }
}

async function login() {
  const response = await fetch(`${ERP_BASE_URL}/api/method/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded'
    },
    body: new URLSearchParams({ usr: USERNAME, pwd: PASSWORD }).toString()
  });
  if (!response.ok) {
    throw new Error(`ERP login failed: ${response.status} ${response.statusText}`);
  }
  const cookies = response.headers.getSetCookie ? response.headers.getSetCookie() : splitSetCookie(response.headers.get('set-cookie'));
  cookieHeader = cookies
    .map(cookie => cookie.split(';', 1)[0].trim())
    .filter(Boolean)
    .join('; ');
  if (!cookieHeader) {
    throw new Error('ERP login did not return a session cookie.');
  }
}

async function listAll(doctype, fields, filters = null) {
  const rows = [];
  let start = 0;
  const pageLength = 500;
  while (true) {
    const params = new URLSearchParams();
    params.set('fields', JSON.stringify(fields));
    params.set('limit_start', String(start));
    params.set('limit_page_length', String(pageLength));
    if (filters) {
      params.set('filters', JSON.stringify(filters));
    }
    const body = await request(`/api/resource/${encodeSegment(doctype)}?${params.toString()}`);
    const page = Array.isArray(body.data) ? body.data : [];
    rows.push(...page);
    if (page.length < pageLength) {
      break;
    }
    start += pageLength;
  }
  return rows;
}

async function updateResource(doctype, name, payload) {
  return request(`/api/resource/${encodeSegment(doctype)}/${encodeSegment(name)}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
}

async function deleteResource(doctype, name) {
  return request(`/api/resource/${encodeSegment(doctype)}/${encodeSegment(name)}`, {
    method: 'DELETE'
  });
}

async function request(path, options = {}) {
  const response = await fetch(`${ERP_BASE_URL}${path}`, {
    ...options,
    headers: {
      Cookie: cookieHeader,
      Accept: 'application/json',
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(options.headers || {})
    }
  });
  const text = await response.text();
  let body = {};
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = { raw: text };
    }
  }
  if (!response.ok) {
    throw new Error(`${options.method || 'GET'} ${path} failed: ${response.status} ${JSON.stringify(body)}`);
  }
  return body;
}

function encodeSegment(value) {
  return encodeURIComponent(value).replace(/%20/g, '%20');
}

function asText(value) {
  return value == null ? '' : String(value).trim();
}

function splitSetCookie(header) {
  if (!header) {
    return [];
  }
  return header.split(/,(?=[^;]+=[^;]+)/g);
}

main().catch(error => {
  console.error(error.stack || String(error));
  process.exitCode = 1;
});
