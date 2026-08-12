import { beforeEach, describe, expect, it, vi } from 'vitest'

const post = vi.fn()

vi.mock('../httpClient', () => ({
  httpClient: { post: (...args: unknown[]) => post(...args) },
}))

const { requestSignOut, setSignedOutHandler } = await import('../sessionSignOut')
const { isSignOutBarrierActive } = await import('../signOutBarrier')

describe('sessionSignOut', () => {
  beforeEach(() => {
    localStorage.clear()
    post.mockReset()
    setSignedOutHandler(() => undefined)
  })

  it('ends the server session and then runs the local handler', async () => {
    const onSignedOut = vi.fn()
    post.mockImplementation(() => {
      expect(isSignOutBarrierActive()).toBe(true)

      return Promise.resolve()
    })
    setSignedOutHandler(onSignedOut)

    await requestSignOut()

    expect(post).toHaveBeenCalledWith('/api/v1/auth/logout')
    expect(isSignOutBarrierActive()).toBe(false)
    expect(onSignedOut).toHaveBeenCalledOnce()
  })

  it('keeps the barrier and skips the success handler when the request fails', async () => {
    const failure = new Error('network unavailable')
    const onSignedOut = vi.fn()
    post.mockRejectedValue(failure)
    setSignedOutHandler(onSignedOut)

    await expect(requestSignOut()).rejects.toBe(failure)
    expect(isSignOutBarrierActive()).toBe(true)
    expect(onSignedOut).not.toHaveBeenCalled()
  })
})
