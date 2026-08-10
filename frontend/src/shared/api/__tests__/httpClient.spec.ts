import axios, { AxiosError, type AxiosAdapter, type AxiosResponse } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

interface StubReply {
  status: number
  body?: unknown
}

interface RecordedCall {
  method: string
  url: string
  csrfHeader: string | undefined
}

/**
 * 커스텀 adapter로 서버를 대신한다.
 *
 * axios는 adapter를 직접 지정하면 상태 코드 판정을 하지 않으므로 여기서 처리한다.
 */
function createStubAdapter(handlers: Record<string, StubReply[]>) {
  const calls: RecordedCall[] = []

  const adapter: AxiosAdapter = (config) => {
    const method = (config.method ?? 'get').toLowerCase()
    const url = config.url ?? ''
    const key = `${method} ${url}`

    calls.push({
      method,
      url,
      csrfHeader: config.headers.get('X-CSRF-TOKEN') as string | undefined,
    })

    const queue = handlers[key]
    const reply = (queue !== undefined && queue.length > 1 ? queue.shift() : queue?.[0]) ?? {
      status: 404,
    }

    const response: AxiosResponse = {
      data: reply.body,
      status: reply.status,
      statusText: '',
      headers: {},
      config,
    }

    if (reply.status >= 200 && reply.status < 300) {
      return Promise.resolve(response)
    }

    return Promise.reject(
      new AxiosError('stub failure', String(reply.status), config, {}, response),
    )
  }

  return { adapter, calls }
}

function countCalls(calls: RecordedCall[], method: string, url: string): number {
  return calls.filter((call) => call.method === method && call.url === url).length
}

const authRequired = {
  success: false,
  error: { code: 'AUTH-003', message: '인증이 필요합니다' },
}

describe('httpClient', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.stubEnv('VITE_API_BASE_URL', 'https://api.example.test')
  })

  afterEach(() => {
    vi.unstubAllEnvs()
  })

  async function loadClient(handlers: Record<string, StubReply[]> = {}) {
    const [{ httpClient }, sessionRecovery] = await Promise.all([
      import('../httpClient'),
      import('../sessionRecovery'),
    ])

    sessionRecovery.resetSessionRecovery()

    const { adapter, calls } = createStubAdapter(handlers)

    httpClient.defaults.adapter = adapter

    return { httpClient, sessionRecovery, calls }
  }

  it('uses the shared API request defaults', async () => {
    const { httpClient } = await loadClient()

    expect(httpClient.defaults.baseURL).toBe('https://api.example.test')
    expect(httpClient.defaults.timeout).toBe(10_000)
    expect(httpClient.defaults.withCredentials).toBe(true)
  })

  it('unwraps the ApiResponse envelope so callers only see data', async () => {
    const { httpClient } = await loadClient({
      'get /api/v1/wallet': [{ status: 200, body: { success: true, data: { balance: '1000' } } }],
    })

    const response = await httpClient.get('/api/v1/wallet')

    expect(response.data).toEqual({ balance: '1000' })
  })

  it('turns a failed envelope into a normalized error with a message key', async () => {
    const { httpClient } = await loadClient({
      'get /api/v1/wallet': [{ status: 200, body: authRequired }],
    })

    await expect(httpClient.get('/api/v1/wallet')).rejects.toMatchObject({
      code: 'AUTH-003',
      messageKey: 'auth.errorCode.AUTH-003',
    })
  })

  it('does not expose the raw server message as the display message', async () => {
    const { httpClient } = await loadClient({
      'get /api/v1/wallet': [{ status: 200, body: authRequired }],
    })

    await expect(httpClient.get('/api/v1/wallet')).rejects.toMatchObject({
      messageKey: 'auth.errorCode.AUTH-003',
    })
  })

  it('normalizes a transport failure instead of leaking the axios error', async () => {
    const { httpClient } = await loadClient({
      'get /api/v1/wallet': [{ status: 500 }],
    })

    await expect(httpClient.get('/api/v1/wallet')).rejects.toMatchObject({
      code: 'UNKNOWN',
      messageKey: 'error.unknown',
      status: 500,
    })
  })

  it('refreshes once on 401 and retries the original request', async () => {
    const { httpClient, calls } = await loadClient({
      'get /api/v1/wallet': [
        { status: 401, body: authRequired },
        { status: 200, body: { success: true, data: { balance: '10' } } },
      ],
      'post /api/v1/auth/refresh': [{ status: 200, body: { success: true } }],
    })

    const response = await httpClient.get('/api/v1/wallet')

    expect(response.data).toEqual({ balance: '10' })
    expect(countCalls(calls, 'post', '/api/v1/auth/refresh')).toBe(1)
    expect(countCalls(calls, 'get', '/api/v1/wallet')).toBe(2)
  })

  // 회귀: refresh는 POST라 CSRF 헤더가 없으면 백엔드가 403 AUTH-005로 거부한다.
  it('sends the CSRF header with the refresh request', async () => {
    const { httpClient, calls } = await loadClient({
      'get /api/v1/auth/csrf': [
        {
          status: 200,
          body: { success: true, data: { token: 'csrf-token', headerName: 'X-CSRF-TOKEN' } },
        },
      ],
      'get /api/v1/wallet': [
        { status: 401, body: authRequired },
        { status: 200, body: { success: true, data: {} } },
      ],
      'post /api/v1/auth/refresh': [{ status: 200, body: { success: true } }],
    })

    await httpClient.get('/api/v1/wallet')

    const refresh = calls.find((call) => call.url === '/api/v1/auth/refresh')

    expect(refresh?.csrfHeader).toBe('csrf-token')
  })

  it('refreshes only once when several requests fail with 401 at the same time', async () => {
    const { httpClient, calls } = await loadClient({
      'get /api/v1/wallet': [
        { status: 401, body: authRequired },
        { status: 200, body: { success: true, data: {} } },
      ],
      'get /api/v1/explore/events': [
        { status: 401, body: authRequired },
        { status: 200, body: { success: true, data: {} } },
      ],
      'post /api/v1/auth/refresh': [{ status: 200, body: { success: true } }],
    })

    await Promise.all([httpClient.get('/api/v1/wallet'), httpClient.get('/api/v1/explore/events')])

    expect(countCalls(calls, 'post', '/api/v1/auth/refresh')).toBe(1)
  })

  it.each([
    ['AUTH-016', 'This account is suspended'],
    ['AUTH-017', 'This account has been withdrawn'],
  ])(
    'refreshes only once and expires the session when refresh fails with %s',
    async (code, message) => {
      const inactiveMember = {
        success: false,
        error: { code, message },
      }
      const { httpClient, sessionRecovery, calls } = await loadClient({
        'get /api/v1/auth/csrf': [
          {
            status: 200,
            body: { success: true, data: { token: 'csrf-token', headerName: 'X-CSRF-TOKEN' } },
          },
        ],
        'get /api/v1/wallet': [{ status: 401, body: authRequired }],
        'get /api/v1/explore/events': [{ status: 401, body: authRequired }],
        'post /api/v1/auth/refresh': [{ status: 403, body: inactiveMember }],
      })
      const onExpired = vi.fn()

      sessionRecovery.setSessionExpiredHandler(onExpired)

      const results = await Promise.allSettled([
        httpClient.get('/api/v1/wallet'),
        httpClient.get('/api/v1/explore/events'),
      ])

      expect(results).toHaveLength(2)
      expect(results.every((result) => result.status === 'rejected')).toBe(true)
      expect(countCalls(calls, 'post', '/api/v1/auth/refresh')).toBe(1)
      expect(countCalls(calls, 'get', '/api/v1/auth/csrf')).toBe(1)
      expect(onExpired).toHaveBeenCalled()
    },
  )

  it('runs the session expired handler when the refresh fails', async () => {
    const { httpClient, sessionRecovery } = await loadClient({
      'get /api/v1/wallet': [{ status: 401, body: authRequired }],
      'post /api/v1/auth/refresh': [{ status: 401 }],
    })
    const onExpired = vi.fn()

    sessionRecovery.setSessionExpiredHandler(onExpired)

    await expect(httpClient.get('/api/v1/wallet')).rejects.toMatchObject({ code: 'AUTH-003' })
    expect(onExpired).toHaveBeenCalledTimes(1)
  })

  // 회귀: 세션 조회가 복구 대상에서 빠져 있으면 access token 만료마다 로그아웃된다.
  it('refreshes and retries the session probe when it returns 401', async () => {
    const { httpClient, calls } = await loadClient({
      'get /api/v1/members/me': [
        { status: 401, body: authRequired },
        { status: 200, body: { success: true, data: { memberId: 1 } } },
      ],
      'post /api/v1/auth/refresh': [{ status: 200, body: { success: true } }],
    })

    const response = await httpClient.get('/api/v1/members/me')

    expect(response.data).toEqual({ memberId: 1 })
    expect(countCalls(calls, 'post', '/api/v1/auth/refresh')).toBe(1)
    expect(countCalls(calls, 'get', '/api/v1/members/me')).toBe(2)
  })

  it('leaves the redirect to the caller when the request suppresses it', async () => {
    const { httpClient, sessionRecovery } = await loadClient({
      'get /api/v1/members/me': [{ status: 401, body: authRequired }],
      'post /api/v1/auth/refresh': [{ status: 401 }],
    })
    const onExpired = vi.fn()

    sessionRecovery.setSessionExpiredHandler(onExpired)

    await expect(
      httpClient.get('/api/v1/members/me', { suppressSessionExpiredRedirect: true }),
    ).rejects.toMatchObject({ code: 'AUTH-003' })

    // 갱신은 시도하되 화면 이동은 라우터 guard가 결정한다.
    expect(onExpired).not.toHaveBeenCalled()
  })

  it('still runs the redirect for requests that do not suppress it', async () => {
    const { httpClient, sessionRecovery } = await loadClient({
      'get /api/v1/members/me': [{ status: 401, body: authRequired }],
      'post /api/v1/auth/refresh': [{ status: 401 }],
    })
    const onExpired = vi.fn()

    sessionRecovery.setSessionExpiredHandler(onExpired)

    await expect(httpClient.get('/api/v1/members/me')).rejects.toMatchObject({ code: 'AUTH-003' })
    expect(onExpired).toHaveBeenCalledTimes(1)
  })

  it('does not try to recover the refresh endpoint itself', async () => {
    const { httpClient, calls } = await loadClient({
      'post /api/v1/auth/refresh': [{ status: 401 }],
    })

    await expect(httpClient.post('/api/v1/auth/refresh')).rejects.toBeDefined()
    expect(countCalls(calls, 'post', '/api/v1/auth/refresh')).toBe(1)
  })

  it('attaches the CSRF header to mutating requests only', async () => {
    const { httpClient, calls } = await loadClient({
      'get /api/v1/auth/csrf': [
        {
          status: 200,
          body: { success: true, data: { token: 'csrf-token', headerName: 'X-CSRF-TOKEN' } },
        },
      ],
      'post /api/v1/auth/logout': [{ status: 200, body: { success: true } }],
      'get /api/v1/wallet': [{ status: 200, body: { success: true, data: {} } }],
    })

    await httpClient.post('/api/v1/auth/logout')
    await httpClient.get('/api/v1/wallet')

    const logout = calls.find((call) => call.url === '/api/v1/auth/logout')
    const wallet = calls.find((call) => call.url === '/api/v1/wallet')

    expect(logout?.csrfHeader).toBe('csrf-token')
    expect(wallet?.csrfHeader).toBeUndefined()
  })

  it('refreshes the CSRF token and retries once after AUTH-005', async () => {
    const invalidCsrf = {
      success: false,
      error: { code: 'AUTH-005', message: 'invalid csrf token' },
    }
    const { httpClient, calls } = await loadClient({
      'get /api/v1/auth/csrf': [
        {
          status: 200,
          body: { success: true, data: { token: 'csrf-token', headerName: 'X-CSRF-TOKEN' } },
        },
        {
          status: 200,
          body: { success: true, data: { token: 'fresh-csrf-token', headerName: 'X-CSRF-TOKEN' } },
        },
      ],
      'post /api/v1/topups/preview': [
        { status: 403, body: invalidCsrf },
        { status: 200, body: { success: true, data: { amount: '50000' } } },
      ],
    })

    const response = await httpClient.post('/api/v1/topups/preview', {
      amount: 50000,
      method: 'STRIPE_CARD',
      currency: 'KRW',
    })

    expect(response.data).toEqual({ amount: '50000' })
    expect(countCalls(calls, 'get', '/api/v1/auth/csrf')).toBe(2)
    expect(countCalls(calls, 'post', '/api/v1/topups/preview')).toBe(2)
  })

  it('keeps axios importable for consumers that need error helpers', () => {
    expect(axios.isAxiosError(new AxiosError('x'))).toBe(true)
  })
})
