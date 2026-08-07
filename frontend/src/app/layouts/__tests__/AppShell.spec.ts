import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
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
        path: '/explore/events/:eventId',
        name: 'explore-event-detail',
        component: { template: '<h1>Event detail</h1>' },
        meta: { requiresAuth: true, hideBottomNav: true },
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

  it('hides the bottom navigation on detail screens', async () => {
    const wrapper = await mountAt('/explore/events/990001')

    expect(wrapper.find('nav').exists()).toBe(false)
  })

  it('gives every icon-only navigation control an accessible name', async () => {
    const wrapper = await mountAt('/wallet')

    const controls = wrapper.findAll('nav li > *')

    expect(controls.length).toBe(5)

    for (const control of controls) {
      expect(control.attributes('aria-label')).toBeTruthy()
    }
  })
})
