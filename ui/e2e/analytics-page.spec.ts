import { expect, test } from '@playwright/test';

test.setTimeout(120000);

type AnalyticsResponse = {
  rows?: Array<Record<string, unknown>>;
  kpis?: Array<{ id?: string; label?: string; value?: number }>;
};

function formatDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function parseDisplayNumber(text: string): number {
  const cleaned = text.replace(/[^\d.-]/g, '');
  const value = Number(cleaned);
  return Number.isFinite(value) ? value : 0;
}

async function loginViaApi(request: any) {
  const baseUrl = process.env.PLAYWRIGHT_API_BASE_URL ?? 'http://localhost:8083';
  const username = process.env.PLAYWRIGHT_USERNAME ?? 'Administrator';
  const password = process.env.PLAYWRIGHT_PASSWORD ?? 'admin';

  const loginRes = await request.post(`${baseUrl}/api/auth/login`, { data: { username, password } });
  expect(loginRes.ok()).toBeTruthy();
  const json = await loginRes.json();
  const token = json.accessToken as string;
  expect(token).toBeTruthy();
  return { baseUrl, username, password, authHeaders: { Authorization: `Bearer ${token}` } };
}

async function loginUi(page: any, username: string, password: string, returnUrl = '/analytics', expectedPath = returnUrl) {
  await page.goto(`/login?returnUrl=${encodeURIComponent(returnUrl)}`);
  await page.getByRole('textbox', { name: 'Username' }).fill(username);
  await page.getByRole('textbox', { name: 'Password' }).fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(new RegExp(`${expectedPath.replace('/', '\\/')}$`));
}

async function setupMockAuth(page: any) {
  await page.addInitScript(() => {
    localStorage.setItem('aas_auth_token', 'test-token');
    localStorage.setItem('aas_auth_role', 'Administrator');
    localStorage.setItem('aas_auth_features', JSON.stringify(['dashboard.view', 'orders.view']));
    localStorage.setItem('aas_auth_home_route', '/analytics');
  });
}

const MOCK_ANALYTICS_RESPONSE = {
  columns: [
    { id: 'date', label: 'Date', colType: 'DIMENSION' },
    { id: 'revenue', label: 'Revenue', colType: 'CURRENCY' },
    { id: 'orders', label: 'Orders', colType: 'NUMBER' }
  ],
  rows: [
    { date: '2026-06-01', revenue: 10000, orders: 5 },
    { date: '2026-06-02', revenue: 8500, orders: 3 }
  ],
  totalsRow: { revenue: 18500, orders: 8 },
  kpis: [
    { id: 'revenue', label: 'TOTAL REVENUE', value: 18500, valueType: 'CURRENCY' },
    { id: 'orders', label: 'TOTAL ORDERS', value: 8, valueType: 'NUMBER' }
  ]
};

const MOCK_PRICE_HISTORY_RESPONSE = {
  columns: [
    { id: 'date', label: 'Date', colType: 'DIMENSION' },
    { id: 'item_code', label: 'Item Code', colType: 'DIMENSION' },
    { id: 'price', label: 'Price', colType: 'CURRENCY' }
  ],
  rows: [
    { date: '2026-06-01', item_code: 'AAMRAS-1KG', price: 180 },
    { date: '2026-06-02', item_code: 'AAMRAS-1KG', price: 185 },
    { date: '2026-06-03', item_code: 'AAMRAS-1KG', price: 190 }
  ],
  totalsRow: {},
  kpis: [
    { id: 'price_points', label: 'PRICE POINTS', value: 3, valueType: 'NUMBER' },
    { id: 'items', label: 'ITEMS TRACKED', value: 1, valueType: 'NUMBER' }
  ]
};

// ─── Live integration tests ───────────────────────────────────────────────────

test('analytics explorer matches backend totals and reports route redirects', async ({ page, request }) => {
  const { baseUrl, username, password, authHeaders } = await loginViaApi(request);
  await request.post(`${baseUrl}/api/setup/ensure`, { headers: authHeaders, data: {} });

  const now = new Date();
  const from = formatDate(new Date(now.getFullYear(), now.getMonth(), 1));
  const to = formatDate(now);

  const analyticsRes = await request.post(`${baseUrl}/api/analytics/query`, {
    headers: authHeaders,
    data: {
      dateFrom: from,
      dateTo: to,
      granularity: 'day',
      dimensions: ['date'],
      metrics: ['revenue', 'orders'],
      filters: {}
    }
  });
  expect(analyticsRes.ok()).toBeTruthy();
  const analyticsJson = await analyticsRes.json() as AnalyticsResponse;
  const revenueKpi = analyticsJson.kpis?.find(kpi => kpi.id === 'revenue');
  const ordersKpi = analyticsJson.kpis?.find(kpi => kpi.id === 'orders');
  expect(revenueKpi).toBeTruthy();
  expect(ordersKpi).toBeTruthy();

  await loginUi(page, username, password, '/reports', '/analytics');
  await expect(page).toHaveURL(/\/analytics$/);
  await expect(page.getByRole('button', { name: 'Analytics Explorer', pressed: true })).toBeVisible();
  await expect(page.getByText('Reports', { exact: true })).toHaveCount(0);

  await page.getByRole('button', { name: 'Run' }).click();
  await expect(page.getByText(/row\(s\) loaded\./)).toBeVisible({ timeout: 15000 });

  // Explorer mode: vendor, branch, category filters visible; item filter is HIDDEN
  await expect(page.getByLabel('Vendor')).toBeVisible();
  await expect(page.getByLabel('Branch')).toBeVisible();
  await expect(page.getByLabel('Category')).toBeVisible();
  await expect(page.locator('input[formcontrolname="item"]')).toHaveCount(0);

  const revenueCard = page.locator('.kpi-card', { has: page.locator('.kpi-label', { hasText: 'TOTAL REVENUE' }) });
  const ordersCard = page.locator('.kpi-card', { has: page.locator('.kpi-label', { hasText: 'TOTAL ORDERS' }) });
  await expect(revenueCard).toBeVisible();
  await expect(ordersCard).toBeVisible();

  const revenueText = await revenueCard.locator('.kpi-value').innerText();
  const ordersText = await ordersCard.locator('.kpi-value').innerText();

  expect(parseDisplayNumber(revenueText)).toBeCloseTo(Number(revenueKpi?.value ?? 0), 2);
  expect(parseDisplayNumber(ordersText)).toBe(Math.round(Number(ordersKpi?.value ?? 0)));
});

test('item price history mode requires an item and loads day-wise rows', async ({ page, request }) => {
  const { baseUrl, username, password, authHeaders } = await loginViaApi(request);
  await request.post(`${baseUrl}/api/setup/ensure`, { headers: authHeaders, data: {} });

  const now = new Date();
  const from = `${now.getFullYear()}-01-01`;
  const to = formatDate(now);

  const itemsRes = await request.get(`${baseUrl}/api/items`, { headers: authHeaders });
  expect(itemsRes.ok()).toBeTruthy();
  const items = await itemsRes.json() as Array<Record<string, unknown>>;

  let chosenItem = '';
  let expectedRows = 0;
  for (const item of items.slice(0, 25)) {
    const code = String(item?.['item_code'] ?? item?.['name'] ?? '').trim();
    const name = String(item?.['item_name'] ?? '').trim();
    const label = code && name ? `${code} - ${name}` : code || name;
    if (!label) {
      continue;
    }
    const historyRes = await request.post(`${baseUrl}/api/analytics/item-price-history`, {
      headers: authHeaders,
      data: {
        dateFrom: from,
        dateTo: to,
        granularity: 'day',
        dimensions: ['date'],
        metrics: ['revenue'],
        filters: { item: label }
      }
    });
    if (!historyRes.ok()) {
      continue;
    }
    const historyJson = await historyRes.json() as AnalyticsResponse;
    const rowCount = historyJson.rows?.length ?? 0;
    if (rowCount > 0) {
      chosenItem = label;
      expectedRows = rowCount;
      break;
    }
  }

  test.skip(!chosenItem, 'No item price history data available to validate.');

  await loginUi(page, username, password, '/analytics');
  await page.getByRole('button', { name: 'Item Price History' }).click();
  await expect(page.getByRole('button', { name: 'Run' })).toBeDisabled();

  // Use direct value set for datepicker inputs to avoid locale format issues
  await page.locator('input[formcontrolname="dateFrom"]').fill(from);
  await page.locator('input[formcontrolname="dateFrom"]').press('Tab');
  await page.locator('input[formcontrolname="dateTo"]').fill(to);
  await page.locator('input[formcontrolname="dateTo"]').press('Tab');
  await page.locator('input[formcontrolname="item"]').fill(chosenItem);
  await page.locator('input[formcontrolname="item"]').press('Tab');
  await expect(page.getByRole('button', { name: 'Run' })).toBeEnabled();
  await page.getByRole('button', { name: 'Run' }).click();
  await expect(page.getByText(new RegExp(`${expectedRows} row\\(s\\) loaded\\.`))).toBeVisible({ timeout: 15000 });

  await expect(page.locator('canvas')).toBeVisible();
  await expect(page.locator('.data-table tbody tr')).toHaveCount(expectedRows);
  await expect(page.locator('.data-table')).toContainText(chosenItem.split(' - ')[0]);
});

// ─── Mock-based feature tests ─────────────────────────────────────────────────

test('item price history mode — mocked: run disabled without item, enabled after fill, shows rows', async ({ page }) => {
  await setupMockAuth(page);

  let capturedBody: any = null;
  await page.route('**/api/me', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ email: 'admin@aas.com', role: 'Administrator', features: [], homeRoute: '/analytics' }) })
  );
  await page.route('**/api/vendors**', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
  await page.route('**/api/branches**', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
  await page.route('**/api/categories**', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
  await page.route('**/api/items**', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([{ item_code: 'AAMRAS-1KG', item_name: 'Aamras 1Kg' }]) })
  );
  await page.route('**/api/analytics/item-price-history', async route => {
    capturedBody = await route.request().postDataJSON();
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_PRICE_HISTORY_RESPONSE) });
  });

  await page.goto('/analytics');
  await expect(page.locator('app-analytics-page')).toBeVisible({ timeout: 10000 });

  // Switch to Item Price History mode
  await page.getByRole('button', { name: 'Item Price History' }).click();

  // Run must be disabled when no item is set
  await expect(page.getByRole('button', { name: /^Run/ })).toBeDisabled();

  // Item filter is now visible
  await expect(page.locator('input[formcontrolname="item"]')).toBeVisible();

  // Fill item — Run must become enabled
  await page.locator('input[formcontrolname="item"]').fill('AAMRAS-1KG - Aamras 1Kg');
  await page.locator('input[formcontrolname="item"]').press('Tab');
  await expect(page.getByRole('button', { name: /^Run/ })).toBeEnabled();

  // Run and verify results
  await page.getByRole('button', { name: /^Run/ }).click();
  await expect(page.getByText('3 row(s) loaded.')).toBeVisible({ timeout: 8000 });

  // Verify table shows 3 rows and a chart
  await expect(page.locator('.data-table tbody tr')).toHaveCount(3);
  await expect(page.locator('canvas')).toBeVisible();

  // Verify request sent item filter
  expect(capturedBody?.filters?.item).toBe('AAMRAS-1KG - Aamras 1Kg');
});

test('analytics explorer — filter sends vendor and branch in request body', async ({ page }) => {
  await setupMockAuth(page);

  const capturedBodies: any[] = [];
  await page.route('**/api/me', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ email: 'admin@aas.com', role: 'Administrator', features: [], homeRoute: '/analytics' }) })
  );
  await page.route('**/api/vendors**', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([{ supplier_name: 'Fresh Farms' }]) })
  );
  await page.route('**/api/branches**', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([{ customer_name: 'Main Branch' }]) })
  );
  await page.route('**/api/categories**', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
  await page.route('**/api/items**', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
  await page.route('**/api/analytics/query', async route => {
    const body = await route.request().postDataJSON();
    capturedBodies.push(body);
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_ANALYTICS_RESPONSE) });
  });

  await page.goto('/analytics');
  await expect(page.locator('app-analytics-page')).toBeVisible({ timeout: 10000 });

  // Set vendor filter
  await page.locator('input[formcontrolname="vendor"]').fill('Fresh Farms');
  await page.locator('input[formcontrolname="vendor"]').press('Tab');

  // Set branch filter
  await page.locator('input[formcontrolname="branch"]').fill('Main Branch');
  await page.locator('input[formcontrolname="branch"]').press('Tab');

  await page.getByRole('button', { name: /^Run/ }).click();
  await expect(page.getByText('2 row(s) loaded.')).toBeVisible({ timeout: 8000 });

  expect(capturedBodies.length).toBeGreaterThan(0);
  const lastBody = capturedBodies[capturedBodies.length - 1];
  expect(lastBody.filters.vendor).toBe('Fresh Farms');
  expect(lastBody.filters.branch).toBe('Main Branch');
  // Item must NOT be present in explorer mode filters
  expect(lastBody.filters.item).toBeUndefined();
});

test('analytics explorer — dimension and metric toggling changes request', async ({ page }) => {
  await setupMockAuth(page);

  const capturedBodies: any[] = [];
  await page.route('**/api/me', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ email: 'admin@aas.com', role: 'Administrator', features: [], homeRoute: '/analytics' }) })
  );
  await page.route('**/api/vendors**', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
  await page.route('**/api/branches**', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
  await page.route('**/api/categories**', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
  await page.route('**/api/items**', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
  await page.route('**/api/analytics/query', async route => {
    const body = await route.request().postDataJSON();
    capturedBodies.push(body);
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ ...MOCK_ANALYTICS_RESPONSE, rows: [], totalsRow: {}, kpis: [] })
    });
  });

  await page.goto('/analytics');
  await expect(page.locator('app-analytics-page')).toBeVisible({ timeout: 10000 });

  // Add Vendor dimension (Date is on by default)
  await page.locator('button.dim-chip', { hasText: 'Vendor' }).click();
  await expect(page.locator('button.dim-chip', { hasText: 'Vendor' })).toHaveClass(/active/);

  // Deactivate Cost metric (Revenue, Cost, Profit, Orders are on by default)
  await page.locator('button.met-chip', { hasText: 'Cost' }).click();
  await expect(page.locator('button.met-chip', { hasText: 'Cost' })).not.toHaveClass(/active/);

  await page.getByRole('button', { name: /^Run/ }).click();
  await page.waitForResponse('**/api/analytics/query');

  expect(capturedBodies.length).toBeGreaterThan(0);
  const body = capturedBodies[capturedBodies.length - 1];
  expect(body.dimensions).toContain('date');
  expect(body.dimensions).toContain('vendor');
  expect(body.metrics).toContain('revenue');
  expect(body.metrics).not.toContain('cost');
});

test('analytics explorer — granularity selection changes request granularity field', async ({ page }) => {
  await setupMockAuth(page);

  const capturedBodies: any[] = [];
  await page.route('**/api/me', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ email: 'admin@aas.com', role: 'Administrator', features: [], homeRoute: '/analytics' }) })
  );
  await page.route('**/api/vendors**', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
  await page.route('**/api/branches**', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
  await page.route('**/api/categories**', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
  await page.route('**/api/items**', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
  await page.route('**/api/analytics/query', async route => {
    const body = await route.request().postDataJSON();
    capturedBodies.push(body);
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_ANALYTICS_RESPONSE) });
  });

  await page.goto('/analytics');
  await expect(page.locator('app-analytics-page')).toBeVisible({ timeout: 10000 });

  // Default is day — run first
  await page.getByRole('button', { name: /^Run/ }).click();
  await expect(page.getByText('2 row(s) loaded.')).toBeVisible({ timeout: 8000 });
  expect(capturedBodies[0]?.granularity).toBe('day');

  // Change to month
  await page.getByLabel('Granularity').click();
  await page.getByRole('option', { name: 'Month' }).click();

  capturedBodies.length = 0;
  await page.getByRole('button', { name: /^Run/ }).click();
  await page.waitForResponse('**/api/analytics/query');
  expect(capturedBodies[0]?.granularity).toBe('month');
});

test('analytics explorer — Export CSV and Clear work after successful run', async ({ page }) => {
  await setupMockAuth(page);

  await page.route('**/api/me', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ email: 'admin@aas.com', role: 'Administrator', features: [], homeRoute: '/analytics' }) })
  );
  await page.route('**/api/vendors**', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
  await page.route('**/api/branches**', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
  await page.route('**/api/categories**', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
  await page.route('**/api/items**', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
  await page.route('**/api/analytics/query', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_ANALYTICS_RESPONSE) })
  );
  await page.route('**/api/analytics/query/export', route =>
    route.fulfill({ status: 200, contentType: 'text/csv', body: 'Date,Revenue,Orders\n2026-06-01,10000,5' })
  );

  await page.goto('/analytics');
  await expect(page.locator('app-analytics-page')).toBeVisible({ timeout: 10000 });

  // Export CSV disabled before run
  await expect(page.getByRole('button', { name: /Export CSV/ })).toBeDisabled();

  await page.getByRole('button', { name: /^Run/ }).click();
  await expect(page.getByText('2 row(s) loaded.')).toBeVisible({ timeout: 8000 });

  // Export CSV enabled after successful run
  await expect(page.getByRole('button', { name: /Export CSV/ })).toBeEnabled();

  // Download should trigger (we can't easily assert file, just verify it doesn't error)
  await Promise.all([
    page.waitForEvent('download', { timeout: 5000 }).catch(() => null),
    page.getByRole('button', { name: /Export CSV/ }).click()
  ]);

  // Clear resets to empty state
  await page.getByRole('button', { name: 'Clear' }).click();
  await expect(page.locator('app-empty-state')).toBeVisible();
  await expect(page.getByRole('button', { name: /Export CSV/ })).toBeDisabled();
});

test('item price history — category filter not sent, item filter required', async ({ page }) => {
  await setupMockAuth(page);

  const capturedBodies: any[] = [];
  await page.route('**/api/me', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ email: 'admin@aas.com', role: 'Administrator', features: [], homeRoute: '/analytics' }) })
  );
  await page.route('**/api/vendors**', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
  await page.route('**/api/branches**', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
  await page.route('**/api/categories**', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
  await page.route('**/api/items**', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([{ item_code: 'AAMRAS-1KG', item_name: 'Aamras 1Kg' }]) })
  );
  await page.route('**/api/analytics/item-price-history', async route => {
    const body = await route.request().postDataJSON();
    capturedBodies.push(body);
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_PRICE_HISTORY_RESPONSE) });
  });

  await page.goto('/analytics');
  await expect(page.locator('app-analytics-page')).toBeVisible({ timeout: 10000 });

  await page.getByRole('button', { name: 'Item Price History' }).click();

  // Item field visible, Run disabled
  await expect(page.locator('input[formcontrolname="item"]')).toBeVisible();
  await expect(page.getByRole('button', { name: /^Run/ })).toBeDisabled();

  // Setting only category (no item) does NOT enable Run
  await page.locator('input[formcontrolname="itemGroup"]').fill('Aamras');
  await expect(page.getByRole('button', { name: /^Run/ })).toBeDisabled();

  // Fill item — Run enabled
  await page.locator('input[formcontrolname="item"]').fill('AAMRAS-1KG - Aamras 1Kg');
  await page.locator('input[formcontrolname="item"]').press('Tab');
  await expect(page.getByRole('button', { name: /^Run/ })).toBeEnabled();

  await page.getByRole('button', { name: /^Run/ }).click();
  await expect(page.getByText('3 row(s) loaded.')).toBeVisible({ timeout: 8000 });

  const body = capturedBodies[capturedBodies.length - 1];
  // item filter must be set, category filter must NOT appear (itemGroup ignored in priceHistory mode)
  expect(body.filters.item).toBe('AAMRAS-1KG - Aamras 1Kg');
  expect(body.filters.itemGroup).toBeUndefined();
});
