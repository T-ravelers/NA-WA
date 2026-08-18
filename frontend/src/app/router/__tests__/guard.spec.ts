import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { RouteLocationNormalized } from 'vue-router'

const ensureMemberProfile = vi.fn()
const syncLocaleWithProfile = vi.fn()

vi.mock('@/features/member/model/memberQueries', () => ({
  ensureMemberProfile: () => ensureMemberProfile(),
}))

vi.mock('@/features/member/model/localeSync', () => ({
  syncLocaleWithProfile: (...args: unknown[]) => syncLocaleWithProfile(...args),
}))

const { authGuard } = await import('../guard')

function routeTo(fullPath: string, meta: RouteLocationNormalized['meta']): RouteLocationNormalized {
  const [path] = fullPath.split('?')

  return { fullPath, path, meta } as RouteLocationNormalized
}

const from = routeTo('/', {})
const next = vi.fn()

describe('authGuard', () => {
  beforeEach(() => {
    localStorage.clear()
    ensureMemberProfile.mockReset()
    syncLocaleWithProfile.mockReset()
    syncLocaleWithProfile.mockResolvedValue(undefined)
  })

  it('allows routes that declare no auth policy without checking the session', async () => {
    const result = await authGuard(routeTo('/auth/callback', {}), from, next)

    expect(result).toBe(true)
    expect(ensureMemberProfile).not.toHaveBeenCalled()
  })

  it('blocks protected routes without probing the session while sign-out is uncertain', async () => {
    localStorage.setItem('nawa.auth.signOutBarrier', 'active')

    const result = await authGuard(routeTo('/wallet', { requiresAuth: true }), from, next)

    expect(result).toEqual({ path: '/sign-in' })
    expect(ensureMemberProfile).not.toHaveBeenCalled()
  })

  it('allows the sign-in route without probing the session while the barrier is active', async () => {
    localStorage.setItem('nawa.auth.signOutBarrier', 'active')

    const result = await authGuard(routeTo('/sign-in', { guestOnly: true }), from, next)

    expect(result).toBe(true)
    expect(ensureMemberProfile).not.toHaveBeenCalled()
  })

  it('sends an unauthenticated visitor to sign-in and keeps the return path', async () => {
    ensureMemberProfile.mockResolvedValue(null)

    const result = await authGuard(routeTo('/wallet', { requiresAuth: true }), from, next)

    expect(result).toEqual({
      path: '/sign-in',
      query: { returnPath: '/wallet' },
    })
  })

  it('lets an authenticated visitor through a protected route', async () => {
    ensureMemberProfile.mockResolvedValue({ memberId: 1, preferredLanguage: 'en' })

    const result = await authGuard(routeTo('/wallet', { requiresAuth: true }), from, next)

    expect(result).toBe(true)
  })

  it('keeps an authenticated visitor away from guest-only routes', async () => {
    ensureMemberProfile.mockResolvedValue({ memberId: 1, preferredLanguage: 'en' })

    const result = await authGuard(routeTo('/sign-in', { guestOnly: true }), from, next)

    expect(result).toEqual({ path: '/explore' })
  })

  it('does not attach a return path when coming from the root route', async () => {
    ensureMemberProfile.mockResolvedValue(null)

    const result = await authGuard(routeTo('/', { requiresAuth: true }), from, next)

    expect(result).toEqual({ path: '/sign-in', query: { returnPath: undefined } })
  })

  it('syncs the locale once a session is confirmed', async () => {
    const profile = { memberId: 1, preferredLanguage: 'vi' }
    ensureMemberProfile.mockResolvedValue(profile)

    await authGuard(routeTo('/wallet', { requiresAuth: true }), from, next)

    expect(syncLocaleWithProfile).toHaveBeenCalledWith(profile)
  })

  it('does not sync the locale when there is no session', async () => {
    ensureMemberProfile.mockResolvedValue(null)

    await authGuard(routeTo('/wallet', { requiresAuth: true }), from, next)

    expect(syncLocaleWithProfile).not.toHaveBeenCalled()
  })

  // 로케일 저장은 부가 기능이다. 실패해도 사용자가 화면에 들어가지 못하면 안 된다.
  it('does not block navigation when locale sync fails', async () => {
    ensureMemberProfile.mockResolvedValue({ memberId: 1, preferredLanguage: 'vi' })
    syncLocaleWithProfile.mockRejectedValue(new Error('offline'))

    const result = await authGuard(routeTo('/wallet', { requiresAuth: true }), from, next)

    expect(result).toBe(true)
  })

  it('keeps a merchant account inside the merchant screen', async () => {
    ensureMemberProfile.mockResolvedValue({
      memberId: 1,
      preferredLanguage: 'en',
      accountType: 'MERCHANT',
    })

    const result = await authGuard(routeTo('/wallet', { requiresAuth: true }), from, next)

    expect(result).toEqual({ path: '/merchant' })
  })

  it('lets a merchant account through its own screen', async () => {
    ensureMemberProfile.mockResolvedValue({
      memberId: 1,
      preferredLanguage: 'en',
      accountType: 'MERCHANT',
    })

    const result = await authGuard(routeTo('/merchant', { requiresAuth: true }), from, next)

    expect(result).toBe(true)
  })

  // 소셜 로그인은 계정을 항상 TRAVELER로 만든다. 등록 전 가맹점주가 여기서 막히면 가입이 끊긴다.
  it('lets a not-yet-registered account reach the merchant screen', async () => {
    ensureMemberProfile.mockResolvedValue({
      memberId: 1,
      preferredLanguage: 'en',
      accountType: 'TRAVELER',
    })

    const result = await authGuard(routeTo('/merchant', { requiresAuth: true }), from, next)

    expect(result).toBe(true)
  })

  it('does not redirect a traveller away from customer screens', async () => {
    ensureMemberProfile.mockResolvedValue({
      memberId: 1,
      preferredLanguage: 'en',
      accountType: 'TRAVELER',
    })

    const result = await authGuard(routeTo('/wallet', { requiresAuth: true }), from, next)

    expect(result).toBe(true)
  })
})
