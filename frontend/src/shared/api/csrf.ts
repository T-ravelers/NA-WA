import type { AxiosInstance, InternalAxiosRequestConfig } from 'axios'

import { isApiResponse } from './apiResponse'

interface CsrfToken {
  token: string
  headerName: string
}

const CSRF_ENDPOINT = '/api/v1/auth/csrf'

const MUTATING_METHODS = new Set(['post', 'put', 'patch', 'delete'])

let cachedToken: CsrfToken | null = null
let inFlight: Promise<CsrfToken | null> | null = null

function isMutating(config: InternalAxiosRequestConfig): boolean {
  return MUTATING_METHODS.has((config.method ?? 'get').toLowerCase())
}

/**
 * CSRF 토큰을 가져온다.
 *
 * 동시에 여러 요청이 시작돼도 조회는 한 번만 수행한다.
 * 토큰 조회 자체가 실패하면 요청을 막지 않고 그대로 진행시킨다. 실패 판정은
 * 서버가 내려주는 오류 코드에 맡긴다.
 */
async function loadCsrfToken(client: AxiosInstance): Promise<CsrfToken | null> {
  if (cachedToken !== null) {
    return cachedToken
  }

  inFlight ??= (async () => {
    try {
      const response = await client.get<unknown>(CSRF_ENDPOINT, {
        // 인터셉터 재진입을 막는다.
        headers: { 'X-Skip-Csrf': 'true' },
      })
      const body: unknown = response.data
      const payload = isApiResponse(body) ? body.data : body

      if (
        typeof payload === 'object' &&
        payload !== null &&
        'token' in payload &&
        'headerName' in payload
      ) {
        cachedToken = payload as CsrfToken

        return cachedToken
      }

      return null
    } catch {
      return null
    } finally {
      inFlight = null
    }
  })()

  return inFlight
}

export function clearCsrfToken(): void {
  cachedToken = null
}

export async function attachCsrfToken(
  client: AxiosInstance,
  config: InternalAxiosRequestConfig,
): Promise<InternalAxiosRequestConfig> {
  if (!isMutating(config) || config.headers.get('X-Skip-Csrf') !== undefined) {
    config.headers.delete('X-Skip-Csrf')

    return config
  }

  const token = await loadCsrfToken(client)

  if (token !== null) {
    config.headers.set(token.headerName, token.token)
  }

  return config
}
