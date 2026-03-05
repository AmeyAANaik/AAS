import { chromium } from 'playwright';
import path from 'node:path';

const UI_BASE_URL = process.env.UI_BASE_URL || 'http://localhost:4201';
const USERNAME = process.env.USERNAME || 'Administrator';
const PASSWORD = process.env.PASSWORD || 'admin';
const PDF_PATH = process.env.PDF_PATH || path.resolve(process.cwd(), '../images/vendor_invoice_freshharvest.pdf');

async function main() {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();

  await page.goto(`${UI_BASE_URL}/login?returnUrl=%2Fvendors`, { waitUntil: 'networkidle' });
  await page.fill('input[name="username"]', USERNAME);
  await page.fill('input[name="password"]', PASSWORD);
  await page.click('button.btn-primary');
  await page.waitForURL('**/vendors', { timeout: 30_000 });

  // Select first vendor row (to open edit form).
  await page.waitForSelector('.vendor-page table', { timeout: 30_000 });
  await page.click('.vendor-page table tr.mat-mdc-row');

  // Upload sample PDF.
  const fileInput = page.locator('input[type="file"][accept="application/pdf"]');
  await fileInput.setInputFiles(PDF_PATH);

  // Assert message shows chosen template key.
  await page.waitForSelector('.form-status', { timeout: 30_000 });
  const status = await page.locator('.form-status').innerText();
  if (!status.includes('Chosen template:')) {
    throw new Error(`Expected template chosen message, got: ${status}`);
  }

  await browser.close();
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});

