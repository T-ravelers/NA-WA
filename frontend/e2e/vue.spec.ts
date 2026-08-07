import { expect, test } from '@playwright/test'

test('renders the NA-WA app shell', async ({ page }) => {
  await page.goto('/')

  await expect(page).toHaveTitle('NA-WA')
  await expect(page.getByText('NA-WA', { exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Your trip, on record' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Get started' })).toBeVisible()
})
