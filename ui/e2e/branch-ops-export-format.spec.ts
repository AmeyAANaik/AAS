import { expect, test } from '@playwright/test';

const MOCK_SUMMARY = {
  totals: { totalBranches: 1, branchesWithPendingOrders: 1, totalPendingOrders: 2, awaitingVendorAssignment: 1, awaitingVendorResponse: 1, openReceivableAmount: 5000 },
  branches: [{ branchId: 'BRANCH-1', branchName: 'Test Branch', pendingOrders: 2, awaitingVendorAssignment: 1, awaitingVendorResponse: 1, inProgress: 0, openReceivableAmount: 5000, lastActivity: '2026-06-10', location: 'Pune', ledgerBalance: 5000 }]
};

const MOCK_DETAIL = {
  branch: { branchId: 'BRANCH-1', branchName: 'Test Branch' },
  kpis: { openReceivableAmount: 5000, invoicedAmount: 6000, paymentCollectionRate: 83 },
  billing: { ledgerBalance: 5000, openInvoices: 1 },
  exceptions: { overdueInvoices: 0 }
};

const MOCK_LEDGER = {
  openingBalance: 0, closingBalance: 5000, balance: 5000,
  entries: [{ date: '2026-06-10', voucherType: 'Sales Invoice', voucherNo: 'SINV-001', reference: '', debit: 5000, credit: 0, netChange: 5000, runningBalance: 5000 }],
  categorySummary: [{ category: 'Spices', amount: 5000, balance: 5000 }]
};

const MOCK_CATEGORY_LEDGER = {
  openingBalance: 0, closingBalance: 5000, balance: 5000,
  entries: [{ date: '2026-06-10', voucherType: 'Sales Invoice', voucherNo: 'SINV-001', reference: '', debit: 5000, credit: 0, netChange: 5000, runningBalance: 5000 }],
  categoryId: 'Spices', categoryLabel: 'Spices'
};

async function setupAuth(page: import('@playwright/test').Page) {
  await page.addInitScript(() => {
    localStorage.setItem('aas_auth_token', 'test-token');
    localStorage.setItem('aas_auth_role', 'Administrator');
    localStorage.setItem('aas_auth_features', JSON.stringify(['branch_ops.view', 'dashboard.view', 'orders.view']));
    localStorage.setItem('aas_auth_home_route', '/branch-ops');
  });
}

async function mockApis(page: import('@playwright/test').Page, exportRequests?: string[]) {
  await page.route('**/api/me', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ email: 'admin@aas.com', role: 'Administrator', features: ['branch_ops.view', 'dashboard.view', 'orders.view'], homeRoute: '/branch-ops' }) })
  );

  // Single handler for all branch-ops routes to avoid ordering conflicts
  await page.route('**/api/branch-ops/**', async route => {
    const url = route.request().url();
    const path = new URL(url).pathname;

    if (path === '/api/branch-ops/summary') {
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_SUMMARY) });
    }
    if (path.endsWith('/ledger/category/export')) {
      exportRequests?.push(url);
      return route.fulfill({ status: 200, contentType: 'text/csv', body: 'date,voucher\n2026-06-10,SINV-001' });
    }
    if (path.endsWith('/ledger/categories/export')) {
      exportRequests?.push(url);
      return route.fulfill({ status: 200, contentType: 'text/csv', body: 'category,balance\nSpices,5000' });
    }
    if (path.endsWith('/ledger/export')) {
      exportRequests?.push(url);
      return route.fulfill({ status: 200, contentType: 'text/csv', body: 'date,voucher\n2026-06-10,SINV-001' });
    }
    if (path.includes('/ledger/categories')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([{ category: 'Spices', amount: 5000, balance: 5000 }]) });
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
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({}) });
    }
    // Branch detail (e.g. /api/branch-ops/BRANCH-1)
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_DETAIL) });
  });

  // Global ledger/categories export (all branches)
  await page.route('**/api/branch-ops/ledger/**', async route => {
    const url = route.request().url();
    exportRequests?.push(url);
    return route.fulfill({ status: 200, contentType: 'text/csv', body: 'branchId,balance\nBRANCH-1,5000' });
  });
}

test('download all ledgers button shows CSV/XLSX/PDF menu', async ({ page }) => {
  await setupAuth(page);
  await mockApis(page);

  await page.goto('/branch-ops');
  await expect(page.locator('.kpi-grid')).toBeVisible({ timeout: 8000 });

  const downloadBtn = page.locator('[aria-label="Download all branch ledgers"]');
  await expect(downloadBtn).toBeVisible();
  await downloadBtn.click();

  await expect(page.getByRole('menuitem', { name: /CSV/i })).toBeVisible();
  await expect(page.getByRole('menuitem', { name: /Excel/i })).toBeVisible();
  await expect(page.getByRole('menuitem', { name: /PDF/i })).toBeVisible();
});

test('download all category ledgers button shows CSV/XLSX/PDF menu', async ({ page }) => {
  await setupAuth(page);
  await mockApis(page);

  await page.goto('/branch-ops');
  await expect(page.locator('.kpi-grid')).toBeVisible({ timeout: 8000 });

  const categoryBtn = page.locator('[aria-label="Download all branch ledgers by category"]');
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

  await page.goto('/branch-ops/BRANCH-1');
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

  await page.goto('/branch-ops/BRANCH-1');
  await expect(page.locator('.detail-grid')).toBeVisible({ timeout: 10000 });
  await expect(page.locator('.category-summary')).toBeVisible({ timeout: 10000 });

  const catSummaryBtn = page.locator('[aria-label="Download branch ledger category summary"]');
  await expect(catSummaryBtn).toBeVisible();
  await catSummaryBtn.click();

  await expect(page.getByRole('menuitem', { name: /PDF/i })).toBeVisible();
  await page.getByRole('menuitem', { name: /PDF/i }).click();
  await page.waitForTimeout(600);

  expect(capturedUrls.some(u => u.includes('format=pdf') && u.includes('/ledger/categories/export')),
    `Expected format=pdf in categories export. Got: ${capturedUrls.join(', ')}`).toBeTruthy();
});
