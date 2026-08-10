# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: branch-ops-export-format.spec.ts >> category summary download shows menu and sends format=pdf
- Location: e2e/branch-ops-export-format.spec.ts:147:5

# Error details

```
Error: expect(locator).toBeVisible() failed

Locator: locator('.category-summary')
Expected: visible
Timeout: 10000ms
Error: element(s) not found

Call log:
  - Expect "toBeVisible" with timeout 10000ms
  - waiting for locator('.category-summary')

```

```yaml
- banner:
  - text: SCM Console Supply chain workspace
  - button "Dark mode"
  - button "admin@aas.com Administrator":
    - strong: admin@aas.com
    - text: Administrator
- complementary:
  - text: Workspace Home / Branch Operations SCM Console · Administrator
  - navigation:
    - text: Home
    - link "Dashboard":
      - /url: /admin/dashboard
  - navigation:
    - text: Procure
    - link "Orders":
      - /url: /orders
  - navigation:
    - text: Operations
    - link "Branch Operations":
      - /url: /branch-ops
- main:
  - text: Home / Branch Operations Operational workspace Supply chain workspace Operations Branch Operations Monitor branch order backlog, receivables, and collections. Total Branches 1 Branches with Pending Orders 1 Pending Orders 2 Awaiting Vendor Assignment 1 Awaiting Vendor Response 1 Amount Receivable 5,000 All Branches Start with branches carrying the largest open backlog or receivable exposure.
  - button "Download all branch ledgers"
  - button "Download all branch ledgers by category"
  - text: Search branch
  - textbox "Search branch":
    - /placeholder: Search by branch name
  - table:
    - rowgroup:
      - row "Branch Pending Awaiting Vendor Vendor Response In Progress Amount Receivable Last Activity Location Ledger Balance Action":
        - columnheader "Branch"
        - columnheader "Pending"
        - columnheader "Awaiting Vendor"
        - columnheader "Vendor Response"
        - columnheader "In Progress"
        - columnheader "Amount Receivable"
        - columnheader "Last Activity"
        - columnheader "Location"
        - columnheader "Ledger Balance"
        - columnheader "Action"
    - rowgroup:
      - row "Test Branch 2 1 1 0 5,000 2026-06-10 Pune 5,000 View":
        - cell "Test Branch"
        - cell "2"
        - cell "1"
        - cell "1"
        - cell "0"
        - cell "5,000"
        - cell "2026-06-10"
        - cell "Pune"
        - cell "5,000"
        - cell "View":
          - button "View"
  - text: Test Branch Open Operational drill-down across branch orders, invoices, and collection progress. Amount Receivable 5,000 Invoiced Amount 6,000 Collection % 83 Pending Orders No open orders This branch does not currently have orders in the active backlog. Select another branch or wait for new orders to appear. Ledger From
  - textbox "From": 7/26/2026
  - button "Open calendar"
  - text: To
  - textbox "To": 8/2/2026
  - button "Open calendar"
  - button "Apply"
  - button "Clear"
  - button "Download full branch ledger"
  - button "Download branch ledger category summary" [disabled]
  - button "Download selected category ledger" [disabled]
  - text: Opening
  - strong: ₹0
  - text: Closing
  - strong: ₹0
  - text: Delta
  - strong: ₹0
  - text: Overall branch balances for the selected date range. No categories Category summary will appear when invoice items are mapped to item groups. Try another date range or check invoice categorization.
```

# Test source

```ts
  54  |       exportRequests?.push(url);
  55  |       return route.fulfill({ status: 200, contentType: 'text/csv', body: 'category,balance\nSpices,5000' });
  56  |     }
  57  |     if (path.endsWith('/ledger/export')) {
  58  |       exportRequests?.push(url);
  59  |       return route.fulfill({ status: 200, contentType: 'text/csv', body: 'date,voucher\n2026-06-10,SINV-001' });
  60  |     }
  61  |     if (path.includes('/ledger/categories')) {
  62  |       return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([{ category: 'Spices', amount: 5000, balance: 5000 }]) });
  63  |     }
  64  |     if (path.includes('/ledger/category')) {
  65  |       return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_CATEGORY_LEDGER) });
  66  |     }
  67  |     if (path.includes('/ledger')) {
  68  |       return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_LEDGER) });
  69  |     }
  70  |     if (path.includes('/orders')) {
  71  |       return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) });
  72  |     }
  73  |     if (path.includes('/analytics')) {
  74  |       return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({}) });
  75  |     }
  76  |     // Branch detail (e.g. /api/branch-ops/BRANCH-1)
  77  |     return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_DETAIL) });
  78  |   });
  79  | 
  80  |   // Global ledger/categories export (all branches)
  81  |   await page.route('**/api/branch-ops/ledger/**', async route => {
  82  |     const url = route.request().url();
  83  |     exportRequests?.push(url);
  84  |     return route.fulfill({ status: 200, contentType: 'text/csv', body: 'branchId,balance\nBRANCH-1,5000' });
  85  |   });
  86  | }
  87  | 
  88  | test('download all ledgers button shows CSV/XLSX/PDF menu', async ({ page }) => {
  89  |   await setupAuth(page);
  90  |   await mockApis(page);
  91  | 
  92  |   await page.goto('/branch-ops');
  93  |   await expect(page.locator('.kpi-grid')).toBeVisible({ timeout: 8000 });
  94  | 
  95  |   const downloadBtn = page.locator('[aria-label="Download all branch ledgers"]');
  96  |   await expect(downloadBtn).toBeVisible();
  97  |   await downloadBtn.click();
  98  | 
  99  |   await expect(page.getByRole('menuitem', { name: /CSV/i })).toBeVisible();
  100 |   await expect(page.getByRole('menuitem', { name: /Excel/i })).toBeVisible();
  101 |   await expect(page.getByRole('menuitem', { name: /PDF/i })).toBeVisible();
  102 | });
  103 | 
  104 | test('download all category ledgers button shows CSV/XLSX/PDF menu', async ({ page }) => {
  105 |   await setupAuth(page);
  106 |   await mockApis(page);
  107 | 
  108 |   await page.goto('/branch-ops');
  109 |   await expect(page.locator('.kpi-grid')).toBeVisible({ timeout: 8000 });
  110 | 
  111 |   const categoryBtn = page.locator('[aria-label="Download all branch ledgers by category"]');
  112 |   await expect(categoryBtn).toBeVisible();
  113 |   await categoryBtn.click();
  114 | 
  115 |   await expect(page.getByRole('menuitem', { name: /CSV/i })).toBeVisible();
  116 |   await expect(page.getByRole('menuitem', { name: /Excel/i })).toBeVisible();
  117 |   await expect(page.getByRole('menuitem', { name: /PDF/i })).toBeVisible();
  118 | });
  119 | 
  120 | test('category ledger download shows menu and sends format=xlsx', async ({ page }) => {
  121 |   await setupAuth(page);
  122 |   const capturedUrls: string[] = [];
  123 |   await mockApis(page, capturedUrls);
  124 | 
  125 |   await page.goto('/branch-ops/BRANCH-1');
  126 |   await expect(page.locator('.detail-grid')).toBeVisible({ timeout: 10000 });
  127 |   await expect(page.locator('.category-summary')).toBeVisible({ timeout: 10000 });
  128 | 
  129 |   const downloadBtn = page.locator('[aria-label="Download selected category ledger"]').first();
  130 |   await expect(downloadBtn).toBeVisible();
  131 |   await downloadBtn.click();
  132 | 
  133 |   const csvItem = page.getByRole('menuitem', { name: /CSV/i });
  134 |   const xlsxItem = page.getByRole('menuitem', { name: /Excel/i });
  135 |   const pdfItem = page.getByRole('menuitem', { name: /PDF/i });
  136 |   await expect(csvItem).toBeVisible();
  137 |   await expect(xlsxItem).toBeVisible();
  138 |   await expect(pdfItem).toBeVisible();
  139 | 
  140 |   await xlsxItem.click();
  141 |   await page.waitForTimeout(600);
  142 | 
  143 |   expect(capturedUrls.some(u => u.includes('format=xlsx') && u.includes('/ledger/category/export')),
  144 |     `Expected format=xlsx in category export. Got: ${capturedUrls.join(', ')}`).toBeTruthy();
  145 | });
  146 | 
  147 | test('category summary download shows menu and sends format=pdf', async ({ page }) => {
  148 |   await setupAuth(page);
  149 |   const capturedUrls: string[] = [];
  150 |   await mockApis(page, capturedUrls);
  151 | 
  152 |   await page.goto('/branch-ops/BRANCH-1');
  153 |   await expect(page.locator('.detail-grid')).toBeVisible({ timeout: 10000 });
> 154 |   await expect(page.locator('.category-summary')).toBeVisible({ timeout: 10000 });
      |                                                   ^ Error: expect(locator).toBeVisible() failed
  155 | 
  156 |   const catSummaryBtn = page.locator('[aria-label="Download branch ledger category summary"]');
  157 |   await expect(catSummaryBtn).toBeVisible();
  158 |   await catSummaryBtn.click();
  159 | 
  160 |   await expect(page.getByRole('menuitem', { name: /PDF/i })).toBeVisible();
  161 |   await page.getByRole('menuitem', { name: /PDF/i }).click();
  162 |   await page.waitForTimeout(600);
  163 | 
  164 |   expect(capturedUrls.some(u => u.includes('format=pdf') && u.includes('/ledger/categories/export')),
  165 |     `Expected format=pdf in categories export. Got: ${capturedUrls.join(', ')}`).toBeTruthy();
  166 | });
  167 | 
```