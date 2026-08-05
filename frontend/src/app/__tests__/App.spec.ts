import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import App from '../App.vue'
import { i18n } from '../i18n'

describe('App', () => {
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
})
