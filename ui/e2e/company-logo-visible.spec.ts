import { expect, test } from '@playwright/test';

test('Company logo loads on Company Settings', async ({ page }) => {
  const requestFailures: string[] = [];
  const debug: string[] = [];

  page.on('requestfailed', request => {
    const url = request.url();
    if (request.resourceType() === 'image' || url.includes('/files/')) {
      requestFailures.push(`${request.failure()?.errorText ?? 'FAILED'} ${url}`);
    }
  });

  try {
    await page.goto('/login?returnUrl=/company-settings');

    await page.getByRole('textbox', { name: 'Username' }).fill(process.env.PLAYWRIGHT_USERNAME ?? 'Administrator');
    await page.getByRole('textbox', { name: 'Password' }).fill(process.env.PLAYWRIGHT_PASSWORD ?? 'admin');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await page.waitForURL(/\/company-settings$/);

    const logo = page.locator('img.logo-preview-image');
    await expect(logo).toBeVisible();

    const src = (await logo.getAttribute('src')) ?? '';
    debug.push(`logo.src=${src || '<empty>'}`);
    expect(src).toBeTruthy();
    expect(src).not.toMatch(/^https?:\/\/localhost:8080/i);
    expect(src).toMatch(/^\/api\/(private\/)?files\//i);

    const resolvedUrl = new URL(src, page.url()).toString();
    debug.push(`logo.resolvedUrl=${resolvedUrl}`);

    const apiResponse = await page.request.get(resolvedUrl);
    debug.push(`logo.fetchStatus=${apiResponse.status()}`);
    debug.push(`logo.fetchContentType=${apiResponse.headers()['content-type'] ?? '<none>'}`);

    // Ensure the browser actually decoded the image (not a broken 404/HTML response).
    await logo.evaluate(img => new Promise<void>(resolve => {
      const el = img as HTMLImageElement;
      if (el.complete) {
        resolve();
        return;
      }
      el.addEventListener('load', () => resolve(), { once: true });
      el.addEventListener('error', () => resolve(), { once: true });
    }));

    const naturalWidth = await logo.evaluate(img => (img as HTMLImageElement).naturalWidth);
    debug.push(`logo.naturalWidth=${naturalWidth}`);
    expect(naturalWidth).toBeGreaterThan(0);
  } finally {
    if (debug.length) {
      test.info().annotations.push({ type: 'debug', description: debug.join('\n') });
    }
    if (requestFailures.length) {
      test.info().annotations.push({ type: 'image-request-failures', description: requestFailures.join('\n') });
    }
  }
});
