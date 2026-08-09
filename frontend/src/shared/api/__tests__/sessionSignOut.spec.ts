import { beforeEach, describe, expect, it, vi } from 'vitest'

const post = vi.fn()

vi.mock('../httpClient', () => ({
  httpClient: { post: (...args: unknown[]) => post(...args) },
}))

const { requestSignOut, setSignedOutHandler } = await import('../sessionSignOut')

describe('sessionSignOut', () => {
  beforeEach(() => {
    post.mockReset()
    setSignedOutHandler(() => undefined)
  })

  it('ends the server session and then runs the local handler', async () => {
    const onSignedOut = vi.fn()
    post.mockResolvedValue(undefined)
    setSignedOutHandler(onSignedOut)

    await requestSignOut()

    expect(post).toHaveBeenCalledWith('/api/v1/auth/logout')
    expect(onSignedOut).toHaveBeenCalledOnce()
  })

  it('runs the local handler even when the server request fails', async () => {
    const failure = new Error('network unavailable')
    const onSignedOut = vi.fn()
    post.mockRejectedValue(failure)
    setSignedOutHandler(onSignedOut)

    await expect(requestSignOut()).rejects.toBe(failure)
    expect(onSignedOut).toHaveBeenCalledOnce()
  })
})
