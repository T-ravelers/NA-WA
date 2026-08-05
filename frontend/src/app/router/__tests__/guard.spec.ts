import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { RouteLocationNormalized } from 'vue-router'

const ensureAuthSession = vi.fn()

vi.mock('@/features/auth/model/authQueries', () => ({
  ensureAuthSession: () => ensureAuthSession(),
}))

const { authGuard } = await import('../guard')

function routeTo(fullPath: string, meta: RouteLocationNormalized['meta']): RouteLocationNormalized {
  return { fullPath, meta } as RouteLocationNormalized
}

const from = routeTo('/', {})
const next = vi.fn()

describe('authGuard', () => {
  beforeEach(() => {
    ensureAuthSession.mockReset()
  })

  it('allows routes that declare no auth policy without checking the session', async () => {
    const result = await authGuard(routeTo('/auth/callback', {}), from, next)

    expect(result).toBe(true)
    expect(ensureAuthSession).not.toHaveBeenCalled()
  })

  it('sends an unauthenticated visitor to sign-in and keeps the return path', async () => {
    ensureAuthSession.mockResolvedValue(null)

    const result = await authGuard(routeTo('/wallet', { requiresAuth: true }), from, next)

    expect(result).toEqual({
      path: '/sign-in',
      query: { returnPath: '/wallet' },
    })
  })

  it('lets an authenticated visitor through a protected route', async () => {
    ensureAuthSession.mockResolvedValue({ memberId: 1 })

    const result = await authGuard(routeTo('/wallet', { requiresAuth: true }), from, next)

    expect(result).toBe(true)
  })

  it('keeps an authenticated visitor away from guest-only routes', async () => {
    ensureAuthSession.mockResolvedValue({ memberId: 1 })

    const result = await authGuard(routeTo('/sign-in', { guestOnly: true }), from, next)

    expect(result).toEqual({ path: '/explore' })
  })

  it('does not attach a return path when coming from the root route', async () => {
    ensureAuthSession.mockResolvedValue(null)

    const result = await authGuard(routeTo('/', { requiresAuth: true }), from, next)

    expect(result).toEqual({ path: '/sign-in', query: { returnPath: undefined } })
  })
})
