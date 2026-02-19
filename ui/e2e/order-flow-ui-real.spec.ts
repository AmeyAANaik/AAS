import { expect, test } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

test('real UI flow against MW/ERP: assign vendor -> vendor pdf -> vendor bill -> sell order', async ({ page, request }) => {
  const baseUrl = process.env.PLAYWRIGHT_API_BASE_URL ?? 'http://localhost:8083';
  const username = process.env.PLAYWRIGHT_USERNAME ?? 'Administrator';
  const password = process.env.PLAYWRIGHT_PASSWORD ?? 'admin';
  const today = new Date().toISOString().slice(0, 10);

  const loginRes = await request.post(`${baseUrl}/api/auth/login`, {
    data: { username, password }
  });
  expect(loginRes.ok()).toBeTruthy();
  const loginJson = await loginRes.json();
  const token: string = loginJson.accessToken;
  expect(token).toBeTruthy();
  const authHeaders = { Authorization: `Bearer ${token}` };
  const fetchOrderStatus = async (): Promise<string> => {
    const res = await request.get(`${baseUrl}/api/orders/${orderId}`, { headers: authHeaders });
    expect(res.ok()).toBeTruthy();
    const json = await res.json();
    const status = String(json?.aas_status ?? json?.status ?? json?.data?.aas_status ?? json?.data?.status ?? '').trim();
    return status;
  };

  await request.post(`${baseUrl}/api/setup/ensure`, {
    headers: authHeaders,
    data: {}
  });

  const createRes = await request.post(`${baseUrl}/api/orders`, {
    headers: authHeaders,
    data: {
      fields: {
        customer: 'Downtown Market Branch',
        company: 'AAS',
        transaction_date: today,
        delivery_date: today,
        aas_status: 'DRAFT',
        items: [{ item_code: 'AAS-BRANCH-IMAGE', qty: 1, rate: 0, amount: 0 }]
      }
    }
  });
  expect(createRes.ok()).toBeTruthy();
  const created = await createRes.json();
  const orderId: string = created?.name ?? created?.data?.name;
  expect(orderId).toBeTruthy();

  await page.goto('/login?returnUrl=/orders');
  await page.getByRole('textbox', { name: 'Username' }).fill(username);
  await page.getByRole('textbox', { name: 'Password' }).fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/\/orders$/);

  const row = page.locator('tr', { hasText: orderId }).first();
  await expect(row).toBeVisible({ timeout: 15000 });
  await row.getByRole('button', { name: 'Manage' }).click();

  await page.locator('mat-select[formcontrolname="vendorId"]').click();
  await page.getByRole('option', { name: 'Green Valley Farms' }).click();
  await page.getByRole('button', { name: 'Assign vendor' }).click();
  await expect
    .poll(fetchOrderStatus, { timeout: 20000, message: 'order status should move to VENDOR_ASSIGNED' })
    .toBe('VENDOR_ASSIGNED');

  const vendorPdfPath = path.resolve(process.cwd(), 'images/vendor_order.pdf');
  expect(fs.existsSync(vendorPdfPath)).toBeTruthy();
  await page.locator('input[type="file"][accept="application/pdf"]').setInputFiles(vendorPdfPath);
  await page.getByRole('button', { name: 'Upload and parse PDF' }).click();
  await expect
    .poll(fetchOrderStatus, { timeout: 30000, message: 'order status should move to VENDOR_PDF_RECEIVED' })
    .toBe('VENDOR_PDF_RECEIVED');

  await page.locator('input[formcontrolname="vendorBillTotal"]').fill('500');
  await page.locator('input[formcontrolname="vendorBillRef"]').fill(`VB-REAL-${Date.now()}`);
  await page.locator('input[formcontrolname="vendorBillDate"]').fill(today);
  await page.locator('input[formcontrolname="marginPercent"]').fill('10');
  await expect(page.getByRole('button', { name: 'Capture vendor bill' })).toBeEnabled({ timeout: 60000 });
  await page.getByRole('button', { name: 'Capture vendor bill' }).click();
  await expect
    .poll(fetchOrderStatus, { timeout: 20000, message: 'order status should move to VENDOR_BILL_CAPTURED' })
    .toBe('VENDOR_BILL_CAPTURED');

  await page.getByRole('button', { name: 'Calculate preview' }).click();
  await expect(page.getByText(/Sell amount:/)).toBeVisible({ timeout: 10000 });

  await page.getByRole('button', { name: 'Create sell order' }).click();
  await expect
    .poll(fetchOrderStatus, { timeout: 20000, message: 'order status should move to SELL_ORDER_CREATED' })
    .toBe('SELL_ORDER_CREATED');
});
