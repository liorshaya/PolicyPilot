import { defineConfig, devices } from '@playwright/test'

/**
 * The access gate and sandbox isolation against the real stack (Document 6, End to end: the second project; Document 5,
 * Security Test Plan, End to end). Docker Compose runs the database, the backend built from its Dockerfile and the web
 * app, and nothing here calls a model. Start the stack first (make up) and give the tests its access code in
 * E2E_ACCESS_CODE; CI stage 7 does both after the demo flows.
 */
export default defineConfig({
  testDir: './e2e/stack',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  // the code exchange allows 5 a minute from one address (Document 5), and a retry would spend them
  retries: 0,
  workers: 1,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL: process.env.STACK_URL ?? 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
