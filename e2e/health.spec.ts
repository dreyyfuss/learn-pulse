import { test, expect } from '@playwright/test';

test.describe('Smoke tests', () => {
  test('homepage returns HTTP 200', async ({ request }) => {
    const response = await request.get('/');
    expect(response.status(), 'Homepage should return 200').toBe(200);
  });

  test('AI service health endpoint returns healthy via Traefik', async ({ request }) => {
    const response = await request.get(
      process.env.E2E_BASE_URL
        ? `${process.env.E2E_BASE_URL.replace(':5173', '')}/api/ai/healthz`
        : 'http://localhost/api/ai/healthz',
    );
    expect(response.status(), 'AI health endpoint should return 200').toBe(200);
    const body = await response.json();
    expect(body.status ?? body.message ?? 'ok').toBeTruthy();
  });

  test('no console errors on landing page load', async ({ page }) => {
    const errors: string[] = [];
    page.on('console', msg => {
      if (msg.type() === 'error') errors.push(msg.text());
    });
    await page.goto('/');
    await page.waitForLoadState('networkidle');
    expect(errors, `Console errors found: ${errors.join(', ')}`).toHaveLength(0);
  });

  test('LearnPulse brand name renders in landing page navbar', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('.lp-nav-logo')).toHaveText('LearnPulse');
  });

  test('login page is reachable', async ({ request }) => {
    const response = await request.get('/login');
    expect(response.status(), '/login should return 200').toBe(200);
  });

  test('register page is reachable', async ({ request }) => {
    const response = await request.get('/register');
    expect(response.status(), '/register should return 200').toBe(200);
  });
});