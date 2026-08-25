import { mount } from '@vue/test-utils'
import { MotionConfig } from 'motion-v'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import App from '../App.vue'
import { i18n } from '../i18n'

describe('App', () => {
  beforeEach(() => {
    // jsdom에는 `matchMedia`가 없다. 가장자리 스와이프는 설치형 앱에서만 켜지므로 탭으로 둔다.
    vi.stubGlobal(
      'matchMedia',
      vi.fn(() => ({ matches: false })),
    )
  })

  it('renders the active route inside the app shell', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        {
          path: '/',
          name: 'stub',
          component: { template: '<h1>NA-WA</h1>' },
        },
      ],
    })

    await router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [i18n, router],
      },
    })

    expect(wrapper.get('h1').text()).toBe('NA-WA')
  })

  it('respects the user reduced-motion preference for Motion components', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        {
          path: '/',
          name: 'stub',
          component: { template: '<h1>NA-WA</h1>' },
        },
      ],
    })

    await router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [i18n, router],
      },
    })

    expect(wrapper.getComponent(MotionConfig).props('reducedMotion')).toBe('user')
  })
})
