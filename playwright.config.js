// @ts-check
const { defineConfig, devices } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './tests/e2e',
  timeout: 30_000,
  expect: {
    timeout: 5_000
  },
  use: {
    baseURL: 'http://127.0.0.1:5600',
    trace: 'on-first-retry'
  },
  webServer: {
    command: 'npx http-server frontend -p 5600 -c-1',
    url: 'http://127.0.0.1:5600/customer-app.html',
    reuseExistingServer: true,
    timeout: 15_000
  },
  projects: [
    {
      name: 'chromium-desktop',
      use: { ...devices['Desktop Chrome'] }
    },
    {
      name: 'mobile-chrome',
      use: { ...devices['Pixel 5'] }
    }
  ]
});
