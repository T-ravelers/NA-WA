import { expect, test } from '@playwright/test'

test('lists ongoing and past journeys and navigates to journey actions', async ({ page }) => {
  await page.route('**/api/v1/members/me', async (route) => {
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

  await page.route('**/api/v1/journeys', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        data: [
          {
            tripId: 42,
            title: 'Seoul Foodie Week',
            startDate: '2098-08-10',
            endDate: '2098-08-12',
          },
          {
            tripId: 7,
            title: 'Busan Weekender',
            startDate: '2020-08-10',
            endDate: '2020-08-12',
          },
        ],
      }),
    })
  })

  await page.goto('/journeys')
  await expect(page.getByRole('heading', { level: 1, name: 'Journeys' })).toBeVisible()
  await expect(page.getByRole('link', { name: /Seoul Foodie Week/ })).toBeVisible()
  await expect(page.getByRole('link', { name: /Busan Weekender/ })).toBeHidden()

  await page.getByRole('radio', { name: 'Past' }).click()
  await expect(page.getByRole('link', { name: /Busan Weekender/ })).toBeVisible()
  await expect(page.getByRole('link', { name: /Seoul Foodie Week/ })).toBeHidden()

  await page.getByRole('button', { name: 'Add journey' }).click()
  await expect(page).toHaveURL(/\/journeys\/new$/)

  await page.goto('/journeys')
  await page.getByRole('link', { name: /Seoul Foodie Week/ }).click()
  await expect(page).toHaveURL(/\/journeys\/42$/)
})

test('creates a journey and opens its empty itinerary', async ({ page }) => {
  let createRequest: unknown

  await page.route('**/api/v1/members/me', async (route) => {
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
