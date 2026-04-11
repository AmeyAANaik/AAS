import { expect, test } from '@playwright/test';

test('order create direct item flow keeps typed rate and enables create order', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('aas_auth_token', 'test-token');
    localStorage.setItem('aas_auth_role', 'Administrator');
    localStorage.setItem('aas_auth_features', JSON.stringify([
      'orders.view',
      'dashboard.view',
      'master_data.view',
      'bills.view',
      'stock.view',
      'reports.view'
    ]));
    localStorage.setItem(
      'aas_item_vendor_pricing',
      JSON.stringify({
        'ITEM-1::SUP-1': {
          itemId: 'ITEM-1',
          itemName: 'Aamchoor Powder',
          vendorId: 'SUP-1',
          vendorName: 'Sanshray Foods',
          originalRate: 24,
          marginPercent: 0,
          finalRate: 24
        }
      })
    );
  });

  await page.route('**/api/company-context', async route => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        company: { id: 'AAS', name: 'AAS' },
        branch: null,
        companies: [{ name: 'AAS' }],
        branches: []
      })
    });
  });

  await page.route('**/api/me', async route => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        role: 'Administrator',
        features: ['orders.view', 'dashboard.view', 'master_data.view', 'bills.view', 'stock.view', 'reports.view'],
        homeRoute: '/orders'
      })
    });
  });

  await page.route('**/api/shops', async route => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([{ name: 'SHOP-1', customer_name: 'Sukarta Aundh' }])
    });
  });

  await page.route('**/api/categories', async route => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([{ name: 'Raw Material', item_group_name: 'Raw Material' }])
    });
  });

  await page.route('**/api/vendors', async route => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([{ name: 'SUP-1', supplier_name: 'Sanshray Foods', category: 'Raw Material', disabled: 0 }])
    });
  });

  await page.route('**/api/items', async route => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        {
          name: 'ITEM-1',
          item_code: 'ITEM-1',
          item_name: 'Aamchoor Powder',
          item_group: 'Raw Material',
          stock_uom: 'Kg',
          aas_vendor: 'SUP-1',
          aas_vendor_rate: 0,
          aas_gst_percent: 5
        }
      ])
    });
  });

  await page.route('**/api/orders/direct-item-flow', async route => {
    const body = route.request().postDataJSON() as { fields?: { items?: Array<{ rate?: number }> } };
    const rate = Number(body?.fields?.items?.[0]?.rate ?? 0);
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        order: { name: 'SO-NEW-1', title: 'SO-NEW-1' },
        orderId: 'SO-NEW-1',
        pricing: { vendorBillTotal: rate }
      })
    });
  });

  await page.goto('/orders/create');

  await page.locator('mat-select[formcontrolname="customer"]').click();
  await page.getByRole('option', { name: 'Sukarta Aundh' }).click();

  await page.locator('mat-select[formcontrolname="category"]').click();
  await page.getByRole('option', { name: 'Raw Material' }).click();

  await page.getByRole('button', { name: 'Select items' }).click();

  await page.locator('mat-select[formcontrolname="vendor"]').click();
  await page.getByRole('option', { name: 'Sanshray Foods' }).click();

  await page.locator('.item-selection-row mat-checkbox input').first().check({ force: true });

  const rateInput = page.locator('input[id^="rate-"]').first();
  await expect(rateInput).toHaveValue('24');

  await rateInput.fill('');
  await rateInput.type('35');
  await rateInput.press('Tab');
  await expect(rateInput).toHaveValue('35');

  await expect(page.getByText('Vendor invoice total').nth(0)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Create order' })).toBeEnabled();

  await page.getByRole('button', { name: 'Create order' }).click();
  await expect(page).toHaveURL(/\/orders\?orderId=SO-NEW-1/);
});
