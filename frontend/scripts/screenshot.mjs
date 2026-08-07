/**
 * 화면 스냅샷.
 *
 * 실행 중인 개발 서버에 붙어 아래 `SCREENS`의 화면을 찍는다. PR에 붙일 이미지를 만드는
 * 용도이며 테스트가 아니다. 통과·실패를 판정하지 않는다.
 *
 * 코드와 단위 테스트로는 확인할 수 없는 것들이 있다. 폰트 폴백이 깨져 CJK가 두부(□□□)로
 * 나와도 DOM 단언은 통과하고, 여백과 대비는 클래스 이름을 봐서는 알 수 없다.
 *
 * 사용법:
 *   0) pnpm --filter @na-wa/frontend exec playwright install chromium  (최초 1회)
 *   1) pnpm dev
 *   2) pnpm --filter @na-wa/frontend screenshot
 *   3) 생긴 PNG를 PR 본문에 끌어다 놓는다
 *
 * 환경변수:
 *   SCREENSHOT_BASE  대상 주소 (기본 http://localhost:5173)
 *   SCREENSHOT_OUT   출력 경로 (기본 frontend/screenshots)
 *   SCREENSHOT_CHANNEL Playwright 브라우저 채널 (예: 설치된 Google Chrome은 chrome)
 *
 * 출력물은 저장소에 커밋하지 않는다. `.gitignore`에 들어 있다.
 */
import { mkdir } from 'node:fs/promises'
import { relative } from 'node:path'

import { chromium } from '@playwright/test'

/**
 * 시안 기준 폭이다. 리뷰어가 시안과 나란히 놓고 볼 수 있도록 고정한다.
 * 2배율로 찍어야 GitHub에서 축소돼도 글자가 뭉개지지 않는다.
 */
const VIEWPORT = { width: 390, height: 844 }
const SCALE = 2

/**
 * 끝 슬래시를 제거한 뒤 화면 경로와 결합한다.
 *
 * `http://localhost:5173/`처럼 주소가 슬래시로 끝나면 `//sign-in`이 만들어진다. 개발
 * 서버는 SPA fallback으로 어떤 경로에나 200을 돌려주므로 이동은 성공하고, 라우터만
 * 매칭에 실패해 모든 화면이 NotFound로 찍힌다. 실패가 성공처럼 보이는 것을 막는다.
 */
const BASE = (process.env.SCREENSHOT_BASE ?? 'http://localhost:5173').replace(/\/+$/, '')
const OUT = process.env.SCREENSHOT_OUT ?? 'screenshots'
const CHANNEL = process.env.SCREENSHOT_CHANNEL

/** @typedef {(page: import('@playwright/test').Page) => Promise<unknown>} Hook */

/** 인증된 회원인 것처럼 프로필 응답을 세운다. 백엔드 `ApiResponse` 봉투를 그대로 흉내낸다. */
function stubMemberProfile(page) {
  return page.route('**/api/v1/members/me', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        data: {
          memberId: 1,
          displayName: 'Mina Park',
          profileImageUrl: null,
          preferredLanguage: 'en',
          preferredCurrencyCode: 'KRW',
          onboardingRequired: false,
        },
      }),
    }),
  )
}

function stubJourneyDetail(page) {
  return Promise.all([
    page.route('**/api/v1/journeys/42', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          data: {
            tripId: 42,
            title: 'Seoul Foodie Week',
            startDate: '2026-08-10',
            endDate: '2026-08-12',
            budgetAmount: 1500000,
            companionPreference: '2-4',
            regions: [],
          },
        }),
      }),
    ),
    page.route('**/api/v1/journeys/42/timeline', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          data: {
            tripId: 42,
            timeline: [
              {
                visitDate: '2026-08-10',
                items: [
                  {
                    tripItemId: 1,
                    itemId: 101,
                    status: 'ADDED',
                    displayOrder: 0,
                    note: 'Try the evening market',
                    exploreItem: {
                      itemType: 'EVENT',
                      title: 'Seoul Night Market',
                      thumbnailUrl: null,
                      imageUrls: [],
                      location: {
                        region1: 'Seoul',
                        region2: 'Yeouido',
                        region3: null,
                        addressRoad: null,
                        addressDetail: null,
                        latitude: null,
                        longitude: null,
                      },
                    },
                  },
                ],
              },
            ],
          },
        }),
      }),
    ),
  ])
}

/**
 * 지갑 홈 응답을 세운다.
 *
 * 금액은 `BigDecimal`이 JSON number로 내려오고, `createdAt`은 `LocalDateTime`이라 오프셋이
 * 없다. 실제 응답 모양을 그대로 흉내내야 자릿수 구분과 날짜 해석이 시안대로 찍힌다.
 *
 * `transactions`에 빈 배열을 넘기면 거래 없는 상태를 찍는다.
 */
function stubWalletHome(page, transactions) {
  return page.route('**/api/v1/wallet', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        data: {
          balance: 84500,
          availabilityStatus: 'ACTIVE',
          recentTransactions: transactions,
        },
      }),
    }),
  )
}

/** 거래 종류마다 아이콘과 문구가 다르므로, 한 장에 여러 종류를 섞어 찍는다. */
const WALLET_TRANSACTIONS = [
  {
    transferId: 1,
    transferType: 'QR_PAYMENT',
    entryType: 'DEBIT',
    amount: 18000,
    balanceAfter: 84500,
    createdAt: '2026-07-25T12:00:00',
  },
  {
    transferId: 2,
    transferType: 'TOPUP',
    entryType: 'CREDIT',
    amount: 100000,
    balanceAfter: 102500,
    createdAt: '2026-07-24T09:12:00',
  },
  {
    transferId: 3,
    transferType: 'SETTLEMENT',
    entryType: 'CREDIT',
    amount: 32500,
    balanceAfter: 2500,
    createdAt: '2026-07-22T21:40:00',
  },
  {
    transferId: 4,
    transferType: 'DEPOSIT_HOLD',
    entryType: 'DEBIT',
    amount: 50000,
    balanceAfter: 52500,
    createdAt: '2026-07-20T18:05:00',
  },
]

/**
 * 찍을 화면.
 *
 * 작업 중인 화면을 여기에 추가한다. `prepare`는 진입한 뒤 실행되며, 바텀시트를 연 상태처럼
 * 조작이 필요한 화면을 찍을 때 쓴다. `setup`은 진입 **전에** 실행된다. 라우터 guard가
 * 이동 중에 API를 부르므로, 인증이 필요한 화면은 여기서 응답을 세워 둬야 한다. 그러지
 * 않으면 guard가 로그인 화면으로 보내고 그 화면이 조용히 대신 찍힌다.
 *
 * 머무르지 않고 스스로 빠져나가는 상태는 넣지 않는다. 예를 들어 `/auth/callback`의 대기
 * 화면은 세션 조회가 끝나는 즉시 `router.replace`로 넘어가므로, 목록에 넣으면 대기 화면이
 * 아니라 이동한 뒤의 화면이 조용히 찍힌다. 그런 상태를 찍으려면 응답을 붙잡아야 하는데
 * 그것은 리뷰용 이미지가 아니라 검증 도구의 일이다.
 *
 * @type {{ name: string, path: string, setup?: Hook, prepare?: Hook }[]}
 */
const SCREENS = [
  { name: '01-sign-in', path: '/sign-in' },
  { name: '02-callback-failed', path: '/auth/callback?error=AUTH-014' },
  { name: '03-not-found', path: '/no-such-page' },
  {
    name: '04-settings',
    path: '/settings',
    // 백엔드를 띄우지 않고 찍는다. 리뷰용 이미지지 통합 검증이 아니다.
    setup: (page) => stubMemberProfile(page),
  },
  {
    name: '05-settings-language',
    path: '/settings',
    setup: (page) => stubMemberProfile(page),
    prepare: async (page) => {
      await page.getByLabel('Change screen language').click()
      await page.waitForSelector('[role="dialog"]')
    },
  },
  {
    name: '06-journey-create',
    path: '/journeys/new',
    setup: (page) => stubMemberProfile(page),
  },
  {
    name: '07-journey-detail',
    path: '/journeys/42',
    setup: (page) => Promise.all([stubMemberProfile(page), stubJourneyDetail(page)]),
  },
  {
    name: '08-wallet',
    path: '/wallet',
    setup: async (page) => {
      await stubMemberProfile(page)
      await stubWalletHome(page, WALLET_TRANSACTIONS)
    },
  },
  {
    name: '09-wallet-empty',
    path: '/wallet',
    setup: async (page) => {
      await stubMemberProfile(page)
      await stubWalletHome(page, [])
    },
  },

  // 조작이 필요한 상태는 이렇게 찍는다.
  //
  // {
  //   name: '04-sign-in-language',
  //   path: '/sign-in',
  //   prepare: async (page) => {
  //     await page.getByLabel('Change screen language').click()
  //     await page.waitForSelector('[role="dialog"]')
  //   },
  // },
]

async function assertServerIsUp() {
  try {
    await fetch(BASE, { signal: AbortSignal.timeout(3000) })
  } catch {
    console.error(
      `개발 서버에 붙지 못했다: ${BASE}\n` +
        '  먼저 `pnpm dev`로 서버를 띄운다.\n' +
        '  다른 주소라면 SCREENSHOT_BASE로 지정한다.',
    )
    process.exit(1)
  }
}

async function launchBrowser() {
  try {
    return await chromium.launch(CHANNEL === undefined ? {} : { channel: CHANNEL })
  } catch (error) {
    // Playwright가 안내하는 `npx playwright install`은 이 워크스페이스에서 맞지 않는다.
    console.error(
      `Chromium을 띄우지 못했다.\n` +
        '  브라우저가 없다면 최초 1회 설치한다.\n' +
        '  pnpm --filter @na-wa/frontend exec playwright install chromium\n' +
        `  원본 오류: ${error.message}`,
    )
    process.exit(1)
  }
}

await assertServerIsUp()
await mkdir(OUT, { recursive: true })

const browser = await launchBrowser()
const context = await browser.newContext({
  viewport: VIEWPORT,
  deviceScaleFactor: SCALE,
  locale: 'en-US',
})

let failed = 0

for (const screen of SCREENS) {
  const page = await context.newPage()

  try {
    await screen.setup?.(page)

    // 화면 경로 앞의 슬래시도 함께 정규화해 중복 슬래시를 만들지 않는다.
    await page.goto(`${BASE}/${screen.path.replace(/^\/+/, '')}`, { waitUntil: 'networkidle' })
    await screen.prepare?.(page)

    // 웹폰트와 전환이 자리를 잡을 시간을 준다. 없으면 폴백 폰트가 찍히는 경우가 있다.
    await page.waitForTimeout(400)
    await page.screenshot({ path: `${OUT}/${screen.name}.png` })

    console.log(`  ✓ ${screen.name}.png  ← ${screen.path}`)
  } catch (error) {
    // 한 화면이 실패해도 나머지는 찍되, 실패했다는 사실은 종료 코드로 남긴다.
    failed += 1
    console.error(`  ✗ ${screen.name}  ← ${screen.path}\n    ${error.message}`)
  } finally {
    await page.close()
  }
}

await browser.close()

// 찍히지 않은 화면을 못 보고 넘어가지 않도록 실패를 종료 코드로 알린다. 이때는 산출물이
// 온전하지 않으므로 PR에 붙이라는 안내도 하지 않는다.
if (failed > 0) {
  console.error(`\n${failed}/${SCREENS.length}개 화면을 찍지 못했다. 위 오류를 확인한다.`)
  process.exit(1)
}

console.log(`\n${relative(process.cwd(), OUT) || OUT}/ 에 저장했다. PR 본문에 끌어다 놓는다.`)
