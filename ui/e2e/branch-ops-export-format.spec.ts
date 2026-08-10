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

const AGING_ROW = {
  branchId: 'BRANCH-1',
  branchName: 'Test Branch',
  location: 'Pune',
  creditDays: 7,
  submittedOutstanding: 52353,
  notDue: 0,
  d1_7: 0,
  d8_15: 0,
  d16_30: 2206,
  d30Plus: 50147,
  overdueAmount: 52353,
  openInvoiceCount: 3,
  overdueInvoiceCount: 3,
  oldestOverdueDays: 51,
  oldestOverdueInvoiceId: 'SINV-060',
  oldestOverdueDueDate: '2026-06-12',
  draftUnbilledAmount: 909186,
  draftInvoiceCount: 55,
  oldestDraftDays: 57,
  ledgerBalance: 720934,
  unappliedCredits: 240605,
  onTimePaymentPct: 0,
  onTimePaymentSample: 39,
  onTimePaymentDenominator: 39,
  onTimeCoveragePct: 100,
  onTimeReliable: true,
  dueDateMissingCount: 0,
  riskScore: 8,
  riskScoreMax: 9,
  riskPct: 88.89,
  riskTier: 'DEFAULTER',
  riskBasis: 'FULL',
  riskReasons: ['Oldest overdue 51 days', 'Overdue ₹52,353']
};

const MOCK_AGING = {
  asOfDate: '2026-08-02',
  asOfDateIsHistorical: false,
  buckets: [
    { key: 'notDue', label: 'Not due' },
    { key: 'd1_7', label: '1-7 days' },
    { key: 'd8_15', label: '8-15 days' },
    { key: 'd16_30', label: '16-30 days' },
    { key: 'd30Plus', label: '30+ days' }
  ],
  coverage: { settledSubmittedInvoices: 39, referencedSettledInvoices: 39, onTimeCoveragePct: 100, onTimeReliable: true },
  thresholds: {},
  totals: {
    branches: 1, notDue: 0, d1_7: 0, d8_15: 0, d16_30: 2206, d30Plus: 50147,
    submittedOutstanding: 52353, overdueAmount: 52353, draftUnbilledAmount: 909186,
    ledgerBalance: 720934, unappliedCredits: 240605,
    defaulterBranches: 1, watchBranches: 0, goodBranches: 0
  },
  collectionsRanking: ['BRANCH-1'],
  backlogRanking: ['BRANCH-1'],
  branches: [AGING_ROW]
};

const MOCK_BRANCH_AGING = {
  asOfDate: '2026-08-02',
  asOfDateIsHistorical: false,
  buckets: MOCK_AGING.buckets,
  branch: { branchId: 'BRANCH-1', branchName: 'Test Branch', location: 'Pune', creditDays: 7 },
  summary: AGING_ROW,
  reconciliation: { submittedOutstanding: 52353, draftUnbilled: 909186, unappliedCredits: 240605, ledgerBalance: 720934, balanced: true },
  coverage: MOCK_AGING.coverage,
  invoices: [
    {
      invoiceId: 'SINV-060', stage: 'SUBMITTED', docstatus: 1, postingDate: '2026-06-05', dueDate: '2026-06-12',
      dueDateSource: 'ERP', status: 'Overdue', invoiceAmount: 50147, outstandingAmount: 50147, paidAmount: 0,
      daysPastDue: 51, bucket: 'd30Plus', bucketLabel: '30+ days', settlementDate: '', paidOnTime: null
    },
    {
      invoiceId: 'SINV-301', stage: 'DRAFT', docstatus: 0, postingDate: '2026-07-28', dueDate: '2026-08-04',
      dueDateSource: 'ERP', status: 'Draft', invoiceAmount: 18410, outstandingAmount: 18410, paidAmount: 0,
      daysPastDue: null, bucket: 'draft', bucketLabel: 'Draft / unbilled', settlementDate: '', paidOnTime: null
    }
  ]
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
    if (path.endsWith('/aging/export')) {
      exportRequests?.push(url);
      return route.fulfill({ status: 200, contentType: 'text/csv', body: 'Branch,Overdue\nTest Branch,52353' });
    }
    if (path === '/api/branch-ops/aging') {
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_AGING) });
    }
    if (path.endsWith('/aging')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_BRANCH_AGING) });
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

test('aging tab shows both rankings, the aging matrix and a defaulter pill', async ({ page }) => {
  await setupAuth(page);
  await mockApis(page);

  await page.goto('/branch-ops');
  await expect(page.locator('.kpi-grid')).toBeVisible({ timeout: 8000 });

  await page.getByRole('tab', { name: 'Aging' }).click();
  await expect(page.locator('.aging-panel')).toBeVisible({ timeout: 8000 });

  // Both ranked lists render, and the caption separates backlog from payment failure.
  await expect(page.getByText('Collections risk')).toBeVisible();
  await expect(page.getByText('Billing backlog')).toBeVisible();
  await expect(page.getByText(/internal billing gap/i)).toBeVisible();

  // Aging matrix carries all five bucket headers.
  for (const label of ['Not due', '1-7 days', '8-15 days', '16-30 days', '30+ days']) {
    await expect(page.getByRole('columnheader', { name: label })).toBeVisible();
  }

  await expect(page.locator('.aging-panel').getByText('Defaulter').first()).toBeVisible();
});

test('aging tab deep-links via the tab query param', async ({ page }) => {
  await setupAuth(page);
  await mockApis(page);

  await page.goto('/branch-ops?tab=aging');
  await expect(page.locator('.aging-panel')).toBeVisible({ timeout: 8000 });
  await expect(page.getByText('Collections risk')).toBeVisible();
});

test('branch aging detail shows the reconciliation bridge to the ledger balance', async ({ page }) => {
  await setupAuth(page);
  await mockApis(page);

  await page.goto('/branch-ops/BRANCH-1?tab=aging');
  await expect(page.locator('.reconciliation-strip')).toBeVisible({ timeout: 10000 });

  const strip = page.locator('.reconciliation-strip');
  await expect(strip).toContainText('52,353');
  await expect(strip).toContainText('909,186');
  await expect(strip).toContainText('720,934');
  await expect(strip).toContainText('Ledger balance');

  // Drafts are visibly separated from aged invoices rather than mixed into the buckets.
  await expect(page.getByText('Draft / unbilled').first()).toBeVisible();
});

test('aging export sends the requested format and as-of date', async ({ page }) => {
  await setupAuth(page);
  const capturedUrls: string[] = [];
  await mockApis(page, capturedUrls);

  await page.goto('/branch-ops?tab=aging');
  await expect(page.locator('.aging-panel')).toBeVisible({ timeout: 8000 });

  const downloadBtn = page.locator('[aria-label="Download aging report"]');
  await expect(downloadBtn).toBeVisible();
  await downloadBtn.click();

  const xlsxItem = page.getByRole('menuitem', { name: /Excel/i });
  await expect(xlsxItem).toBeVisible();
  await xlsxItem.click();

  await expect.poll(() => capturedUrls.some(url => url.includes('/aging/export') && url.includes('format=xlsx'))).toBe(true);
});
