import { expect, test } from './fixtures'

const memberEnvelope = {
  success: true,
  data: {
    memberId: 1,
    displayName: 'Mina Park',
    profileImageUrl: null,
    preferredLanguage: 'en',
    preferredCurrencyCode: 'KRW',
    onboardingRequired: false,
  },
}

const reportDetail = {
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
      totalSpent: 23000,
      dailyAverage: 2300,
      categoryBreakdown: [
        { category: 'FOOD', amount: 18000, percentage: 78.26 },
        { category: 'OTHER', amount: 5000, percentage: 21.74 },
      ],
      dailyTrend: [
        { date: '2021-07-18', amount: 18000 },
        { date: '2021-07-19', amount: 5000 },
        { date: '2021-07-20', amount: 0 },
      ],
    },
  },
}

const avatarPixel =
  'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=='

const groupComparison = {
  scope: 'GROUP',
  basis: 'LIVE',
  me: {
    memberId: 1,
    displayName: 'Mina Park',
    profileImageUrl: null,
    totalSpent: 25_000,
    dailyAverage: 2_500,
    categoryBreakdown: [{ category: 'FOOD', amount: 20_000, percentage: 80 }],
  },
  peers: [
    {
      memberId: 2,
      displayName: 'Alex',
      profileImageUrl: avatarPixel,
      totalSpent: 12_000,
      dailyAverage: 1_200,
      categoryBreakdown: [{ category: 'FOOD', amount: 12_000, percentage: 100 }],
    },
    {
      memberId: 3,
      displayName: '🇰🇷 Jae',
      profileImageUrl: null,
      totalSpent: 9_000,
      dailyAverage: 900,
      categoryBreakdown: [{ category: 'OTHER', amount: 9_000, percentage: 100 }],
    },
  ],
  cohort: {
    size: 2,
    avgTotalSpent: 10_500,
    avgDailyAverage: 1_050,
    categoryBreakdown: [
      { category: 'FOOD', amount: 6_000, percentage: 57.14 },
      { category: 'OTHER', amount: 4_500, percentage: 42.86 },
    ],
  },
  ranks: [{ category: 'FOOD', rank: 1, of: 3 }],
}

const similarComparison = {
  ...groupComparison,
  scope: 'SIMILAR',
  basis: 'SNAPSHOT',
  peers: [],
  cohort: { ...groupComparison.cohort, size: 12 },
  ranks: [],
}

test('selects expenses, prevents duplicate generation, and opens the report', async ({ page }) => {
  let createRequest: unknown

  await page.route('**/api/v1/members/me', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(memberEnvelope),
    }),
  )
  await page.route('**/api/v1/auth/csrf', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        data: { token: 'csrf-token', headerName: 'X-CSRF-TOKEN' },
      }),
    }),
  )
  await page.route('**/api/v1/journeys', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        data: [
          {
            tripId: 42,
            title: 'Future Journey',
            startDate: '2098-08-10',
            endDate: '2098-08-12',
          },
          {
            tripId: 9,
            title: 'Jeju Island',
            startDate: '2021-07-18',
            endDate: '2021-07-27',
          },
        ],
      }),
    }),
  )
  await page.route('**/api/v1/reports', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ success: true, data: [] }),
    }),
  )
  await page.route('**/api/v1/journeys/9/report-expense-candidates', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        data: [
          {
            transferId: 30,
            amount: 18000,
            occurredOn: '2021-07-18',
            category: 'FOOD',
            memo: 'Night market',
            selected: true,
          },
          {
            transferId: 10,
            amount: 5000,
            occurredOn: '2021-07-19',
            category: 'OTHER',
            memo: null,
            selected: false,
          },
        ],
      }),
    }),
  )
  await page.route('**/api/v1/journeys/9/reports', async (route) => {
    createRequest = route.request().postDataJSON()
    await new Promise((resolve) => setTimeout(resolve, 150))
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({ success: true, data: reportDetail }),
    })
  })
  await page.route('**/api/v1/reports/101', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ success: true, data: reportDetail }),
    }),
  )
  await page.route(/\/api\/v1\/reports\/101\/comparison(\?.*)?$/, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        data: route.request().url().includes('scope=SIMILAR') ? similarComparison : groupComparison,
      }),
    }),
  )

  await page.goto('/reports')
  await expect(page.getByRole('heading', { level: 1, name: 'Reports' })).toBeVisible()
  await expect(page.getByText('Jeju Island')).toBeVisible()
  await expect(page.getByText('Future Journey')).toBeHidden()

  await page.getByRole('button', { name: 'Choose expenses' }).click()
  await expect(page.getByLabel(/FOOD.*18,000 P/)).toBeChecked()
  await page.getByLabel(/OTHER.*5,000 P/).check()

  const generate = page.getByRole('button', { name: 'Generate report' })
  await generate.click()
  await expect(generate).toBeDisabled()

  await expect(page).toHaveURL(/\/reports\/101$/)
  await expect(page.getByRole('heading', { level: 1, name: 'Report' })).toBeVisible()
  await expect(page.getByText('23,000 P')).toBeVisible()
  await expect(page.getByRole('heading', { level: 2, name: 'By category' })).toBeVisible()
  // 범례는 분류명과 비율을 각각 다른 요소로 그린다. 한 문자열로 묶어 찾으면
  // 마크업이 조금만 바뀌어도 깨지므로 행 단위로 확인한다.
  // 비교 섹션에도 같은 카테고리 목록이 있으므로 `By category` 섹션 안으로 한정한다.
  const categorySection = page.getByRole('heading', { level: 2, name: 'By category' }).locator('..')
  const foodRow = categorySection.getByRole('listitem').filter({ hasText: 'FOOD' })
  await expect(foodRow).toContainText('78%')
  await expect(page.getByRole('heading', { level: 2, name: 'Spending trend' })).toBeVisible()
  await expect(page.getByRole('listitem').filter({ hasText: '2021.07.20' })).toBeVisible()
  const comparison = page.locator('section[aria-labelledby="report-comparison-title"]')
  const memberChips = comparison.getByRole('radiogroup', { name: 'Group members' })
  await expect(memberChips.getByRole('radio')).toHaveCount(2)
  await expect(memberChips.locator('img')).toHaveCount(1)
  await expect(memberChips.getByText('🇰🇷', { exact: true })).toBeVisible()
  await expect(comparison.getByText('Daily avg', { exact: true })).toBeVisible()
  await expect(comparison.getByText('1,200 P', { exact: true })).toBeVisible()
  expect(createRequest).toEqual({ locale: 'en', transferIds: [10, 30] })
})
