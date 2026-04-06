const { chromium } = require('playwright');

(async () => {
  const token = process.env.TOKEN;
  if (!token) {
    throw new Error('TOKEN environment variable is required.');
  }

  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();
  page.setDefaultTimeout(15000);

  const log = (stage, extra = {}) => {
    console.log(JSON.stringify({ stage, ...extra }));
  };

  await page.addInitScript((authToken) => {
    localStorage.setItem('aas_auth_token', authToken);
    localStorage.setItem('aas_auth_role', 'Administrator');
    localStorage.setItem('aas_auth_features', JSON.stringify([
      'orders.view',
      'dashboard.view',
      'master_data.view',
      'bills.view',
      'stock.view',
      'reports.view',
      'vendor_ops.view',
      'branch_ops.view',
      'company_settings.view'
    ]));
  }, token);

  await page.goto('http://localhost:4200/orders/create', { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('networkidle');
  log('loaded', { url: page.url() });

  await page.locator('mat-select[formcontrolname="customer"]').click();
  await page.getByRole('option', { name: 'Sukarta Aundh' }).click();
  log('customer-selected');

  await page.locator('mat-select[formcontrolname="category"]').click();
  await page.getByRole('option', { name: 'Raw Material' }).click();
  log('category-selected');

  await page.getByRole('button', { name: 'Select items' }).click();
  log('mode-selected');

  await page.locator('mat-select[formcontrolname="vendor"]').click();
  await page.getByRole('option', { name: 'Sanshray Foods' }).click();
  log('vendor-selected');

  const firstCheckbox = page.locator('.item-selection-row mat-checkbox input').first();
  await firstCheckbox.check({ force: true });
  log('item-selected');

  const rateInput = page.locator('input[id^="rate-"]').first();
  await rateInput.fill('120');
  await rateInput.press('Tab');
  log('rate-updated', { value: await rateInput.inputValue() });

  const gstInput = page.locator('input[id^="gst-"]').first();
  await gstInput.fill('5');
  await gstInput.press('Tab');
  log('gst-updated', { value: await gstInput.inputValue() });

  const createButton = page.getByRole('button', { name: 'Create order' });
  const disabled = await createButton.isDisabled();
  log('button-state', { disabled });
  if (disabled) {
    throw new Error('Create order button is disabled.');
  }

  await Promise.all([
    page.waitForURL(/\/orders\?orderId=/, { timeout: 45000 }),
    createButton.click()
  ]);

  const bodyText = await page.locator('body').innerText();
  log('redirected', {
    url: page.url(),
    hasManageContext: /Vendor bill|Review invoice items|Sell order|Track demand/i.test(bodyText)
  });

  await browser.close();
})();
