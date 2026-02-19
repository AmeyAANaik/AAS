import { expect, test } from '@playwright/test';

test('order workflow through UI: assign vendor -> vendor pdf -> vendor bill -> sell preview -> sell order', async ({ page }) => {
  let currentStatus = 'DRAFT';
  let currentVendor = '';
  let vendorBillTotal = 0;
  let marginPercent = 10;

  const buildOrders = () => [
    {
      name: 'SO-0001',
      customer: 'Shop A',
      company: 'AAS',
      transaction_date: '2026-02-19',
      delivery_date: '2026-02-20',
      aas_vendor: currentVendor,
      aas_status: currentStatus,
      grand_total: 100
    }
  ];

  await page.route('**/api/auth/login', async route => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ accessToken: 'test-token' }) });
  });

  await page.route('**/api/shops', async route => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([{ name: 'SHOP-1', customer_name: 'Shop A' }])
    });
  });

  await page.route('**/api/vendors', async route => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([{ name: 'SUP-1', supplier_name: 'Vendor A' }])
    });
  });

  await page.route('**/api/items', async route => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([{ name: 'ITEM-1', item_code: 'ITEM-1', item_name: 'Tomatoes', item_group: 'Food', stock_uom: 'Kg' }])
    });
  });

  await page.route('**/api/orders?**', async route => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(buildOrders()) });
  });

  await page.route('**/api/orders/SO-0001/assign-vendor', async route => {
    currentVendor = 'SUP-1';
    currentStatus = 'VENDOR_ASSIGNED';
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ name: 'SO-0001' }) });
  });

  await page.route('**/api/orders/SO-0001/vendor-pdf', async route => {
    currentStatus = 'VENDOR_PDF_RECEIVED';
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ orderId: 'SO-0001', purchaseOrder: { name: 'PO-0001' }, marginPercent: 10 })
    });
  });

  await page.route('**/api/orders/SO-0001/vendor-bill', async route => {
    const payload = route.request().postDataJSON() as { fields?: { vendor_bill_total?: number; margin_percent?: number } };
    vendorBillTotal = Number(payload?.fields?.vendor_bill_total ?? 0);
    marginPercent = Number(payload?.fields?.margin_percent ?? 10);
    currentStatus = 'VENDOR_BILL_CAPTURED';
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ orderId: 'SO-0001', vendorBillTotal, marginPercent, purchaseInvoice: { name: 'PINV-0001' } })
    });
  });

  await page.route('**/api/orders/SO-0001/sell-preview', async route => {
    const sellAmount = Number((vendorBillTotal * (1 + marginPercent / 100)).toFixed(2));
    const marginAmount = Number((sellAmount - vendorBillTotal).toFixed(2));
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        orderId: 'SO-0001',
        vendorBillTotal,
        marginPercent,
        sellAmount,
        marginAmount
      })
    });
  });

  await page.route('**/api/orders/SO-0001/sell-order', async route => {
    currentStatus = 'SELL_ORDER_CREATED';
    const sellAmount = Number((vendorBillTotal * (1 + marginPercent / 100)).toFixed(2));
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        orderId: 'SO-0001',
        sellTotal: sellAmount,
        salesOrder: { name: 'SO-BR-0001' },
        salesInvoice: { name: 'SI-BR-0001' }
      })
    });
  });

  await page.goto('/login?returnUrl=/orders');
  await page.getByRole('textbox', { name: 'Username' }).fill('admin');
  await page.getByRole('textbox', { name: 'Password' }).fill('admin');
  await page.getByRole('button', { name: 'Sign in' }).click();

  await expect(page).toHaveURL(/\/orders$/);
  await page.getByRole('button', { name: 'Manage' }).click();

  await page.locator('mat-select[formcontrolname="vendorId"]').click();
  await page.getByRole('option', { name: 'Vendor A' }).click();
  await page.getByRole('button', { name: 'Assign vendor' }).click();
  await expect(page.getByText('Vendor assigned.')).toBeVisible();

  await page.locator('input[type="file"][accept="application/pdf"]').setInputFiles({
    name: 'vendor.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.4 test')
  });
  await page.getByRole('button', { name: 'Upload and parse PDF' }).click();
  await expect(page.getByText('Vendor PDF uploaded and parsed.')).toBeVisible();

  await page.locator('input[formcontrolname="vendorBillTotal"]').fill('100');
  await page.locator('input[formcontrolname="vendorBillRef"]').fill('VB-001');
  await page.locator('input[formcontrolname="vendorBillDate"]').fill('2026-02-19');
  await page.locator('input[formcontrolname="marginPercent"]').fill('10');
  await page.getByRole('button', { name: 'Capture vendor bill' }).click();
  await expect(page.getByText('Vendor bill captured.')).toBeVisible();

  await page.getByRole('button', { name: 'Calculate preview' }).click();
  await expect(page.getByText('Sell amount: 110.00')).toBeVisible();

  await page.getByRole('button', { name: 'Create sell order' }).click();
  await expect(page.getByText('SELL_ORDER_CREATED').first()).toBeVisible();
});
