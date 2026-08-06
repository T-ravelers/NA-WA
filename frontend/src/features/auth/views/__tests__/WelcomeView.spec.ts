import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import { i18n } from '@/app/i18n'

import WelcomeView from '../WelcomeView.vue'

function createTestRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'welcome', component: WelcomeView },
      { path: '/sign-in', name: 'sign-in', component: { template: '<div />' } },
    ],
  })
}

async function mountView() {
  const router = createTestRouter()

  await router.push('/')
  await router.isReady()

  const wrapper = mount(WelcomeView, { global: { plugins: [i18n, router] } })

  await flushPromises()

  return { wrapper, router }
}

describe('WelcomeView', () => {
  it('leads with the product promise', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.get('h1').text()).toContain('Your trip,')
    expect(wrapper.get('h1').text()).toContain('on record')
  })

  it('sends the visitor to sign-in', async () => {
    const { wrapper, router } = await mountView()

    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/sign-in')
  })

  /*
   * 보딩패스는 브랜드 장식이다. 정보를 담고 있지 않으므로 스크린 리더가 읽을 필요가 없다.
   */
  it('hides the decorative boarding pass from assistive technology', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.get('[aria-hidden="true"]').text()).toContain('Seoul & Beyond')
  })
})
