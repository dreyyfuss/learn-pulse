import { test, expect } from '@playwright/test';

const LEARNER_EMAIL    = process.env.E2E_LEARNER_EMAIL    ?? 'learner@example.com';
const LEARNER_PASSWORD = process.env.E2E_LEARNER_PASSWORD ?? 'password123';

test.describe('Happy-path smoke tests', () => {
  test('landing page loads', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveTitle(/LearnPulse/i);
  });

  test('login page renders and shows form', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByPlaceholder('you@example.com')).toBeVisible();
    await expect(page.getByPlaceholder('Enter your password')).toBeVisible();
    await expect(page.getByRole('button', { name: /Continue/i })).toBeVisible();
  });

  test('invalid login shows error', async ({ page }) => {
    await page.goto('/login');
    await page.getByPlaceholder('you@example.com').fill('nobody@example.com');
    await page.getByPlaceholder('Enter your password').fill('wrongpassword');
    await page.getByRole('button', { name: /Continue/i }).click();
    await expect(page.getByText(/Login failed|Invalid|credentials/i)).toBeVisible({ timeout: 8000 });
  });

  test('learner can log in and reach dashboard', async ({ page }) => {
    await page.goto('/login');
    await page.getByPlaceholder('you@example.com').fill(LEARNER_EMAIL);
    await page.getByPlaceholder('Enter your password').fill(LEARNER_PASSWORD);
    await page.getByRole('button', { name: /Continue/i }).click();
    await expect(page).toHaveURL(/\/learn\/dashboard/, { timeout: 10_000 });
    await expect(page.getByText(/dashboard/i).first()).toBeVisible();
  });

  test('course discovery page lists courses', async ({ page }) => {
    await page.goto('/login');
    await page.getByPlaceholder('you@example.com').fill(LEARNER_EMAIL);
    await page.getByPlaceholder('Enter your password').fill(LEARNER_PASSWORD);
    await page.getByRole('button', { name: /Continue/i }).click();
    await page.waitForURL(/\/learn\/dashboard/);

    await page.goto('/learn/browse');
    await expect(page.getByText(/Find your next course/i)).toBeVisible();
  });

  test('my certificates page renders', async ({ page }) => {
    await page.goto('/login');
    await page.getByPlaceholder('you@example.com').fill(LEARNER_EMAIL);
    await page.getByPlaceholder('Enter your password').fill(LEARNER_PASSWORD);
    await page.getByRole('button', { name: /Continue/i }).click();
    await page.waitForURL(/\/learn\/dashboard/);

    await page.goto('/learn/certificates');
    await expect(page.getByText(/certificates/i).first()).toBeVisible();
  });

  test('profile page renders and shows form', async ({ page }) => {
    await page.goto('/login');
    await page.getByPlaceholder('you@example.com').fill(LEARNER_EMAIL);
    await page.getByPlaceholder('Enter your password').fill(LEARNER_PASSWORD);
    await page.getByRole('button', { name: /Continue/i }).click();
    await page.waitForURL(/\/learn\/dashboard/);

    await page.goto('/profile');
    await expect(page.getByText(/Your profile/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /Save changes/i })).toBeVisible();
  });
});
