import { expect, test } from '@playwright/test'

test('renders the NA-WA app shell', async ({ page }) => {
  await page.goto('/')

  await expect(page).toHaveTitle('NA-WA')
  const wordmark = page.getByRole('img', { name: 'NAWA' })
  await expect(wordmark).toBeVisible()

  const wordmarkBox = await wordmark.boundingBox()
  expect(wordmarkBox?.height).toBeCloseTo(52, 1)
  expect(wordmarkBox?.width).toBeCloseTo(196.4, 1)

  await expect(page.getByRole('heading', { name: 'Your trip, on record' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Get started' })).toBeVisible()
})
