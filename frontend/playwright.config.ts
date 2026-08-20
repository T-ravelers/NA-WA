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
    // CI에서는 빌드까지 여기서 한다. 아래 VITE_API_BASE_URL이 빌드 시점에 박혀야
    // 하므로, 미리 만들어 둔 dist를 쓰면 설정이 반영되지 않는다.
    command: isCi ? 'pnpm build && pnpm preview --host 127.0.0.1' : 'pnpm dev --host 127.0.0.1',
    url: baseURL,
    reuseExistingServer: !isCi,
    timeout: 180_000,
    env: {
      // API 주소를 테스트 서버 자신으로 둔다. 스펙은 모든 API를 page.route로
      // 가로채므로 실제로 이 주소에 서버가 있을 필요는 없지만, 교차 출처면
      // 브라우저가 먼저 preflight를 보내고 그 preflight는 가로채지지 않는다.
      // 그러면 WebKit은 목킹된 응답 대신 실제 응답(또는 실패)을 받아 스펙이
      // 환경에 따라 달라진다 — 로컬에 백엔드가 떠 있는지에 결과가 좌우된다.
      // 같은 출처면 preflight 자체가 없어 세 브라우저가 같은 조건이 된다.
      //
      // 빈 값은 쓸 수 없다. assertApiBaseUrlConfigured가 기동을 막는다.
      VITE_API_BASE_URL: baseURL,
    },
  },
})
