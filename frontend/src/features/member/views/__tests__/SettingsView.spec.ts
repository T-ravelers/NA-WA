import { VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/app/i18n'
import { queryClient } from '@/app/query/client'
import { NormalizedApiError } from '@/shared/api/apiError'

import type { MemberProfile } from '../../api/memberApi'

const fetchMemberProfile = vi.fn()
const updateMemberProfile = vi.fn()
const applyLocale = vi.fn()
const requestSignOut = vi.fn()

vi.mock('../../api/memberApi', () => ({
  fetchMemberProfile: () => fetchMemberProfile(),
  updateMemberProfile: (payload: unknown) => updateMemberProfile(payload),
}))

vi.mock('@/app/i18n/applyLocale', () => ({
  applyLocale: (...args: unknown[]) => applyLocale(...args),
}))

vi.mock('@/shared/api/sessionSignOut', () => ({
  requestSignOut: () => requestSignOut(),
}))

const SettingsView = (await import('../SettingsView.vue')).default

const PROFILE: MemberProfile = {
  memberId: 1,
  displayName: 'Mina',
  profileImageUrl: null,
  nationalityCode: null,
  preferredLanguage: 'en',
  preferredCurrencyCode: null,
  accountType: 'TRAVELER',
  onboardingRequired: false,
}

async function mountView() {
  const wrapper = mount(SettingsView, {
    global: { plugins: [i18n, [VueQueryPlugin, { queryClient }]] },
  })

  await flushPromises()

  return wrapper
}

/** 시트에서 n번째 언어를 고른다. 순서는 SUPPORTED_LOCALES를 따른다. */
async function chooseLanguage(wrapper: Awaited<ReturnType<typeof mountView>>, index: number) {
  await wrapper.get('[aria-label="Change screen language"]').trigger('click')
  await wrapper.findAll('[role="dialog"] [role="radio"]')[index]?.trigger('click')
  await flushPromises()
}

describe('SettingsView', () => {
  beforeEach(() => {
    // 재시도가 켜져 있으면 실패 상태가 화면에 늦게 도달해 단언이 흔들린다.
    queryClient.setDefaultOptions({ queries: { retry: false } })
    fetchMemberProfile.mockResolvedValue(PROFILE)
    updateMemberProfile.mockResolvedValue(PROFILE)
    requestSignOut.mockResolvedValue(undefined)
  })

  afterEach(() => {
    queryClient.clear()
    vi.clearAllMocks()
  })

  it('shows the signed-in member', async () => {
    expect((await mountView()).text()).toContain('Mina')
  })

  it('signs out from the account section', async () => {
    const wrapper = await mountView()

    await wrapper.get('[aria-label="Sign out"]').trigger('click')
    await flushPromises()

    expect(requestSignOut).toHaveBeenCalledOnce()
  })

  it('shows an error state with a retry when the profile cannot be loaded', async () => {
    fetchMemberProfile.mockRejectedValue(new NormalizedApiError('MEMBER-001', 404, 'not found'))

    const wrapper = await mountView()

    expect(wrapper.get('[role="alert"]').text()).toContain('Something went wrong')
    expect(wrapper.text()).not.toContain('Mina')
  })

  it('names the applied screen language', async () => {
    expect((await mountView()).text()).toContain('English')
  })

  it('keeps the language sheet closed until asked', async () => {
    expect((await mountView()).find('[role="dialog"]').exists()).toBe(false)
  })

  it('applies the chosen language and saves it to the account', async () => {
    const wrapper = await mountView()

    await chooseLanguage(wrapper, 1)

    expect(applyLocale).toHaveBeenCalledWith('ja', { persist: true })
    expect(updateMemberProfile).toHaveBeenCalledWith({ preferredLanguage: 'ja' })
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  // 이미 쓰고 있는 언어를 다시 고르면 MEMBER-004(변경할 항목 없음)를 부르게 된다.
  it('does not call the server when the language did not change', async () => {
    const wrapper = await mountView()

    await chooseLanguage(wrapper, 0)

    expect(updateMemberProfile).not.toHaveBeenCalled()
    expect(applyLocale).not.toHaveBeenCalled()
  })

  it('reports a failed save without undoing the screen language', async () => {
    updateMemberProfile.mockRejectedValue(
      new NormalizedApiError('MEMBER-002', 400, 'unsupported language'),
    )

    const wrapper = await mountView()

    await chooseLanguage(wrapper, 1)

    const alert = wrapper.get('[role="alert"]')

    expect(alert.text()).toContain('could not save it to your account')
    expect(alert.text()).toContain('That language is not supported yet')
    // 화면 언어는 되돌리지 않는다. 사용자가 방금 고른 선택이다.
    expect(applyLocale).toHaveBeenCalledWith('ja', { persist: true })
  })

  it('falls back to the generic notice when the failure has no mapped code', async () => {
    updateMemberProfile.mockRejectedValue(new Error('boom'))

    const wrapper = await mountView()

    await chooseLanguage(wrapper, 1)

    const alert = wrapper.get('[role="alert"]')

    expect(alert.text()).toContain('could not save it to your account')
    expect(alert.text()).not.toContain('not supported')
  })
})
