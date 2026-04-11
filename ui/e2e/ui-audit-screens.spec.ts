import { test } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

test('capture UI audit screenshots', async ({ page }) => {
  const outDir = path.resolve(process.cwd(), 'ui/test-artifacts/ui-audit');
  fs.mkdirSync(outDir, { recursive: true });

  await page.goto('/login?returnUrl=/orders');
  await page.getByRole('textbox', { name: 'Username' }).fill('Administrator');
  await page.getByRole('textbox', { name: 'Password' }).fill('admin');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.waitForURL(/\/orders$/);

  const routes: Array<[string, string]> = [
    ['/orders', 'orders.png'],
    ['/bills', 'bills.png'],
    ['/vendors', 'vendors.png'],
    ['/branches', 'branches.png'],
    ['/categories', 'categories.png'],
    ['/items', 'items.png'],
    ['/stock', 'stock.png'],
    ['/reports', 'reports.png'],
    ['/admin/dashboard', 'dashboard.png']
  ];

  for (const [route, file] of routes) {
    await page.goto(route);
    if (route === '/admin/dashboard') {
      await page.waitForSelector('.kpi-grid', { timeout: 15000 });
    } else {
      await page.waitForTimeout(900);
    }
    await page.screenshot({ path: path.join(outDir, file), fullPage: true });
  }

  await page.goto('/orders');
  await page.waitForTimeout(800);
  const createPanel = page.getByRole('button', { name: /Create a new order/i }).first();
  if (await createPanel.isVisible()) {
    await createPanel.click();
    await page.waitForTimeout(600);
    await page.screenshot({ path: path.join(outDir, 'orders-create-open.png'), fullPage: true });
  }
});
