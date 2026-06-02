import { expect, test } from '@playwright/test';

function parseCardNumber(text: string): number {
  const cleaned = text.replace(/[^\d.-]/g, '');
  const value = Number(cleaned);
  return Number.isFinite(value) ? value : 0;
}

test('dashboard “with dues” cards match ops summaries', async ({ page, request }) => {
  const baseUrl = process.env.PLAYWRIGHT_API_BASE_URL ?? 'http://localhost:8083';
  const username = process.env.PLAYWRIGHT_USERNAME ?? 'Administrator';
  const password = process.env.PLAYWRIGHT_PASSWORD ?? 'admin';

  const loginRes = await request.post(`${baseUrl}/api/auth/login`, { data: { username, password } });
  expect(loginRes.ok()).toBeTruthy();
  const token = (await loginRes.json()).accessToken as string;
  expect(token).toBeTruthy();
  const authHeaders = { Authorization: `Bearer ${token}` };

  await request.post(`${baseUrl}/api/setup/ensure`, { headers: authHeaders, data: {} });

  const [branchOpsRes, vendorOpsRes] = await Promise.all([
    request.get(`${baseUrl}/api/branch-ops/summary`, { headers: authHeaders }),
    request.get(`${baseUrl}/api/vendor-ops/summary`, { headers: authHeaders }),
  ]);
  expect(branchOpsRes.ok()).toBeTruthy();
  expect(vendorOpsRes.ok()).toBeTruthy();

  const branchOpsJson = await branchOpsRes.json();
  const vendorOpsJson = await vendorOpsRes.json();

  const branches = Array.isArray(branchOpsJson?.branches) ? branchOpsJson.branches : [];
  const vendors = Array.isArray(vendorOpsJson?.vendors) ? vendorOpsJson.vendors : [];

  const expectedBranchesWithDues = branches.filter((row: any) => (Number(row?.openReceivableAmount) || 0) > 0).length;
  const expectedVendorsWithDues = vendors.filter((row: any) => (Number(row?.pendingBillAmount) || 0) > 0).length;

  await page.goto('/login?returnUrl=/admin/dashboard');
  await page.getByRole('textbox', { name: 'Username' }).fill(username);
  await page.getByRole('textbox', { name: 'Password' }).fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/\/admin\/dashboard$/);

  const branchCard = page.locator('.stat-card', { has: page.locator('.stat-label', { hasText: 'Branches with dues' }) });
  const vendorCard = page.locator('.stat-card', { has: page.locator('.stat-label', { hasText: 'Vendors with dues' }) });

  await expect(branchCard).toBeVisible({ timeout: 15000 });
  await expect(vendorCard).toBeVisible({ timeout: 15000 });

  const branchValueText = await branchCard.locator('.stat-value').innerText();
  const vendorValueText = await vendorCard.locator('.stat-value').innerText();

  const actualBranchesWithDues = parseCardNumber(branchValueText);
  const actualVendorsWithDues = parseCardNumber(vendorValueText);

  expect(actualBranchesWithDues, `Branches with dues card mismatch. expected=${expectedBranchesWithDues} actual=${actualBranchesWithDues}`).toBe(
    expectedBranchesWithDues
  );
  expect(actualVendorsWithDues, `Vendors with dues card mismatch. expected=${expectedVendorsWithDues} actual=${actualVendorsWithDues}`).toBe(
    expectedVendorsWithDues
  );
});

