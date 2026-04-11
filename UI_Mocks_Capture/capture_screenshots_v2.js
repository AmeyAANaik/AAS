const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

const SCREENSHOTS_DIR = path.join(__dirname, 'ui_screenshots');
const BASE_URL = 'http://localhost:4200';

// Create screenshots directory
if (!fs.existsSync(SCREENSHOTS_DIR)) {
  fs.mkdirSync(SCREENSHOTS_DIR);
}

// Mock API responses
const mockApiResponses = {
  '/api/me': {
    role: 'admin',
    features: [
      'dashboard.view', 'orders.view', 'vendors.view', 'branches.view',
      'categories.view', 'items.view', 'stock.view', 'bills.view',
      'vendor_ops.view', 'branch_ops.view'
    ],
    homeRoute: '/admin/dashboard',
    username: 'admin@aas.com',
    email: 'admin@aas.com'
  },
  '/api/company-context': {
    companyName: 'Test Company',
    defaultCurrency: 'INR',
    shopId: 'SHOP001',
    shopName: 'Main Branch',
    shopLocation: 'New York, NY'
  },
  '/api/orders': {
    data: [
      { id: 'ORD001', status: 'VENDOR_ASSIGNED', vendor: 'Vendor A', total: 5000 },
      { id: 'ORD002', status: 'VENDOR_PDF_RECEIVED', vendor: 'Vendor B', total: 3000 },
      { id: 'ORD003', status: 'DRAFT', vendor: 'Unassigned', total: 2000 }
    ]
  },
  '/api/shops': [
    { id: 'SHOP001', name: 'Main Branch', location: 'New York' },
    { id: 'SHOP002', name: 'Downtown Branch', location: 'Boston' }
  ],
  '/api/categories': [
    { id: 'CAT001', name: 'Electronics' },
    { id: 'CAT002', name: 'Accessories' }
  ],
  '/api/items': [
    { id: 'ITM001', name: 'Laptop', category: 'Electronics', price: 1000 },
    { id: 'ITM002', name: 'Mouse', category: 'Accessories', price: 25 }
  ],
  '/api/vendors': [
    { id: 'VEN001', name: 'Vendor A', city: 'Chicago' },
    { id: 'VEN002', name: 'Vendor B', city: 'Dallas' }
  ],
  '/api/invoices': {
    data: [
      { id: 'INV001', customer: 'Branch A', amount: 5000, status: 'Paid' },
      { id: 'INV002', customer: 'Branch B', amount: 3000, status: 'Pending' }
    ]
  },
  '/api/stock': [
    { id: 'ITM001', name: 'Laptop', quantity: 50, threshold: 10 },
    { id: 'ITM002', name: 'Mouse', quantity: 200, threshold: 50 }
  ],
  '/api/dashboard': {
    orderStatus: [
      { status: 'DRAFT', count: 5 },
      { status: 'VENDOR_ASSIGNED', count: 8 },
      { status: 'INVOICED', count: 12 }
    ],
    salesSummary: { invoiceCount: 20, totalRevenue: 50000 },
    stockSnapshot: { totalItems: 45, totalQuantity: 1250 }
  },
  '/api/vendor-ops/summary': {
    data: [
      { id: 'VEN001', name: 'Vendor A', pendingOrders: 3, awaitingPdf: 1, billAmount: 5000 },
      { id: 'VEN002', name: 'Vendor B', pendingOrders: 2, awaitingPdf: 0, billAmount: 3000 }
    ]
  },
  '/api/branch-ops/summary': {
    data: [
      { id: 'SHOP001', name: 'Main Branch', pendingOrders: 5, receivables: 8000 },
      { id: 'SHOP002', name: 'Downtown Branch', pendingOrders: 3, receivables: 4000 }
    ]
  }
};

async function captureScreenshots() {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });

  try {
    console.log('Starting enhanced screenshot capture...\n');

    // Intercept and mock API calls
    await page.route('**/api/**', async (route) => {
      const url = route.request().url();
      const pathname = new URL(url).pathname;

      // Find matching mock response
      for (const [path, response] of Object.entries(mockApiResponses)) {
        if (pathname.includes(path)) {
          await route.abort('blockedbyclient');
          return;
        }
      }

      // Allow other requests
      route.continue();
    });

    // 1. Login Page
    console.log('Capturing login page...');
    await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1500);
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '01_login.png'), fullPage: false });
    console.log('✓ Login page captured\n');

    // Set auth tokens for logged-in pages
    await page.evaluate(() => {
      localStorage.setItem('aas_auth_token', 'mock-jwt-token-demo');
      localStorage.setItem('aas_auth_role', 'admin');
      localStorage.setItem('aas_auth_features', JSON.stringify([
        'dashboard.view', 'orders.view', 'vendors.view', 'branches.view',
        'categories.view', 'items.view', 'stock.view', 'bills.view',
        'vendor_ops.view', 'branch_ops.view', 'company_settings.view', 'user_settings.view'
      ]));
      localStorage.setItem('aas_auth_home_route', '/admin/dashboard');
    });

    // Add visual delay and scrolling for better screenshots
    const delay = (ms) => new Promise(resolve => setTimeout(resolve, ms));

    // 2. Dashboard
    console.log('Capturing dashboard...');
    await page.goto(`${BASE_URL}/admin/dashboard`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2000);
    // Scroll to see more content
    await page.evaluate(() => window.scrollBy(0, 300));
    await delay(500);
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '02_dashboard.png'), fullPage: true });
    console.log('✓ Dashboard captured\n');

    // 3. Orders List
    console.log('Capturing orders list...');
    await page.goto(`${BASE_URL}/orders`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2000);
    await page.evaluate(() => window.scrollBy(0, 200));
    await delay(500);
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '03_orders_list.png'), fullPage: true });
    console.log('✓ Orders list captured\n');

    // 4. Order Create
    console.log('Capturing order create page...');
    await page.goto(`${BASE_URL}/orders/create`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2000);
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '04_order_create.png'), fullPage: true });
    console.log('✓ Order create page captured\n');

    // 5. Vendor Ops
    console.log('Capturing vendor operations...');
    await page.goto(`${BASE_URL}/vendor-ops`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2000);
    await page.evaluate(() => window.scrollBy(0, 200));
    await delay(500);
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '05_vendor_ops.png'), fullPage: true });
    console.log('✓ Vendor operations captured\n');

    // 6. Branch Ops
    console.log('Capturing branch operations...');
    await page.goto(`${BASE_URL}/branch-ops`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2000);
    await page.evaluate(() => window.scrollBy(0, 200));
    await delay(500);
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '06_branch_ops.png'), fullPage: true });
    console.log('✓ Branch operations captured\n');

    // 7. Bills
    console.log('Capturing bills page...');
    await page.goto(`${BASE_URL}/bills`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2000);
    await page.evaluate(() => window.scrollBy(0, 200));
    await delay(500);
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '07_bills.png'), fullPage: true });
    console.log('✓ Bills page captured\n');

    // 8. Stock
    console.log('Capturing stock page...');
    await page.goto(`${BASE_URL}/stock`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2000);
    await page.evaluate(() => window.scrollBy(0, 200));
    await delay(500);
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '08_stock.png'), fullPage: true });
    console.log('✓ Stock page captured\n');

    // 9. Vendors
    console.log('Capturing vendors page...');
    await page.goto(`${BASE_URL}/vendors`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2000);
    await page.evaluate(() => window.scrollBy(0, 200));
    await delay(500);
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '09_vendors.png'), fullPage: true });
    console.log('✓ Vendors page captured\n');

    // 10. Items
    console.log('Capturing items page...');
    await page.goto(`${BASE_URL}/items`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2000);
    await page.evaluate(() => window.scrollBy(0, 200));
    await delay(500);
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '10_items.png'), fullPage: true });
    console.log('✓ Items page captured\n');

    console.log('\n=== All screenshots captured successfully! ===');
    console.log(`Screenshots saved to: ${SCREENSHOTS_DIR}`);

  } catch (error) {
    console.error('Error capturing screenshots:', error);
    process.exit(1);
  } finally {
    await browser.close();
  }
}

captureScreenshots().catch(error => {
  console.error('Fatal error:', error);
  process.exit(1);
});
