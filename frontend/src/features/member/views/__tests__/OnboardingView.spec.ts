import { VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/app/i18n'
import { queryClient } from '@/app/query/client'

import type { MemberProfile } from '../../api/memberApi'

const fetchMemberProfile = vi.fn()
const completeOnboarding = vi.fn()
const requestSignOut = vi.fn()

vi.mock('../../api/memberApi', () => ({
  fetchMemberProfile: () => fetchMemberProfile(),
  completeOnboarding: (payload: unknown) => completeOnboarding(payload),
}))

vi.mock('@/shared/api/sessionSignOut', () => ({
  requestSignOut: () => requestSignOut(),
}))

const OnboardingView = (await import('../OnboardingView.vue')).default

const PROFILE: MemberProfile = {
  memberId: 1,
  displayName: 'Mina',
  profileImageUrl: null,
  nationalityCode: null,
  preferredLanguage: 'en',
  preferredCurrencyCode: null,
  accountType: 'TRAVELER',
  onboardingRequired: true,
}

async function mountView(profile: MemberProfile = PROFILE) {
  fetchMemberProfile.mockResolvedValue(profile)

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/explore', component: { template: '<div />' } },
    ],
  })

  const wrapper = mount(OnboardingView, {
    global: { plugins: [i18n, router, [VueQueryPlugin, { queryClient }]] },
  })

  await router.isReady()
  await flushPromises()

  return { wrapper, router }
}

describe('OnboardingView', () => {
  beforeEach(() => {
    queryClient.setDefaultOptions({ queries: { retry: false } })
    completeOnboarding.mockResolvedValue({ ...PROFILE, onboardingRequired: false })
    requestSignOut.mockResolvedValue(undefined)
  })

  afterEach(() => {
    queryClient.clear()
    vi.clearAllMocks()
  })

  it('greets the new member and offers the form', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Welcome')
    expect(wrapper.find('form').exists()).toBe(true)
  })

  /*
   * 온보딩을 마치기 전에는 계정을 빠져나갈 곳이 없다 — `/profile`은 게이트가 막고,
   * `/sign-in`은 `guestOnly`라 되돌아온다. 이 화면의 출구가 유일하다.
   */
  it('lets someone sign out instead of being trapped here', async () => {
    const { wrapper } = await mountView()

    await wrapper.get('button[type="button"]').trigger('click')
    await flushPromises()

    expect(requestSignOut).toHaveBeenCalledOnce()
  })

  it('sends the four fields the server requires and goes to the service', async () => {
    const { wrapper, router } = await mountView()

    await wrapper.get('input').setValue('Mina Park')
    await wrapper.get('select').setValue('JP')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(completeOnboarding).toHaveBeenCalledWith({
      displayName: 'Mina Park',
      nationalityCode: 'JP',
      preferredLanguage: 'en',
      // 고를 수 있는 통화가 하나뿐이라 묻지 않고 싣는다.
      preferredCurrencyCode: 'KRW',
    })
    expect(router.currentRoute.value.path).toBe('/explore')
  })

  it('does not offer a way back out of the form itself', async () => {
    const { wrapper } = await mountView()

    // 편집 폼의 Cancel은 온보딩에 없다. 돌아갈 곳이 없기 때문이다.
    expect(wrapper.text()).not.toContain('Cancel')
  })
})
