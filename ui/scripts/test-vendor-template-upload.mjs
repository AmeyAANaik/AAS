import { chromium } from 'playwright';

const UI_BASE_URL = process.env.UI_BASE_URL || 'http://localhost:4200';
const USERNAME = process.env.USERNAME || 'Administrator';
const PASSWORD = process.env.PASSWORD || 'admin';
const VENDOR_NAME = process.env.VENDOR_NAME || 'SANSHRAY FOODS';

const TEMPLATE_JSON = JSON.stringify(
  {
    version: 1,
    itemLineRegex:
      '^(?:\\\\d+\\\\s*[\\\\|\\\\]]?\\\\s*)?(?<name>.+?)\\\\s+(?<hsn>\\\\d{4,10})\\\\s+(?:\\\\d+\\\\s*%\\\\s*\\\\|?)\\\\s*(?<qty>\\\\d+(?:\\\\.\\\\d+)?)\\\\s*(?:[A-Za-z]{1,6})\\\\s*\\\\)?\\\\s*(?:\\\\|?\\\\s*\\\\d+(?:,\\\\d{3})*(?:\\\\.\\\\d+)?)?\\\\s+(?<rate>\\\\d+(?:,\\\\d{3})*(?:\\\\.\\\\d+)?)\\\\s+(?<amount>\\\\d+(?:,\\\\d{3})*(?:\\\\.\\\\d+)?)$',
    billDateRegex: '(?i)\\\\bdated\\\\b\\\\s*(?<date>[^\\\\n\\\\r]+)'
  },
  null,
  2
);

async function main() {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();

  await page.goto(`${UI_BASE_URL}/login?returnUrl=%2Fvendors`, { waitUntil: 'networkidle' });
  await page.fill('input[name="username"]', USERNAME);
  await page.fill('input[name="password"]', PASSWORD);
  await page.click('button.btn-primary');
  await page.waitForURL('**/vendors', { timeout: 30_000 });

  // Select vendor row (to open edit form).
  await page.waitForSelector('.vendor-page table', { timeout: 30_000 });
  const vendorRow = page.locator('.vendor-page table tr.mat-mdc-row', { hasText: VENDOR_NAME }).first();
  if ((await vendorRow.count()) > 0) {
    await vendorRow.click();
  } else {
    await page.click('.vendor-page table tr.mat-mdc-row');
  }

  // Enable template parsing and paste JSON.
  const toggle = page.locator('mat-slide-toggle', { hasText: 'Enable template-based parsing' }).first();
  if ((await toggle.count()) > 0) {
    // Ensure enabled (best-effort).
    const ariaChecked = await toggle.getAttribute('aria-checked').catch(() => null);
    if (ariaChecked !== 'true') {
      await toggle.click();
    }
  }
  const jsonField = page.locator('textarea[formcontrolname="invoiceTemplateJson"]').first();
  await jsonField.fill(TEMPLATE_JSON);

  await page.click('button:has-text("Update vendor"), button:has-text("Save vendor")');
  await page.waitForSelector('.vendor-page .feedback', { timeout: 30_000 });
  const status = (await page.locator('.vendor-page .feedback').first().innerText()).trim();
  if (!status.includes('Vendor updated.') && !status.includes('Vendor saved.')) {
    throw new Error(`Expected vendor saved/updated message, got: ${status}`);
  }

  await browser.close();
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
