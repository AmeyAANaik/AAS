import { expect, test } from '@playwright/test';

const MOCK_SUMMARY = {
  totals: {
    totalBranches: 1,
    branchesWithPendingOrders: 1,
    totalPendingOrders: 2,
    awaitingVendorAssignment: 1,
    awaitingVendorResponse: 1,
    openReceivableAmount: 5000
  },
  branches: [
    {
      branchId: 'BRANCH-1',
      branchName: 'Test Branch',
      pendingOrders: 2,
      awaitingVendorAssignment: 1,
      awaitingVendorResponse: 1,
      inProgress: 0,
      openReceivableAmount: 5000,
      lastActivity: '2026-06-10',
      location: 'Pune',
      ledgerBalance: 5000
    }
  ]
};

const MOCK_LEDGER = {
  openingBalance: 0,
  closingBalance: 5000,
  balance: 5000,
  entries: [
    { date: '2026-06-10', voucherType: 'Sales Invoice', voucherNo: 'SINV-001', reference: '', debit: 5000, credit: 0, netChange: 5000, runningBalance: 5000 }
  ],
  categorySummary: [
    { category: 'Spices', amount: 5000, balance: 5000 }
  ]
};

const MOCK_CATEGORY_LEDGER = {
  openingBalance: 0,
  closingBalance: 5000,
  balance: 5000,
  entries: [
    { date: '2026-06-10', voucherType: 'Sales Invoice', voucherNo: 'SINV-001', reference: '', debit: 5000, credit: 0, netChange: 5000, runningBalance: 5000 }
  ],
  categoryId: 'Spices',
  categoryLabel: 'Spices'
};

async function setupAuth(page: import('@playwright/test').Page) {
  await page.addInitScript(() => {
    localStorage.setItem('aas_auth_token', 'test-token');
    localStorage.setItem('aas_auth_role', 'Administrator');
    localStorage.setItem('aas_auth_features', JSON.stringify([
      'branch_ops.view', 'dashboard.view', 'orders.view'
    ]));
    localStorage.setItem('aas_auth_home_route', '/branch-ops');
  });
}

async function mockBranchOpsApis(page: import('@playwright/test').Page) {
  await page.route('**/api/branch-ops/summary', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_SUMMARY) })
  );
  await page.route('**/api/branch-ops/BRANCH-1', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ branch: { branchId: 'BRANCH-1', branchName: 'Test Branch' }, kpis: { openReceivableAmount: 5000, invoicedAmount: 6000, paymentCollectionRate: 83 }, billing: { ledgerBalance: 5000, openInvoices: 1 }, exceptions: { overdueInvoices: 0 } }) })
  );
  await page.route('**/api/branch-ops/BRANCH-1/orders', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) })
  );
  await page.route('**/api/branch-ops/BRANCH-1/ledger*', route => {
    const url = route.request().url();
    if (url.includes('/export')) {
      route.fulfill({ status: 200, contentType: 'text/csv', body: 'date,voucherType\n2026-06-10,Sales Invoice' });
    } else {
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_LEDGER) });
    }
  });
  await page.route('**/api/branch-ops/BRANCH-1/ledger/category**', route => {
    const url = route.request().url();
    if (url.includes('/export')) {
      route.fulfill({ status: 200, contentType: 'text/csv', body: 'date,voucherType\n2026-06-10,Sales Invoice' });
    } else {
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_CATEGORY_LEDGER) });
    }
  });
  await page.route('**/api/branch-ops/BRANCH-1/ledger/categories/export**', route =>
    route.fulfill({ status: 200, contentType: 'text/csv', body: 'category,balance\nSpices,5000' })
  );
  await page.route('**/api/branch-ops/ledger/export**', route =>
    route.fulfill({ status: 200, contentType: 'text/csv', body: 'branchId,balance\nBRANCH-1,5000' })
  );
  await page.route('**/api/branch-ops/ledger/categories/export**', route =>
    route.fulfill({ status: 200, contentType: 'text/csv', body: 'branchId,category,balance\nBRANCH-1,Spices,5000' })
  );
}

test('download all ledgers button shows CSV/XLSX/PDF menu', async ({ page }) => {
  await setupAuth(page);
  await mockBranchOpsApis(page);

  await page.goto('/branch-ops');
  await expect(page.locator('table')).toBeVisible({ timeout: 5000 });

  // Find the "Download all branch ledgers" button (download icon in summary card)
  const downloadBtn = page.locator('[aria-label="Download all branch ledgers"]');
  await expect(downloadBtn).toBeVisible();
  await downloadBtn.click();

  // Menu should open with three format options
  await expect(page.getByRole('menuitem', { name: /CSV/i })).toBeVisible();
  await expect(page.getByRole('menuitem', { name: /Excel/i })).toBeVisible();
  await expect(page.getByRole('menuitem', { name: /PDF/i })).toBeVisible();
});

test('download category ledgers button shows CSV/XLSX/PDF menu', async ({ page }) => {
  await setupAuth(page);
  await mockBranchOpsApis(page);

  await page.goto('/branch-ops');
  await expect(page.locator('table')).toBeVisible({ timeout: 5000 });

  const categoryBtn = page.locator('[aria-label="Download all branch ledgers by category"]');
  await expect(categoryBtn).toBeVisible();
  await categoryBtn.click();

  await expect(page.getByRole('menuitem', { name: /CSV/i })).toBeVisible();
  await expect(page.getByRole('menuitem', { name: /Excel/i })).toBeVisible();
  await expect(page.getByRole('menuitem', { name: /PDF/i })).toBeVisible();
});

test('branch detail ledger export shows format menu and passes format param', async ({ page }) => {
  await setupAuth(page);
  await mockBranchOpsApis(page);

  // Track download requests to verify format param is sent
  const exportRequests: string[] = [];
  await page.route('**/api/branch-ops/BRANCH-1/ledger/category/export**', route => {
    exportRequests.push(route.request().url());
    route.fulfill({ status: 200, contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', body: Buffer.from('PK') });
  });

  await page.goto('/branch-ops/BRANCH-1');
  // Wait for the ledger category detail to render
  await expect(page.locator('.category-summary')).toBeVisible({ timeout: 8000 });

  // Click the "Download selected category ledger" button in the ledger panel header
  const downloadBtn = page.locator('[aria-label="Download selected category ledger"]').first();
  await expect(downloadBtn).toBeVisible();
  await downloadBtn.click();

  const xlsxItem = page.getByRole('menuitem', { name: /Excel/i });
  await expect(xlsxItem).toBeVisible();

  // Click XLSX option and verify format=xlsx is in the request URL
  await xlsxItem.click();

  await page.waitForTimeout(500);
  expect(exportRequests.some(url => url.includes('format=xlsx'))).toBeTruthy();
});

test('category summary download sends correct format', async ({ page }) => {
  await setupAuth(page);
  await mockBranchOpsApis(page);

  const capturedUrls: string[] = [];
  await page.route('**/api/branch-ops/BRANCH-1/ledger/categories/export**', route => {
    capturedUrls.push(route.request().url());
    route.fulfill({ status: 200, contentType: 'application/pdf', body: Buffer.from('%PDF-1.4') });
  });

  await page.goto('/branch-ops/BRANCH-1');
  await expect(page.locator('.category-summary')).toBeVisible({ timeout: 8000 });

  const catSummaryBtn = page.locator('[aria-label="Download branch ledger category summary"]');
  await expect(catSummaryBtn).toBeVisible();
  await catSummaryBtn.click();

  await expect(page.getByRole('menuitem', { name: /PDF/i })).toBeVisible();
  await page.getByRole('menuitem', { name: /PDF/i }).click();

  await page.waitForTimeout(500);
  expect(capturedUrls.some(url => url.includes('format=pdf'))).toBeTruthy();
});
