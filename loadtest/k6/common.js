import http from 'k6/http'
import { check, fail } from 'k6'

/**
 * 두 시나리오가 공유하는 것들.
 *
 * 부하 테스트에서 조용히 틀리기 쉬운 세 가지를 여기 모아 둔다.
 *   1. 로그인 — OAuth를 못 타므로 테스트 전용 경로를 쓴다
 *   2. 멱등성 키 — VU끼리 겹치면 서버가 실제 작업 대신 멱등 응답을 돌려준다
 *   3. 체크 — 실패한 요청도 응답은 오므로, 상태 코드를 안 보면 "다 성공"으로 보인다
 */

export const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:9080'

/**
 * 모든 요청에 붙이는 Origin.
 *
 * 백엔드가 `AUTH_ALLOWED_ORIGINS`에 없는 출처를 `AUTH-006`(403)으로 막는다. 브라우저는
 * 알아서 붙이지만 k6는 안 붙이므로 **로그인부터 403**이 난다. 응답이 오긴 해서
 * 상태 코드를 확인하지 않으면 부하는 걸리는데 전부 실패인 상태로 측정이 끝난다.
 *
 * `.env.loadtest`의 `AUTH_ALLOWED_ORIGINS`와 같은 값이어야 한다.
 */
export const ORIGIN = __ENV.ORIGIN || 'http://127.0.0.1:5173'

/** 공통 헤더. JSON 본문을 보내는 요청은 여기에 Content-Type을 더한다. */
export const baseHeaders = { Origin: ORIGIN }

/** 서버가 내려주는 이름. `GET /api/v1/auth/csrf` 응답의 headerName 과 같아야 한다. */
const CSRF_HEADER_NAME = 'X-XSRF-TOKEN'
const CSRF_COOKIE_NAME = 'XSRF-TOKEN'

export const jsonHeaders = { Origin: ORIGIN, 'Content-Type': 'application/json' }

/** 백엔드의 loadtest.login-secret 과 같아야 한다. */
const LOGIN_SECRET = __ENV.LOADTEST_LOGIN_SECRET

if (!LOGIN_SECRET) {
  fail('LOADTEST_LOGIN_SECRET 이 없습니다. .env.loadtest 를 읽어 환경변수로 넘기세요.')
}

/**
 * 이 실행을 다른 실행과 구분하는 값.
 *
 * 멱등성 키에 섞는다. 없으면 어제 돌린 키와 겹쳐서, 서버가 결제를 다시 하지 않고
 * 저장해 둔 응답을 돌려준다. 그러면 빠른 응답이 나오지만 그건 결제 성능이 아니다.
 */
const RUN_ID = __ENV.RUN_ID || `${Date.now()}`

/**
 * 이번이 몇 번째 실행인가.
 *
 * 참여한 약속은 재참여가 막히고(ALREADY_JOINED), 출석을 확정한 약속은 COMPLETED가
 * 되어 두 번째 확정을 받지 않는다. 시드가 `RUNS`회차분을 미리 깔아 두므로 실행할
 * 때마다 이 값을 1씩 올리면 볼륨을 초기화하지 않고 다시 돌릴 수 있다.
 *
 * 시드의 `RUNS`보다 큰 값을 주면 없는 약속을 불러 404가 난다.
 */
export const RUN_INDEX = Number(__ENV.RUN_INDEX || 1)

/** 회차 사이의 ID 간격. 시드의 `@run_stride`와 같아야 한다. */
export const RUN_STRIDE = Number(__ENV.RUN_STRIDE || 10000)

/** 회차 블록의 시작 번호를 구한다. */
export function runScopedBase(base) {
  return base + (RUN_INDEX - 1) * RUN_STRIDE
}

/** VU·iteration·실행을 모두 섞어 전역에서 겹치지 않는 키를 만든다. */
export function idempotencyKey(label) {
  return `${RUN_ID}-${label}-vu${__VU}-iter${__ITER}`
}

/**
 * 테스트 전용 로그인.
 *
 * 소셜 로그인은 브라우저 동의 화면을 사람이 거쳐야 완료돼서 k6가 통과할 수 없다.
 * 이 경로는 `-Ploadtest` 로 빌드한 산출물에만 존재한다(운영 이미지에는 클래스가 없다).
 *
 * 실제 사용자는 authorization·callback 두 번을 더 타므로, k6가 만드는 로그인 부하는
 * 시나리오 문서의 계산보다 요청 하나만큼 적다. 자세한 것은 README 참고.
 *
 * @param {http.CookieJar} jar 이 세션의 쿠키를 담을 자루
 */
export function login(memberId, jar) {
  const response = http.post(
    `${BASE_URL}/internal/loadtest/login`,
    JSON.stringify({ secret: LOGIN_SECRET, memberId }),
    { headers: jsonHeaders, jar, tags: { name: 'test-login' } },
  )

  check(response, { 'login 200': (r) => r.status === 200 })

  return response
}

/**
 * 실제 로그인 첫 요청을 재현한다. 리다이렉트를 따라가면 Google로 외부 통신하므로
 * 302까지만 확인한다. callback은 test-login이 대신한다.
 */
export function beginOAuthLogin(jar) {
  const provider = __ENV.OAUTH_PROVIDER || 'google'
  const response = http.get(
    `${BASE_URL}/api/v1/auth/oauth2/authorization/${provider}`,
    {
      headers: baseHeaders,
      jar,
      redirects: 0,
      tags: { name: 'oauth-authorization' },
    },
  )

  check(response, { 'oauth authorization 302': (r) => r.status === 302 })
  return response
}

/**
 * 첫 쓰기 요청 전에 CSRF 쿠키와 헤더 값을 받는다. CookieCsrfTokenRepository는
 * 쿠키만으로는 통과시키지 않으므로 응답의 headerName/token도 이후 요청에 붙여야 한다.
 */
export function issueCsrfHeaders(jar) {
  const response = expectOk(
    http.get(`${BASE_URL}/api/v1/auth/csrf`, {
      headers: baseHeaders,
      jar,
      tags: { name: 'csrf' },
    }),
    'csrf',
  )
  const csrf = dataOf(response)

  if (!csrf || !csrf.token || !csrf.headerName) {
    fail('CSRF 토큰 또는 헤더 이름을 받지 못했습니다.')
  }

  return { [csrf.headerName]: csrf.token }
}

/**
 * 쓰기 요청에 붙일 헤더.
 *
 * **토큰은 쿠키에서 매번 새로 읽는다.** 서버가 쓰기 요청 하나를 처리할 때마다
 * `XSRF-TOKEN` 쿠키를 새 값으로 갈아 끼우기 때문이다. 처음 받은 값을 계속 쓰면
 * 첫 POST만 통과하고 그다음부터 전부 `AUTH-005`(403)가 난다 — 부하는 정상으로
 * 걸려서 상태 코드를 안 보면 눈치채기 어렵다.
 *
 * 프런트엔드는 403을 받고 토큰을 다시 받아 재시도하는 방식으로 이 회전을 흡수한다
 * (`shared/api/csrf.ts`). k6는 쿠키 자를 직접 볼 수 있으니 재시도 없이 최신 값을 읽는다.
 *
 * @param {http.CookieJar} jar 로그인·CSRF 응답의 쿠키가 담긴 자루
 * @param {boolean} json 본문이 JSON이면 Content-Type을 더한다
 */
export function withCsrf(jar, json = false) {
  const base = json ? jsonHeaders : baseHeaders
  const cookies = jar.cookiesForURL(`${BASE_URL}/`)
  const token = cookies[CSRF_COOKIE_NAME] ? cookies[CSRF_COOKIE_NAME][0] : null

  if (!token) {
    // 토큰 없이 보내면 403이 날 뿐이라 여기서 멈춰 원인을 분명히 한다.
    fail(`${CSRF_COOKIE_NAME} 쿠키가 없습니다. issueCsrfHeaders 를 먼저 호출했는지 확인하세요.`)
  }

  return { ...base, [CSRF_HEADER_NAME]: token }
}

/**
 * 상태 코드를 확인하고 JSON 본문을 돌려준다.
 *
 * 4xx·5xx도 응답은 오기 때문에, 확인하지 않으면 부하는 걸리는데 아무것도 성공하지
 * 않은 상태로 "측정 완료"가 된다. 시드 데이터가 모자랄 때 실제로 이렇게 된다.
 */
export function expectOk(response, name) {
  const ok = check(response, { [`${name} 2xx`]: (r) => r.status >= 200 && r.status < 300 })

  if (!ok) {
    // 응답 본문·쿠키·헤더는 로그에 남기지 않고 공개 계약인 오류 코드만
    // 남긴다. 시드 누락과 요청 계약 오류를 분리하는 데 필요하다.
    let errorCode = 'UNKNOWN'
    try {
      const body = response.json()
      errorCode = body && body.error && body.error.code ? body.error.code : errorCode
    } catch (error) {
      // JSON이 아닌 오류에도 상태 코드는 아래 로그에 남는다.
    }
    console.error(`${name} failed: status=${response.status} code=${errorCode}`)
    return null
  }

  try {
    return response.json()
  } catch (error) {
    return null
  }
}

/** 성공 응답 봉투(ApiResponse)에서 data 를 꺼낸다. */
export function dataOf(body) {
  return body && typeof body === 'object' && 'data' in body ? body.data : body
}
