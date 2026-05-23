import { test, expect } from '@playwright/test';

test.describe('Core learner flow', () => {
  test('landing page loads with hero heading', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('.lp-hl-1')).toContainText('Quietly confident');
  });

  test('landing page navigation links are present', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('link', { name: 'Sign in' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Start learning' })).toBeVisible();
  });

  test('sign-in nav link navigates to /login', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('link', { name: 'Sign in' }).click();
    await expect(page).toHaveURL('/login');
    await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
  });

  test('start learning CTA navigates to /register', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('link', { name: 'Start learning' }).first().click();
    await expect(page).toHaveURL('/register');
    await expect(page.getByRole('heading', { name: 'Create an account' })).toBeVisible();
  });

  test('landing page course section shows sample courses', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('#courses')).toBeVisible();
    await expect(page.locator('.lp-cc').first()).toBeVisible();
  });

  test('unauthenticated access to dashboard redirects to login', async ({ page }) => {
    await page.goto('/learn/dashboard');
    await expect(page).toHaveURL(/login/);
  });
});