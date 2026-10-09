import { defineConfig, devices } from '@playwright/test'

const externalBaseURL = process.env.PLAYWRIGHT_BASE_URL
const backendURL = 'http://127.0.0.1:18080'
const frontendURL = 'http://127.0.0.1:14173'
const maven = process.platform === 'win32' ? '.\\mvnw.cmd' : 'bash ./mvnw'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  forbidOnly: Boolean(process.env.CI),
  failOnFlakyTests: Boolean(process.env.CI),
  retries: process.env.CI ? 2 : 0,
  reporter: [[process.env.CI ? 'github' : 'list'], ['html', { open: 'never' }]],
  use: {
    baseURL: externalBaseURL ?? frontendURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: externalBaseURL ? undefined : [
    {
      command: `${maven} --batch-mode spring-boot:test-run "-Dspring-boot.run.main-class=com.LDQuang.mini_ledger.TestMiniLedgerApplication" "-Dspring-boot.run.arguments=--spring.docker.compose.enabled=false --server.address=127.0.0.1 --server.port=18080 --server.servlet.session.cookie.secure=false --debug=false"`,
      cwd: '..',
      url: `${backendURL}/actuator/health/readiness`,
      timeout: 240_000,
      reuseExistingServer: false,
      stderr: 'pipe',
    },
    {
      command: 'npm run preview -- --host 127.0.0.1 --port 14173 --strictPort',
      env: { API_PROXY_TARGET: backendURL },
      url: frontendURL,
      timeout: 30_000,
      reuseExistingServer: false,
      stderr: 'pipe',
    },
  ],
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
})
