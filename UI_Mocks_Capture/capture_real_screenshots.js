const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

const SCREENSHOTS_DIR = path.join(__dirname, 'ui_screenshots_real');
const BASE_URL = 'http://localhost:4200';

if (!fs.existsSync(SCREENSHOTS_DIR)) {
  fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true });
}

async function captureRealScreenshots() {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1280, height: 800 } });

  try {
    console.log('Capturing REAL application screenshots...\n');

    // 1. LOGIN PAGE
    console.log('1. Capturing login page...');
    await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1500);
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '01_login.png'), fullPage: false });
    console.log('✓ Login page captured\n');

    // 2. LOGIN WITH CREDENTIALS
    console.log('2. Logging in with credentials...');
    await page.fill('input[type="text"]', 'Administrator');
    await page.fill('input[type="password"]', 'admin');
    await page.click('button[type="submit"]');
    await page.waitForNavigation({ waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2000);
    console.log('✓ Login successful\n');

    // 3. DASHBOARD
    console.log('3. Capturing dashboard...');
    await page.goto(`${BASE_URL}/admin/dashboard`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2500);
    // Scroll to see more content
    await page.evaluate(() => window.scrollBy(0, 300));
    await page.waitForTimeout(500);
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '02_dashboard.png'), fullPage: true });
    console.log('✓ Dashboard captured\n');

    // 4. ORDERS
    console.log('4. Capturing orders page...');
    await page.goto(`${BASE_URL}/orders`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2500);
    await page.evaluate(() => window.scrollBy(0, 200));
    await page.waitForTimeout(500);
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '03_orders.png'), fullPage: true });
    console.log('✓ Orders page captured\n');

    // 5. VENDOR OPS
    console.log('5. Capturing vendor ops...');
    await page.goto(`${BASE_URL}/vendor-ops`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2500);
    await page.evaluate(() => window.scrollBy(0, 200));
    await page.waitForTimeout(500);
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '04_vendor_ops.png'), fullPage: true });
    console.log('✓ Vendor ops captured\n');

    // 6. BRANCH OPS
    console.log('6. Capturing branch ops...');
    await page.goto(`${BASE_URL}/branch-ops`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2500);
    await page.evaluate(() => window.scrollBy(0, 200));
    await page.waitForTimeout(500);
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '05_branch_ops.png'), fullPage: true });
    console.log('✓ Branch ops captured\n');

    // 7. BILLS
    console.log('7. Capturing bills...');
    await page.goto(`${BASE_URL}/bills`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2500);
    await page.evaluate(() => window.scrollBy(0, 200));
    await page.waitForTimeout(500);
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '06_bills.png'), fullPage: true });
    console.log('✓ Bills captured\n');

    // 8. STOCK
    console.log('8. Capturing stock...');
    await page.goto(`${BASE_URL}/stock`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2500);
    await page.evaluate(() => window.scrollBy(0, 200));
    await page.waitForTimeout(500);
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '07_stock.png'), fullPage: true });
    console.log('✓ Stock captured\n');

    // 9. VENDORS
    console.log('9. Capturing vendors...');
    await page.goto(`${BASE_URL}/vendors`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2500);
    await page.evaluate(() => window.scrollBy(0, 200));
    await page.waitForTimeout(500);
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '08_vendors.png'), fullPage: true });
    console.log('✓ Vendors captured\n');

    // 10. ITEMS
    console.log('10. Capturing items...');
    await page.goto(`${BASE_URL}/items`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2500);
    await page.evaluate(() => window.scrollBy(0, 200));
    await page.waitForTimeout(500);
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '09_items.png'), fullPage: true });
    console.log('✓ Items captured\n');

    // 11. CATEGORIES
    console.log('11. Capturing categories...');
    await page.goto(`${BASE_URL}/categories`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2500);
    await page.evaluate(() => window.scrollBy(0, 200));
    await page.waitForTimeout(500);
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '10_categories.png'), fullPage: true });
    console.log('✓ Categories captured\n');

    console.log('\n=== SUCCESS: All REAL screenshots captured! ===');
    console.log(`Screenshots saved to: ${SCREENSHOTS_DIR}`);
    console.log(`Total files: ${fs.readdirSync(SCREENSHOTS_DIR).length}`);

  } catch (error) {
    console.error('Error capturing screenshots:', error.message);
    console.error(error);
    process.exit(1);
  } finally {
    await browser.close();
  }
}

captureRealScreenshots().catch(error => {
  console.error('Fatal error:', error);
  process.exit(1);
});
