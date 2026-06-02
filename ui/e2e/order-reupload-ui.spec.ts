import { expect, test } from '@playwright/test';

test('order workflow UI: reupload vendor PDF resets steps 3 & 4 (mocked)', async ({ page }) => {
  let currentStatus: 'VENDOR_BILL_CAPTURED' | 'SELL_ORDER_CREATED' | 'VENDOR_PDF_RECEIVED' = 'VENDOR_BILL_CAPTURED';
  let currentVendor = 'SUP-1';
  let vendorPdfUrl = '/files/vendor-invoice-v1.pdf';
  let currentPurchaseInvoice = 'PINV-OLD';
  let currentSalesInvoice = 'SI-OLD';

  const buildOrders = () => [
    {
      name: 'SO-0001',
      customer: 'Sukarta Aundh',
      company: 'AAS',
      transaction_date: '2026-05-30',
      delivery_date: '2026-05-31',
      aas_vendor: currentVendor,
      aas_category: 'Raw Material',
      aas_status: currentStatus,
      aas_vendor_pdf: vendorPdfUrl,
      aas_pi_vendor: currentPurchaseInvoice,
      aas_si_branch: currentSalesInvoice,
      grand_total: 100
    }
  ];

  await page.addInitScript(() => {
    localStorage.setItem('aas_auth_token', 'test-token');
    localStorage.setItem('aas_auth_role', 'Administrator');
    localStorage.setItem('aas_auth_features', JSON.stringify([
      'orders.view',
      'dashboard.view',
      'master_data.view',
      'bills.view',
      'stock.view',
      'reports.view'
    ]));
  });

  await page.route('**/api/company-context', async route => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        company: { id: 'AAS', name: 'AAS' },
        branch: null,
        companies: [{ name: 'AAS' }],
        branches: []
      })
    });
  });

  await page.route('**/api/me', async route => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        role: 'Administrator',
        features: ['orders.view', 'dashboard.view', 'master_data.view', 'bills.view', 'stock.view', 'reports.view'],
        homeRoute: '/orders'
      })
    });
  });

  await page.route('**/api/shops', async route => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([{ name: 'SHOP-1', customer_name: 'Sukarta Aundh' }])
    });
  });

  await page.route('**/api/vendors', async route => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([{ name: 'SUP-1', supplier_name: 'Vendor A', category: 'Raw Material', disabled: 0 }])
    });
  });

  await page.route('**/api/items', async route => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([{ name: 'ITEM-1', item_code: 'ITEM-1', item_name: 'Tomatoes', item_group: 'Raw Material', stock_uom: 'Kg' }])
    });
  });

  await page.route('**/api/orders', async route => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(buildOrders()) });
  });

  await page.route('**/api/orders?**', async route => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(buildOrders()) });
  });

  await page.route('**/api/orders/SO-0001/vendor-pdf', async route => {
    const phase = currentStatus;
    vendorPdfUrl = phase === 'VENDOR_BILL_CAPTURED' ? '/files/vendor-invoice-v2.pdf' : '/files/vendor-invoice-v3.pdf';
    // New behavior: re-upload resets order back to VENDOR_PDF_RECEIVED and clears linked invoices.
    currentStatus = 'VENDOR_PDF_RECEIVED';
    currentPurchaseInvoice = '';
    currentSalesInvoice = '';
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        orderId: 'SO-0001',
        vendorBillTotal: 100,
        workflowReset: true,
        file: { fileName: 'vendor.pdf', fileUrl: vendorPdfUrl, fileId: 'FILE-2' }
      })
    });
  });

  await page.route('**/api/orders/SO-0001/sell-preview', async route => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        orderId: 'SO-0001',
        vendorBillTotal: 100,
        marginPercent: 7,
        sellAmount: 110,
        marginAmount: 10
      })
    });
  });

  await page.goto('/orders?orderId=SO-0001');
  await expect(page).toHaveURL(/\/orders\?orderId=SO-0001/);

  // Phase 1: status=VENDOR_BILL_CAPTURED -> reupload resets to VENDOR_PDF_RECEIVED (Step 3 active).
  await expect(page.getByRole('dialog')).toBeVisible();
  await expect(page.getByRole('dialog').locator('.status-badge', { hasText: 'Bill captured' })).toBeVisible();

  await page.locator('input[type="file"]').setInputFiles({
    name: 'vendor.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.4 test')
  });
  await page.getByRole('button', { name: 'Re-upload and Parse PDF' }).click();
  await expect(page.getByRole('dialog').locator('.status-badge', { hasText: 'PDF received' })).toBeVisible();

  // Close manage modal to force a UI refresh path similar to user flow.
  await page.getByRole('button', { name: 'Close dialog' }).click();
  await expect(page.getByRole('dialog')).toBeHidden();

  // Phase 2: status=SELL_ORDER_CREATED -> reupload resets to VENDOR_PDF_RECEIVED (Step 3 active).
  currentStatus = 'SELL_ORDER_CREATED';
  currentPurchaseInvoice = 'PINV-OLD';
  currentSalesInvoice = 'SI-OLD';
  await page.reload();
  await expect(page.getByRole('dialog')).toBeVisible();
  await expect(page.getByRole('dialog').locator('.status-badge', { hasText: 'Sell order created' })).toBeVisible();
  await page.locator('input[type="file"]').setInputFiles({
    name: 'vendor.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.4 test')
  });
  await page.getByRole('button', { name: 'Re-upload and Parse PDF' }).click();
  await expect(page.getByRole('dialog').locator('.status-badge', { hasText: 'PDF received' })).toBeVisible();

  await page.getByRole('button', { name: 'Close dialog' }).click();
  await expect(page.getByRole('dialog')).toBeHidden();
});
