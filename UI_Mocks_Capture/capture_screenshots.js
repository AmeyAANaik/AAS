const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

const SCREENSHOTS_DIR = path.join(__dirname, 'ui_screenshots');
const BASE_URL = 'http://localhost:4200';

// Create screenshots directory
if (!fs.existsSync(SCREENSHOTS_DIR)) {
  fs.mkdirSync(SCREENSHOTS_DIR);
}

async function captureScreenshots() {
  const browser = await chromium.launch({
    headless: true
  });

  const page = await browser.newPage({
    viewport: { width: 1280, height: 720 }
  });

  try {
    console.log('Starting screenshot capture...\n');

    // 1. Login Page
    console.log('Capturing login page...');
    await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle' });
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '01_login.png') });
    console.log('✓ Login page captured\n');

    // Mock login by setting localStorage tokens
    console.log('Setting up mock authentication...');
    await page.evaluate(() => {
      localStorage.setItem('aas_auth_token', 'mock-jwt-token-for-demo');
      localStorage.setItem('aas_auth_role', 'admin');
      localStorage.setItem('aas_auth_features', JSON.stringify([
        'dashboard.view',
        'orders.view',
        'vendors.view',
        'branches.view',
        'categories.view',
        'items.view',
        'stock.view',
        'bills.view',
        'vendor_ops.view',
        'branch_ops.view',
        'company_settings.view',
        'user_settings.view',
        'reports.view'
      ]));
      localStorage.setItem('aas_auth_home_route', '/admin/dashboard');
    });

    // 2. Dashboard
    console.log('Capturing dashboard...');
    await page.goto(`${BASE_URL}/admin/dashboard`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1000); // Wait for animations
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '02_dashboard.png') });
    console.log('✓ Dashboard captured\n');

    // 3. Orders List
    console.log('Capturing orders list...');
    await page.goto(`${BASE_URL}/orders`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '03_orders_list.png') });
    console.log('✓ Orders list captured\n');

    // 4. Order Create
    console.log('Capturing order create page...');
    await page.goto(`${BASE_URL}/orders/create`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '04_order_create.png') });
    console.log('✓ Order create page captured\n');

    // 5. Vendor Ops
    console.log('Capturing vendor operations...');
    await page.goto(`${BASE_URL}/vendor-ops`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '05_vendor_ops.png') });
    console.log('✓ Vendor operations captured\n');

    // 6. Branch Ops
    console.log('Capturing branch operations...');
    await page.goto(`${BASE_URL}/branch-ops`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '06_branch_ops.png') });
    console.log('✓ Branch operations captured\n');

    // 7. Bills
    console.log('Capturing bills page...');
    await page.goto(`${BASE_URL}/bills`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '07_bills.png') });
    console.log('✓ Bills page captured\n');

    // 8. Stock
    console.log('Capturing stock page...');
    await page.goto(`${BASE_URL}/stock`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '08_stock.png') });
    console.log('✓ Stock page captured\n');

    // 9. Vendors
    console.log('Capturing vendors page...');
    await page.goto(`${BASE_URL}/vendors`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '09_vendors.png') });
    console.log('✓ Vendors page captured\n');

    // 10. Items
    console.log('Capturing items page...');
    await page.goto(`${BASE_URL}/items`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '10_items.png') });
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

// Run with error handling
captureScreenshots().catch(error => {
  console.error('Fatal error:', error);
  process.exit(1);
});
