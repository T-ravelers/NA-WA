import type { AxiosInstance, InternalAxiosRequestConfig } from 'axios'

import { clearCsrfToken } from './csrf'

const REFRESH_ENDPOINT = '/api/v1/auth/refresh'

/**
 * 갱신을 시도하지 않는 경로.
 *
 * 갱신 자체와 로그아웃만 제외한다. 세션 조회(`/members/me`)는 제외하지 않는다. access
 * token TTL이 15분이라 만료 직후 `/members/me`가 401을 받는 것이 가장 흔한 갱신 시점이고,
 * 여기서 갱신하지 않으면 유효한 refresh 쿠키가 있어도 사용자가 로그인 화면으로 밀려난다.
 */
const NON_RECOVERABLE_PATHS = [REFRESH_ENDPOINT, '/api/v1/auth/logout']

declare module 'axios' {
  interface AxiosRequestConfig {
    /**
     * 갱신에 실패해도 세션 만료 처리를 실행하지 않는다.
     *
     * 라우터 guard의 세션 확인처럼 화면 이동을 호출자가 직접 결정하는 요청에 쓴다.
     * 갱신 시도와 재시도는 그대로 수행한다.
     */
    suppressSessionExpiredRedirect?: boolean
  }
}

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
 *
 * 이 요청은 POST이므로 CSRF 헤더가 필요하다. 헤더를 생략하면 백엔드가 403 AUTH-005로
 * 거부해 갱신이 항상 실패한다. 토큰 조회 요청 자체는 GET이라 재진입이 일어나지 않는다.
 */
async function refreshOnce(client: AxiosInstance): Promise<boolean> {
  refreshInFlight ??= (async () => {
    try {
      await client.post(REFRESH_ENDPOINT)

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
 * 다만 `suppressSessionExpiredRedirect`가 켜진 요청은 만료 처리를 호출자에게 맡긴다.
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

    if (config.suppressSessionExpiredRedirect !== true) {
      onSessionExpired()
    }

    return null
  }

  config.__sessionRetried = true

  return client.request(config)
}
