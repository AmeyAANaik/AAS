import path from 'node:path';
import { chromium } from 'playwright';

const UI_BASE_URL = process.env.UI_BASE_URL || 'http://localhost:4200';
const API_BASE_URL = process.env.API_BASE_URL || 'http://localhost:8083';
const USERNAME = process.env.USERNAME || 'Administrator';
const PASSWORD = process.env.PASSWORD || 'admin';
const VENDOR_NAME = process.env.VENDOR_NAME || 'Sanshray Foods';
const PDF_PATH = process.env.PDF_PATH || '/Users/roshninaik/Downloads/Sales_3391.pdf';
const ACTIVATE = process.env.ACTIVATE === 'true';

async function loginThroughUi(page) {
  await page.goto(`${UI_BASE_URL}/login?returnUrl=%2Fvendors`, { waitUntil: 'networkidle' });
  await page.fill('input[name="username"]', USERNAME);
  await page.fill('input[name="password"]', PASSWORD);
  await page.click('button.btn-primary');
  await page.waitForURL('**/vendors', { timeout: 30_000 });
}

async function selectVendor(page, vendorName) {
  await page.waitForSelector('.vendor-page table', { timeout: 30_000 });
  const vendorRow = page.locator('.vendor-page table tr.mat-mdc-row', { hasText: vendorName }).first();
  await vendorRow.waitFor({ timeout: 30_000 });
  await vendorRow.click();
  await page.locator('app-vendor-form').waitFor({ state: 'visible', timeout: 15_000 });
}

async function openSetupDialog(page) {
  await page.getByRole('button', { name: /(Open|Update) setup view/i }).click();
  const dialog = page.locator('mat-dialog-container');
  await dialog.waitFor({ state: 'visible', timeout: 15_000 });
  return dialog;
}

async function analyzeSample(dialog) {
  const [response] = await Promise.all([
    dialog.page().waitForResponse(
      response =>
        response.url().includes('/invoice-template/analyze') &&
        response.request().method() === 'POST',
      { timeout: 60_000 }
    ),
    dialog.getByRole('button', { name: /Analyze sample PDF/i }).click()
  ]);
  const json = await response.json().catch(() => ({}));
  if (!response.ok()) {
    throw new Error(`Analyze failed with status ${response.status()}: ${JSON.stringify(json)}`);
  }
  await dialog.locator('.mapping-card').first().waitFor({ timeout: 30_000 });
  return json;
}

async function preview(dialog) {
  const [response] = await Promise.all([
    dialog.page().waitForResponse(
      response =>
        response.url().includes('/invoice-template/mapping/preview') &&
        response.request().method() === 'POST',
      { timeout: 60_000 }
    ),
    dialog.getByRole('button', { name: /Preview generated template|Preview extraction/i }).click()
  ]);
  const json = await response.json().catch(() => ({}));
  if (!response.ok()) {
    throw new Error(`Preview failed with status ${response.status()}: ${JSON.stringify(json)}`);
  }
  await dialog.locator('.review-panel').waitFor({ timeout: 30_000 });
  return json;
}

async function saveSetup(dialog) {
  const [response] = await Promise.all([
    dialog.page().waitForResponse(
      response =>
        response.url().includes('/invoice-template/mapping/save') &&
        response.request().method() === 'POST',
      { timeout: 60_000 }
    ),
    dialog.getByRole('button', { name: /Save template|Save setup/i }).click()
  ]);
  const json = await response.json().catch(() => ({}));
  if (!response.ok()) {
    throw new Error(`Save setup failed with status ${response.status()}: ${JSON.stringify(json)}`);
  }
  await dialog.waitFor({ state: 'hidden', timeout: 15_000 });
  return json;
}

async function activateVendor(page) {
  await page.locator('app-vendor-form mat-select[formcontrolname="status"]').click();
  await page.locator('mat-option').filter({ hasText: 'Active' }).click();
  const updateResponse = page.waitForResponse(
    response => response.url().includes('/api/vendors/') && response.request().method() === 'PUT',
    { timeout: 60_000 }
  );
  await page.getByRole('button', { name: /Update vendor/i }).click();
  const response = await updateResponse;
  const json = await response.json().catch(() => ({}));
  if (!response.ok()) {
    throw new Error(`Vendor update failed with status ${response.status()}: ${JSON.stringify(json)}`);
  }
}

async function fetchVendorState() {
  const loginResponse = await fetch(`${API_BASE_URL}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: USERNAME, password: PASSWORD }),
  });
  const loginJson = await loginResponse.json();
  const token = loginJson.accessToken;
  const response = await fetch(`${API_BASE_URL}/api/vendors`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const vendors = await response.json();
  return vendors.find(vendor => String(vendor.supplier_name ?? vendor.name ?? '') === VENDOR_NAME);
}

async function main() {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1600, height: 1200 } });
  const page = await context.newPage();

  try {
    await loginThroughUi(page);
    await selectVendor(page, VENDOR_NAME);
    const dialog = await openSetupDialog(page);

    await dialog.locator('input[type="file"][accept="application/pdf"]').setInputFiles(PDF_PATH);
    const analysis = await analyzeSample(dialog);

    await dialog.locator('.mapping-card').first().waitFor({ timeout: 15_000 });
    const previewJson = await preview(dialog);
    await dialog.locator('.review-panel', { hasText: 'Review extracted data' }).waitFor({ timeout: 30_000 });

    const metrics = {
      itemsDetected: await dialog.locator('.review-metrics .summary-stat').nth(0).locator('strong').innerText(),
      rowsScanned: await dialog.locator('.review-metrics .summary-stat').nth(1).locator('strong').innerText(),
      billAmount: await dialog.locator('.review-metrics .summary-stat').nth(2).locator('strong').innerText(),
      transportCharge: await dialog.locator('.review-metrics .summary-stat').nth(3).locator('strong').innerText(),
    };

    const saveJson = await saveSetup(dialog);
    await page.locator('.feedback').filter({ hasText: /saved/i }).first().waitFor({ timeout: 30_000 });

    if (ACTIVATE) {
      await selectVendor(page, VENDOR_NAME);
      await activateVendor(page);
    }

    const vendor = await fetchVendorState();
    if (!vendor) {
      throw new Error(`Unable to find vendor ${VENDOR_NAME} after UI flow.`);
    }

    console.log(JSON.stringify({
      vendor: VENDOR_NAME,
      pdf: path.basename(PDF_PATH),
      analysis: {
        itemsDetected: analysis.previewMetrics?.itemsDetected ?? null,
        totalRows: analysis.previewMetrics?.totalRows ?? null,
        detectedItemColumns: analysis.detectedColumns?.items?.map(option => option.label) ?? [],
        detectedSummaryColumns: analysis.detectedColumns?.summary?.map(option => option.label) ?? []
      },
      preview: {
        mappingReady: previewJson.mappingReady ?? null,
        parsedItems: previewJson.previewItems?.length ?? null,
        missingItems: previewJson.missingFields?.items ?? [],
        missingSummary: previewJson.missingFields?.summary ?? []
      },
      save: {
        templateJsonLength: String(saveJson.templateJson ?? '').length,
        sampleFileUrl: saveJson.file?.fileUrl ?? null
      },
      backend: {
        disabled: vendor.disabled,
        templateJsonLength: String(vendor.invoice_template_json ?? vendor.aas_invoice_template_json ?? '').trim().length,
        samplePdf: vendor.invoice_template_sample_pdf ?? vendor.aas_invoice_template_sample_pdf ?? null
      },
      metrics
    }, null, 2));
  } catch (error) {
    const screenshotPath = path.resolve(process.cwd(), 'test-artifacts', `vendor-invoice-setup-failure-${Date.now()}.png`);
    await page.screenshot({ path: screenshotPath, fullPage: true }).catch(() => {});
    console.error(`Screenshot saved to ${screenshotPath}`);
    throw error;
  } finally {
    await browser.close();
  }
}

main().catch(error => {
  console.error(error);
  process.exit(1);
});
