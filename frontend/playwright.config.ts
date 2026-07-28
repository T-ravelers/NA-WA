import process from 'node:process'

import { defineConfig, devices } from '@playwright/test'

const isCi = Boolean(process.env.CI)
const baseURL = isCi ? 'http://127.0.0.1:4173' : 'http://127.0.0.1:5173'

export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  expect: {
    timeout: 5_000,
  },
  forbidOnly: isCi,
  retries: isCi ? 2 : 0,
  workers: isCi ? 1 : undefined,
  reporter: isCi ? 'line' : 'html',
  use: {
    baseURL,
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
      },
    },
    {
      name: 'firefox',
      use: {
        ...devices['Desktop Firefox'],
      },
    },
    {
      name: 'webkit',
      use: {
        ...devices['Desktop Safari'],
      },
    },
  ],
  webServer: {
    command: isCi ? 'pnpm preview --host 127.0.0.1' : 'pnpm dev --host 127.0.0.1',
    url: baseURL,
    reuseExistingServer: !isCi,
    timeout: 60_000,
  },
})
