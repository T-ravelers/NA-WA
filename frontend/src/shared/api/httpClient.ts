import axios, { type AxiosError, type AxiosResponse } from 'axios'

import { normalizeApiError } from './apiError'
import { isApiResponse } from './apiResponse'
import { attachCsrfToken, clearCsrfToken } from './csrf'
import { validateResponseData } from './responseSchema'
import { recoverAndRetry } from './sessionRecovery'

const HTTP_TIMEOUT_MS = 10_000

type CsrfRetriableConfig = NonNullable<AxiosError['config']> & {
  __csrfRetried?: boolean
}

function isInvalidCsrfResponse(error: unknown): error is AxiosError {
  if (!axios.isAxiosError(error)) {
    return false
  }

  const body: unknown = error.response?.data

  return error.response?.status === 403 && isApiResponse(body) && body.error?.code === 'AUTH-005'
}

/**
 * 공통 HTTP 클라이언트.
 *
 * 인증은 HttpOnly 쿠키로만 오간다. 토큰을 localStorage나 Pinia에 저장하지 않으므로
 * `withCredentials`가 반드시 켜져 있어야 한다.
 *
 * 인터셉터가 처리하는 것:
 * 1. 변경 요청에 CSRF 헤더 부착
 * 2. `ApiResponse<T>` 봉투를 벗겨 화면이 `data`만 다루게 함
 * 3. 401을 받으면 갱신 1회 후 원 요청 재시도
 * 4. 모든 실패를 `NormalizedApiError` 하나로 통일
 */
export const httpClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: HTTP_TIMEOUT_MS,
  withCredentials: true,
})

httpClient.interceptors.request.use((config) => attachCsrfToken(httpClient, config))

httpClient.interceptors.response.use(
  (response: AxiosResponse<unknown>) => {
    const body: unknown = response.data

    if (!isApiResponse(body)) {
      return response
    }

    if (body.success) {
      validateResponseData(response.config, response.status, body.data)

      return { ...response, data: body.data }
    }

    throw normalizeApiError(
      new axios.AxiosError(
        body.error?.message ?? 'API request failed',
        body.error?.code,
        response.config,
        response.request,
        response as AxiosResponse,
      ),
    )
  },
  async (error: unknown) => {
    if (isInvalidCsrfResponse(error)) {
      const config = error.config as CsrfRetriableConfig | undefined

      if (config !== undefined && config.__csrfRetried !== true) {
        clearCsrfToken()
        config.__csrfRetried = true

        return httpClient.request(config)
      }
    }

    if (axios.isAxiosError(error) && error.response?.status === 401) {
      const retried = await recoverAndRetry(httpClient, error.config)

      if (retried !== null) {
        return retried
      }
    }

    throw normalizeApiError(error)
  },
)
