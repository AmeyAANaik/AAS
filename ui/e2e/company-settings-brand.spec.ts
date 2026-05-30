import { expect, test } from '@playwright/test';

test('Company Settings updates shell company name', async ({ page }) => {
  await page.goto('/login?returnUrl=/company-settings');

  await page.getByRole('textbox', { name: 'Username' }).fill(process.env.PLAYWRIGHT_USERNAME ?? 'Administrator');
  await page.getByRole('textbox', { name: 'Password' }).fill(process.env.PLAYWRIGHT_PASSWORD ?? 'admin');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.waitForURL(/\/company-settings$/);

  const desiredName = 'sidhvinaya';

  await page.getByLabel('Company Name').fill(desiredName);
  await page.getByRole('button', { name: 'Save Company Details' }).click();

  await expect(page.getByText('Company details updated.')).toBeVisible();
  await expect(page.locator('.header-company')).toHaveText(desiredName, { timeout: 15000 });
});

