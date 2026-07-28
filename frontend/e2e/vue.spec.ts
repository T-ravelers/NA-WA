import { expect, test } from '@playwright/test'

test('renders the NA-WA app shell', async ({ page }) => {
  await page.goto('/')

  await expect(page).toHaveTitle('NA-WA')
  await expect(page.getByRole('heading', { name: 'NA-WA' })).toBeVisible()
  await expect(page.getByText('함께 만드는 여행의 시작')).toBeVisible()
})
