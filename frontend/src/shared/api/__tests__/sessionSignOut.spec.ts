import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const post = vi.fn()

vi.mock('../httpClient', () => ({
  httpClient: { post: (...args: unknown[]) => post(...args) },
}))

const { requestSignOut, setBeforeSignOutHook, setSignedOutHandler } =
  await import('../sessionSignOut')
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

describe('sessionSignOut before-sign-out hook', () => {
  beforeEach(() => {
    localStorage.clear()
    post.mockReset()
    post.mockResolvedValue(undefined)
    setSignedOutHandler(() => undefined)
  })

  afterEach(() => {
    vi.useRealTimers()
    setBeforeSignOutHook(undefined)
  })

  /**
   * 정리는 인증이 살아 있을 때만 된다. 장벽이 서면 인증 갱신이 막히고, 로그아웃 요청이 나가면
   * 쿠키가 만료되므로 둘보다 먼저여야 한다.
   */
  it('runs before the barrier goes up and before the logout request', async () => {
    const order: string[] = []
    setBeforeSignOutHook(() => {
      order.push(`hook(barrier=${isSignOutBarrierActive()})`)
      return Promise.resolve()
    })
    post.mockImplementation(() => {
      order.push('logout')
      return Promise.resolve()
    })

    await requestSignOut()

    expect(order).toEqual(['hook(barrier=false)', 'logout'])
  })

  /** 부가 정리가 실패했다고 사용자가 로그아웃하지 못하면 안 된다. */
  it.each([
    ['rejects', () => Promise.reject(new Error('401'))],
    [
      'throws',
      () => {
        throw new Error('broken')
      },
    ],
  ])('still signs out when the hook %s', async (_label, hook) => {
    const onSignedOut = vi.fn()
    setSignedOutHandler(onSignedOut)
    setBeforeSignOutHook(hook)

    await requestSignOut()

    expect(post).toHaveBeenCalledWith('/api/v1/auth/logout')
    expect(onSignedOut).toHaveBeenCalledOnce()
  })

  it('does not wait for a hook that never finishes', async () => {
    vi.useFakeTimers()
    const onSignedOut = vi.fn()
    setSignedOutHandler(onSignedOut)
    setBeforeSignOutHook(() => new Promise<void>(() => undefined))

    const signOut = requestSignOut()
    await vi.advanceTimersByTimeAsync(2_999)
    expect(post).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(1)
    await signOut

    expect(post).toHaveBeenCalledOnce()
    expect(onSignedOut).toHaveBeenCalledOnce()
  })

  /** 정리가 일찍 끝났는데 3초 타이머가 남아 있으면 로그아웃할 때마다 타이머가 쌓인다. */
  it('cancels its timeout once the hook has finished', async () => {
    vi.useFakeTimers()
    const setSpy = vi.spyOn(globalThis, 'setTimeout')
    const clearSpy = vi.spyOn(globalThis, 'clearTimeout')
    setBeforeSignOutHook(() => Promise.resolve())

    await requestSignOut()

    const timeouts = setSpy.mock.calls
      .map((call, index) => ({
        delay: call[1],
        timer: setSpy.mock.results[index]?.value as unknown,
      }))
      .filter(({ delay }) => delay === 3_000)
    expect(timeouts).toHaveLength(1)
    expect(clearSpy).toHaveBeenCalledWith(timeouts[0]?.timer)
  })

  it('signs out as before when no hook is set', async () => {
    await requestSignOut()

    expect(post).toHaveBeenCalledOnce()
  })
})
