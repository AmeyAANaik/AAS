import { chromium } from 'playwright';

const UI = 'http://localhost:4200';
const API = 'http://localhost:8083';
const VENDOR = 'Sanshray Foods';
const FILES = [
  '/Users/roshninaik/Downloads/invoice_template.pdf',
  '/Users/roshninaik/Downloads/Sales3329.pdf',
];

async function loginApi() {
  const res = await fetch(`${API}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'Administrator', password: 'admin' })
  });
  const json = await res.json();
  return json.accessToken || json.token;
}

async function fetchVendorState(token) {
  const res = await fetch(`${API}/api/vendors`, { headers: { Authorization: `Bearer ${token}` } });
  const json = await res.json();
  const vendors = Array.isArray(json) ? json : (json.data || json.vendors || []);
  const vendor = vendors.find(v => (v.supplier_name || v.supplierName || v.name) === VENDOR);
  const rawJson = vendor?.raw?.aas_invoice_template_json || vendor?.invoice_template_json || vendor?.aas_invoice_template_json || '';
  let parsed = null;
  try { parsed = rawJson ? JSON.parse(rawJson) : null; } catch {}
  return {
    samplePdfUrl: vendor?.invoice_template_sample_pdf || vendor?.samplePdfUrl || vendor?.aas_invoice_template_sample_pdf || null,
    sampleMeta: parsed?.sample || null,
    validation: parsed?.validation || null,
    mapping: parsed?.mapping || null,
  };
}

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1600, height: 1200 } });
const token = await loginApi();
const results = [];

try {
  await page.goto(`${UI}/login?returnUrl=%2Fvendors`, { waitUntil: 'networkidle' });
  await page.fill('input[name="username"]', 'Administrator');
  await page.fill('input[name="password"]', 'admin');
  await page.click('button.btn-primary');
  await page.waitForURL('**/vendors', { timeout: 30000 });

  for (const filePath of FILES) {
    const fileName = filePath.split('/').pop();
    const row = page.locator('.vendor-page table tr.mat-mdc-row', { hasText: VENDOR }).first();
    await row.waitFor({ timeout: 30000 });
    await row.click();
    await page.locator('app-vendor-form').waitFor({ state: 'visible', timeout: 15000 });
    await page.getByRole('button', { name: /(Open|Update) setup view/i }).click();
    const dialog = page.locator('mat-dialog-container');
    await dialog.waitFor({ state: 'visible', timeout: 15000 });

    await dialog.locator('input[type="file"][accept="application/pdf"]').setInputFiles(filePath);
    const [analyzeResp] = await Promise.all([
      page.waitForResponse(r => r.url().includes('/invoice-template/analyze') && r.request().method() === 'POST', { timeout: 60000 }),
      dialog.getByRole('button', { name: /Analyze sample PDF/i }).click()
    ]);
    const analyzeJson = await analyzeResp.json().catch(() => ({}));
    await dialog.locator('.mapping-card').first().waitFor({ timeout: 30000 });

    const [previewResp] = await Promise.all([
      page.waitForResponse(r => r.url().includes('/invoice-template/mapping/preview') && r.request().method() === 'POST', { timeout: 60000 }),
      dialog.getByRole('button', { name: /Preview generated template|Preview extraction/i }).click()
    ]);
    const previewJson = await previewResp.json().catch(() => ({}));
    await dialog.locator('.review-panel').waitFor({ timeout: 30000 });

    const saveButton = dialog.getByRole('button', { name: /Save template|Save setup/i });
    const saveEnabled = await saveButton.isEnabled();
    let saveJson = null;
    if (saveEnabled) {
      const [saveResp] = await Promise.all([
        page.waitForResponse(r => r.url().includes('/invoice-template/mapping/save') && r.request().method() === 'POST', { timeout: 60000 }),
        saveButton.click()
      ]);
      saveJson = await saveResp.json().catch(() => ({}));
      await dialog.waitFor({ state: 'hidden', timeout: 20000 });
    } else {
      await dialog.getByRole('button', { name: /Cancel/i }).click();
      await dialog.waitFor({ state: 'hidden', timeout: 20000 });
    }

    const vendorState = await fetchVendorState(token);
    results.push({
      fileName,
      analyze: {
        sample: analyzeJson.sample || null,
        metrics: analyzeJson.previewMetrics || null,
        itemColumns: (analyzeJson.detectedColumns?.items || []).map(o => o.label),
        summaryColumns: (analyzeJson.detectedColumns?.summary || []).map(o => o.label),
      },
      preview: {
        mappingReady: previewJson.mappingReady ?? null,
        metrics: previewJson.previewMetrics || null,
        missingItems: previewJson.missingFields?.items || [],
        missingSummary: previewJson.missingFields?.summary || [],
        previewRows: (previewJson.previewItems || []).slice(0, 5),
      },
      saveEnabled,
      save: saveJson ? {
        file: saveJson.file || null,
        templateJsonLength: String(saveJson.templateJson || '').length,
      } : null,
      backendAfter: vendorState,
    });
  }

  console.log(JSON.stringify(results, null, 2));
} catch (err) {
  await page.screenshot({ path: 'test-artifacts/two-file-sequence-failure.png', fullPage: true }).catch(() => {});
  console.error(err);
  process.exitCode = 1;
} finally {
  await browser.close();
}
