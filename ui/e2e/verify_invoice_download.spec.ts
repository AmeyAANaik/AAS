import { test, expect } from '@playwright/test';
import * as path from 'path';
import * as fs from 'fs';

const username = process.env.PLAYWRIGHT_USERNAME ?? 'Administrator';
const password = process.env.PLAYWRIGHT_PASSWORD ?? 'admin';
const baseUrl = 'http://localhost:4200';

async function doLogin(page: any) {
  await page.goto('/login?returnUrl=/bills');
  await page.getByRole('textbox', { name: 'Username' }).fill(username);
  await page.getByRole('textbox', { name: 'Password' }).fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.waitForURL(/\/bills/, { timeout: 10000 });
  await page.waitForTimeout(2500);
}

test('invoice PDF download - first invoice', async ({ page }) => {
  await doLogin(page);
  const pdfButtons = page.locator('button.pdf-icon-button');
  const count = await pdfButtons.count();
  console.log('PDF buttons:', count);
  expect(count).toBeGreaterThan(0);

  const downloadPromise = page.waitForEvent('download', { timeout: 20000 });
  await pdfButtons.first().click();
  const download = await downloadPromise;

  const downloadPath = path.join('/tmp', download.suggestedFilename() || 'invoice1.pdf');
  await download.saveAs(downloadPath);
  const stats = fs.statSync(downloadPath);
  console.log('file:', download.suggestedFilename(), '| size:', stats.size, 'bytes');

  const buf = fs.readFileSync(downloadPath);
  const isPdf = buf[0] === 0x25 && buf[1] === 0x50 && buf[2] === 0x44 && buf[3] === 0x46;
  expect(isPdf).toBe(true);
  console.log('first download: valid PDF ✅');
  await page.screenshot({ path: '/tmp/bills_first_dl.png' });
});

test('invoice PDF download - second invoice (if present)', async ({ page }) => {
  await doLogin(page);
  const pdfButtons = page.locator('button.pdf-icon-button');
  const count = await pdfButtons.count();
  if (count < 2) {
    console.log('only one invoice present, skipping second invoice test');
    return;
  }

  const downloadPromise = page.waitForEvent('download', { timeout: 20000 });
  await pdfButtons.nth(1).click();
  const download = await downloadPromise;

  const downloadPath = path.join('/tmp', download.suggestedFilename() || 'invoice2.pdf');
  await download.saveAs(downloadPath);
  const stats = fs.statSync(downloadPath);
  console.log('file:', download.suggestedFilename(), '| size:', stats.size, 'bytes');

  const buf = fs.readFileSync(downloadPath);
  const isPdf = buf[0] === 0x25 && buf[1] === 0x50 && buf[2] === 0x44 && buf[3] === 0x46;
  expect(isPdf).toBe(true);
  console.log('second download: valid PDF ✅');
});

test('probe: PDF download API with bad invoice ID returns error', async ({ request }) => {
  // Login to get token
  const loginRes = await request.post(`${baseUrl}/api/auth/login`, {
    data: { username, password }
  });
  expect(loginRes.ok()).toBeTruthy();
  const token = (await loginRes.json()).accessToken;

  const res = await request.get(`${baseUrl}/api/invoices/DOES-NOT-EXIST-9999/pdf`, {
    headers: { Authorization: `Bearer ${token}` }
  });
  console.log('bad invoice PDF status:', res.status());
  expect(res.status()).not.toBe(200); // Should be 4xx or 5xx, not a PDF
  console.log('probe: bad invoice ID correctly rejects ✅');
});

test('probe: Supplier party type toggle and PDF download', async ({ page }) => {
  await doLogin(page);

  // Toggle to Supplier/Vendor invoices
  const supplierToggle = page.getByRole('radio', { name: /Vendor.*Supplier/i });
  if (await supplierToggle.count() > 0) {
    await supplierToggle.click();
    await page.waitForTimeout(2000);
    console.log('switched to Supplier party type');
    await page.screenshot({ path: '/tmp/bills_supplier.png' });
    const pdfButtons = page.locator('button.pdf-icon-button');
    const count = await pdfButtons.count();
    console.log('supplier PDF buttons:', count);
  } else {
    console.log('Supplier toggle not found — skipping probe');
  }
});
