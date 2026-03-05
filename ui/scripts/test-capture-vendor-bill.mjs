import { chromium } from 'playwright';
import path from 'node:path';

const UI_BASE_URL = process.env.UI_BASE_URL || 'http://localhost:4200';
const USERNAME = process.env.USERNAME || 'Administrator';
const PASSWORD = process.env.PASSWORD || 'admin';
const ORDER_ID = process.env.ORDER_ID;
const VENDOR_NAME = process.env.VENDOR_NAME || '';
const PDF_PATH = process.env.PDF_PATH || path.resolve(process.cwd(), 'temp/Sales_3231.pdf');

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

    // Ensure the order is present.
    const refreshBtn = page.locator('button:has-text("Refresh")').first();
    if (await refreshBtn.isVisible().catch(() => false)) {
      await refreshBtn.click();
      await page.waitForTimeout(800);
    }

    // Search + open Manage.
    const searchInput = page.locator('.orders-list-card input[matinput]').first();
    await searchInput.waitFor({ timeout: 30_000 });
    await searchInput.fill(ORDER_ID);
    await page.waitForTimeout(400);

    const row = page.locator('tr.mat-mdc-row', { hasText: ORDER_ID }).first();
    await row.waitFor({ timeout: 30_000 });
    await row.locator('button:has-text("Manage")').click();
    await page.waitForSelector('.manage-modal', { timeout: 30_000 });

    // Step 1: assign vendor if needed.
    if (await page.locator('text=Step 1: Vendor Assignment').isVisible().catch(() => false)) {
      const assignBtn = page.locator('button:has-text("Assign Vendor")');
      if (await assignBtn.isVisible().catch(() => false)) {
        await page.locator('mat-select').first().click();
        await page.waitForSelector('mat-option', { timeout: 30_000 });
        if (VENDOR_NAME) {
          const opt = page.locator('mat-option', { hasText: VENDOR_NAME }).first();
          if ((await opt.count()) > 0) {
            await opt.click();
          } else {
            await page.locator('mat-option').nth(1).click();
          }
        } else {
          await page.locator('mat-option').nth(1).click();
        }
        await assignBtn.click();
        await page.waitForTimeout(900);
      }
    }

    // Step 2: upload pdf if upload UI is present.
    const fileInput = page.locator('input[type="file"][accept=".pdf"]');
    if ((await fileInput.count()) > 0) {
      await fileInput.setInputFiles(PDF_PATH);
      const uploadRespPromise = page.waitForResponse(
        (r) => r.url().includes('/vendor-pdf') && r.request().method() === 'POST',
        { timeout: 60_000 }
      );
      await page.click('button:has-text("Upload and Parse PDF")');
      const uploadResp = await uploadRespPromise;
      let uploadJson = null;
      try {
        uploadJson = await uploadResp.json();
      } catch {
        // ignore
      }
      console.log(
        JSON.stringify({
          vendorPdfStatus: uploadResp.status(),
          vendorPdfOrderItems: Array.isArray(uploadJson?.orderItems) ? uploadJson.orderItems.length : null,
          template: uploadJson?.template?.key ?? null
        })
      );
      await page.waitForSelector('text=Review invoice items', { timeout: 60_000 });
    }

    // Step 3: vendor bill capture is available in status VENDOR_PDF_RECEIVED.
    await page.waitForSelector('text=Step 3: Vendor Bill Capture', { timeout: 30_000 });

    const billTotalInput = page.locator('input[formcontrolname], input[formcontrol]').filter({ hasText: '' });
    // Use a stable selector based on the mat-label text.
    const billTotalField = page.locator('mat-form-field', { hasText: 'Vendor Bill Total' }).locator('input').first();
    const billRefField = page.locator('mat-form-field', { hasText: 'Vendor Bill Reference' }).locator('input').first();
    const captureBtn = page.locator('button:has-text("Capture Vendor Bill")').first();

    await billRefField.fill('UI-TEST-REF');

    // Force mismatch
    await billTotalField.fill('999999');
    await page.waitForTimeout(300);
    const errorVisible = await page.locator('.bill-validation', { hasText: 'Bill total must match items total' }).first().isVisible().catch(() => false);
    const disabled = await captureBtn.isDisabled();
    if (!errorVisible || !disabled) {
      throw new Error(`Expected mismatch validation error and disabled Capture button. errorVisible=${errorVisible} disabled=${disabled}`);
    }

    // Fix mismatch using the helper button.
    await page.click('button:has-text("Set bill total = items total")');
    await page.waitForTimeout(300);

    // Capture should succeed.
    const billRespPromise = page.waitForResponse(
      (r) => r.url().includes('/vendor-bill') && r.request().method() === 'POST',
      { timeout: 60_000 }
    );
    await captureBtn.click();
    const billResp = await billRespPromise;
    console.log(JSON.stringify({ vendorBillStatus: billResp.status() }));
    if (billResp.status() !== 200) {
      let payload = null;
      try {
        payload = await billResp.json();
      } catch {
        // ignore
      }
      throw new Error(`Expected vendor-bill 200, got ${billResp.status()} payload=${JSON.stringify(payload)}`);
    }

    await browser.close();
  } catch (err) {
    const outDir = path.resolve(process.cwd(), 'temp');
    await page.screenshot({ path: path.join(outDir, `capture-vendor-bill-failure-${Date.now()}.png`), fullPage: true });
    await browser.close();
    throw err;
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
