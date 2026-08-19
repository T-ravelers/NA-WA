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

/**
 * 바이너리로 받는 요청은 실패했을 때 오류 본문까지 Blob으로 들어온다.
 *
 * 그대로 두면 봉투를 알아보지 못해 어떤 오류든 UNKNOWN이 된다. 글자로 풀어 되돌려야
 * 화면이 오류 코드로 분기할 수 있다. JSON이 아니면 손대지 않고 넘긴다.
 */
async function unwrapBlobErrorBody(error: unknown): Promise<void> {
  if (!axios.isAxiosError(error) || error.response === undefined) {
    return
  }

  const body: unknown = error.response.data

  if (!(body instanceof Blob)) {
    return
  }

  try {
    error.response.data = JSON.parse(await body.text())
  } catch {
    // 오류 본문이 JSON이 아니면 그대로 둔다. 정규화가 UNKNOWN으로 처리한다.
  }
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

    await unwrapBlobErrorBody(error)

    throw normalizeApiError(error)
  },
)
