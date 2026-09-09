import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './tests',
  outputDir: 'artifacts/test-results',
  timeout: 90_000,
  fullyParallel: false,
  forbidOnly: true,
  retries: 0,
  reporter: [
    ['line'],
    ['junit', { outputFile: 'artifacts/e2e-junit.xml' }]
  ],
  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:8081',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure'
  }
})
