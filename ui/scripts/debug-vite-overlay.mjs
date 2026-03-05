import { chromium } from 'playwright';
import fs from 'node:fs';

const UI_BASE_URL = process.env.UI_BASE_URL || 'http://localhost:4201';
const OUT = process.env.OUT || '/tmp/vite-overlay.png';

async function main() {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  await page.goto(`${UI_BASE_URL}/login`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(1000);

  const hasOverlay = await page.locator('vite-error-overlay').count();
  console.log('hasOverlay', hasOverlay);

  if (hasOverlay) {
    const overlayText = await page.evaluate(() => {
      const el = document.querySelector('vite-error-overlay');
      if (!el) return null;
      const root = el.shadowRoot;
      return root ? root.textContent : el.textContent;
    });
    console.log('overlayText', (overlayText || '').trim().slice(0, 2000));
  }

  await page.screenshot({ path: OUT, fullPage: true });
  await browser.close();
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
