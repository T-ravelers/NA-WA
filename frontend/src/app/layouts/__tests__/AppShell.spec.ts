import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'

import AppShell from '../AppShell.vue'

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/sign-in',
        name: 'sign-in',
        component: { template: '<h1>Sign in</h1>' },
      },
      {
        path: '/wallet',
        name: 'wallet',
        component: { template: '<h1>Wallet</h1>' },
        meta: { requiresAuth: true },
      },
      {
        path: '/wallet/top-up',
        name: 'wallet-top-up',
        component: { template: '<h1>Top up</h1>' },
        meta: { requiresAuth: true, hideBottomNav: true },
      },
      {
        path: '/journeys',
        name: 'journey-list',
        component: { template: '<h1>Journeys</h1>' },
        meta: { requiresAuth: true },
      },
    ],
  })
}

async function mountAt(path: string) {
  const router = createTestRouter()

  await router.push(path)
  await router.isReady()

  const wrapper = mount(AppShell, { global: { plugins: [i18n, router] } })

  await flushPromises()

  return wrapper
}

describe('AppShell', () => {
  beforeEach(() => {
    // jsdom에는 `matchMedia`가 없다. 가장자리 스와이프는 설치형 앱에서만 켜지므로 탭으로 둔다.
    vi.stubGlobal(
      'matchMedia',
      vi.fn(() => ({ matches: false })),
    )
  })

  it('renders the active route', async () => {
    const wrapper = await mountAt('/wallet')

    expect(wrapper.get('h1').text()).toBe('Wallet')
  })

  it('shows the bottom navigation on service screens', async () => {
    const wrapper = await mountAt('/wallet')

    expect(wrapper.find('nav').exists()).toBe(true)
  })

  it('hides the bottom navigation on the sign-in screen', async () => {
    const wrapper = await mountAt('/sign-in')

    expect(wrapper.find('nav').exists()).toBe(false)
  })

  it('hides the bottom navigation during the top-up flow', async () => {
    const wrapper = await mountAt('/wallet/top-up')

    expect(wrapper.find('nav').exists()).toBe(false)
  })

  it('gives every navigation control an accessible name', async () => {
    const wrapper = await mountAt('/wallet')

    const controls = wrapper.findAll('nav li > *')

    expect(controls.length).toBe(5)

    // 시안이 아이콘 아래에 라벨을 함께 그린다. 이름은 그 글자가 맡는다.
    for (const control of controls) {
      expect(control.text()).not.toBe('')
    }
  })

  it('links the Journey item to the list and marks it active', async () => {
    const wrapper = await mountAt('/journeys')
    const journeyLink = wrapper.get('a[href="/journeys"]')

    expect(journeyLink.text()).toBe('Journey')
    expect(journeyLink.attributes('aria-current')).toBe('page')
  })
})
