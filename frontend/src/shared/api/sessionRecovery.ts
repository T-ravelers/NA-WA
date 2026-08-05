import type { AxiosInstance, InternalAxiosRequestConfig } from 'axios'

import { clearCsrfToken } from './csrf'

const REFRESH_ENDPOINT = '/api/v1/auth/refresh'

/** 갱신을 시도하지 않는 경로. 갱신 자체와 세션 조회는 재시도 대상이 아니다. */
const NON_RECOVERABLE_PATHS = [REFRESH_ENDPOINT, '/api/v1/auth/logout', '/api/v1/auth/me']

type RetriableConfig = InternalAxiosRequestConfig & { __sessionRetried?: boolean }

let refreshInFlight: Promise<boolean> | null = null

type SessionExpiredHandler = () => void

let onSessionExpired: SessionExpiredHandler = () => {
  // 기본값은 아무것도 하지 않는다. app 계층이 등록한다.
}

/**
 * 세션이 완전히 끊겼을 때 실행할 동작을 등록한다.
 *
 * shared는 router와 feature를 import하지 않는다. 화면 이동과 캐시 정리는 app 계층이
 * 이 훅으로 주입한다.
 */
export function setSessionExpiredHandler(handler: SessionExpiredHandler): void {
  onSessionExpired = handler
}

function isRecoverable(config: RetriableConfig | undefined): config is RetriableConfig {
  if (config === undefined || config.__sessionRetried === true) {
    return false
  }

  const url = config.url ?? ''

  return !NON_RECOVERABLE_PATHS.some((path) => url.includes(path))
}

/**
 * access token을 1회 갱신한다.
 *
 * 여러 요청이 동시에 401을 받아도 갱신은 한 번만 실행하고 결과를 공유한다.
 * 이 단일 비행이 없으면 화면 진입 시 동시에 뜬 요청 수만큼 refresh가 실행되고,
 * 백엔드의 refresh token 회전·재사용 감지(AUTH-002)에 걸려 세션이 폐기된다.
 */
async function refreshOnce(client: AxiosInstance): Promise<boolean> {
  refreshInFlight ??= (async () => {
    try {
      await client.post(REFRESH_ENDPOINT, undefined, {
        headers: { 'X-Skip-Csrf': 'true' },
      })

      return true
    } catch {
      return false
    } finally {
      refreshInFlight = null
    }
  })()

  return refreshInFlight
}

/** 테스트에서 모듈 상태를 초기화한다. */
export function resetSessionRecovery(): void {
  refreshInFlight = null
}

/**
 * 401을 받은 요청을 갱신 후 재시도한다.
 *
 * 갱신에 실패하면 세션 만료 처리를 실행하고 원래 오류를 그대로 올려보낸다.
 * 재시도 대상이 아니면 `null`을 돌려준다.
 */
export async function recoverAndRetry(
  client: AxiosInstance,
  config: RetriableConfig | undefined,
): Promise<ReturnType<AxiosInstance> | null> {
  if (!isRecoverable(config)) {
    return null
  }

  const refreshed = await refreshOnce(client)

  if (!refreshed) {
    clearCsrfToken()
    onSessionExpired()

    return null
  }

  config.__sessionRetried = true

  return client.request(config)
}
