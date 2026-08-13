import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'

import BottomNav from '../BottomNav.vue'

async function mountAt(path: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/explore', component: { template: '<div />' } },
      { path: '/reports', component: { template: '<div />' } },
      { path: '/reports/:reportId', component: { template: '<div />' } },
      { path: '/settings', component: { template: '<div />' } },
      { path: '/wallet', component: { template: '<div />' } },
      { path: '/journeys', component: { template: '<div />' } },
    ],
  })

  await router.push(path)
  await router.isReady()

  return mount(BottomNav, { global: { plugins: [i18n, router] } })
}

describe('BottomNav', () => {
  it('renders five tabs with the Report tab second and linked to /reports', async () => {
    const wrapper = await mountAt('/explore')
    const links = wrapper.findAll('a')

    expect(wrapper.findAll('li')).toHaveLength(5)
    expect(links[1]?.attributes('href')).toBe('/reports')
    expect(links[1]?.attributes('aria-label')).toBe('Report')
  })

  it('marks the Report tab active on the report list and on a report detail route', async () => {
    const onList = await mountAt('/reports')
    expect(onList.get('a[href="/reports"]').attributes('aria-current')).toBe('page')

    const onDetail = await mountAt('/reports/42')
    expect(onDetail.get('a[href="/reports"]').attributes('aria-current')).toBe('page')
  })

  it('gives every tab an accessible name', async () => {
    const wrapper = await mountAt('/explore')

    for (const control of wrapper.findAll('li > *')) {
      expect(control.attributes('aria-label')).toBeTruthy()
    }
  })
})
