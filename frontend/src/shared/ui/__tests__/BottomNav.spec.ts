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
      { path: '/profile', component: { template: '<div />' } },
      { path: '/wallet', component: { template: '<div />' } },
      { path: '/journeys', component: { template: '<div />' } },
    ],
  })

  await router.push(path)
  await router.isReady()

  return mount(BottomNav, { global: { plugins: [i18n, router] } })
}

describe('BottomNav', () => {
  it('orders the tabs as the V2 design does', async () => {
    const wrapper = await mountAt('/explore')

    expect(wrapper.findAll('a').map((link) => link.attributes('href'))).toEqual([
      '/journeys',
      '/explore',
      '/wallet',
      '/reports',
      '/profile',
    ])
  })

  it('sends the last tab to the profile screen', async () => {
    const wrapper = await mountAt('/explore')

    expect(wrapper.get('a[href="/profile"]').text()).toBe('My')
  })

  it('marks the Report tab active on the report list and on a report detail route', async () => {
    const onList = await mountAt('/reports')
    expect(onList.get('a[href="/reports"]').attributes('aria-current')).toBe('page')

    const onDetail = await mountAt('/reports/42')
    expect(onDetail.get('a[href="/reports"]').attributes('aria-current')).toBe('page')
  })

  /** 라벨이 보이므로 이름은 글자가 맡는다. `aria-label`을 겹쳐 붙이면 두 번 읽힌다. */
  it('names every tab with its visible label', async () => {
    const wrapper = await mountAt('/explore')

    for (const link of wrapper.findAll('li a')) {
      expect(link.text()).not.toBe('')
      expect(link.attributes('aria-label')).toBeUndefined()
    }
  })
})
