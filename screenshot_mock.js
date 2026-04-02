const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

const SCREENSHOTS_DIR = path.join(__dirname, 'ui_screenshots');
const FILE_PATH = `file://${path.join(__dirname, 'create_mock_ui.html')}`;

if (!fs.existsSync(SCREENSHOTS_DIR)) {
  fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true });
}

async function captureScreenshots() {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1280, height: 800 } });

  try {
    console.log('Capturing mock UI screenshots...\n');

    // Open HTML file
    await page.goto(FILE_PATH, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1000);

    const screens = [
      { id: 'dashboard', name: '02_dashboard.png', label: 'Dashboard' },
      { id: 'orders', name: '03_orders_list.png', label: 'Orders' },
      { id: 'create', name: '04_order_create.png', label: 'Order Create' },
      { id: 'vendors', name: '05_vendor_ops.png', label: 'Vendor Ops' },
      { id: 'branches', name: '06_branch_ops.png', label: 'Branch Ops' },
      { id: 'bills', name: '07_bills.png', label: 'Bills' },
      { id: 'stock', name: '08_stock.png', label: 'Stock' },
      { id: 'master-vendors', name: '09_vendors.png', label: 'Vendors' },
      { id: 'master-items', name: '10_items.png', label: 'Items' }
    ];

    for (const screen of screens) {
      console.log(`Capturing ${screen.label}...`);
      await page.evaluate((screenId) => {
        showScreen(screenId);
      }, screen.id);
      await page.waitForTimeout(500);
      await page.screenshot({
        path: path.join(SCREENSHOTS_DIR, screen.name),
        fullPage: false
      });
      console.log(`✓ ${screen.label} captured\n`);
    }

    console.log('=== All screenshots captured successfully! ===');
    console.log(`Saved to: ${SCREENSHOTS_DIR}`);

  } catch (error) {
    console.error('Error:', error);
    process.exit(1);
  } finally {
    await browser.close();
  }
}

captureScreenshots().catch(err => {
  console.error('Fatal error:', err);
  process.exit(1);
});
