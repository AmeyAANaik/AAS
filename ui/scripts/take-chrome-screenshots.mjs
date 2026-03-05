import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';

const UI_BASE_URL = process.env.UI_BASE_URL || 'http://localhost:4200';
const USERNAME = process.env.USERNAME || 'Administrator';
const PASSWORD = process.env.PASSWORD || 'admin';
const OUT_DIR = process.env.OUT_DIR || path.resolve(process.cwd(), '../docs/screenshots');

fs.mkdirSync(OUT_DIR, { recursive: true });

function out(name) {
  return path.join(OUT_DIR, name);
}

async function main() {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 }
  });

  // Ensure a consistent clean session for screenshots.
  await context.addInitScript(() => {
    try {
      localStorage.clear();
      sessionStorage.clear();
    } catch {}
  });

  const page = await context.newPage();

  // Login page (go straight to vendors after sign-in to avoid extra calls from dashboard).
  await page.goto(`${UI_BASE_URL}/login?returnUrl=%2Fvendors`, { waitUntil: 'networkidle' });
  await page.waitForSelector('form.form input[name="username"]', { timeout: 30_000 });
  await page.screenshot({ path: out('01-login.png'), fullPage: true });

  // Sign in
  await page.fill('input[name="username"]', USERNAME);
  await page.fill('input[name="password"]', PASSWORD);
  await page.click('button.btn-primary');
  await page.waitForURL('**/vendors', { timeout: 30_000 });
  await page.screenshot({ path: out('02a-vendors-nav.png'), fullPage: true });
  try {
    await page.waitForSelector('.vendor-page', { timeout: 30_000 });
    await Promise.race([
      page.waitForSelector('.vendor-page table', { timeout: 30_000 }),
      page.waitForSelector('.vendor-page app-empty-state', { timeout: 30_000 })
    ]);
    await page.screenshot({ path: out('02-vendors.png'), fullPage: true });
  } catch (err) {
    await page.screenshot({ path: out('02-vendors-error.png'), fullPage: true });
    const url = page.url();
    throw new Error(`Vendors page did not render correctly (current URL: ${url}). See 02-vendors-error.png. ${err}`);
  }

  await browser.close();
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
