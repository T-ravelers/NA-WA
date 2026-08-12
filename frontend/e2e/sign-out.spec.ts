import { expect, test } from '@playwright/test'

test('signs out, clears the pending return path, and protects browser history', async ({
  page,
}) => {
  let signedOut = false

  await page.route('**/api/v1/members/me', async (route) => {
    if (signedOut) {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({
          success: false,
          error: { code: 'AUTH-003', message: 'authentication required' },
        }),
      })
      return
    }

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

  await page.route('**/api/v1/auth/logout', async (route) => {
    signedOut = true
    await route.fulfill({ status: 200, contentType: 'application/json', body: '{"success":true}' })
  })

  await page.route('**/api/v1/auth/refresh', async (route) => {
    await route.fulfill({
      status: 401,
      contentType: 'application/json',
      body: JSON.stringify({
        success: false,
        error: { code: 'AUTH-001', message: 'session expired' },
      }),
    })
  })

  await page.goto('/wallet')
  await page.goto('/settings')
  await expect(page.getByText('Mina')).toBeVisible()
  await page.evaluate(() => {
    localStorage.setItem('nawa.locale', 'en')
    sessionStorage.setItem('nawa.auth.returnPath', '/stale-destination')
  })

  await page.getByRole('button', { name: 'Sign out' }).click()

  await expect(page).toHaveURL(/\/sign-in$/)
  await expect
    .poll(() => page.evaluate(() => sessionStorage.getItem('nawa.auth.returnPath')))
    .toBeNull()
  await expect.poll(() => page.evaluate(() => localStorage.getItem('nawa.locale'))).toBe('en')

  await page.goBack()

  await expect(page).toHaveURL((url) => {
    return url.pathname === '/sign-in' && url.searchParams.get('returnPath') === '/wallet'
  })
})

test('keeps an uncertain sign-out across tabs and reload, then signs out on retry', async ({
  context,
  page,
}) => {
  let logoutAttempts = 0
  let signedOut = false
  let memberProfileRequests = 0
  let refreshRequests = 0

  await context.route('**/api/v1/members/me', async (route) => {
    memberProfileRequests += 1

    if (signedOut) {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({
          success: false,
          error: { code: 'AUTH-003', message: 'authentication required' },
        }),
      })
      return
    }

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

  await context.route('**/api/v1/auth/csrf', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        data: { token: 'csrf-token', headerName: 'X-CSRF-TOKEN' },
      }),
    })
  })

  await context.route('**/api/v1/auth/logout', async (route) => {
    logoutAttempts += 1

    if (logoutAttempts === 1) {
      await route.abort('connectionfailed')
      return
    }

    signedOut = true
    await route.fulfill({ status: 200, contentType: 'application/json', body: '{"success":true}' })
  })

  await context.route('**/api/v1/auth/refresh', async (route) => {
    refreshRequests += 1
    await route.fulfill({
      status: 401,
      contentType: 'application/json',
      body: JSON.stringify({
        success: false,
        error: { code: 'AUTH-001', message: 'session expired' },
      }),
    })
  })

  const secondPage = await context.newPage()

  await page.goto('/settings')
  await secondPage.goto('/settings')
  await expect(page.getByText('Mina')).toBeVisible()
  await expect(secondPage.getByText('Mina')).toBeVisible()

  const memberRequestsBeforeBarrier = memberProfileRequests

  await page.getByRole('button', { name: 'Sign out' }).click()

  await expect(page).toHaveURL(/\/sign-in$/)
  await expect(secondPage).toHaveURL(/\/sign-in$/)
  await expect(page.getByRole('alert')).toContainText('We could not confirm that you signed out')
  expect(logoutAttempts).toBe(1)

  await page.reload()
  await expect(page.getByRole('alert')).toContainText('We could not confirm that you signed out')

  await page.goto('/wallet')
  await expect(page).toHaveURL(/\/sign-in$/)
  expect(memberProfileRequests).toBe(memberRequestsBeforeBarrier)
  expect(refreshRequests).toBe(0)

  await page.getByRole('button', { name: 'Try signing out again' }).click()

  await expect(page).toHaveURL(/\/sign-in$/)
  await expect(page.getByRole('alert')).toHaveCount(0)
  expect(logoutAttempts).toBe(2)
})

test('keeps the barrier after a failed callback and clears it after a successful sign-in', async ({
  page,
}) => {
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

  await page.route('**/api/v1/auth/logout', async (route) => {
    await route.abort('connectionfailed')
  })

  await page.goto('/settings')
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page).toHaveURL(/\/sign-in$/)

  await page.goto('/auth/callback?error=AUTH-014')
  await expect
    .poll(() => page.evaluate(() => localStorage.getItem('nawa.auth.signOutBarrier')))
    .toBe('active')

  await page.goto('/auth/callback')

  await expect(page).toHaveURL(/\/explore$/)
  await expect
    .poll(() => page.evaluate(() => localStorage.getItem('nawa.auth.signOutBarrier')))
    .toBeNull()
})
