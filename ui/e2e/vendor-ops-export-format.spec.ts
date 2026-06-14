import { expect, test } from '@playwright/test';

const MOCK_SUMMARY = {
  totals: { totalVendors: 1, vendorsWithPendingOrders: 1, totalPendingOrders: 2, awaitingPdf: 1, awaitingBillCapture: 1, totalPendingBillAmount: 4500 },
  vendors: [{ vendorId: 'VENDOR-1', vendorName: 'Test Vendor', pendingOrders: 2, awaitingPdf: 1, awaitingBillCapture: 1, inProgress: 0, pendingBillAmount: 4500, lastActivity: '2026-06-10', templateStatus: 'ACTIVE', ledgerBalance: 4500, parseSuccessRate: 95 }]
};

const MOCK_DETAIL = {
  vendor: { vendorId: 'VENDOR-1', vendorName: 'Test Vendor', templateStatus: 'ACTIVE', lastActivity: '2026-06-10' },
  kpis: { pendingOrders: 2, awaitingPdf: 1, awaitingBillCapture: 1, totalVendorBillAmount: 4500, outstandingBalance: 4500, parseSuccessRate: 95 },
  template: { status: 'ACTIVE', hasTemplateJson: true, hasSamplePdf: true, active: true },
  billing: { billsCaptured: 5, unpaidPurchaseInvoices: 1, outstandingAmount: 4500, ledgerBalance: 4500 },
  exceptions: { mismatchCount: 0, parseFailureCount: 0, missingTemplate: false, awaitingPdfTooLong: 0 }
};

const MOCK_LEDGER = {
  openingBalance: 0, closingBalance: 4500, balance: 4500,
  entries: [{ date: '2026-06-10', voucherType: 'Purchase Invoice', voucherNo: 'ACC-PINV-2026-00001', reference: '', debit: 4500, credit: 0, netChange: 4500, runningBalance: 4500 }],
  categorySummary: [{ category: 'Spices', amount: 4500, balance: 4500 }],
  vendorName: 'Test Vendor'
};

const MOCK_CATEGORY_LEDGER = {
  openingBalance: 0, closingBalance: 4500, balance: 4500,
  entries: [{ date: '2026-06-10', voucherType: 'Purchase Invoice', voucherNo: 'ACC-PINV-2026-00001', reference: '', debit: 4500, credit: 0, netChange: 4500, runningBalance: 4500 }],
  categoryId: 'Spices', categoryLabel: 'Spices', vendorName: 'Test Vendor'
};

async function setupAuth(page: import('@playwright/test').Page) {
  await page.addInitScript(() => {
    localStorage.setItem('aas_auth_token', 'test-token');
    localStorage.setItem('aas_auth_role', 'Administrator');
    localStorage.setItem('aas_auth_features', JSON.stringify(['vendor_ops.view', 'dashboard.view', 'orders.view']));
    localStorage.setItem('aas_auth_home_route', '/vendor-ops');
  });
}

async function mockApis(page: import('@playwright/test').Page, exportRequests?: string[]) {
  await page.route('**/api/me', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ email: 'admin@aas.com', role: 'Administrator', features: ['vendor_ops.view', 'dashboard.view', 'orders.view'], homeRoute: '/vendor-ops' }) })
  );

  await page.route('**/api/vendor-ops/**', async route => {
    const url = route.request().url();
    const path = new URL(url).pathname;

    if (path === '/api/vendor-ops/summary') {
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_SUMMARY) });
    }
    if (path.endsWith('/ledger/category/export')) {
      exportRequests?.push(url);
      return route.fulfill({ status: 200, contentType: 'text/csv', body: 'date,voucher\n2026-06-10,ACC-PINV-001' });
    }
    if (path.endsWith('/ledger/categories/export')) {
      exportRequests?.push(url);
      return route.fulfill({ status: 200, contentType: 'text/csv', body: 'category,balance\nSpices,4500' });
    }
    if (path.endsWith('/ledger/export')) {
      exportRequests?.push(url);
      return route.fulfill({ status: 200, contentType: 'text/csv', body: 'date,voucher\n2026-06-10,ACC-PINV-001' });
    }
    if (path.includes('/ledger/category')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_CATEGORY_LEDGER) });
    }
    if (path.includes('/ledger')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_LEDGER) });
    }
    if (path.includes('/orders')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) });
    }
    if (path.includes('/analytics')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ vendorId: 'VENDOR-1', ordersByStatus: [], billedAmountByBranch: [], topItemsByQty: [], topItemsByValue: [], turnaround: { avgAssignmentToPdfHours: 0, avgPdfToBillHours: 0, parseSuccessRate: 95, billCaptureRate: 80 } }) });
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_DETAIL) });
  });

  await page.route('**/api/vendor-ops/ledger/**', async route => {
    const url = route.request().url();
    exportRequests?.push(url);
    return route.fulfill({ status: 200, contentType: 'text/csv', body: 'vendorId,balance\nVENDOR-1,4500' });
  });
}

test('download all vendor ledgers button shows CSV/XLSX/PDF menu', async ({ page }) => {
  await setupAuth(page);
  await mockApis(page);

  await page.goto('/vendor-ops');
  await expect(page.locator('.kpi-grid')).toBeVisible({ timeout: 8000 });

  const downloadBtn = page.locator('[aria-label="Download all vendor ledgers"]');
  await expect(downloadBtn).toBeVisible();
  await downloadBtn.click();

  await expect(page.getByRole('menuitem', { name: /CSV/i })).toBeVisible();
  await expect(page.getByRole('menuitem', { name: /Excel/i })).toBeVisible();
  await expect(page.getByRole('menuitem', { name: /PDF/i })).toBeVisible();
});

test('download all vendor ledgers by category button shows CSV/XLSX/PDF menu', async ({ page }) => {
  await setupAuth(page);
  await mockApis(page);

  await page.goto('/vendor-ops');
  await expect(page.locator('.kpi-grid')).toBeVisible({ timeout: 8000 });

  const categoryBtn = page.locator('[aria-label="Download all vendor ledgers by category"]');
  await expect(categoryBtn).toBeVisible();
  await categoryBtn.click();

  await expect(page.getByRole('menuitem', { name: /CSV/i })).toBeVisible();
  await expect(page.getByRole('menuitem', { name: /Excel/i })).toBeVisible();
  await expect(page.getByRole('menuitem', { name: /PDF/i })).toBeVisible();
});

test('category ledger download shows menu and sends format=xlsx', async ({ page }) => {
  await setupAuth(page);
  const capturedUrls: string[] = [];
  await mockApis(page, capturedUrls);

  await page.goto('/vendor-ops/VENDOR-1');
  await expect(page.locator('.detail-grid')).toBeVisible({ timeout: 10000 });
  await expect(page.locator('.category-summary')).toBeVisible({ timeout: 10000 });

  const downloadBtn = page.locator('[aria-label="Download selected category ledger"]').first();
  await expect(downloadBtn).toBeVisible();
  await downloadBtn.click();

  const csvItem = page.getByRole('menuitem', { name: /CSV/i });
  const xlsxItem = page.getByRole('menuitem', { name: /Excel/i });
  const pdfItem = page.getByRole('menuitem', { name: /PDF/i });
  await expect(csvItem).toBeVisible();
  await expect(xlsxItem).toBeVisible();
  await expect(pdfItem).toBeVisible();

  await xlsxItem.click();
  await page.waitForTimeout(600);

  expect(capturedUrls.some(u => u.includes('format=xlsx') && u.includes('/ledger/category/export')),
    `Expected format=xlsx in category export. Got: ${capturedUrls.join(', ')}`).toBeTruthy();
});

test('category summary download shows menu and sends format=pdf', async ({ page }) => {
  await setupAuth(page);
  const capturedUrls: string[] = [];
  await mockApis(page, capturedUrls);

  await page.goto('/vendor-ops/VENDOR-1');
  await expect(page.locator('.detail-grid')).toBeVisible({ timeout: 10000 });
  await expect(page.locator('.category-summary')).toBeVisible({ timeout: 10000 });

  const catSummaryBtn = page.locator('[aria-label="Download vendor ledger category summary"]');
  await expect(catSummaryBtn).toBeVisible();
  await catSummaryBtn.click();

  await expect(page.getByRole('menuitem', { name: /PDF/i })).toBeVisible();
  await page.getByRole('menuitem', { name: /PDF/i }).click();
  await page.waitForTimeout(600);

  expect(capturedUrls.some(u => u.includes('format=pdf') && u.includes('/ledger/categories/export')),
    `Expected format=pdf in categories export. Got: ${capturedUrls.join(', ')}`).toBeTruthy();
});
