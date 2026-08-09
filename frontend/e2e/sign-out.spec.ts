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

test('stays on settings after a network failure and signs out on retry', async ({ page }) => {
  let logoutAttempts = 0
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
    logoutAttempts += 1

    if (logoutAttempts === 1) {
      await route.abort('connectionfailed')
      return
    }

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

  await page.goto('/settings')
  await expect(page.getByText('Mina')).toBeVisible()

  await page.getByRole('button', { name: 'Sign out' }).click()

  await expect(page).toHaveURL(/\/settings$/)
  await expect(page.getByRole('alert')).toContainText('We could not sign you out')
  expect(logoutAttempts).toBe(1)

  await page.getByRole('button', { name: 'Try again' }).click()

  await expect(page).toHaveURL(/\/sign-in$/)
  expect(logoutAttempts).toBe(2)
})
