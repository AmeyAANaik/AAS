import { test, expect } from '@playwright/test';

test('debug branch detail page', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('aas_auth_token', 'test-token');
    localStorage.setItem('aas_auth_role', 'Administrator');
    localStorage.setItem('aas_auth_features', JSON.stringify(['branch_ops.view', 'dashboard.view', 'orders.view']));
    localStorage.setItem('aas_auth_home_route', '/branch-ops');
  });
  const intercepted: string[] = [];
  await page.on('request', req => {
    if (req.url().includes('/api/')) intercepted.push(req.url());
  });
  await page.route('**/api/me', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ email: 'admin@aas.com', role: 'Administrator', features: ['branch_ops.view', 'dashboard.view', 'orders.view'], homeRoute: '/branch-ops' }) })
  );
  await page.route('**/api/branch-ops/summary', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ totals: { totalBranches: 1, branchesWithPendingOrders: 1, totalPendingOrders: 2, awaitingVendorAssignment: 1, awaitingVendorResponse: 1, openReceivableAmount: 5000 }, branches: [{ branchId: 'BRANCH-1', branchName: 'Test Branch', pendingOrders: 2, awaitingVendorAssignment: 1, awaitingVendorResponse: 1, inProgress: 0, openReceivableAmount: 5000, lastActivity: '2026-06-10', location: 'Pune', ledgerBalance: 5000 }] }) })
  );
  await page.route(/\/api\/branch-ops\/BRANCH-1$/, route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ branch: { branchId: 'BRANCH-1', branchName: 'Test Branch' }, kpis: { openReceivableAmount: 5000, invoicedAmount: 6000, paymentCollectionRate: 83 }, billing: { ledgerBalance: 5000, openInvoices: 1 }, exceptions: { overdueInvoices: 0 } }) })
  );
  await page.route('**/api/branch-ops/BRANCH-1/orders', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) })
  );
  await page.route(/\/api\/branch-ops\/BRANCH-1\/ledger\b/, route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ openingBalance: 0, closingBalance: 5000, balance: 5000, entries: [{ date: '2026-06-10', voucherType: 'Sales Invoice', voucherNo: 'SINV-001', reference: '', debit: 5000, credit: 0, netChange: 5000, runningBalance: 5000 }], categorySummary: [{ category: 'Spices', amount: 5000, balance: 5000 }] }) })
  );
  await page.route(/\/api\/branch-ops\/BRANCH-1\/ledger\/category\b/, route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ openingBalance: 0, closingBalance: 5000, balance: 5000, entries: [{ date: '2026-06-10', voucherType: 'Sales Invoice', voucherNo: 'SINV-001', reference: '', debit: 5000, credit: 0, netChange: 5000, runningBalance: 5000 }], categoryId: 'Spices', categoryLabel: 'Spices' }) })
  );
  await page.goto('/branch-ops/BRANCH-1');
  await page.waitForTimeout(5000);
  await page.screenshot({ path: 'test-results/debug-branch-detail.png', fullPage: true });
  console.log('URL:', page.url());
  console.log('Intercepted API calls:', JSON.stringify(intercepted, null, 2));
  const categorySummaryVisible = await page.locator('.category-summary').isVisible();
  console.log('.category-summary visible:', categorySummaryVisible);
  const kpiVisible = await page.locator('.kpi-grid').isVisible();
  console.log('.kpi-grid visible:', kpiVisible);
  const ledgerPanel = await page.locator('.ledger-panel').isVisible();
  console.log('.ledger-panel visible:', ledgerPanel);
  console.log('Body snippet:', (await page.locator('body').innerText()).substring(0, 500));
});
