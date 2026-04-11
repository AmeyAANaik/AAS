import { chromium } from 'playwright';
import path from 'node:path';

const UI_BASE_URL = process.env.UI_BASE_URL || 'http://localhost:4200';
const USERNAME = process.env.USERNAME || 'Administrator';
const PASSWORD = process.env.PASSWORD || 'admin';
const ORDER_ID = process.env.ORDER_ID;
const VENDOR_NAME = process.env.VENDOR_NAME || '';
const PDF_PATH = process.env.PDF_PATH || path.resolve(process.cwd(), 'images/vendor_order.pdf');

if (!ORDER_ID) {
  throw new Error('ORDER_ID env var is required');
}

async function main() {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();

  try {
    await page.goto(`${UI_BASE_URL}/login?returnUrl=%2Forders`, { waitUntil: 'networkidle' });
    await page.fill('input[name="username"]', USERNAME);
    await page.fill('input[name="password"]', PASSWORD);
    await page.click('button.btn-primary');
    await page.waitForURL('**/orders', { timeout: 30_000 });

    // Ensure the freshly created order is in the table.
    const refreshBtn = page.locator('button:has-text("Refresh")').first();
    if (await refreshBtn.isVisible().catch(() => false)) {
      await refreshBtn.click();
      await page.waitForTimeout(800);
    }

    // Search for the order id and click Manage (button in the matching row).
    const searchInput = page.locator('.orders-list-card input[matinput]').first();
    await searchInput.waitFor({ timeout: 30_000 });
    await searchInput.fill(ORDER_ID);
    await page.waitForTimeout(400);

    const row = page.locator('tr.mat-mdc-row', { hasText: ORDER_ID }).first();
    await row.waitFor({ timeout: 30_000 });
    await row.locator('button:has-text("Manage")').click();
    await page.waitForSelector('.manage-modal', { timeout: 30_000 });

    // If vendor assignment step is active, assign vendor.
    if (await page.locator('text=Step 1: Vendor Assignment').isVisible()) {
      const assignBtn = page.locator('button:has-text("Assign Vendor")');
      if (await assignBtn.isVisible()) {
        await page.locator('mat-select').first().click();
        await page.waitForSelector('mat-option', { timeout: 30_000 });
        if (VENDOR_NAME) {
          const opt = page.locator('mat-option', { hasText: VENDOR_NAME }).first();
          if (await opt.count()) {
            await opt.click();
          } else {
            await page.locator('mat-option').nth(1).click();
          }
        } else {
          await page.locator('mat-option').nth(1).click(); // first real vendor option
        }
        await assignBtn.click();
        await page.waitForTimeout(800);
      }
    }

    // Upload PDF using hidden input.
    const fileInput = page.locator('input[type="file"][accept=".pdf"]');
    await fileInput.setInputFiles(PDF_PATH);
    const uploadRespPromise = page.waitForResponse((r) => r.url().includes('/vendor-pdf') && r.request().method() === 'POST', { timeout: 60_000 });
    await page.click('button:has-text("Upload and Parse PDF")');
    const uploadResp = await uploadRespPromise;
    let uploadJson = null;
    try {
      uploadJson = await uploadResp.json();
    } catch {
      // ignore
    }
    if (uploadJson) {
      const count = Array.isArray(uploadJson.orderItems) ? uploadJson.orderItems.length : 0;
      console.log(JSON.stringify({ vendorPdfStatus: uploadResp.status(), vendorPdfOrderItems: count, template: uploadJson.template?.key ?? null }));
    } else {
      console.log(JSON.stringify({ vendorPdfStatus: uploadResp.status(), vendorPdfOrderItems: null, template: null }));
    }

    // Wait for the review section.
    await page.waitForSelector('text=Review invoice items', { timeout: 60_000 });
    await page.waitForSelector('.pricing-summary .value', { timeout: 30_000 });

    const values = page.locator('.pricing-summary .value');
    const beforeItemsTotalText = (await values.nth(0).innerText()).trim();

    // Click + on the first row with a non-zero rate so totals should change.
    const rows = page.locator('.orders-table.compact tr.mat-mdc-row');
    const rowCount = await rows.count();
    let clicked = false;
    let clickedInfo = null;
    for (let i = 0; i < rowCount; i++) {
      const row = rows.nth(i);
      const rateVal = await row.locator('td.mat-mdc-cell input[type=\"number\"]').nth(1).inputValue().catch(() => '0');
      const rate = Number(String(rateVal).replace(/,/g, ''));
      // Ensure the change is visible in the 2-decimal UI formatting.
      if (Number.isFinite(rate) && rate >= 0.01) {
        await row.locator('.qty-cell button:has(mat-icon:text(\"add\"))').click();
        clicked = true;
        clickedInfo = { row: i, rate };
        break;
      }
    }
    if (!clicked) {
      await page.click('.orders-table.compact .qty-cell button:has(mat-icon:text(\"add\"))');
      clickedInfo = { row: 0, rate: null };
    }
    console.log(JSON.stringify({ clickedInfo }));
    // Wait for totals to reflect the change (Angular/material updates can be async).
    await page.waitForFunction(
      (before) => {
        const el = document.querySelector('.pricing-summary .value');
        return !!el && el.textContent && el.textContent.trim() !== before;
      },
      beforeItemsTotalText,
      { timeout: 5_000 }
    ).catch(() => page.waitForTimeout(400));

    const afterItemsTotalText = (await values.nth(0).innerText()).trim();
    if (beforeItemsTotalText === afterItemsTotalText) {
      throw new Error(`Expected items total to change after qty +. Before=${beforeItemsTotalText} After=${afterItemsTotalText}`);
    }

    const updRespPromise = page.waitForResponse((r) => r.url().includes('/api/orders/') && r.url().endsWith('/items') && r.request().method() === 'PUT', { timeout: 60_000 });
    await page.click('button:has-text("Update order items")');
    const updResp = await updRespPromise;
    console.log(JSON.stringify({ updateItemsStatus: updResp.status() }));

    // Snack bar confirmation (best-effort; overlay timing can be flaky in headless mode).
    await page.waitForSelector('text=Order items updated.', { timeout: 5_000 }).catch(() => {});

    await browser.close();
  } catch (err) {
    const outDir = path.resolve(process.cwd(), 'temp');
    await page.screenshot({ path: path.join(outDir, `manage-order-failure-${Date.now()}.png`), fullPage: true });
    await browser.close();
    throw err;
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
