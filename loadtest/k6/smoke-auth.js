import http from 'k6/http'
import { check } from 'k6'
import {
  BASE_URL,
  baseHeaders,
  issueCsrfHeaders,
  login,
} from './common.js'

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    checks: ['rate==1'],
  },
}

/**
 * 시드 없이 실행하는 연결 진단입니다.
 *
 * test-login은 DB를 조회하지 않고 토큰을 발급하므로 임의 회원 번호를 쓸 수 있습니다.
 * members/me가 200이거나 MEMBER-001까지 도달하면 인증 필터는 통과한 것입니다.
 * 전체 사용자 흐름의 스모크 테스트는 아니며 시드가 준비된 뒤 별도로 실행합니다.
 */
export default function () {
  const jar = new http.CookieJar()
  login(Number(__ENV.SMOKE_MEMBER_ID || 999999999), jar)

  const me = http.get(`${BASE_URL}/api/v1/members/me`, {
    headers: baseHeaders,
    jar,
    responseCallback: http.expectedStatuses(200, 404),
  })
  check(me, {
    'access cookie reaches authenticated API': (response) => {
      if (response.status === 200) return true
      try {
        return response.json().error.code === 'MEMBER-001'
      } catch (error) {
        return false
      }
    },
  })

  const csrfHeaders = issueCsrfHeaders(jar)
  check(csrfHeaders, {
    'csrf header issued': (headers) => Object.keys(headers).length === 1,
  })
}
