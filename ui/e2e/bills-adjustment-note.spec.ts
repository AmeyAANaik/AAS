import { expect, test } from '@playwright/test';

function seedAuth(page: import('@playwright/test').Page, features: string[] = ['bills.view', 'bill_review.view']) {
  return page.addInitScript((grantedFeatures: string[]) => {
    localStorage.setItem('aas_auth_token', 'test-token');
    localStorage.setItem('aas_auth_role', 'Administrator');
    localStorage.setItem('aas_auth_features', JSON.stringify(grantedFeatures));
    localStorage.setItem('aas_auth_home_route', '/bills');
  }, features);
}

async function mockShell(page: import('@playwright/test').Page, features: string[] = ['bills.view', 'bill_review.view']) {
  await page.route('**/api/company-context', async route => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        company: { id: 'AAS', name: 'Shree Siddhivinayak Suppliers', default_currency: 'INR' },
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
        features,
        homeRoute: '/bills'
      })
    });
  });

  await page.route('**/api/bill-review/count', async route => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ pendingCount: 2 })
    });
  });
}

async function mockBillsBootstrap(page: import('@playwright/test').Page) {
  await page.route('**/api/shops', async route => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        { name: 'BR-1', customer_name: 'Sukhkarta Pure Veg Dining Hall, Baner' }
      ])
    });
  });

  await page.route('**/api/vendors', async route => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        { name: 'SUP-1', supplier_name: 'Pragati Foods', aas_category: 'Grocery' }
      ])
    });
  });

  await page.route('**/api/categories', async route => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        { name: 'Dairy', item_group_name: 'Dairy' },
        { name: 'Grocery', item_group_name: 'Grocery' }
      ])
    });
  });

  await page.route('**/api/items', async route => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([])
    });
  });

  await page.route('**/api/orders?**', async route => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        { name: 'SO-1', customer: 'BR-1', company: 'AAS' }
      ])
    });
  });

  await page.route('**/api/invoices?**', async route => {
    const url = new URL(route.request().url());
    const partyType = url.searchParams.get('partyType');
    const partyId = url.searchParams.get('partyId');

    if (partyType === 'Supplier' && partyId === 'SUP-1') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            name: 'PINV-001',
            customer: '',
            company: 'AAS',
            posting_date: '2026-06-05',
            grand_total: 45000,
            outstanding_amount: 45000,
            status: 'Unpaid'
          }
        ])
      });
      return;
    }

    if (partyType === 'Customer' && partyId === 'BR-1') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            name: 'SINV-001',
            customer: 'Sukhkarta Pure Veg Dining Hall, Baner',
            company: 'AAS',
            posting_date: '2026-06-05',
            grand_total: 106679,
            outstanding_amount: 106679,
            status: 'Unpaid'
          }
        ])
      });
      return;
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([])
    });
  });
}

test.describe('Bills credit/debit note workflow', () => {
  test('branch credit note enforces evidence, shows due impact, and submits review payload', async ({ page }) => {
    await seedAuth(page);
    await mockShell(page);
    await mockBillsBootstrap(page);

    await page.route('**/api/payments/due-by-category**', async route => {
      const url = new URL(route.request().url());
      const partyType = url.searchParams.get('partyType');
      const partyId = url.searchParams.get('partyId');
      const categoryId = url.searchParams.get('categoryId');
      expect(partyType).toBe('Customer');
      expect(partyId).toBe('BR-1');
      expect(categoryId).toBe('Dairy');
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          dueAmount: 106679,
          pendingAdminApprovalAmount: 1077,
          pendingAdjustmentAmount: 500,
          availableDueAmount: 105602
        })
      });
    });

    let multipartBody = '';
    await page.route('**/api/adjustment-notes/with-attachments', async route => {
      multipartBody = route.request().postData() ?? '';
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          note: {
            name: 'ACC-JV-0001',
            docstatus: 0,
            aas_adjustment_note_type: 'CREDIT_NOTE',
            aas_adjustment_review_status: 'UNDER_REVIEW'
          },
          files: [{ fileName: 'evidence.pdf', fileUrl: '/files/evidence.pdf' }]
        })
      });
    });

    await page.goto('/bills');
    await page.getByText('Credit / Debit note').click();

    await expect(page.getByTestId('adjustment-note-title')).toHaveText('Credit note');
    await expect(page.getByTestId('adjustment-note-direction-hint')).toContainText('admin approval');
    await expect(page.getByTestId('adjustment-note-submit')).toBeDisabled();
    await expect(page.getByTestId('adjustment-note-evidence-required')).toBeVisible();

    await page.getByTestId('adjustment-note-party-select').click();
    await page.getByRole('option', { name: 'Sukhkarta Pure Veg Dining Hall, Baner' }).click();

    await page.getByTestId('adjustment-note-category-select').click();
    await page.getByRole('option', { name: 'Dairy' }).click();

    await expect(page.getByTestId('adjustment-note-current-due')).toContainText('106,679.00');
    await expect(page.getByTestId('adjustment-note-pending-payments')).toContainText('1,077.00');
    await expect(page.getByTestId('adjustment-note-pending-notes')).toContainText('500.00');

    await page.getByTestId('adjustment-note-invoice-select').click();
    await page.getByRole('option', { name: /SINV-001/ }).click();

    await page.getByTestId('adjustment-note-amount-input').fill('5000');
    await page.getByTestId('adjustment-note-reason-input').fill('Returned stock adjustment');

    await expect(page.getByTestId('adjustment-note-preview-type')).toHaveText('Credit note');
    await expect(page.getByTestId('adjustment-note-preview-direction')).toHaveText('Give');
    await expect(page.getByTestId('adjustment-note-preview-impact')).toContainText('-5,000.00');
    await expect(page.getByTestId('adjustment-note-preview-due-after')).toContainText('101,679.00');

    await page.getByTestId('adjustment-note-evidence-input').setInputFiles({
      name: 'evidence.pdf',
      mimeType: 'application/pdf',
      buffer: Buffer.from('%PDF-1.4 adjustment test')
    });

    await expect(page.getByTestId('adjustment-note-submit')).toBeEnabled();
    await page.getByTestId('adjustment-note-submit').click();

    await expect(page.getByTestId('adjustment-note-amount-input')).toHaveValue('0');
    await expect(page.getByTestId('adjustment-note-preview-hint')).toContainText('Select party and category');
    expect(multipartBody).toContain('"partyType":"Customer"');
    expect(multipartBody).toContain('"partyId":"BR-1"');
    expect(multipartBody).toContain('"categoryId":"Dairy"');
    expect(multipartBody).toContain('"invoiceId":"SINV-001"');
    expect(multipartBody).toContain('"direction":"GIVE"');
    expect(multipartBody).toContain('"amount":5000');
    expect(multipartBody).toContain('"reason":"Returned stock adjustment"');
  });

  test('vendor debit note switches labels and computes supplier due impact correctly', async ({ page }) => {
    await seedAuth(page);
    await mockShell(page);
    await mockBillsBootstrap(page);

    await page.route('**/api/payments/due-by-category**', async route => {
      const url = new URL(route.request().url());
      expect(url.searchParams.get('partyType')).toBe('Supplier');
      expect(url.searchParams.get('partyId')).toBe('SUP-1');
      expect(url.searchParams.get('categoryId')).toBe('Grocery');
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          dueAmount: 45000,
          pendingAdminApprovalAmount: 0,
          pendingAdjustmentAmount: 2400,
          availableDueAmount: 45000
        })
      });
    });

    let multipartBody = '';
    await page.route('**/api/adjustment-notes/with-attachments', async route => {
      multipartBody = route.request().postData() ?? '';
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          note: {
            name: 'ACC-JV-0002',
            docstatus: 0,
            aas_adjustment_note_type: 'DEBIT_NOTE',
            aas_adjustment_review_status: 'UNDER_REVIEW'
          },
          files: [{ fileName: 'vendor-note.jpg', fileUrl: '/files/vendor-note.jpg' }]
        })
      });
    });

    await page.goto('/bills');
    await page.getByText('Credit / Debit note').click();

    await page.getByTestId('adjustment-note-party-supplier').click();
    await page.getByTestId('adjustment-note-party-select').click();
    await page.getByRole('option', { name: 'Pragati Foods' }).click();

    await page.getByTestId('adjustment-note-category-select').click();
    await page.getByRole('option', { name: 'Grocery' }).click();

    await page.getByTestId('adjustment-note-direction-select').click();
    await page.getByRole('option', { name: 'Take · Debit note' }).click();

    await expect(page.getByTestId('adjustment-note-title')).toHaveText('Debit note');

    await page.getByTestId('adjustment-note-invoice-select').click();
    await page.getByRole('option', { name: /PINV-001/ }).click();
    await page.getByTestId('adjustment-note-amount-input').fill('1200');

    await expect(page.getByTestId('adjustment-note-preview-direction')).toHaveText('Take');
    await expect(page.getByTestId('adjustment-note-preview-impact')).toContainText('-1,200.00');
    await expect(page.getByTestId('adjustment-note-preview-due-after')).toContainText('43,800.00');

    await page.getByTestId('adjustment-note-evidence-input').setInputFiles({
      name: 'vendor-note.jpg',
      mimeType: 'image/jpeg',
      buffer: Buffer.from([1, 2, 3, 4])
    });
    await page.getByTestId('adjustment-note-submit').click();

    expect(multipartBody).toContain('"partyType":"Supplier"');
    expect(multipartBody).toContain('"partyId":"SUP-1"');
    expect(multipartBody).toContain('"categoryId":"Grocery"');
    expect(multipartBody).toContain('"invoiceId":"PINV-001"');
    expect(multipartBody).toContain('"direction":"TAKE"');
    expect(multipartBody).toContain('"amount":1200');
  });

  test('bill review queue shows mixed payment and note items and approves a credit note', async ({ page }) => {
    await seedAuth(page, ['bill_review.view']);
    await mockShell(page, ['bill_review.view']);

    await page.route('**/api/bill-review/items?**', async route => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            itemType: 'PAYMENT',
            documentId: 'PAY-001',
            documentLabel: 'Payment',
            partyType: 'Customer',
            party: 'BR-1',
            partyName: 'Sukhkarta Pure Veg Dining Hall, Baner',
            postingDate: '2026-06-06',
            amount: 1077,
            categoryId: 'Dairy',
            dueAmount: 106679,
            modeOfPayment: 'Cash',
            docstatus: 0,
            createdAt: '2026-06-06 10:00:00',
            createdBy: 'helper',
            reviewStatus: 'UNDER_REVIEW',
            reviewedAt: '',
            reviewedBy: ''
          },
          {
            itemType: 'CREDIT_NOTE',
            documentId: 'ACC-JV-0001',
            documentLabel: 'Credit note',
            partyType: 'Customer',
            party: 'BR-1',
            partyName: 'Sukhkarta Pure Veg Dining Hall, Baner',
            postingDate: '2026-06-06',
            amount: 5000,
            categoryId: 'Dairy',
            dueAmount: 106679,
            direction: 'GIVE',
            referenceInvoice: 'SINV-001',
            reason: 'Returned stock adjustment',
            docstatus: 0,
            createdAt: '2026-06-06 10:05:00',
            createdBy: 'admin',
            reviewStatus: 'UNDER_REVIEW',
            reviewedAt: '',
            reviewedBy: ''
          }
        ])
      });
    });

    await page.route('**/api/bill-review/items/CREDIT_NOTE/ACC-JV-0001', async route => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          itemType: 'CREDIT_NOTE',
          documentId: 'ACC-JV-0001',
          document: {
            posting_date: '2026-06-06',
            aas_adjustment_party_type: 'Customer',
            aas_adjustment_party: 'BR-1',
            aas_adjustment_amount: 5000,
            aas_category: 'Dairy',
            aas_due_amount: 106679,
            aas_reference_invoice: 'SINV-001',
            aas_adjustment_reason: 'Returned stock adjustment',
            aas_adjustment_direction: 'GIVE',
            aas_adjustment_created_by: 'admin',
            aas_adjustment_created_at: '2026-06-06 10:05:00'
          },
          attachments: [{ id: 'FILE-1', name: 'evidence.pdf', url: '/files/evidence.pdf', isPrivate: false, createdAt: '2026-06-06 10:05:00' }],
          currentDueAmount: 106679,
          expectedDueAfterApproval: 101679,
          signedImpact: -5000
        })
      });
    });

    await page.route('**/api/bill-review/items/CREDIT_NOTE/ACC-JV-0001/approve', async route => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          itemType: 'CREDIT_NOTE',
          documentId: 'ACC-JV-0001',
          document: {
            aas_due_amount: 106679
          },
          attachments: [],
          warnings: [],
          currentDueAmount: 106679,
          expectedDueAfterApproval: 101679
        })
      });
    });

    await page.goto('/bill-review');

    await expect(page.getByTestId('bill-review-notification')).toContainText('2 review items');
    await expect(page.getByTestId('bill-review-item-label-PAY-001')).toHaveText('Payment');
    await expect(page.getByTestId('bill-review-item-label-ACC-JV-0001')).toHaveText('Credit note');

    await page.getByTestId('bill-review-item-CREDIT_NOTE-ACC-JV-0001').click();
    await expect(page.getByTestId('bill-review-detail')).toBeVisible();
    await expect(page.getByTestId('bill-review-detail-direction')).toHaveText('GIVE');
    await expect(page.getByTestId('bill-review-detail-reference')).toHaveText('SINV-001');
    await expect(page.getByTestId('bill-review-detail-reason')).toHaveText('Returned stock adjustment');
    await expect(page.getByTestId('bill-review-detail-due-after')).toContainText('101,679.00');

    await page.getByTestId('bill-review-approve').click();
    await expect(page.getByText('Credit note approved and posted.')).toBeVisible();
  });
});
