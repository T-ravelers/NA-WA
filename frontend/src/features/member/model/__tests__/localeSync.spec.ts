import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { MemberProfile } from '../../api/memberApi'

const applyLocale = vi.fn()
const updateMemberProfile = vi.fn()
const setMemberProfile = vi.fn()

vi.mock('@/app/i18n/applyLocale', () => ({
  applyLocale: (...args: unknown[]) => applyLocale(...args),
}))

vi.mock('../../api/memberApi', () => ({
  updateMemberProfile: (...args: unknown[]) => updateMemberProfile(...args),
}))

vi.mock('../memberQueries', () => ({
  setMemberProfile: (...args: unknown[]) => setMemberProfile(...args),
}))

const { syncLocaleWithProfile } = await import('../localeSync')

function profile(preferredLanguage: string): MemberProfile {
  return {
    memberId: 1,
    displayName: 'Traveler',
    profileImageUrl: null,
    preferredLanguage,
    preferredCurrencyCode: null,
    onboardingRequired: false,
  }
}

describe('syncLocaleWithProfile', () => {
  beforeEach(() => {
    localStorage.clear()
    applyLocale.mockReset()
    updateMemberProfile.mockReset()
    setMemberProfile.mockReset()
    updateMemberProfile.mockResolvedValue(profile('ja'))
  })

  it('adopts the server locale when the visitor never chose one', async () => {
    await syncLocaleWithProfile(profile('vi'))

    expect(applyLocale).toHaveBeenCalledWith('vi', { persist: true })
    expect(updateMemberProfile).not.toHaveBeenCalled()
  })

  /*
   * 회귀: 서버가 NOT NULL DEFAULT 'en'이라 신규 가입자는 전부 en이다. 서버를 우선하면
   * 비인증 상태에서 일본어를 고른 사용자가 로그인하자마자 영어로 뒤집힌다.
   */
  it('keeps the explicit choice and pushes it to the server when they disagree', async () => {
    localStorage.setItem('nawa.locale', 'ja')

    await syncLocaleWithProfile(profile('en'))

    expect(applyLocale).toHaveBeenCalledWith('ja', { persist: true })
    expect(updateMemberProfile).toHaveBeenCalledWith({ preferredLanguage: 'ja' })
    expect(setMemberProfile).toHaveBeenCalledWith(profile('ja'))
  })

  it('does not call the server when the explicit choice already matches it', async () => {
    localStorage.setItem('nawa.locale', 'ja')

    await syncLocaleWithProfile(profile('ja'))

    expect(applyLocale).toHaveBeenCalledWith('ja', { persist: true })
    expect(updateMemberProfile).not.toHaveBeenCalled()
  })

  it('keeps the local locale and does not reject when the server refuses the update', async () => {
    localStorage.setItem('nawa.locale', 'ja')
    updateMemberProfile.mockRejectedValue(new Error('MEMBER-002'))

    await expect(syncLocaleWithProfile(profile('en'))).resolves.toBeUndefined()

    expect(applyLocale).toHaveBeenCalledWith('ja', { persist: true })
    expect(setMemberProfile).not.toHaveBeenCalled()
  })

  it('leaves the screen alone when the server reports an unsupported locale', async () => {
    await syncLocaleWithProfile(profile('ko'))

    expect(applyLocale).not.toHaveBeenCalled()
    expect(updateMemberProfile).not.toHaveBeenCalled()
  })
})
