import { expect, test } from '@playwright/test'

test('creates a journey and opens its empty itinerary', async ({ page }) => {
  let createRequest: unknown

  await page.route('**/api/v1/auth/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        data: {
          memberId: 1,
          displayName: 'Mina',
          profileImageUrl: null,
          preferredLanguage: 'en',
          preferredCurrencyCode: 'KRW',
          onboardingRequired: false,
        },
      }),
    })
  })

  await page.route('**/api/v1/auth/csrf', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        data: { token: 'csrf-token', headerName: 'X-CSRF-TOKEN' },
      }),
    })
  })

  await page.route('**/api/v1/journeys', async (route) => {
    if (route.request().method() !== 'POST') {
      await route.fallback()
      return
    }

    createRequest = route.request().postDataJSON()
    await new Promise((resolve) => setTimeout(resolve, 150))
    await route.fulfill({
      status: 201,
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
    })
  })

  await page.route('**/api/v1/journeys/42/timeline', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ success: true, data: { tripId: 42, timeline: [] } }),
    })
  })

  await page.route('**/api/v1/journeys/42', async (route) => {
    await route.fulfill({
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
    })
  })

  await page.goto('/journeys/new')
  await page.getByLabel('Trip name').fill('Seoul Foodie Week')
  await page.getByLabel('Start date').fill('2026-08-10')
  await page.getByLabel('End date').fill('2026-08-12')
  await page.getByRole('button', { name: 'Next' }).click()
  await page.getByLabel('Budget').fill('1500000')
  await page.getByRole('button', { name: /2–4 Small group travel/ }).click()

  const createButton = page.getByRole('button', { name: 'Create journey' })
  await createButton.click()
  await expect(createButton).toBeDisabled()

  await expect(page).toHaveURL(/\/journeys\/42$/)
  await expect(page.getByRole('heading', { level: 1, name: 'Seoul Foodie Week' })).toBeVisible()
  await expect(page.getByText('No itinerary yet')).toBeVisible()
  expect(createRequest).toEqual({
    title: 'Seoul Foodie Week',
    startDate: '2026-08-10',
    endDate: '2026-08-12',
    budgetAmount: 1500000,
    companionPreference: '2-4',
    regions: [],
  })
})
