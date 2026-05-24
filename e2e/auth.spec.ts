import { test, expect } from '@playwright/test';

test.describe('Authentication', () => {
  test('login page loads with correct heading', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
  });

  test('register page loads with correct heading', async ({ page }) => {
    await page.goto('/register');
    await expect(page.getByRole('heading', { name: 'Create an account' })).toBeVisible();
  });

  test('login page shows error with wrong password', async ({ page }) => {
    await page.goto('/login');
    await page.getByPlaceholder('you@example.com').fill('nobody@example.com');
    await page.getByPlaceholder('Enter your password').fill('wrongpassword');
    await page.getByRole('button', { name: /Continue/i }).click();
    await expect(page.locator('p[style*="color"]')).toBeVisible({ timeout: 8000 });
  });

  test('register form rejects submission when no role is selected', async ({ page }) => {
    await page.goto('/register');
    await page.getByPlaceholder('Alex Reyes').fill('Test User');
    await page.getByPlaceholder('you@example.com').fill('test@example.com');
    await page.getByPlaceholder('Min. 8 characters').fill('Password123!');

    // Deselect default "Learn" role so neither role is chosen
    await page.locator('.role-option').first().click();

    await page.getByRole('button', { name: /Create account/i }).click();
    await expect(page.locator('p[style*="color"]')).toContainText(/role/i);
  });

  test('login page has link to register and register page has link to login', async ({ page }) => {
    await page.goto('/login');
    const registerLink = page.getByRole('link', { name: 'Create an account' });
    await expect(registerLink).toBeVisible();
    await registerLink.click();
    await expect(page).toHaveURL('/register');

    const signInLink = page.getByRole('link', { name: 'Sign in' });
    await expect(signInLink).toBeVisible();
  });
});