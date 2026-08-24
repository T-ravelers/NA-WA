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
 *   SCREENSHOT_OUT   출력 경로 (기본 frontend/screenshots — 아래 두 옵션이 접미사를 붙인다)
 *   SCREENSHOT_CHANNEL Playwright 브라우저 채널 (예: 설치된 Google Chrome은 chrome)
 *   SCREENSHOT_WIDTH  뷰포트 폭 (기본 390 — 시안 기준. 280 같은 다른 값은 반응형
 *                     검증용이며 출력이 screenshots-<width>/로 분리된다)
 *   SCREENSHOT_LOCALE 앱 로케일 (기본 en. ja·zh-TW·vi는 출력이 screenshots-<locale>/로
 *                     분리된다. 번역이 없는 문구는 en 폴백으로 찍힌다)
 *   SCREENSHOT_ONLY   화면 이름에 포함된 글자로 고른다. 쉼표로 여러 개(예: report,wallet).
 *                     플로우는 `<flow>-<step>` 이름으로 고른다. 한 화면만 고치고 전량을
 *                     다시 찍지 않기 위한 것이고, PR 첨부용 기본 스냅샷은 여전히 전량
 *                     실행으로 만든다.
 *   SCREENSHOT_FULL_PAGE 1이면 뷰포트가 아니라 페이지 전체를 찍는다. 스크롤이 긴 화면(리포트
 *                     상세)의 아래쪽 섹션을 PR에 보일 때 쓴다. 기본 스냅샷은 뷰포트 그대로다.
 *
 * 출력물은 저장소에 커밋하지 않는다. `.gitignore`에 들어 있다.
 */
import { mkdir } from 'node:fs/promises'
import { relative } from 'node:path'

import { chromium } from '@playwright/test'

/**
 * 시안 기준 폭 390이 기본이다. 리뷰어가 시안과 나란히 놓고 볼 수 있도록 기본 실행은
 * 그대로 두고, SCREENSHOT_WIDTH는 반응형 검증(폴더블 커버 280 등)에만 쓴다.
 * 2배율로 찍어야 GitHub에서 축소돼도 글자가 뭉개지지 않는다.
 */
const DEFAULT_WIDTH = 390
const WIDTH = Number(process.env.SCREENSHOT_WIDTH ?? DEFAULT_WIDTH)

if (!Number.isInteger(WIDTH) || WIDTH <= 0) {
  console.error(`SCREENSHOT_WIDTH가 올바른 폭이 아니다: ${process.env.SCREENSHOT_WIDTH}`)
  process.exit(1)
}

const VIEWPORT = { width: WIDTH, height: 844 }
const SCALE = 2

/**
 * 앱이 지원하는 로케일. 정본은 `src/shared/i18n/locales.ts`다 — 이 스크립트는 Vite 밖에서
 * 도는 플레인 Node라 TS를 import하지 못해 값을 복사해 둔다. 로케일을 늘리면 여기도 맞춘다.
 */
const SUPPORTED_LOCALES = ['en', 'ja', 'zh-TW', 'vi']
const DEFAULT_LOCALE = 'en'
const LOCALE = process.env.SCREENSHOT_LOCALE ?? DEFAULT_LOCALE

if (!SUPPORTED_LOCALES.includes(LOCALE)) {
  console.error(
    `SCREENSHOT_LOCALE이 지원 로케일이 아니다: ${LOCALE} (지원: ${SUPPORTED_LOCALES.join(', ')})`,
  )
  process.exit(1)
}

/**
 * 끝 슬래시를 제거한 뒤 화면 경로와 결합한다.
 *
 * `http://localhost:5173/`처럼 주소가 슬래시로 끝나면 `//sign-in`이 만들어진다. 개발
 * 서버는 SPA fallback으로 어떤 경로에나 200을 돌려주므로 이동은 성공하고, 라우터만
 * 매칭에 실패해 모든 화면이 NotFound로 찍힌다. 실패가 성공처럼 보이는 것을 막는다.
 */
const BASE = (process.env.SCREENSHOT_BASE ?? 'http://localhost:5173').replace(/\/+$/, '')

/**
 * 옵션 실행 산출물은 기본 산출물(screenshots/)과 폴더를 나눈다. 같은 폴더에 덮어쓰면
 * PR에 첨부할 기본 스냅샷이 어떤 조건에서 찍힌 것인지 알 수 없게 된다.
 */
const OUT_SUFFIX =
  (WIDTH === DEFAULT_WIDTH ? '' : `-${WIDTH}`) + (LOCALE === DEFAULT_LOCALE ? '' : `-${LOCALE}`)
const OUT = process.env.SCREENSHOT_OUT ?? `screenshots${OUT_SUFFIX}`
const CHANNEL = process.env.SCREENSHOT_CHANNEL
const ONLY = (process.env.SCREENSHOT_ONLY ?? '')
  .split(',')
  .map((part) => part.trim())
  .filter((part) => part.length > 0)
const FULL_PAGE = process.env.SCREENSHOT_FULL_PAGE === '1'

/** @typedef {(page: import('@playwright/test').Page) => Promise<unknown>} Hook */

/** 인증된 회원인 것처럼 프로필 응답을 세운다. 백엔드 `ApiResponse` 봉투를 그대로 흉내낸다. */
function stubMemberProfile(page, overrides = {}) {
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
          // 저장된 명시 선택이 없으면 프로필의 preferredLanguage가 로케일을 덮는다
          // (localeSync). 러너 로케일과 어긋나면 로그인 화면만 en으로 되돌아간다.
          preferredLanguage: LOCALE,
          preferredCurrencyCode: 'KRW',
          nationalityCode: 'JP',
          accountType: 'TRAVELER',
          onboardingRequired: false,
          ...overrides,
        },
      }),
    }),
  )
}

/**
 * 프로필의 찜·약속 탭.
 *
 * 찜은 Discover 목록 조회를 `savedOnly`로 재사용하므로 목록 엔드포인트를 그대로 세운다.
 * 두 응답 모두 `responseSchema`(zod)를 지나므로 요약 DTO의 필수 필드를 빠짐없이 채운다 —
 * 하나라도 빠지면 검증이 실패해 화면이 오류 상태로 찍힌다.
 */
function stubProfileTabs(page) {
  return Promise.all([
    stubJson(page, '/api/v1/explore/events', {
      content: [
        {
          itemId: 301,
          eventKind: 'FESTIVAL',
          status: 'SCHEDULED',
          title: 'Seoul Lantern Festival',
          subtitle: null,
          thumbnailUrl: null,
          region1: 'Seoul',
          region2: 'Jongno-gu',
          region3: null,
          latitude: 37.5709,
          longitude: 126.9925,
          startDate: '2098-11-01',
          endDate: '2098-11-17',
          saved: true,
        },
        {
          itemId: 302,
          eventKind: 'EXHIBITION',
          status: 'ONGOING',
          title: 'Hanok Craft Week',
          subtitle: null,
          thumbnailUrl: null,
          region1: 'Seoul',
          region2: 'Jung-gu',
          region3: null,
          latitude: 37.5636,
          longitude: 126.9976,
          startDate: '2098-09-04',
          endDate: '2098-09-20',
          saved: true,
        },
      ],
      page: 0,
      size: 30,
      totalElements: 2,
      totalPages: 1,
      hasNext: false,
    }),
    stubJson(page, '/api/v1/explore/places', {
      content: [
        {
          itemId: 501,
          name: 'Gwangjang Market',
          brand: null,
          branch: null,
          placeKind: 'RESTAURANT',
          thumbnailUrl: null,
          imageUrls: [],
          region1: 'Seoul',
          region2: 'Jongno-gu',
          region3: null,
          addressRoad: '88 Changgyeonggung-ro',
          addressDetail: null,
          latitude: 37.5701,
          longitude: 126.9997,
          isActive: true,
          viewCount: 0,
          favoriteCount: 0,
          saved: true,
        },
      ],
      page: 0,
      size: 30,
      totalElements: 1,
      totalPages: 1,
      hasNext: false,
    }),
    stubJson(page, '/api/v1/appointments/me', [
      {
        appointmentId: 71,
        appointmentName: 'Lantern night walk',
        tripId: 42,
        meetingPlace: 'Jongno 3-ga Exit 3',
        activityStartAt: '2098-11-02T19:00:00',
        activityEndAt: '2098-11-02T21:00:00',
        itemId: 301,
        itemType: 'EVENT',
        appointmentStatus: 'RECRUITING',
      },
      {
        appointmentId: 72,
        appointmentName: 'Market food crawl',
        tripId: 42,
        meetingPlace: 'Gwangjang Market north gate',
        activityStartAt: '2098-11-05T12:30:00',
        activityEndAt: '2098-11-05T14:30:00',
        itemId: 501,
        itemType: 'PLACE',
        appointmentStatus: 'IN_PROGRESS',
      },
    ]),
  ])
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
 * Journey 상세는 종료 여부와 무관하게 `/api/v1/reports`를 항상 부른다. 스텁이 없으면
 * 이 요청이 목킹되지 않아 화면이 pending에 머무르거나 실제 백엔드를 친다.
 */
function stubEmptyReportList(page) {
  return page.route('**/api/v1/reports', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ success: true, data: [] }),
    }),
  )
}

/**
 * 여정 커버 자리에 넣는 1x1 PNG. 러너가 외부 주소를 부르지 않게 한다.
 * 실제 커버는 수집 항목의 썸네일이라 여기서 재현할 수 없고, 확인하려는 것은
 * 사진 갈래와 자리표시 갈래가 같은 칸을 채우는지다.
 */
const COVER_PIXEL =
  'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=='

function stubJourneyList(page) {
  return page.route('**/api/v1/journeys', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        data: [
          // 목록은 가로 스냅 캐러셀이다. ongoing이 한 장뿐이면 스냅도 다음 카드
          // 엿보기도 화면에 나타나지 않아 캡처가 조형을 증명하지 못한다. 진행 중
          // 여정을 세 장 둔다.
          // 커버가 있는 여정과 없는 여정이 한 화면에 섞인다. 두 갈래를 모두 찍는다.
          {
            tripId: 42,
            title: 'Seoul Foodie Week',
            startDate: '2098-08-10',
            endDate: '2098-08-12',
            eventCount: 8,
            placeCount: 4,
            coverImageUrl: COVER_PIXEL,
          },
          {
            tripId: 43,
            title: 'Jeju Island Escape',
            startDate: '2098-09-02',
            endDate: '2098-09-07',
            eventCount: 5,
            placeCount: 9,
            coverImageUrl: null,
          },
          {
            tripId: 44,
            title: 'Gangneung Coast Run',
            startDate: '2098-10-11',
            endDate: '2098-10-13',
            eventCount: 0,
            placeCount: 6,
            coverImageUrl: COVER_PIXEL,
          },
          {
            tripId: 7,
            title: 'Busan Weekender',
            startDate: '2020-08-10',
            endDate: '2020-08-12',
            eventCount: 4,
            placeCount: 2,
            coverImageUrl: null,
          },
        ],
      }),
    }),
  )
}

/** 같은 국적 여행자 코호트(#421). 동료 없이 평균만 있고, 내 비중과 5%p 안팎으로 갈리게 둔다. */
const REPORT_SIMILAR_COMPARISON = {
  scope: 'SIMILAR',
  basis: 'SNAPSHOT',
  me: {
    memberId: 1,
    displayName: 'Me',
    profileImageUrl: null,
    totalSpent: 1284500,
    dailyAverage: 128450,
    categoryBreakdown: [
      { category: 'FOOD', amount: 539500, percentage: 42 },
      { category: 'SHOPPING', amount: 398200, percentage: 31 },
      { category: 'SHOW', amount: 218400, percentage: 17 },
      { category: 'BEAUTY', amount: 128400, percentage: 10 },
    ],
  },
  peers: [],
  cohort: {
    size: 37,
    avgTotalSpent: 1052000,
    avgDailyAverage: 105200,
    categoryBreakdown: [
      { category: 'FOOD', amount: 315600, percentage: 30 },
      { category: 'SHOPPING', amount: 263000, percentage: 25 },
      { category: 'BEAUTY', amount: 263000, percentage: 25 },
      { category: 'SHOW', amount: 210400, percentage: 20 },
    ],
  },
  ranks: [],
}

function stubReportApis(page) {
  const detail = {
    reportId: 101,
    tripId: 9,
    title: 'Jeju Island',
    startDate: '2021-07-18',
    endDate: '2021-07-27',
    generationStatus: 'COMPLETED',
    locale: 'en',
    generatedAt: '2021-07-28T09:00:00',
    createdAt: '2021-07-28T09:00:00',
    reportContent: {
      journey: {
        tripId: 9,
        title: 'Jeju Island',
        startDate: '2021-07-18',
        endDate: '2021-07-27',
      },
      days: [
        {
          visitDate: '2021-07-18',
          items: [
            {
              tripItemId: 1,
              itemId: 101,
              itemType: 'EVENT',
              title: 'Jeju Night Market',
              status: 'ADDED',
            },
          ],
        },
      ],
      analytics: {
        totalSpent: 1284500,
        dailyAverage: 128450,
        categoryBreakdown: [
          { category: 'FOOD', amount: 539500, percentage: 42 },
          { category: 'SHOPPING', amount: 398200, percentage: 31 },
          { category: 'SHOW', amount: 218400, percentage: 17 },
          { category: 'BEAUTY', amount: 128400, percentage: 10 },
        ],
        dailyTrend: [
          { date: '2021-07-18', amount: 262000 },
          { date: '2021-07-19', amount: 148500 },
          { date: '2021-07-20', amount: 76000 },
          { date: '2021-07-21', amount: 178000 },
          { date: '2021-07-22', amount: 122000 },
          { date: '2021-07-23', amount: 290000 },
          { date: '2021-07-27', amount: 208000 },
        ],
      },
    },
  }

  return Promise.all([
    page.route('**/api/v1/journeys', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          data: [
            {
              tripId: 9,
              title: 'Jeju Island',
              startDate: '2021-07-18',
              endDate: '2021-07-27',
              eventCount: 5,
              placeCount: 9,
            },
            {
              tripId: 7,
              title: 'Busan Weekender',
              startDate: '2020-08-10',
              endDate: '2020-08-12',
              eventCount: 4,
              placeCount: 2,
            },
          ],
        }),
      }),
    ),
    page.route('**/api/v1/reports', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          data: [
            {
              reportId: 101,
              tripId: 9,
              title: 'Jeju Island',
              startDate: '2021-07-18',
              endDate: '2021-07-27',
              generationStatus: 'COMPLETED',
              locale: 'en',
              generatedAt: '2021-07-28T09:00:00',
              createdAt: '2021-07-28T09:00:00',
            },
          ],
        }),
      }),
    ),
    // 동료 비교(#404). 쿼리(scope=)가 붙으므로 정규식으로 받고, SIMILAR는 코호트만 있는 응답을 준다(#421).
    page.route(/\/api\/v1\/reports\/101\/comparison(\?.*)?$/, (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          data: route.request().url().includes('scope=SIMILAR')
            ? REPORT_SIMILAR_COMPARISON
            : {
                scope: 'GROUP',
                basis: 'LIVE',
                me: {
                  memberId: 1,
                  displayName: 'Me',
                  profileImageUrl: null,
                  // 스냅샷(analytics 1,284,500)과 일부러 어긋나게 둔다 — LIVE 재합산이라
                  // 두 숫자가 다를 수 있고, 같은 값이면 캡처에서 그 차이가 안 드러난다.
                  totalSpent: 1310000,
                  dailyAverage: 131000,
                  categoryBreakdown: [
                    { category: 'FOOD', amount: 539500, percentage: 42 },
                    { category: 'SHOPPING', amount: 398200, percentage: 31 },
                    { category: 'SHOW', amount: 218400, percentage: 17 },
                    { category: 'BEAUTY', amount: 128400, percentage: 10 },
                  ],
                },
                peers: [
                  {
                    memberId: 2,
                    displayName: 'Mina',
                    profileImageUrl: null,
                    totalSpent: 978400,
                    dailyAverage: 97840,
                    categoryBreakdown: [
                      { category: 'FOOD', amount: 420000, percentage: 42.93 },
                      { category: 'SHOPPING', amount: 310000, percentage: 31.68 },
                      { category: 'BEAUTY', amount: 248400, percentage: 25.39 },
                    ],
                  },
                  {
                    memberId: 3,
                    displayName: 'Jae',
                    profileImageUrl: null,
                    totalSpent: 510000,
                    dailyAverage: 51000,
                    categoryBreakdown: [
                      { category: 'SHOW', amount: 300000, percentage: 58.82 },
                      { category: 'FOOD', amount: 150000, percentage: 29.41 },
                      { category: 'TRANSPORT', amount: 60000, percentage: 11.76 },
                    ],
                  },
                  {
                    memberId: 4,
                    displayName: 'Sora',
                    profileImageUrl: null,
                    totalSpent: 740000,
                    dailyAverage: 74000,
                    categoryBreakdown: [
                      { category: 'SHOPPING', amount: 520000, percentage: 70.27 },
                      { category: 'FOOD', amount: 130000, percentage: 17.57 },
                      { category: 'BEAUTY', amount: 90000, percentage: 12.16 },
                    ],
                  },
                ],
                cohort: {
                  size: 3,
                  avgTotalSpent: 742800,
                  avgDailyAverage: 74280,
                  categoryBreakdown: [
                    { category: 'SHOPPING', amount: 276666.67, percentage: 37.25 },
                    { category: 'FOOD', amount: 233333.33, percentage: 31.41 },
                    { category: 'BEAUTY', amount: 112800, percentage: 15.19 },
                    { category: 'SHOW', amount: 100000, percentage: 13.46 },
                    { category: 'TRANSPORT', amount: 20000, percentage: 2.69 },
                  ],
                },
                ranks: [
                  { category: 'FOOD', rank: 1, of: 4 },
                  { category: 'SHOPPING', rank: 2, of: 4 },
                  { category: 'SHOW', rank: 2, of: 4 },
                  { category: 'BEAUTY', rank: 2, of: 4 },
                ],
              },
        }),
      }),
    ),
    page.route('**/api/v1/reports/101', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true, data: detail }),
      }),
    ),
  ])
}

/**
 * QR 결제 미리보기 응답을 세운다.
 *
 * 카테고리는 결제 금액과 잔액을 바꾸지 않으므로 요청 본문과 무관하게 같은 값을 준다.
 */
function stubQrPaymentPreview(page) {
  return page.route('**/api/v1/wallet/qr/payment/preview', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        data: {
          qrPaymentId: 10,
          payeeName: 'Jieun Coffee',
          amount: 18500,
          currentBalance: 128500,
          balanceAfter: 110000,
          currencyCode: 'KRW',
          spendingScope: 'PERSONAL',
          trip: null,
          appointment: null,
          canPay: true,
          expiresAt: '2026-08-20T18:00:00',
        },
      }),
    }),
  )
}

/**
 * 스캔이 끝난 QR 결제 세션을 심는다.
 *
 * 이 세션은 카메라로 QR을 읽어야만 채워지는데 스크린샷은 카메라를 쓸 수 없다. 디코더가
 * WASM이라 `BarcodeDetector`를 흉내내도 통하지 않는다. 그래서 화면이 마운트돼 스토어가
 * 등록된 뒤 Pinia 인스턴스에 직접 값을 넣는다.
 *
 * **프로덕션 소스에는 이 경로를 위한 분기를 만들지 않는다.** 화면은 평소와 똑같이
 * 스토어만 읽고, 값을 넣는 쪽이 여기 하나다.
 */
async function seedQrPaymentSession(page) {
  await page.waitForSelector('main')
  await page.evaluate(() => {
    const provides = document.querySelector('#app').__vue_app__._context.provides
    const piniaSymbol = Object.getOwnPropertySymbols(provides).find(
      (symbol) => symbol.toString() === 'Symbol(pinia)',
    )

    provides[piniaSymbol]._s.get('wallet-qr-payment-session').setSession({
      qrToken: 'screenshot-qr-token',
      resolved: {
        qrPaymentId: 10,
        payeeName: 'Jieun Coffee',
        amount: 18500,
        amountInputRequired: false,
        memo: 'Night market coffee',
        status: 'ACTIVE',
        currencyCode: 'KRW',
        expiresAt: '2026-08-20T18:00:00',
      },
    })
  })
}

/**
 * 지갑 홈 응답을 세운다.
 *
 * 금액은 `BigDecimal`이 JSON number로 내려오고, `createdAt`은 `LocalDateTime`이 오프셋 없이
 * `[년, 월, 일, 시, 분]` 배열로 직렬화된 것이다(#108). 실제 응답 모양을 그대로 흉내내야
 * 자릿수 구분과 날짜 해석이 시안대로 찍힌다.
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
          balance: 109500,
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
    transferId: 5,
    transferType: 'DEPOSIT_NO_SHOW_DISTRIBUTION',
    entryType: 'CREDIT',
    amount: 25000,
    balanceAfter: 109500,
    createdAt: [2026, 7, 26, 10, 30],
  },
  {
    transferId: 1,
    transferType: 'QR_PAYMENT',
    entryType: 'DEBIT',
    amount: 18000,
    balanceAfter: 84500,
    createdAt: [2026, 7, 25, 12, 0],
  },
  {
    transferId: 2,
    transferType: 'TOPUP',
    entryType: 'CREDIT',
    amount: 100000,
    balanceAfter: 102500,
    createdAt: [2026, 7, 24, 9, 12],
  },
  {
    transferId: 3,
    transferType: 'SETTLEMENT',
    entryType: 'CREDIT',
    amount: 32500,
    balanceAfter: 2500,
    createdAt: [2026, 7, 22, 21, 40],
  },
  {
    transferId: 4,
    transferType: 'DEPOSIT_HOLD',
    entryType: 'DEBIT',
    amount: 50000,
    balanceAfter: 52500,
    createdAt: [2026, 7, 20, 18, 5],
  },
]

/** 성공 봉투로 감싸 응답한다. 스텁마다 봉투 모양을 다시 적지 않도록 한 곳에 모은다. */
function fulfillJson(route, data) {
  return route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ success: true, data }),
  })
}

/**
 * 경로가 정확히 같을 때만 응답한다.
 *
 * 글롭으로 `/api/v1/settlements`를 걸면 `/api/v1/settlements/candidates`와
 * `/api/v1/settlements/42`까지 함께 걸려 목록 응답이 상세 자리에 들어간다.
 */
function stubJson(page, pathname, data) {
  return page.route(
    (url) => url.pathname === pathname,
    (route) => fulfillJson(route, data),
  )
}

/**
 * 응답을 붙잡아 두는 문.
 *
 * "보내는 중" 화면은 응답이 도착하는 순간 사라진다. 그 상태를 찍으려면 응답을 잡아둬야 하는데,
 * 몇 초처럼 시간으로 잡으면 실행 속도에 따라 결과가 달라진다. 느리면 이미 넘어간 화면을 찍고,
 * 빠르면 남은 시간만큼 그냥 기다린다. 그래서 시간이 아니라 신호로 잡는다. 흐름이 그 화면을
 * 찍은 다음 `open()`을 부르면 그때 응답이 나간다.
 */
function createGate() {
  const { promise, resolve } = Promise.withResolvers()
  return { promise, open: resolve }
}

/**
 * CSRF 토큰 조회.
 *
 * `shared/api/csrf.ts`가 모든 POST 앞에 이 요청을 먼저 보낸다. 세워 두지 않으면 매번
 * 실제 백엔드로 새어 나가고, 응답이 없으면 그만큼 느려진다.
 */
function stubCsrf(page) {
  return stubJson(page, '/api/v1/auth/csrf', {
    token: 'screenshot-csrf-token',
    headerName: 'X-CSRF-TOKEN',
  })
}

function stubWalletApis(page) {
  stubJson(page, '/api/v1/wallet', {
    balance: 84500,
    availabilityStatus: 'ACTIVE',
    recentTransactions: [
      {
        transferId: 9,
        transferType: 'TOPUP',
        entryType: 'CREDIT',
        amount: 50000,
        balanceAfter: 84500,
        createdAt: [2026, 8, 7, 12, 18, 2],
      },
      {
        transferId: 8,
        transferType: 'TOPUP',
        entryType: 'CREDIT',
        amount: 30000,
        balanceAfter: 34500,
        createdAt: [2026, 8, 7, 9, 28, 57],
      },
    ],
  })

  stubJson(page, '/api/v1/topups/methods', {
    methods: [
      {
        type: 'STRIPE_CARD',
        displayName: 'Stripe',
        testMode: true,
        enabled: true,
      },
    ],
    guideMessage: null,
  })

  stubJson(page, '/api/v1/me/transactions', {
    transactions: [
      {
        transferId: 9,
        transferType: 'TOPUP',
        entryType: 'CREDIT',
        amount: 50000,
        balanceAfter: 452000,
        createdAt: [2026, 8, 7, 12, 18, 2],
      },
      {
        transferId: 8,
        transferType: 'TOPUP',
        entryType: 'CREDIT',
        amount: 100000,
        balanceAfter: 402000,
        createdAt: [2026, 8, 7, 9, 28, 57],
      },
      {
        transferId: 7,
        transferType: 'TOPUP',
        entryType: 'CREDIT',
        amount: 30000,
        balanceAfter: 302000,
        createdAt: [2026, 8, 7, 8, 6, 53],
      },
    ],
    nextCursor: null,
    appliedFilters: {
      type: null,
      status: null,
      from: null,
      to: null,
    },
  })

  stubJson(page, '/api/v1/me/transactions/9', {
    amount: 50000,
    occurredAt: [2026, 8, 7, 12, 18, 2],
    counterparty: { type: 'EXTERNAL', name: 'Stripe' },
    status: 'COMPLETED',
    receipt: {
      transactionNumber: 'ST-20260807-0009',
      memo: 'Wallet top-up',
      spendingCategory: null,
    },
    transactionNumber: 'ST-20260807-0009',
    fx: null,
  })
}

const SETTLEMENT_PARTICIPANTS = [
  { id: 12, name: 'Alex', initials: 'AL' },
  { id: 19, name: 'Mina', initials: 'MI' },
  { id: 27, name: 'Leo', initials: 'LE' },
]

/**
 * 낼 쪽(참여자) 목록 항목.
 *
 * `paid`는 `status`와 다른 축이다. 정산 자체는 아직 `REQUESTED`인데 나만 이미 낸 상태가
 * 있고, 목록 카드는 그때만 `Paid` 표시를 낸다. 결제 직후 목록을 찍으려면 이 둘을 따로
 * 줘야 한다.
 */
function settlementSummary({
  id,
  title,
  status = 'REQUESTED',
  paid = status === 'COMPLETED',
  createdAt,
  completedAt = null,
}) {
  return {
    id,
    title,
    totalAmount: '60300',
    receivableAmount: '40200',
    type: 'EQUAL',
    status,
    createdAt,
    completedAt,
    viewer: {
      role: 'PARTICIPANT',
      shareAmount: '20100',
      payableAmount: paid ? '0' : '20100',
      requestStatus: paid ? 'PAID' : 'PENDING',
      allowedActions: paid ? [] : ['PAY'],
    },
  }
}

/** 받을 쪽(요청자) 목록 항목. 요청자에게는 낼 몫이 없어 허용 동작도 비어 있다. */
function collectSummary({ id, title, status = 'REQUESTED', createdAt, completedAt = null }) {
  return {
    id,
    title,
    totalAmount: '48000',
    receivableAmount: '32000',
    type: 'EQUAL',
    status,
    createdAt,
    completedAt,
    viewer: {
      role: 'CREATOR',
      shareAmount: '16000',
      payableAmount: '0',
      requestStatus: 'NOT_REQUESTED',
      allowedActions: [],
    },
  }
}

/**
 * `paidId`를 주면 그 정산만 이미 낸 상태로 바꾼다. 나머지는 그대로 둔다.
 *
 * 완료 날짜를 두 달에 걸쳐 흩어 둔다. 전체 내역의 기간 필터가 실제로 무엇을 걸러내는지는
 * 한 달에 몰려 있으면 사진으로 보이지 않는다. 마지막 한 건은 완료 시각이 없는 예전
 * 정산이라 만든 날짜로 대신 나온다.
 */
function receivedSettlements(paidId = null) {
  return [
    settlementSummary({
      id: 42,
      title: 'Late-night Burger Club',
      paid: paidId === 42,
      createdAt: '2026-08-18T20:42:00',
    }),
    settlementSummary({
      id: 41,
      title: 'Hongdae Karaoke',
      status: 'COMPLETED',
      createdAt: '2026-08-12T20:00:00',
      completedAt: '2026-08-12T21:10:00',
    }),
    settlementSummary({
      id: 40,
      title: 'Seongsu Coffee',
      status: 'COMPLETED',
      createdAt: '2026-07-30T14:30:00',
      completedAt: '2026-07-30T15:05:00',
    }),
    settlementSummary({
      id: 39,
      title: 'Namsan Cable Car',
      status: 'COMPLETED',
      createdAt: '2026-07-04T17:50:00',
      completedAt: '2026-07-04T18:20:00',
    }),
    settlementSummary({
      id: 38,
      title: 'Gwangjang Market',
      status: 'COMPLETED',
      createdAt: '2026-06-21T12:40:00',
    }),
  ]
}

/** 완료 건이 하나는 있어야 수금 쪽에도 "View all"이 나와 전체 내역으로 들어갈 수 있다. */
function sentSettlements() {
  return [
    collectSummary({ id: 50, title: 'Han River Picnic', createdAt: '2026-08-16T19:00:00' }),
    collectSummary({
      id: 49,
      title: 'Ikseon-dong Tea House',
      status: 'COMPLETED',
      createdAt: '2026-08-02T18:00:00',
      completedAt: '2026-08-02T19:30:00',
    }),
  ]
}

const SETTLEMENT_CANDIDATES = [
  {
    transferId: 7,
    appointmentId: 9,
    payerAppointmentMemberId: 12,
    journeyName: 'Seoul Summer',
    gatheringName: 'Late-night Burger Club',
    merchantName: 'Late-night Burger Club',
    amount: '60300',
    paidAt: '2026-07-26T20:42:00',
    payerName: 'Alex',
    participants: SETTLEMENT_PARTICIPANTS,
  },
  {
    transferId: 8,
    appointmentId: 9,
    payerAppointmentMemberId: 12,
    journeyName: 'Seoul Summer',
    gatheringName: 'Late-night Burger Club',
    merchantName: 'Late-night Burger Club',
    amount: '18500',
    paidAt: '2026-07-26T22:10:00',
    payerName: 'Alex',
    participants: SETTLEMENT_PARTICIPANTS,
  },
  {
    transferId: 9,
    appointmentId: 10,
    payerAppointmentMemberId: 12,
    journeyName: 'Seoul Summer',
    gatheringName: 'Seongsu Coffee Walk',
    merchantName: 'Seongsu Coffee Walk',
    amount: '24000',
    paidAt: '2026-07-27T11:05:00',
    payerName: 'Alex',
    participants: SETTLEMENT_PARTICIPANTS,
  },
  {
    transferId: 10,
    appointmentId: 11,
    payerAppointmentMemberId: 12,
    journeyName: 'Busan Weekend',
    gatheringName: 'Haeundae Brunch',
    merchantName: 'Haeundae Brunch',
    amount: '32400',
    paidAt: '2026-08-02T10:20:00',
    payerName: 'Alex',
    participants: SETTLEMENT_PARTICIPANTS,
  },
]

/** 아직 내지 않은 상세. 하단에 결제 버튼이 나오는 유일한 상태다. */
const PAYABLE_SETTLEMENT_DETAIL = {
  id: 42,
  type: 'ITEMIZED',
  totalAmount: '60300',
  status: 'REQUESTED',
  requestedBy: 'Alex',
  gatheringName: 'Late-night Burger Club',
  merchantName: "McDonald's Hongdae",
  viewerItems: [
    {
      settlementItemId: 1,
      name: 'Burger set',
      allocatedQuantity: '1',
      allocatedAmount: '20100',
    },
  ],
  transactionId: null,
  paidBy: 'Alex',
  viewer: {
    role: 'PARTICIPANT',
    shareAmount: '20100',
    payableAmount: '20100',
    requestStatus: 'PENDING',
    allowedActions: ['PAY'],
  },
  /* 낼 사람에게는 누가 냈는지가 오지 않는다. */
  collection: null,
}

/** 결제를 마친 뒤의 같은 상세. 결제 완료 화면이 머물려면 이 모양이어야 한다. */
const PAID_SETTLEMENT_DETAIL = {
  ...PAYABLE_SETTLEMENT_DETAIL,
  transactionId: 'TR-20260807-0042',
  viewer: {
    role: 'PARTICIPANT',
    shareAmount: '20100',
    payableAmount: '0',
    requestStatus: 'PAID',
    allowedActions: [],
  },
}

/** 요청자 시점 상세. 낼 몫이 아니라 "누가 냈는지"를 보여주는 분기다. */
const COLLECT_SETTLEMENT_DETAIL = {
  id: 50,
  type: 'EQUAL',
  totalAmount: '48000',
  status: 'REQUESTED',
  requestedBy: 'Mina Park',
  gatheringName: 'Han River Picnic',
  merchantName: 'Han River Picnic',
  viewerItems: [],
  transactionId: null,
  paidBy: 'Mina Park',
  viewer: {
    role: 'CREATOR',
    shareAmount: '16000',
    payableAmount: '0',
    requestStatus: 'NOT_REQUESTED',
    allowedActions: [],
  },
  /*
   * 셋이 나눈 48000원 중 요청자 본인 몫은 빠져서 받을 사람은 둘이다.
   * 서버가 안 낸 사람을 먼저 내려주므로 여기서도 그 순서로 둔다.
   */
  collection: {
    totalCount: 2,
    paidCount: 1,
    participants: [
      { id: 73, name: 'Tae Kim', initials: 'T', shareAmount: '16000', requestStatus: 'PENDING' },
      { id: 72, name: 'Jisoo Han', initials: 'J', shareAmount: '16000', requestStatus: 'PAID' },
    ],
  },
}

/** 이미 끝난 정산 상세. 완료 배지와 거래번호 행은 여기서만 나온다. */
const COMPLETED_SETTLEMENT_DETAIL = {
  id: 41,
  type: 'EQUAL',
  totalAmount: '36000',
  status: 'COMPLETED',
  requestedBy: 'Alex',
  gatheringName: 'Hongdae Karaoke',
  merchantName: 'Hongdae Karaoke',
  viewerItems: [],
  transactionId: 'TR-20260726-0031',
  paidBy: 'Alex',
  viewer: {
    role: 'PARTICIPANT',
    shareAmount: '12000',
    payableAmount: '0',
    requestStatus: 'PAID',
    allowedActions: [],
  },
  collection: null,
}

function stubSettlementApis(page) {
  return Promise.all([
    stubJson(page, '/api/v1/settlements', {
      received: receivedSettlements(),
      sent: sentSettlements(),
    }),
    stubJson(page, '/api/v1/settlements/candidates', SETTLEMENT_CANDIDATES),
    stubJson(page, '/api/v1/settlements/42', PAYABLE_SETTLEMENT_DETAIL),
  ])
}

/**
 * 결제 전과 후를 모두 서빙하는 정산 스텁.
 *
 * 결제 완료 화면은 상세의 `requestStatus`가 `PAID`가 아니면 즉시 상세로 되돌아간다. 결제가
 * 성공하면 목록·상세 조회가 다시 나가므로, 고정 응답으로는 결제 이후 화면을 한 장도 찍을
 * 수 없다. POST가 뒤집는 플래그를 조회 핸들러가 함께 읽게 해서 서버가 상태를 바꾼 것처럼
 * 보이게 한다.
 */
async function stubPayableSettlement(page, { gate }) {
  let paid = false

  await page.route(
    (url) => url.pathname === '/api/v1/settlements/42/members/me/pay',
    async (route) => {
      await gate.promise
      // 순서를 지킨다. 결제 직후 다시 나가는 상세·목록 조회가 이 플래그를 읽으므로,
      // 응답보다 먼저 세워 두지 않으면 결제 전 데이터가 돌아간다.
      paid = true
      await fulfillJson(route, {
        settlementId: 42,
        settlementStatus: 'REQUESTED',
        transferId: 771,
        viewer: PAID_SETTLEMENT_DETAIL.viewer,
      })
    },
  )
  await page.route(
    (url) => url.pathname === '/api/v1/settlements/42',
    (route) => fulfillJson(route, paid ? PAID_SETTLEMENT_DETAIL : PAYABLE_SETTLEMENT_DETAIL),
  )
  await page.route(
    (url) => url.pathname === '/api/v1/settlements',
    (route) =>
      fulfillJson(route, {
        received: receivedSettlements(paid ? 42 : null),
        sent: sentSettlements(),
      }),
  )
}

/**
 * 요청 생성. 흐름이 문을 열 때까지 응답을 잡아둬 "Sending your request" 화면을 찍게 한다.
 *
 * 요청 완료 화면은 서버가 나를 이 정산의 요청자로 인정해야 완료를 표시한다. 만든 정산의
 * 상세를 함께 서빙하지 않으면 완료 화면이 영원히 처리 중에 머문다.
 *
 * 생성까지 가지 않는 흐름은 문을 열지 않아도 된다. 열리지 않은 문은 아무 일도 하지 않는다.
 */
function stubSettlementCreate(page, { id = 77, gate }) {
  return Promise.all([
    page.route(
      (url) => url.pathname === '/api/v1/appointments/9/settlements',
      async (route) => {
        await gate.promise
        await fulfillJson(route, { id })
      },
    ),
    stubJson(page, `/api/v1/settlements/${id}`, {
      ...COLLECT_SETTLEMENT_DETAIL,
      id,
    }),
  ])
}

function stubEmptySettlementCandidates(page) {
  return stubJson(page, '/api/v1/settlements/candidates', [])
}

function stubSettlementCandidatesError(page) {
  return page.route(
    (url) => url.pathname === '/api/v1/settlements/candidates',
    (route) =>
      route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({
          success: false,
          error: { code: 'SETTLEMENT-001', message: 'Settlement candidates are unavailable' },
        }),
      }),
  )
}

/**
 * 잔액이 모자라 지급이 거절되는 상태.
 *
 * 지갑 이체가 내는 `WALLET-015`는 정산 코드가 아니라서, 이 갈래가 없던 동안에는 일반
 * 오류 문구와 "Try again" 버튼만 떴다. 잔액은 다시 눌러도 그대로라 사용자가 그 화면에
 * 갇힌다 — 그래서 찍어 두는 것은 충전으로 이어 주는 팝업이다(#452).
 */
function stubInsufficientSettlementPayment(page) {
  return Promise.all([
    stubJson(page, '/api/v1/settlements/42', PAYABLE_SETTLEMENT_DETAIL),
    page.route(
      (url) => url.pathname === '/api/v1/settlements/42/members/me/pay',
      (route) =>
        route.fulfill({
          status: 409,
          contentType: 'application/json',
          body: JSON.stringify({
            success: false,
            error: { code: 'WALLET-015', message: 'Wallet balance is too low' },
          }),
        }),
    ),
  ])
}

/** Place 상세 응답을 세운다. 좌표가 있어 지도 버튼 두 개가 함께 찍힌다(#221). */
function stubPlaceDetail(page) {
  // 상세 API는 `?language=` 쿼리를 붙이므로 패턴이 쿼리까지 매칭해야 한다.
  return page.route('**/api/v1/explore/places/501?*', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        data: {
          placeId: 501,
          itemId: 501,
          name: 'Onion Anguk',
          brand: 'Onion',
          branch: 'Anguk',
          placeKind: 'CAFE',
          thumbnailUrl: null,
          imageUrls: [],
          region1: 'Seoul',
          region2: 'Jongno-gu',
          region3: null,
          addressRoad: '5 Gyedong-gil, Jongno-gu',
          addressDetail: null,
          latitude: 37.5768,
          longitude: 126.9857,
          hasForeignLang: true,
          hasParking: false,
          reservable: null,
          takeoutAvailable: true,
          cardPaymentAvailable: true,
          smokeFree: true,
          kidFacility: null,
          hasRestroom: true,
          isActive: true,
          viewCount: 1024,
          favoriteCount: 87,
          // #280 찜 서버 연동부터 필수 필드다. 빠지면 responseSchema 검증이 실패해
          // 상세가 오류 상태로 뜨고, 지도 버튼 대기가 타임아웃으로 죽는다.
          saved: false,
          sourceUrl: null,
          postalCode: null,
          openingHours: { 'Mon–Sun': '10:00–21:00' },
          closedDays: [],
          menuSummary: 'Pandoro · Espresso · Ssuk latte',
          tel: '02-0000-0000',
          activities: [],
        },
      }),
    }),
  )
}

/**
 * Event 상세의 여정 담기 시트에 쓸 여정 목록.
 *
 * `stubJourneyList`를 그대로 쓸 수 없다. 그쪽 날짜는 2098·2020이라 이벤트 301의
 * 개최 기간(2026-08-10 ~ 2026-08-31)과 하나도 겹치지 않아, 시트가 전부 비활성으로만
 * 나온다. 겹치는 여정과 겹치지 않는 여정을 섞어야 #357이 만든 두 상태가 한 장에 든다.
 *
 * `overlapping: false`면 겹치는 여정이 하나도 없어 "여정 만들기" 진입점이 드러난다.
 */
function stubJourneyListForEvent(page, { overlapping = true } = {}) {
  const journeys = [
    {
      tripId: 51,
      title: 'Seoul Lantern Trip',
      startDate: '2026-08-14',
      endDate: '2026-08-18',
      eventCount: 3,
      placeCount: 5,
    },
    {
      tripId: 52,
      title: 'Autumn in Gyeongju',
      startDate: '2026-09-04',
      endDate: '2026-09-08',
      eventCount: 2,
      placeCount: 4,
    },
    {
      tripId: 53,
      title: 'Spring Jeju Escape',
      startDate: '2026-04-02',
      endDate: '2026-04-06',
      eventCount: 6,
      placeCount: 2,
    },
  ]

  return page.route('**/api/v1/journeys', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        data: overlapping ? journeys : journeys.slice(1),
      }),
    }),
  )
}

/**
 * Discover 목록(Events 탭) 응답을 세운다.
 *
 * 목록 화면은 그동안 낱장으로 찍히지 않아 카드 조형·필터 칩·280px 폭을 눈으로 확인할
 * 방법이 없었다. 사진은 외부 주소를 부르지 않도록 비워 `ImagePlaceholder`가 그려지게 한다.
 *
 * `eventKind`와 `status`는 `EVENT_KINDS`·`EVENT_STATUSES`에 있는 값만 쓴다. 다른 값을 쓰면
 * `responseSchema`가 응답을 통째로 거절해 목록이 오류로 떨어진다.
 */
function stubExploreEventList(page) {
  /**
   * 사진 갈래도 한 장은 찍어야 한다. 자리표시만 나오면 `<img>` 쪽 회귀가 스냅샷에 잡히지
   * 않는다. 외부 주소를 부르지 않도록 1x1 PNG를 data URI로 박아 둔다.
   */
  const PIXEL =
    'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=='

  const event = (
    itemId,
    eventKind,
    status,
    title,
    region2,
    startDate,
    endDate,
    thumbnailUrl = null,
  ) => ({
    itemId,
    eventKind,
    status,
    title,
    subtitle: null,
    thumbnailUrl,
    region1: 'Seoul',
    region2,
    region3: null,
    latitude: 37.5665,
    longitude: 126.978,
    startDate,
    endDate,
    saved: false,
  })

  return page.route('**/api/v1/explore/events?*', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        data: {
          content: [
            event(
              301,
              'FESTIVAL',
              'ONGOING',
              'DDP Architecture Tour',
              'Dongdaemun',
              '2026-01-01',
              '2026-12-31',
              PIXEL,
            ),
            /*
             * 세 상태가 한 화면에 모두 보이도록 순서를 잡았다. 러너는 뷰포트만 찍으므로
             * 네 번째 카드는 스냅샷에 들어오지 않는다. 종료는 표현이 가장 약한 상태라
             * 목록 아래로 밀어 두면 확인할 방법이 사라진다.
             */
            event(
              304,
              'EXHIBITION',
              'ENDED',
              'Hongdae Vintage Fashion Fair',
              'Hongdae',
              '2026-06-01',
              '2026-06-30',
            ),
            event(
              303,
              'FESTIVAL',
              'SCHEDULED',
              'Han River Food Festival',
              'Yeouido',
              '2026-08-14',
              '2026-08-23',
            ),
            event(
              302,
              'POPUP',
              'ONGOING',
              'Seongsu Character Goods Pop-up',
              'Seongsu',
              '2026-07-20',
              '2026-08-17',
            ),
          ],
          page: 0,
          size: 20,
          totalElements: 496,
          totalPages: 25,
          hasNext: true,
        },
      }),
    }),
  )
}

/**
 * Event 상세 응답을 세운다. 좌표가 있는 상세와, 좌표가 NULL이라 지도 버튼이 숨는
 * 상세(#221 완료 기준)를 id로 나눠 찍는다.
 */
function stubEventDetail(page, { withCoordinates }) {
  const eventId = withCoordinates ? 301 : 302
  return page.route(`**/api/v1/explore/events/${eventId}?*`, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        data: {
          eventId,
          // #280 찜 서버 연동부터 필수 필드다. 빠지면 responseSchema 검증이 실패한다.
          saved: false,
          eventType: null,
          eventKind: 'FESTIVAL',
          title: withCoordinates ? 'Seoul Lantern Festival' : 'Hidden Alley Pop-up',
          subtitle: null,
          description: 'Evening lantern walk along the stream with local food stalls.',
          programText: null,
          thumbnailUrl: null,
          imageUrls: [],
          links: null,
          reservationUrl: null,
          preReservation: null,
          status: 'ONGOING',
          isPermanent: false,
          startDate: '2026-08-10',
          endDate: '2026-08-31',
          operatingHours: { 'Mon–Sun': '18:00–22:00' },
          openDays: null,
          openWeekend: true,
          opensLate: true,
          venueName: 'Cheonggyecheon Plaza',
          region1: 'Seoul',
          region2: 'Jung-gu',
          region3: null,
          addressRoad: '1 Cheonggyecheon-ro, Jung-gu',
          latitude: withCoordinates ? 37.5696 : null,
          longitude: withCoordinates ? 126.9784 : null,
          hasPhotoZone: true,
          isExperience: false,
          ageLimit: null,
          isFree: true,
          priceText: null,
          hasBenefit: null,
          reservable: null,
          contact: null,
          organizer: 'Seoul Metropolitan Government',
          activities: [],
        },
      }),
    }),
  )
}

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
  { name: '00-welcome', path: '/' },
  { name: '01-sign-in', path: '/sign-in' },
  { name: '02-callback-failed', path: '/auth/callback?error=AUTH-014' },
  { name: '03-not-found', path: '/no-such-page' },
  {
    name: '04-profile',
    path: '/profile',
    // 백엔드를 띄우지 않고 찍는다. 리뷰용 이미지지 통합 검증이 아니다.
    setup: (page) => Promise.all([stubMemberProfile(page), stubProfileTabs(page)]),
  },
  {
    name: '04b-profile-appointments',
    path: '/profile',
    setup: (page) => Promise.all([stubMemberProfile(page), stubProfileTabs(page)]),
    prepare: async (page) => {
      await page.getByTestId('segment-appointments').click()
      await page.getByTestId('profile-list').getByRole('link').first().waitFor()
    },
  },
  {
    name: '04c-profile-edit',
    path: '/profile/edit',
    setup: (page) => stubMemberProfile(page),
  },
  {
    /*
     * 온보딩은 guard가 보내 주는 화면이라 주소로 바로 들어가면 프로필이 이미 끝난 상태로
     * 판정돼 홈으로 되돌려진다. 여기서만 온보딩이 남은 프로필을 세운다.
     */
    name: '04d-onboarding',
    path: '/onboarding',
    setup: (page) => stubMemberProfile(page, { onboardingRequired: true }),
  },
  {
    name: '05-profile-language',
    path: '/profile',
    setup: (page) => Promise.all([stubMemberProfile(page), stubProfileTabs(page)]),
    prepare: async (page) => {
      await page.getByTestId('profile-language').click()
      await page.waitForSelector('[role="dialog"]')
    },
  },
  {
    name: '13-journey-list',
    path: '/journeys',
    setup: (page) => Promise.all([stubMemberProfile(page), stubJourneyList(page)]),
  },
  {
    name: '06-journey-create',
    path: '/journeys/new',
    setup: (page) => stubMemberProfile(page),
  },
  {
    name: '06a-journey-create-dates',
    path: '/journeys/new',
    setup: (page) => stubMemberProfile(page),
    prepare: async (page) => {
      await page.getByTestId('journey-date-start').click()
      await page.getByRole('dialog').waitFor()
    },
  },
  {
    // 생성 2단계는 1단계를 채워야만 나온다. 예산·동행 입력의 디자인은 여기서만 찍힌다.
    name: '06b-journey-create-preferences',
    path: '/journeys/new',
    setup: (page) => stubMemberProfile(page),
    prepare: async (page) => {
      // `getByLabel`은 이 환경에서 걸리지 않는다. 폼 구조로 직접 잡는다.
      await page.locator('input[type="text"]').first().fill('Seoul Foodie Week')
      await page.getByTestId('journey-date-start').click()
      const dateDialog = page.getByRole('dialog')
      await dateDialog.locator('button').filter({ hasText: /^10$/ }).click()
      await dateDialog.locator('button').filter({ hasText: /^12$/ }).click()
      await dateDialog.locator('button').last().click()
      await page.getByTestId('journey-create-next').click()
      await page.getByTestId('journey-create-step-2').waitFor()
    },
  },
  {
    name: '07-journey-detail',
    path: '/journeys/42',
    setup: (page) =>
      Promise.all([stubMemberProfile(page), stubJourneyDetail(page), stubEmptyReportList(page)]),
  },
  {
    name: '07b-journey-settings',
    path: '/journeys/42/settings',
    setup: (page) => Promise.all([stubMemberProfile(page), stubJourneyDetail(page)]),
  },
  {
    name: '07d-journey-settings-dates',
    path: '/journeys/42/settings',
    setup: (page) => Promise.all([stubMemberProfile(page), stubJourneyDetail(page)]),
    prepare: async (page) => {
      await page.getByTestId('journey-date-end').click()
      await page.getByRole('dialog').waitFor()
    },
  },
  {
    name: '07c-journey-remove-item',
    path: '/journeys/42',
    setup: (page) =>
      Promise.all([stubMemberProfile(page), stubJourneyDetail(page), stubEmptyReportList(page)]),
    prepare: async (page) => {
      await page.getByTestId('itinerary-remove-1').click()
      await page.getByRole('dialog').waitFor()
    },
  },
  {
    name: '17-report-list',
    path: '/reports',
    setup: (page) => Promise.all([stubMemberProfile(page), stubReportApis(page)]),
  },
  {
    name: '18-report-detail',
    path: '/reports/101',
    setup: (page) => Promise.all([stubMemberProfile(page), stubReportApis(page)]),
  },
  {
    // tripId 9는 이미 report(101)가 있다. 리스트가 아니라 상세로 바로 넘어간 화면이
    // 찍혀야 `?tripId=` 자동선택이 기존 Report를 다시 생성하려 들지 않는다는 증거가 된다.
    name: '19-report-preselect-existing',
    path: '/reports?tripId=9',
    setup: (page) => Promise.all([stubMemberProfile(page), stubReportApis(page)]),
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
  {
    name: '10-wallet-top-up',
    path: '/wallet/top-up',
    setup: async (page) => {
      await stubMemberProfile(page)
      stubWalletApis(page)
    },
    prepare: async (page) => {
      await page.getByTestId('topup-amount').locator('input').fill('50000')
    },
  },
  {
    name: '11-wallet-transactions',
    path: '/wallet/transactions',
    setup: async (page) => {
      await stubMemberProfile(page)
      stubWalletApis(page)
    },
  },
  {
    name: '12-wallet-transaction-detail',
    path: '/wallet/transactions/9',
    setup: async (page) => {
      await stubMemberProfile(page)
      stubWalletApis(page)
    },
  },
  {
    name: '13-wallet-qr',
    path: '/wallet/qr',
    setup: async (page) => {
      await stubMemberProfile(page)
      await stubWalletHome(page, [])
    },
  },
  {
    name: '17-wallet-qr-create',
    path: '/wallet/qr/create',
    setup: async (page) => {
      await stubMemberProfile(page)
    },
  },
  {
    name: '14-wallet-qr-scan',
    path: '/wallet/qr/scan',
    setup: async (page) => {
      await stubMemberProfile(page)
    },
  },
  {
    name: '15-wallet-qr-payment-preview',
    path: '/wallet/qr/payment/preview',
    setup: async (page) => {
      await stubMemberProfile(page)
    },
  },
  {
    name: '15b-wallet-qr-payment-preview-ready',
    path: '/wallet/qr/payment/preview',
    setup: async (page) => {
      await stubMemberProfile(page)
      await stubQrPaymentPreview(page)
    },
    prepare: async (page) => {
      await seedQrPaymentSession(page)
      // 소비 카테고리 칩이 그려질 때까지 기다린다. 세션이 없으면 빈 상태만 찍힌다.
      await page.getByTestId('payment-category-FOOD').waitFor()
    },
  },
  {
    name: '16-wallet-qr-payment-complete',
    path: '/wallet/qr/payment/complete?scope=shared&appointment=seoul-night-tour',
    setup: async (page) => {
      await stubMemberProfile(page)
    },
  },
  // 정산 화면은 낱장으로 두지 않고 `FLOWS`에서 클릭으로 이어 찍는다. 여기 남은 둘은
  // 후보가 없거나 조회가 실패한 상태라 지갑에서 눌러서는 도달할 수 없다.
  {
    name: '23-settlement-create-empty',
    path: '/settlements/new',
    setup: async (page) => {
      await stubMemberProfile(page)
      await stubEmptySettlementCandidates(page)
    },
    prepare: (page) => page.getByTestId('settlement-no-payments').waitFor(),
  },
  {
    name: '24-settlement-create-error',
    path: '/settlements/new',
    setup: async (page) => {
      await stubMemberProfile(page)
      await stubSettlementCandidatesError(page)
    },
    prepare: (page) => page.getByRole('alert').waitFor({ timeout: 10_000 }),
  },
  {
    // 목록은 상세와 달리 카드가 여러 장 쌓이는 화면이라 폭이 좁아질 때 먼저 무너진다.
    name: '35-explore-discover',
    path: '/explore',
    setup: (page) => Promise.all([stubMemberProfile(page), stubExploreEventList(page)]),
  },
  {
    name: '25-explore-place-detail',
    path: '/explore/places/501',
    setup: async (page) => {
      await stubMemberProfile(page)
      await stubPlaceDetail(page)
    },
    // 지도 버튼은 접힌 아래쪽에 있어 스크롤해야 뷰포트에 들어온다.
    prepare: async (page) => {
      const mapButton = page.getByRole('button', { name: 'Google Maps' })
      await mapButton.waitFor()
      await mapButton.scrollIntoViewIfNeeded()
    },
  },
  {
    name: '26-explore-event-detail',
    path: '/explore/events/301',
    setup: async (page) => {
      await stubMemberProfile(page)
      await stubEventDetail(page, { withCoordinates: true })
    },
    prepare: async (page) => {
      const mapButton = page.getByRole('button', { name: 'Google Maps' })
      await mapButton.waitFor()
      await mapButton.scrollIntoViewIfNeeded()
    },
  },
  {
    // 좌표가 NULL인 Event 상세. 지도 버튼이 숨는 것을 이미지로 증명한다(#221).
    name: '27-explore-event-detail-no-coords',
    path: '/explore/events/302',
    setup: async (page) => {
      await stubMemberProfile(page)
      await stubEventDetail(page, { withCoordinates: false })
    },
    // 버튼이 없는 것을 보여줘야 하므로 위치 섹션 자체로 스크롤한다.
    prepare: async (page) => {
      const locationHeading = page.getByTestId('event-location')
      await locationHeading.waitFor()
      await locationHeading.scrollIntoViewIfNeeded()
    },
  },

  {
    // 기간이 겹치지 않는 여정은 감추지 않고 사유와 함께 비활성으로 둔다(#357).
    name: '28-explore-event-journey-select',
    path: '/explore/events/301',
    setup: async (page) => {
      await stubMemberProfile(page)
      await stubEventDetail(page, { withCoordinates: true })
      await stubJourneyListForEvent(page)
    },
    prepare: async (page) => {
      const addButton = page.getByRole('button', { name: 'Add to journey' })
      await addButton.waitFor()
      await addButton.click()
      await page.getByRole('dialog').getByText('Seoul Lantern Trip').waitFor()
    },
  },
  {
    // 겹치는 여정이 하나도 없을 때도 같은 자리에서 만들러 나갈 수 있어야 한다(#357).
    name: '29-explore-event-journey-select-none',
    path: '/explore/events/301',
    setup: async (page) => {
      await stubMemberProfile(page)
      await stubEventDetail(page, { withCoordinates: true })
      await stubJourneyListForEvent(page, { overlapping: false })
    },
    prepare: async (page) => {
      const addButton = page.getByRole('button', { name: 'Add to journey' })
      await addButton.waitFor()
      await addButton.click()
      await page.getByRole('dialog').getByRole('button', { name: 'Create a journey' }).waitFor()
    },
  },
  {
    // 달력이 이벤트 기간과 여정 기간의 교집합(8/14~8/18)만 연다(#357).
    name: '30-explore-event-journey-date',
    path: '/explore/events/301',
    setup: async (page) => {
      await stubMemberProfile(page)
      await stubEventDetail(page, { withCoordinates: true })
      await stubJourneyListForEvent(page)
    },
    prepare: async (page) => {
      const addButton = page.getByRole('button', { name: 'Add to journey' })
      await addButton.waitFor()
      await addButton.click()
      await page.getByRole('dialog').getByText('Seoul Lantern Trip').click()
      await page.getByRole('dialog').getByText('Which day?').waitFor()
    },
  },
  {
    // 주소로 열면 확인을 한 번 받으므로, 그 버튼을 눌러 잔액 부족 거절까지 간다.
    name: '31-settlement-topup-prompt',
    path: '/settlements/42/pay',
    setup: async (page) => {
      await stubMemberProfile(page)
      await stubCsrf(page)
      await stubInsufficientSettlementPayment(page)
    },
    prepare: async (page) => {
      await page.locator('[data-action="confirm-pay"]').click()
      await page.getByRole('dialog').waitFor({ timeout: 10_000 })
    },
  },

  {
    /*
     * 같은 `LocaleSheet`를 프로필(`05-profile-language`)과 로그인 두 곳이 쓴다. 로그인 쪽은
     * 로그인 전이라 배경이 달라, 시트 조형이 바뀌면 여기서만 드러나는 회귀가 있을 수 있다.
     */
    name: '04-sign-in-language',
    path: '/sign-in',
    prepare: async (page) => {
      await page.getByLabel('Change screen language').click()
      await page.waitForSelector('[role="dialog"]')
    },
  },
]

/** 요청서 1단계를 여정 → 약속 → 거래 순으로 좁힌 뒤 2단계로 넘어간다. */
async function drillIntoRequestDetails(page) {
  await page.locator('[data-journey-key]').first().click()
  await page.locator('[data-appointment-id]').first().click()
  await page.locator('[data-payment-id]').first().click()
  await page.locator('[data-action="next"]').click()
  await page.locator('[data-type="EQUAL"]').waitFor()
}

/**
 * 클릭으로 이어 찍는 흐름.
 *
 * `SCREENS`가 낱장이라면 이쪽은 과정이다. 페이지를 한 번만 열어 두고 단계마다 조작한 뒤 한
 * 장씩 찍는다. 그래서 화면이 시안대로인지뿐 아니라 버튼이 실제로 다음 화면에 이어져
 * 있는지도 함께 드러난다. 중간에서 어긋나면 뒤 단계는 엉뚱한 화면을 찍게 되므로 남은
 * 단계는 버리고 실패로 센다.
 *
 * `act`가 도착 화면을 직접 기다린다. 러너가 `networkidle`을 걸지 않는 것은, "보내는 중"
 * 화면이 응답을 기다리는 그 사이에만 존재하기 때문이다. 러너가 응답을 끝까지 기다리면
 * 찍으려던 화면은 이미 지나가 있다.
 *
 * 그 사이를 만드는 것이 `createGate`다. 스텁이 문 앞에서 응답을 잡고 있다가, 그 화면을 찍은
 * 뒤 다음 단계의 `act` 첫 줄에서 `open()`을 부르면 그때 응답이 나간다. 캡처는 앞 단계 `act`가
 * 끝난 직후에 일어나므로 다음 `act`의 첫 줄이 곧 "찍은 직후"다.
 *
 * `focus`는 찍기 직전의 화면 위치다. 뷰포트만 찍히므로 접힌 아래쪽을 보여줘야 하면 선택자를
 * 준다. 기본값은 맨 위이며, 하단 버튼을 누른 뒤 스크롤이 남아 머리말이 잘리는 것도 막는다.
 *
 * @typedef {{ name: string, act?: Hook, focus?: string }} Step
 * @type {{ name: string, path: string, setup?: Hook, steps: Step[] }[]}
 */
/**
 * 흐름이 직접 여는 응답의 문. 흐름 하나가 문 하나를 갖는다. 나눠 쓰면 앞 흐름이 연 문이
 * 뒤 흐름에서 이미 열려 있어, 찍으려던 "보내는 중" 화면을 그냥 지나친다.
 */
const payResponseGate = createGate()
const createResponseGate = createGate()
/** 32번은 생성까지 가지 않아 이 문을 열 일이 없다. 스텁의 짝을 맞추려고 둔다. */
const itemizedCreateGate = createGate()

const FLOWS = [
  {
    // 리포트 상세에서 Similar 탭으로 바꾼 화면(#421). 세그먼트 한 번이라 화면 목록이 아니라 흐름에 둔다.
    name: '18-report-similar',
    path: '/reports/101',
    setup: (page) => Promise.all([stubMemberProfile(page), stubReportApis(page)]),
    steps: [
      {
        name: '01-similar',
        act: async (page) => {
          await page.getByTestId('segment-SIMILAR').click()
          await page.getByText('Vs. similar travelers').waitFor()
        },
      },
    ],
  },
  {
    // 지갑에서 시작해 내 몫을 결제하기까지. 도중에 서버 상태가 바뀌는 유일한 흐름이다.
    name: '30-pay',
    path: '/wallet',
    setup: async (page) => {
      await stubMemberProfile(page)
      await stubWalletHome(page, WALLET_TRANSACTIONS)
      await stubCsrf(page)
      await stubPayableSettlement(page, { gate: payResponseGate })
    },
    steps: [
      { name: '01-wallet' },
      {
        name: '02-splits-to-pay',
        act: async (page) => {
          await page.getByTestId('wallet-action-settlement').click()
          await page.getByTestId('settlement-home').waitFor()
        },
      },
      {
        name: '03-detail',
        act: async (page) => {
          await page.locator('[data-settlement-id="42"]').click()
          await page.locator('[data-action="pay"]').waitFor()
        },
      },
      {
        name: '04-paying',
        act: async (page) => {
          await page.locator('[data-action="pay"]').click()
          await page.getByTestId('settlement-status-processing').waitFor()
        },
      },
      {
        // 앞 단계를 찍은 뒤다. 여기서 결제 응답을 내보내면 화면이 스스로 완료로 넘어간다.
        name: '05-paid',
        act: async (page) => {
          payResponseGate.open()
          await page.getByTestId('settlement-status-done').waitFor({ timeout: 10_000 })
        },
      },
      {
        name: '06-detail-paid',
        act: async (page) => {
          await page.locator('[data-action="status-action"]').click()
          await page.locator('[data-action="pay-completed"]').waitFor()
        },
      },
      {
        name: '07-list-paid',
        act: async (page) => {
          await page.getByTestId('settlement-back').click()
          await page
            .locator('[data-settlement-id="42"]')
            .getByTestId('settlement-paid-mark')
            .waitFor()
        },
      },
    ],
  },
  {
    // 요청을 만드는 쪽. 후보를 여정 → 약속 → 거래로 좁혀 균등 분할로 보낸다.
    name: '31-request-equal',
    path: '/wallet',
    setup: async (page) => {
      await stubMemberProfile(page)
      await stubWalletHome(page, WALLET_TRANSACTIONS)
      await stubCsrf(page)
      await stubSettlementApis(page)
      await stubSettlementCreate(page, { gate: createResponseGate })
    },
    steps: [
      { name: '01-wallet' },
      {
        name: '02-splits',
        act: async (page) => {
          await page.getByTestId('wallet-action-settlement').click()
          await page.getByTestId('settlement-home').waitFor()
        },
      },
      {
        name: '03-journeys',
        act: async (page) => {
          await page.getByTestId('settlement-start').click()
          await page.locator('[data-journey-key]').first().waitFor()
        },
      },
      {
        name: '04-appointments',
        act: async (page) => {
          await page.locator('[data-journey-key]').first().click()
          await page.locator('[data-appointment-id]').first().waitFor()
        },
      },
      {
        name: '05-payments',
        act: async (page) => {
          await page.locator('[data-appointment-id]').first().click()
          await page.locator('[data-payment-id]').first().waitFor()
        },
      },
      {
        name: '06-payment-selected',
        act: async (page) => {
          await page.locator('[data-payment-id]').first().click()
          await page.locator('[data-payment-id][aria-pressed="true"]').waitFor()
        },
      },
      {
        name: '07-method-equal',
        act: async (page) => {
          await page.locator('[data-action="next"]').click()
          await page.locator('[data-type="EQUAL"]').waitFor()
        },
      },
      {
        // 결제자는 이미 고정돼 있다. 균등 분할은 두 명 이상이어야 다음으로 넘어간다.
        name: '08-participants',
        focus: '[data-participant-id="27"]',
        act: async (page) => {
          await page.locator('[data-participant-id="19"]').click()
          await page.locator('[data-participant-id="27"]').click()
          await page.locator('[data-participant-id="27"][aria-pressed="true"]').waitFor()
        },
      },
      {
        name: '09-review',
        act: async (page) => {
          await page.locator('[data-action="next"]').click()
          await page.locator('[data-action="create"]').waitFor()
        },
      },
      {
        name: '10-requesting',
        act: async (page) => {
          await page.locator('[data-action="create"]').click()
          await page.getByTestId('settlement-status-processing').waitFor()
        },
      },
      {
        // 앞 단계를 찍은 뒤다. 여기서 생성 응답을 내보내면 화면이 스스로 완료로 넘어간다.
        name: '11-requested',
        act: async (page) => {
          createResponseGate.open()
          await page.getByTestId('settlement-status-done').waitFor({ timeout: 10_000 })
        },
      },
      {
        name: '12-collect-list',
        act: async (page) => {
          await page.locator('[data-action="status-action"]').click()
          await page.locator('[data-settlement-id="50"]').waitFor()
        },
      },
    ],
  },
  {
    // 항목별 분할은 요청서 2단계에서만 갈라진다. 앞 여섯 장은 위 흐름과 같아 다시 찍지 않고
    // 갈라지는 지점부터 시작한다.
    name: '32-request-itemized',
    path: '/settlements/new',
    setup: async (page) => {
      await stubMemberProfile(page)
      await stubCsrf(page)
      await stubSettlementApis(page)
      await stubSettlementCreate(page, { gate: itemizedCreateGate })
    },
    steps: [
      {
        name: '01-method',
        act: async (page) => {
          await drillIntoRequestDetails(page)
          await page.locator('[data-type="ITEMIZED"]').click()
          await page.locator('[data-action="add-item"]').waitFor()
        },
      },
      {
        name: '02-participants',
        focus: '[data-participant-id="27"]',
        act: async (page) => {
          await page.locator('[data-participant-id="19"]').click()
          await page.locator('[data-participant-id="27"]').click()
          await page.locator('[data-participant-id="27"][aria-pressed="true"]').waitFor()
        },
      },
      {
        // #279부터 위저드가 첫 품목 카드를 미리 깔아 둔다(ensureFirstItem — OCR이 채울
        // 자리). 여기서 add-item을 누르면 빈 둘째 카드가 생겨 배분 검증에 걸리고
        // 5단계 전진이 막힌다. 첫 카드가 뜨기를 기다리기만 한다.
        name: '03-items-added',
        focus: '[data-item-name="0"]',
        act: async (page) => {
          await page.locator('[data-item-name="0"]').waitFor()
        },
      },
      {
        // 항목 수량과 배분 합계가 같아야 다음으로 넘어간다.
        name: '04-items-filled',
        focus: '[data-allocation-quantity="0:27"]',
        act: async (page) => {
          await page.locator('[data-item-name="0"]').fill('Burger set')
          await page.locator('[data-item-unit-price="0"]').fill('20100')
          await page.locator('[data-item-quantity="0"]').fill('3')
          await page.locator('[data-allocation-quantity="0:12"]').fill('1')
          await page.locator('[data-allocation-quantity="0:19"]').fill('1')
          await page.locator('[data-allocation-quantity="0:27"]').fill('1')
        },
      },
      {
        name: '05-review',
        act: async (page) => {
          await page.locator('[data-action="next"]').click()
          await page.locator('[data-action="create"]').waitFor()
        },
      },
    ],
  },
  {
    // 받을 쪽. 같은 목록이라도 요청자에게는 낼 버튼이 없고 누가 냈는지가 대신 온다.
    name: '33-collect',
    path: '/wallet',
    setup: async (page) => {
      await stubMemberProfile(page)
      await stubWalletHome(page, WALLET_TRANSACTIONS)
      await stubSettlementApis(page)
      await stubJson(page, '/api/v1/settlements/50', COLLECT_SETTLEMENT_DETAIL)
    },
    steps: [
      { name: '01-wallet' },
      {
        name: '02-splits-to-pay',
        act: async (page) => {
          await page.getByTestId('wallet-action-settlement').click()
          await page.getByTestId('settlement-home').waitFor()
        },
      },
      {
        name: '03-splits-to-collect',
        act: async (page) => {
          await page.getByTestId('segment-sent').click()
          await page.locator('[data-settlement-id="50"]').waitFor()
        },
      },
      {
        name: '04-detail-creator',
        act: async (page) => {
          await page.locator('[data-settlement-id="50"]').click()
          await page.getByTestId('collection-summary').waitFor()
        },
      },
      {
        name: '05-back-to-collect',
        act: async (page) => {
          await page.getByTestId('settlement-back-to-collect').click()
          await page.locator('[data-settlement-id="50"]').waitFor()
        },
      },
    ],
  },
  {
    // 완료된 정산은 목록에서 세 건만 미리 보여준다. 나머지는 전체 내역에서 본다.
    name: '34-history',
    path: '/wallet',
    setup: async (page) => {
      await stubMemberProfile(page)
      await stubWalletHome(page, WALLET_TRANSACTIONS)
      await stubSettlementApis(page)
      await stubJson(page, '/api/v1/settlements/41', COMPLETED_SETTLEMENT_DETAIL)
    },
    steps: [
      { name: '01-wallet' },
      {
        name: '02-splits',
        act: async (page) => {
          await page.getByTestId('wallet-action-settlement').click()
          await page.getByTestId('settlement-home').waitFor()
        },
      },
      {
        name: '03-history-paid',
        act: async (page) => {
          await page.locator('[data-action="view-all"]').click()
          await page.getByTestId('settlement-history-received').waitFor()
        },
      },
      {
        name: '04-detail-completed',
        act: async (page) => {
          // 출발 화면의 카드에도 'Completed' 배지가 있어 문구로는 도착을 알 수 없다.
          // 주소와 상세에만 있는 버튼으로 판정한다.
          await page.locator('[data-settlement-id="41"]').click()
          await page.waitForURL(/\/settlements\/41(\?|$)/)
          await page.locator('[data-action="pay-completed"]').waitFor()
        },
      },
      {
        name: '05-history-collected',
        act: async (page) => {
          /*
           * 상세는 전체 내역에서 열었으므로 뒤로 가면 정산 홈이 아니라 전체 내역으로
           * 돌아온다(`resolveDetailBackTarget`). 토글은 홈에만 있으니 한 번 더 나가야
           * `segment-sent`가 있는 화면에 선다. 이 한 단계를 빼면 전체 내역 화면에서
           * 없는 토글을 기다리다 30초 뒤에 끊긴다.
           */
          await page.getByTestId('settlement-back').click()
          await page.getByTestId('settlement-history-received').waitFor()
          await page.getByTestId('settlement-back').click()
          await page.getByTestId('settlement-home').waitFor()
          await page.getByTestId('segment-sent').click()
          await page.locator('[data-action="view-all"]').click()
          await page.getByTestId('settlement-history-sent').waitFor()
        },
      },
      {
        /*
         * 고른 기간은 주소에 남는다. 주소로 바로 들어가 찍는 것은 지름길이 아니라 그
         * 약속을 그대로 확인하는 것이다 — 달력을 눌러 들어오든 새로고침으로 들어오든
         * 같은 화면이어야 한다. 달력에서 고르면 찍는 달이 실행하는 날에 따라 달라져
         * 고정된 예시 날짜와 어긋난다.
         */
        name: '06-history-period',
        act: async (page) => {
          await page.goto(
            `${BASE}/settlements/history?side=received&from=2026-07-01&to=2026-07-31`,
            {
              waitUntil: 'networkidle',
            },
          )
          await page.getByRole('heading', { level: 1, name: 'Paid splits' }).waitFor()
        },
      },
      {
        name: '07-history-period-sheet',
        act: async (page) => {
          await page.locator('[data-testid="period-filter"]').click()
          await page.getByRole('dialog', { name: 'Choose a period' }).waitFor()
        },
      },
      {
        // 기간 안에 아무것도 없는 것과 완료 건이 아예 없는 것은 다른 화면이어야 한다.
        name: '08-history-period-empty',
        act: async (page) => {
          await page.goto(
            `${BASE}/settlements/history?side=received&from=2026-01-01&to=2026-01-31`,
            {
              waitUntil: 'networkidle',
            },
          )
          await page.getByText('Nothing in this period').waitFor()
        },
      },
    ],
  },
]

const selectedScreens =
  ONLY.length === 0
    ? SCREENS
    : SCREENS.filter((screen) => ONLY.some((part) => screen.name.includes(part)))
const selectedFlows =
  ONLY.length === 0
    ? FLOWS
    : FLOWS.filter((flow) =>
        flow.steps.some((step) => ONLY.some((part) => `${flow.name}-${step.name}`.includes(part))),
      )

if (selectedScreens.length === 0 && selectedFlows.length === 0) {
  console.error(`SCREENSHOT_ONLY에 맞는 화면이 없다: ${ONLY.join(', ')}`)
  process.exit(1)
}

const TOTAL =
  selectedScreens.length + selectedFlows.reduce((count, flow) => count + flow.steps.length, 0)

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
  // 앱 로케일 코드가 그대로 BCP 47 태그라 비로그인 화면의 언어 감지(navigator.languages)에
  // 그대로 먹는다. en만 기존 산출물과 같도록 en-US를 유지한다.
  locale: LOCALE === DEFAULT_LOCALE ? 'en-US' : LOCALE,
  // 화면에 모션이 들어와도 전환 중간 프레임이 찍히지 않도록 정적 상태로 고정한다.
  reducedMotion: 'reduce',
})

let failed = 0

for (const screen of selectedScreens) {
  const page = await context.newPage()

  try {
    await screen.setup?.(page)

    // 화면 경로 앞의 슬래시도 함께 정규화해 중복 슬래시를 만들지 않는다.
    await page.goto(`${BASE}/${screen.path.replace(/^\/+/, '')}`, { waitUntil: 'networkidle' })
    await screen.prepare?.(page)

    // 웹폰트가 도착한 뒤에 찍는다. 글자 폭에 맞춰 크기를 줄이는 제목·버튼(`v-fit-text`)은 폰트가
    // 오면 다시 재므로, 도착 전에 찍으면 한 프레임 전 상태가 남는다. 전환이 자리 잡을 시간도 준다.
    await page.evaluate(() => document.fonts.ready)
    await page.waitForTimeout(400)
    await page.screenshot({ path: `${OUT}/${screen.name}.png`, fullPage: FULL_PAGE })

    console.log(`  ✓ ${screen.name}.png  ← ${screen.path}`)
  } catch (error) {
    // 한 화면이 실패해도 나머지는 찍되, 실패했다는 사실은 종료 코드로 남긴다.
    failed += 1
    console.error(`  ✗ ${screen.name}  ← ${screen.path}\n    ${error.message}`)
  } finally {
    await page.close()
  }
}

for (const flow of selectedFlows) {
  const page = await context.newPage()

  try {
    await flow.setup?.(page)
    await page.goto(`${BASE}/${flow.path.replace(/^\/+/, '')}`, { waitUntil: 'networkidle' })

    for (const [index, step] of flow.steps.entries()) {
      const name = `${flow.name}-${step.name}`

      try {
        await step.act?.(page)

        if (step.focus === undefined) await page.evaluate(() => window.scrollTo(0, 0))
        else await page.locator(step.focus).scrollIntoViewIfNeeded()

        // 웹폰트가 도착한 뒤에 찍는다. 글자 폭에 맞춰 크기를 줄이는 제목·버튼(`v-fit-text`)은 폰트가
        // 오면 다시 재므로, 도착 전에 찍으면 한 프레임 전 상태가 남는다. 전환이 자리 잡을 시간도 준다.
        await page.evaluate(() => document.fonts.ready)
        await page.waitForTimeout(400)
        await page.screenshot({ path: `${OUT}/${name}.png`, fullPage: FULL_PAGE })

        console.log(`  ✓ ${name}.png`)
      } catch (error) {
        // 한 단계가 어긋나면 그 뒤는 엉뚱한 화면에서 찍힌다. 남은 단계는 버리고 실패로 센다.
        const remaining = flow.steps.length - index
        failed += remaining
        console.error(`  ✗ ${name}  (남은 ${remaining - 1}단계 건너뜀)\n    ${error.message}`)
        break
      }
    }
  } catch (error) {
    failed += flow.steps.length
    console.error(`  ✗ ${flow.name}  ← ${flow.path}\n    ${error.message}`)
  } finally {
    await page.close()
  }
}

await browser.close()

// 찍히지 않은 화면을 못 보고 넘어가지 않도록 실패를 종료 코드로 알린다. 이때는 산출물이
// 온전하지 않으므로 PR에 붙이라는 안내도 하지 않는다.
if (failed > 0) {
  console.error(`\n${failed}/${TOTAL}개 화면을 찍지 못했다. 위 오류를 확인한다.`)
  process.exit(1)
}

console.log(`\n${relative(process.cwd(), OUT) || OUT}/ 에 저장했다. PR 본문에 끌어다 놓는다.`)
