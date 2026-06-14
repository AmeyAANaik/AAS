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
  await expect(page.getByText('Analytics Operational workspace')).toBeVisible();
  await expect(page.getByText('Reports', { exact: true })).toHaveCount(0);

  await page.getByRole('button', { name: 'Run' }).click();
  await expect(page.getByText(/row\(s\) loaded\./)).toBeVisible({ timeout: 15000 });

  await expect(page.getByLabel('Vendor')).toBeVisible();
  await expect(page.getByLabel('Branch')).toBeVisible();
  await expect(page.getByLabel('Category')).toBeVisible();
  await expect(page.getByLabel('Item')).toBeVisible();

  const revenueCard = page.locator('.kpi-card', { has: page.locator('.kpi-label', { hasText: 'TOTAL REVENUE' }) });
  const ordersCard = page.locator('.kpi-card', { has: page.locator('.kpi-label', { hasText: 'TOTAL ORDERS' }) });
  await expect(revenueCard).toBeVisible();
  await expect(ordersCard).toBeVisible();

  const revenueText = await revenueCard.locator('.kpi-value').innerText();
  const ordersText = await ordersCard.locator('.kpi-value').innerText();

  expect(parseDisplayNumber(revenueText)).toBe(Math.round(Number(revenueKpi?.value ?? 0)));
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
    const code = String(item?.item_code ?? item?.name ?? '').trim();
    const name = String(item?.item_name ?? '').trim();
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

  await page.getByLabel('From').fill(from);
  await page.getByLabel('To').fill(to);
  await page.getByLabel('Item').fill(chosenItem);
  await expect(page.getByRole('button', { name: 'Run' })).toBeEnabled();
  await page.getByRole('button', { name: 'Run' }).click();
  await expect(page.getByText(new RegExp(`${expectedRows} row\\(s\\) loaded\\.`))).toBeVisible({ timeout: 15000 });

  await expect(page.locator('canvas')).toBeVisible();
  await expect(page.locator('.data-table tbody tr')).toHaveCount(expectedRows);
  await expect(page.locator('.data-table')).toContainText(chosenItem.split(' - ')[0]);
});
