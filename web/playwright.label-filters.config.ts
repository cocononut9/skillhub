import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  testMatch: 'label-filters.spec.ts',
  workers: 1,
  reporter: 'list',
  use: {
    ...devices['Desktop Chrome'],
    channel: process.env.PLAYWRIGHT_CHANNEL,
    baseURL: 'http://127.0.0.1:3104',
    screenshot: 'only-on-failure',
  },
  webServer: {
    command: 'pnpm exec vite --host 127.0.0.1 --port 3104 --strictPort',
    url: 'http://127.0.0.1:3104',
    reuseExistingServer: false,
  },
})
