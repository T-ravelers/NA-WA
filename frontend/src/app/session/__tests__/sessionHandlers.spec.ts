import { beforeEach, describe, expect, it, vi } from 'vitest'

const clearQueries = vi.fn()
const clearMemberProfile = vi.fn()
const clearReturnPath = vi.fn()
const replace = vi.fn()
const currentRoute = { value: { path: '/wallet', fullPath: '/wallet?tab=history' } }

vi.mock('@/app/query/client', () => ({
  queryClient: { clear: () => clearQueries() },
}))

vi.mock('@/app/router', () => ({
  router: { currentRoute, replace: (...args: unknown[]) => replace(...args) },
}))

vi.mock('@/features/auth/model/returnPath', () => ({
  clearReturnPath: () => clearReturnPath(),
}))

vi.mock('@/features/member/model/memberQueries', () => ({
  clearMemberProfile: () => clearMemberProfile(),
}))

const { handleSessionExpired, handleSignedOut } = await import('../sessionHandlers')

describe('sessionHandlers', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    currentRoute.value = { path: '/wallet', fullPath: '/wallet?tab=history' }
  })

  it('clears every query and preserves the protected route after session expiry', () => {
    handleSessionExpired()

    expect(clearMemberProfile).toHaveBeenCalledOnce()
    expect(clearQueries).toHaveBeenCalledOnce()
    expect(replace).toHaveBeenCalledWith({
      path: '/sign-in',
      query: { returnPath: '/wallet?tab=history' },
    })
  })

  it('does not redirect again from an authentication route', () => {
    currentRoute.value = { path: '/sign-in', fullPath: '/sign-in' }

    handleSessionExpired()

    expect(clearQueries).toHaveBeenCalledOnce()
    expect(replace).not.toHaveBeenCalled()
  })

  it('clears cached data and stale return paths after explicit sign-out', () => {
    handleSignedOut()

    expect(clearMemberProfile).toHaveBeenCalledOnce()
    expect(clearQueries).toHaveBeenCalledOnce()
    expect(clearReturnPath).toHaveBeenCalledOnce()
    expect(replace).toHaveBeenCalledWith({ path: '/sign-in' })
  })
})
