#!/usr/bin/env node

import { chromium } from '@playwright/test';

const BASE_URL = (process.env.UI_BASE_URL || 'http://localhost:4200').replace(/\/$/, '');
const USERNAME = process.env.UI_USERNAME || 'Administrator';
const PASSWORD = process.env.UI_PASSWORD || 'admin';
const HEADLESS = process.env.HEADLESS !== '0';

const data = {
  vendors: [
    {
      supplierName: 'Green Valley Farms',
      address: '12 Orchard Road, Albany, NY',
      phone: '518-555-0132',
      gst: 'GST-10GVF',
      pan: 'PAN-GVF-001',
      foodLicenseNo: 'FL-1001',
      priority: '1',
      status: 'Active'
    },
    {
      supplierName: 'Sunrise Produce Co',
      address: '88 Market St, Newark, NJ',
      phone: '973-555-0119',
      gst: 'GST-20SPC',
      pan: 'PAN-SPC-002',
      foodLicenseNo: 'FL-1002',
      priority: '2',
      status: 'Active'
    },
    {
      supplierName: 'Harvest Ridge',
      address: '245 County Rd, Lancaster, PA',
      phone: '717-555-0168',
      gst: 'GST-30HR',
      pan: 'PAN-HR-003',
      foodLicenseNo: 'FL-1003',
      priority: '3',
      status: 'Active'
    },
    {
      supplierName: 'FreshBridge Traders',
      address: '51 Warehouse Way, Edison, NJ',
      phone: '732-555-0125',
      gst: 'GST-40FBT',
      pan: 'PAN-FBT-004',
      foodLicenseNo: 'FL-1004',
      priority: '4',
      status: 'Active'
    },
    {
      supplierName: 'Lakeside Organics',
      address: '7 Bay St, Cleveland, OH',
      phone: '216-555-0142',
      gst: 'GST-50LO',
      pan: 'PAN-LO-005',
      foodLicenseNo: 'FL-1005',
      priority: '5',
      status: 'Active'
    }
  ],
  branches: [
    { branchName: 'Downtown Market Branch', location: 'Lower Manhattan, NY', whatsappGroupName: 'DT Market Ops' },
    { branchName: 'Uptown Bistro Branch', location: 'Upper West Side, NY', whatsappGroupName: 'Uptown Bistro Team' },
    { branchName: 'Riverfront Cafe Branch', location: 'Jersey City, NJ', whatsappGroupName: 'Riverfront Cafe' },
    { branchName: 'Hillview Restaurant Branch', location: 'Hoboken, NJ', whatsappGroupName: 'Hillview Restaurant' },
    { branchName: 'Airport Lounge Branch', location: 'JFK Airport, NY', whatsappGroupName: 'Airport Lounge Ops' }
  ],
  categories: [
    { categoryName: 'Leafy Greens' },
    { categoryName: 'Root Vegetables' },
    { categoryName: 'Fruits' },
    { categoryName: 'Grains & Pulses' },
    { categoryName: 'Dairy & Eggs' }
  ],
  items: [
    { itemCode: 'ITM-LETTUCE', itemName: 'Romaine Lettuce', category: 'Leafy Greens', measureUnit: 'Nos', packagingUnit: 'Crate' },
    { itemCode: 'ITM-SPINACH', itemName: 'Baby Spinach', category: 'Leafy Greens', measureUnit: 'Nos', packagingUnit: 'Crate' },
    { itemCode: 'ITM-CARROT', itemName: 'Carrot Bunch', category: 'Root Vegetables', measureUnit: 'Nos', packagingUnit: 'Bag' },
    { itemCode: 'ITM-POTATO', itemName: 'Yukon Potato', category: 'Root Vegetables', measureUnit: 'Nos', packagingUnit: 'Bag' },
    { itemCode: 'ITM-APPLE', itemName: 'Honeycrisp Apple', category: 'Fruits', measureUnit: 'Nos', packagingUnit: 'Crate' },
    { itemCode: 'ITM-BANANA', itemName: 'Cavendish Banana', category: 'Fruits', measureUnit: 'Nos', packagingUnit: 'Bunch' },
    { itemCode: 'ITM-RICE', itemName: 'Basmati Rice', category: 'Grains & Pulses', measureUnit: 'Nos', packagingUnit: 'Bag' },
    { itemCode: 'ITM-LENTIL', itemName: 'Red Lentil', category: 'Grains & Pulses', measureUnit: 'Nos', packagingUnit: 'Bag' },
    { itemCode: 'ITM-EGGS', itemName: 'Free Range Eggs', category: 'Dairy & Eggs', measureUnit: 'Nos', packagingUnit: 'Tray' },
    { itemCode: 'ITM-MILK', itemName: 'Whole Milk 1L', category: 'Dairy & Eggs', measureUnit: 'Nos', packagingUnit: 'Case' }
  ],
  pricing: [
    { itemName: 'Romaine Lettuce', vendorName: 'Green Valley Farms', originalRate: '24', marginPercent: '12' },
    { itemName: 'Baby Spinach', vendorName: 'Green Valley Farms', originalRate: '18', marginPercent: '10' },
    { itemName: 'Carrot Bunch', vendorName: 'Sunrise Produce Co', originalRate: '15', marginPercent: '8' },
    { itemName: 'Yukon Potato', vendorName: 'Sunrise Produce Co', originalRate: '12', marginPercent: '6' },
    { itemName: 'Honeycrisp Apple', vendorName: 'Harvest Ridge', originalRate: '30', marginPercent: '15' },
    { itemName: 'Cavendish Banana', vendorName: 'Harvest Ridge', originalRate: '14', marginPercent: '10' },
    { itemName: 'Basmati Rice', vendorName: 'FreshBridge Traders', originalRate: '55', marginPercent: '9' },
    { itemName: 'Red Lentil', vendorName: 'FreshBridge Traders', originalRate: '48', marginPercent: '7' },
    { itemName: 'Free Range Eggs', vendorName: 'Lakeside Organics', originalRate: '36', marginPercent: '11' },
    { itemName: 'Whole Milk 1L', vendorName: 'Lakeside Organics', originalRate: '22', marginPercent: '8' }
  ]
};

async function hasTableCell(page, text) {
  const locator = page.locator('table').getByText(text, { exact: true });
  return (await locator.count()) > 0;
}

async function waitForTableCell(page, text) {
  await page.waitForFunction(name => {
    return Array.from(document.querySelectorAll('table td')).some(td => td.textContent?.trim() === name);
  }, text);
}

async function selectMatOption(page, label, optionText) {
  const combobox = page.getByRole('combobox', { name: label, exact: true });
  if (await combobox.count()) {
    await combobox.first().click();
  } else {
    await page.getByLabel(label).click();
  }
  const option = page.getByRole('option', { name: optionText, exact: true });
  await option.waitFor({ state: 'visible', timeout: 15000 });
  await option.click();
}

async function selectMatOptionByCombobox(page, comboboxName, optionText) {
  await page.getByRole('combobox', { name: comboboxName }).click();
  const option = page.getByRole('option', { name: optionText, exact: true });
  await option.waitFor({ state: 'visible', timeout: 15000 });
  await option.click();
}

async function comboboxHasOption(page, comboboxName, optionText) {
  await page.getByRole('combobox', { name: comboboxName }).click();
  const options = page.locator('mat-option');
  await options.first().waitFor({ state: 'visible', timeout: 15000 });
  const texts = (await options.allTextContents()).map(text => text.trim());
  await page.keyboard.press('Escape');
  return texts.includes(optionText);
}

async function setItemCategoryViaAngular(page, categoryName) {
  return page.evaluate(name => {
    const ng = window.ng;
    const host = document.querySelector('app-item-form');
    if (!ng || !host) {
      return false;
    }
    const component = ng.getComponent(host);
    const form = component?.form;
    const control = form?.get('category');
    if (!control) {
      return false;
    }
    control.setValue(name);
    control.markAsDirty();
    control.updateValueAndValidity();
    return true;
  }, categoryName);
}

async function login(page) {
  await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle' });

  const continueBtn = page.getByRole('button', { name: 'Continue to dashboard' });
  if (await continueBtn.isVisible().catch(() => false)) {
    await continueBtn.click();
    await page.waitForURL('**/admin/dashboard');
    return;
  }

  await page.getByLabel('Username').fill(USERNAME);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.waitForURL('**/admin/dashboard');
}

async function seedVendors(page) {
  await page.goto(`${BASE_URL}/vendors`, { waitUntil: 'networkidle' });
  for (const vendor of data.vendors) {
    if (await hasTableCell(page, vendor.supplierName)) {
      continue;
    }
    await page.getByRole('button', { name: 'Create vendor' }).click();
    await page.getByLabel('Vendor name').fill(vendor.supplierName);
    await page.getByLabel('Address').fill(vendor.address);
    await page.getByLabel('Phone no').fill(vendor.phone);
    await page.getByLabel('GST').fill(vendor.gst);
    await page.getByLabel('PAN').fill(vendor.pan);
    await page.getByLabel('Food license no').fill(vendor.foodLicenseNo);
    await page.getByLabel('Priority').fill(vendor.priority);
    await selectMatOption(page, 'Status', vendor.status);
    await page.getByRole('button', { name: 'Save vendor' }).click();
    await waitForTableCell(page, vendor.supplierName);
  }
}

async function seedBranches(page) {
  await page.goto(`${BASE_URL}/branches`, { waitUntil: 'networkidle' });
  for (const branch of data.branches) {
    if (await hasTableCell(page, branch.branchName)) {
      continue;
    }
    await page.getByLabel('Branch name').fill(branch.branchName);
    await page.getByLabel('Location').fill(branch.location);
    await page.getByLabel('WhatsApp Group Name').fill(branch.whatsappGroupName);
    await page.getByRole('button', { name: 'Save branch' }).click();
    await waitForTableCell(page, branch.branchName);
  }
}

async function seedCategories(page) {
  await page.goto(`${BASE_URL}/categories`, { waitUntil: 'networkidle' });
  for (const category of data.categories) {
    if (await hasTableCell(page, category.categoryName)) {
      continue;
    }
    await page.getByLabel('Category name').fill(category.categoryName);
    await page.getByRole('button', { name: 'Save category' }).click();
    await waitForTableCell(page, category.categoryName);
  }
}

async function seedItems(page) {
  await page.goto(`${BASE_URL}/items`, { waitUntil: 'networkidle' });
  const categoriesResponse = page.waitForResponse(
    response => response.url().includes('/api/categories') && response.status() === 200
  );
  await page.getByRole('button', { name: 'Refresh' }).click();
  await categoriesResponse;
  for (const item of data.items) {
    if (await hasTableCell(page, item.itemCode)) {
      continue;
    }
    await page.getByLabel('Item code').fill(item.itemCode);
    await page.getByLabel('Item name').fill(item.itemName);
    const setByAngular = await setItemCategoryViaAngular(page, item.category);
    if (!setByAngular) {
      await selectMatOption(page, 'Category', item.category);
    }
    await page.getByLabel('Measure unit').fill(item.measureUnit);
    await page.getByLabel('Packaging unit').fill(item.packagingUnit);
    const saveResponse = page.waitForResponse(
      response => response.url().includes('/api/items') && response.request().method() === 'POST'
    );
    await page.getByRole('button', { name: 'Save item' }).click();
    const response = await saveResponse;
    if (response.status() === 409) {
      continue;
    }
    if (!response.ok()) {
      throw new Error(`Item create failed for ${item.itemCode}: ${response.status()} ${response.statusText()}`);
    }
    await page.waitForTimeout(250);
  }
}

async function seedPricing(page) {
  await page.goto(`${BASE_URL}/items`, { waitUntil: 'networkidle' });
  for (const entry of data.pricing) {
    const hasItem = await comboboxHasOption(page, /^Item/, entry.itemName);
    const hasVendor = await comboboxHasOption(page, /^Vendor/, entry.vendorName);
    if (!hasItem || !hasVendor) {
      continue;
    }
    await selectMatOptionByCombobox(page, /^Item/, entry.itemName);
    await selectMatOptionByCombobox(page, /^Vendor/, entry.vendorName);
    await page.getByLabel('Original rate').fill(entry.originalRate);
    await page.getByLabel('Margin %').fill(entry.marginPercent);
    await page.getByRole('button', { name: 'Save pricing' }).click();
    await page.waitForTimeout(300);
  }
}

async function run() {
  const browser = await chromium.launch({
    headless: HEADLESS,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage']
  });
  const page = await browser.newPage();
  page.setDefaultTimeout(30000);

  await login(page);
  await seedVendors(page);
  await seedBranches(page);
  await seedCategories(page);
  await seedItems(page);
  await seedPricing(page);

  await browser.close();
}

run().catch(err => {
  console.error(err);
  process.exitCode = 1;
});
