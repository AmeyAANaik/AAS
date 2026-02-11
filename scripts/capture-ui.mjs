import { chromium } from '@playwright/test';

const base = 'http://localhost:4200';
const routes = [
  { path: '/vendors', file: '/tmp/ui-vendors.png' },
  { path: '/items', file: '/tmp/ui-items.png' },
  { path: '/categories', file: '/tmp/ui-categories.png' },
];

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const run = async () => {
  const browser = await chromium.launch({
    args: ['--no-sandbox', '--disable-setuid-sandbox'],
  });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
  });
  await context.addInitScript(() => {
    localStorage.setItem('aas_auth_token', 'debug-token');
  });
  const page = await context.newPage();

  for (const { path, file } of routes) {
    const url = `${base}${path}`;
    try {
      await page.goto(url, { waitUntil: 'networkidle', timeout: 15000 });
    } catch {
      await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 15000 });
    }
    await sleep(1000);
    await page.screenshot({ path: file, fullPage: true });
  }

  await browser.close();
};

run().catch((err) => {
  console.error(err);
  process.exit(1);
});
