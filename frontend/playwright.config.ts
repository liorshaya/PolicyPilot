import { defineConfig, devices } from '@playwright/test'

/**
 * End-to-end tests of the web app (Document 6, Frontend Test Design): the access gate and the four scripted demo steps,
 * alone and in one run, with every API call answered from the committed fixtures. The dev server is started for the
 * run; CI stage 7 runs this on pull requests. The tests against the real stack are playwright.stack.config.ts's.
 */
export default defineConfig({
  testDir: './e2e',
  testIgnore: 'stack/**',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 2 : undefined,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
})
