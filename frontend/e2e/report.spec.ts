import { expect, test } from '@playwright/test'

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
  },
  analytics: {
    totalSpent: '23000.0000',
    dailyAverage: '2300.0000',
    categoryBreakdown: [
      { category: 'FOOD', amount: '18000.0000', percentage: '78.26' },
      { category: 'OTHER', amount: '5000.0000', percentage: '21.74' },
    ],
    dailyTrend: [
      { date: '2021-07-18', amount: '18000.0000' },
      { date: '2021-07-19', amount: '5000.0000' },
      { date: '2021-07-20', amount: '0.0000' },
    ],
  },
}

test('selects expenses, prevents duplicate generation, and opens the final report', async ({
  page,
}) => {
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
        data: {
          tripId: 9,
          candidates: [
            {
              transferId: 30,
              amount: '18000.0000',
              occurredDate: '2021-07-18',
              category: 'FOOD',
              displayMemo: 'Night market',
              selected: true,
            },
            {
              transferId: 10,
              amount: '5000.0000',
              occurredDate: '2021-07-19',
              category: 'OTHER',
              displayMemo: null,
              selected: false,
            },
          ],
        },
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

  await page.goto('/reports')
  await expect(page.getByRole('heading', { level: 1, name: 'Reports' })).toBeVisible()
  await expect(page.getByText('Jeju Island')).toBeVisible()
  await expect(page.getByText('Future Journey')).toBeHidden()

  await page.getByRole('button', { name: 'Choose expenses' }).click()
  await expect(page.getByLabel(/FOOD.*₩18,000/)).toBeChecked()
  await page.getByLabel(/OTHER.*₩5,000/).check()

  const generate = page.getByRole('button', { name: 'Generate final report' })
  await generate.click()
  await expect(generate).toBeDisabled()

  await expect(page).toHaveURL(/\/reports\/101$/)
  await expect(page.getByRole('heading', { level: 1, name: 'Final report' })).toBeVisible()
  await expect(page.getByText('₩23,000')).toBeVisible()
  await expect(page.getByRole('table')).toHaveCount(2)
  expect(createRequest).toEqual({ locale: 'en', transferIds: [10, 30] })
})
