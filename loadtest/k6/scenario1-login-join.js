import http from 'k6/http'
import { group, sleep } from 'k6'
import {
  BASE_URL,
  baseHeaders,
  beginOAuthLogin,
  dataOf,
  expectOk,
  idempotencyKey,
  issueCsrfHeaders,
  login,
  runScopedBase,
  withCsrf,
} from './common.js'

/**
 * 시나리오 1 — 소셜 로그인 → 약속 참여 (22건 / 5분 / 목표 654 TPS)
 *
 * 구성 근거는 Notion "부하 테스트 시나리오" 2-3절을 그대로 따른다.
 * 에러 재시도 없음, 필터링 1회, 순수 기본 경로다.
 */

const DAU = Number(__ENV.VUS || 8920)
const START_WINDOW_SECONDS = Number(__ENV.START_WINDOW_SECONDS || 300)

export const options = {
  scenarios: {
    login_to_join: {
      // TPS 산식은 "8,920명이 5분 창 안에서 각자 한 번"을 전제로 한다.
      // ramping-vus는 같은 VU가 플로우를 반복하므로 산식보다 큰 부하를 만든다.
      executor: 'per-vu-iterations',
      vus: DAU,
      iterations: 1,
      maxDuration: __ENV.MAX_DURATION || '10m',
    },
  },
  thresholds: {
    // 에러가 나기 시작하면 그 뒤 수치는 해석할 값이 아니다. 일찍 끊는다.
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<2000'],
  },
}

/**
 * 이 VU가 참여할 약속.
 *
 * 시드가 `CEIL(VUS / 슬롯수)`개의 약속을 만들고 VU를 순서대로 나눠 담는다.
 * 슬롯 수는 `정원 - 1`이다 — `current_member_count`가 방장까지 세기 때문이다.
 * 이 값이 시드의 `@vu_slots_per_appointment`와 어긋나면 뒤쪽 VU가 정원 초과로
 * 실패하거나 빈 약속이 남는다.
 */
function assignedAppointmentId(vu) {
  const slots = Number(__ENV.VU_SLOTS_PER_APPOINTMENT || 5)
  const base = runScopedBase(Number(__ENV.RECRUITING_APPOINTMENT_BASE || 1000000))

  return base + Math.ceil(vu / slots)
}

/** VU 하나가 쓸 회원 번호. 시드가 만든 범위와 맞춰야 한다. */
function memberIdFor(vu) {
  const base = Number(__ENV.MEMBER_ID_BASE || 900000)

  return base + vu
}

export default function () {
  sleep(((__VU - 1) * START_WINDOW_SECONDS) / DAU)

  const jar = new http.CookieJar()
  const memberId = memberIdFor(__VU)

  group('1. 소셜 로그인', () => {
    beginOAuthLogin(jar)
    login(memberId, jar)
    expectOk(http.get(`${BASE_URL}/api/v1/members/me`, { headers: baseHeaders, jar }), 'members/me')
  })

  let tripId = null

  group('2. 여정 등록', () => {
    expectOk(http.get(`${BASE_URL}/api/v1/journeys`, { headers: baseHeaders, jar }), 'journeys 목록')
    // 쿠키에 XSRF-TOKEN 을 심는다. 이후 withCsrf 가 쿠키에서 최신 값을 읽는다.
    issueCsrfHeaders(jar)

    const created = dataOf(
      expectOk(
        http.post(
          `${BASE_URL}/api/v1/journeys`,
          JSON.stringify({
            title: `loadtest-journey-${__VU}-${__ITER}`,
            startDate: '2026-09-01',
            endDate: '2026-09-05',
          }),
          { headers: withCsrf(jar, true), jar },
        ),
        'journeys 생성',
      ),
    )

    tripId = created && created.tripId

    if (tripId) {
      expectOk(http.get(http.url`${BASE_URL}/api/v1/journeys/${tripId}`, { headers: baseHeaders, jar }), 'journey 상세')
      expectOk(http.get(http.url`${BASE_URL}/api/v1/journeys/${tripId}/timeline`, { headers: baseHeaders, jar }), 'timeline')
    }
  })

  group('3. 이벤트 탐색', () => {
    const list = dataOf(
      expectOk(
        http.get(`${BASE_URL}/api/v1/explore/events?page=0&size=20&language=en`, { headers: baseHeaders, jar }),
        'events 목록',
      ),
    )

    // 필터를 한 번 바꾼다. 쿼리 키가 달라져 캐시가 아니라 새 조회가 나간다.
    expectOk(
      http.get(`${BASE_URL}/api/v1/explore/events?page=0&size=20&language=en&sort=POPULAR`, {
        headers: baseHeaders,
        jar,
      }),
      'events 필터 재조회',
    )

    const eventId = list && list.content && list.content.length > 0 ? list.content[0].itemId : null

    if (eventId && tripId) {
      expectOk(http.get(http.url`${BASE_URL}/api/v1/explore/events/${eventId}`, { headers: baseHeaders, jar }), 'event 상세')
      expectOk(
        http.get(http.url`${BASE_URL}/api/v1/journeys/${tripId}/items/exists?itemId=${eventId}&visitDate=2026-09-02`, {
          headers: baseHeaders,
          jar,
        }),
        'items/exists',
      )
      expectOk(
        http.post(
          http.url`${BASE_URL}/api/v1/journeys/${tripId}/items`,
          JSON.stringify({ itemId: eventId, visitDate: '2026-09-02' }),
          { headers: withCsrf(jar, true), jar },
        ),
        'journey items 담기',
      )
    }
  })

  group('4. 지갑 충전', () => {
    expectOk(http.get(`${BASE_URL}/api/v1/topups/methods`, { headers: baseHeaders, jar }), 'topups/methods')
    expectOk(
      http.post(`${BASE_URL}/api/v1/topups/preview`, JSON.stringify({
        amount: 50000,
        method: 'STRIPE_CARD',
        currency: 'KRW',
      }), {
        headers: withCsrf(jar, true),
        jar,
      }),
      'topups/preview',
    )

    const intent = dataOf(
      expectOk(
        http.post(
          `${BASE_URL}/api/v1/topups/stripe/intent`,
          JSON.stringify({ amount: 50000, currency: 'KRW' }),
          {
            headers: {
              ...withCsrf(jar, true),
              'Idempotency-Key': idempotencyKey('topup'),
            },
            jar,
          },
        ),
        'stripe/intent',
      ),
    )

    // stripe.confirmPayment 는 브라우저가 Stripe 서버로 직접 보내는 호출이라
    // 여기서 재현하지 않는다. 상태 조회만 한 번 태워 우리 백엔드 부하를 맞춘다.
    if (intent && intent.topupId) {
      expectOk(
        http.get(http.url`${BASE_URL}/api/v1/topups/stripe/${intent.topupId}`, { headers: baseHeaders, jar }),
        'stripe 상태',
      )
    }
  })

  group('5. 약속 참여', () => {
    const list = dataOf(
      expectOk(
        http.get(`${BASE_URL}/api/v1/appointments?page=0&size=20&status=RECRUITING`, {
          headers: baseHeaders,
          jar,
        }),
        '약속 목록',
      ),
    )

    // 목록에서 첫 항목을 고르지 않는다. 그러면 모든 VU가 같은 약속에 몰려
    // joinAppointment의 FOR UPDATE 락 대기를 측정하게 되고, 정원(6)이 차는
    // 순간부터 나머지는 전부 JOIN_NOT_AVAILABLE로 실패한다.
    // 목록 조회 자체는 실제 사용자 흐름이라 그대로 두고, 참여 대상만 시드가
    // 나눠 준 약속으로 고른다.
    const appointmentId = assignedAppointmentId(__VU)

    if (!list || !list.content) {
      return
    }

    expectOk(http.get(http.url`${BASE_URL}/api/v1/appointments/${appointmentId}`, { headers: baseHeaders, jar }), '약속 상세')
    expectOk(
      http.get(http.url`${BASE_URL}/api/v1/appointments/${appointmentId}/members`, { headers: baseHeaders, jar }),
      '약속 멤버',
    )
    expectOk(
      http.get(http.url`${BASE_URL}/api/v1/appointments/${appointmentId}/members/me`, { headers: baseHeaders, jar }),
      '내 참여 상태',
    )

    // 보증금 예치가 이 트랜잭션 안에서 함께 처리된다. 별도 엔드포인트가 없다.
    expectOk(
      http.post(http.url`${BASE_URL}/api/v1/appointments/${appointmentId}/members`, null, {
        headers: withCsrf(jar),
        jar,
      }),
      '약속 참여',
    )
  })
}
