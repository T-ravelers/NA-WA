import http from 'k6/http'
import { sleep } from 'k6'
import { Counter, Trend } from 'k6/metrics'
import {
  BASE_URL,
  RUN_INDEX,
  dataOf,
  expectOk,
  issueCsrfHeaders,
  login,
  withCsrf,
} from './common.js'

/**
 * 시나리오 3 — 한 약속에 동시 참여 (#570)
 *
 * 시나리오 1과 반대로 모든 VU를 약속 하나에 몬다. `joinAppointment`가 약속 행을
 * `FOR UPDATE`로 잠그므로 요청이 그 행 앞에서 줄을 선다. 재려는 것은 셋이다.
 *
 *   1. 정원 초과 0건 — 몇 명이 몰려도 남은 자리보다 많이 들어가면 안 된다
 *   2. 참여 요청의 응답시간 — 줄 선 시간이 대부분이다
 *   3. 정원 초과 거절(APPOINTMENT-002)을 뺀 오류
 *
 * 로그인·여정 생성은 준비 단계로 먼저 마치고, 모든 VU가 같은 시각에 참여 요청만
 * 보낸다. 준비가 끝나는 대로 보내면 먼저 끝난 VU가 자리를 채워 "동시"가 아니게 된다.
 */

const VUS = Number(__ENV.VUS || 100)
/** 로그인·여정 생성을 마치는 데 주는 시간. 모자라면 focus_late_start가 늘어난다. */
const PREPARE_SECONDS = Number(__ENV.PREPARE_SECONDS || 60)
/**
 * 준비 시작을 나눠 놓는 구간. 준비 시간의 앞쪽 절반이다.
 *
 * 1,000 VU가 0초에 한꺼번에 로그인하면 서버가 연결 일부를 끊어(EOF) 그 VU들이 출발선에
 * 서지 못한다. 재려는 것은 참여 순간의 몰림이지 로그인 순간의 몰림이 아니므로, 준비는
 * 나눠서 하고 참여 요청만 같은 시각에 보낸다. 시나리오 1이 시작을 나눠 두는 것과 같다.
 */
const PREPARE_SPREAD_SECONDS = Number(__ENV.PREPARE_SPREAD_SECONDS || PREPARE_SECONDS / 2)
/** 방장을 뺀 남은 자리. 시드의 `@vu_slots_per_appointment`와 같아야 한다. */
const SLOTS = Number(__ENV.VU_SLOTS_PER_APPOINTMENT || 5)
const EXPECTED_JOINS = Math.min(VUS, SLOTS)

const joinSuccess = new Counter('focus_join_success')
const joinFull = new Counter('focus_join_full')
const joinUnexpected = new Counter('focus_join_unexpected')
const prepareFailed = new Counter('focus_prepare_failed')
const lateStart = new Counter('focus_late_start')
const joinDuration = new Trend('focus_join_duration', true)

export const options = {
  scenarios: {
    one_appointment: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: 1,
      maxDuration: __ENV.MAX_DURATION || '10m',
    },
  },
  thresholds: {
    // 남은 자리만큼 정확히 들어가야 한다. 많으면 정원 초과, 적으면 들어갈 수 있던
    // 요청까지 거절했다는 뜻이다.
    focus_join_success: [`count<=${EXPECTED_JOINS}`, `count>=${EXPECTED_JOINS}`],
    focus_join_unexpected: ['count==0'],
    // 준비가 늦은 VU가 있으면 동시 요청 수가 VUS보다 적어 결과를 그대로 읽을 수 없다.
    focus_prepare_failed: ['count==0'],
    focus_late_start: ['count==0'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
}

/** 이번 회차의 집중 측정용 약속. 시드가 `3000000 + 회차`로 만든다. */
const APPOINTMENT_ID = Number(__ENV.FOCUS_APPOINTMENT_BASE || 3000000) + RUN_INDEX

function memberIdFor(vu) {
  return Number(__ENV.MEMBER_ID_BASE || 900000) + vu
}

function toDateString(date) {
  return date.toISOString().slice(0, 10)
}

/** 약속 활동일(시드 기준 30일 뒤)을 감싸는 여정 기간. 시나리오 1과 같은 계산이다. */
function journeyDateRange() {
  const now = Date.now()
  const day = 24 * 60 * 60 * 1000

  return {
    startDate: toDateString(new Date(now + 29 * day)),
    endDate: toDateString(new Date(now + 31 * day)),
  }
}

/** 모든 VU가 함께 참여 요청을 보낼 시각. setup은 한 번만 돌아 모든 VU가 같은 값을 받는다. */
export function setup() {
  return { startAt: Date.now() + PREPARE_SECONDS * 1000 }
}

function prepare(jar) {
  login(memberIdFor(__VU), jar)
  issueCsrfHeaders(jar)

  const { startDate, endDate } = journeyDateRange()
  const created = dataOf(
    expectOk(
      http.post(
        `${BASE_URL}/api/v1/journeys`,
        JSON.stringify({ title: `loadtest-focus-${__VU}`, startDate, endDate }),
        { headers: withCsrf(jar, true), jar, tags: { name: 'focus-journey' } },
      ),
      'focus 여정 생성',
    ),
  )

  return created && created.tripId ? created.tripId : null
}

function errorCodeOf(response) {
  try {
    const body = response.json()
    return body && body.error && body.error.code ? body.error.code : 'UNKNOWN'
  } catch (error) {
    return 'UNKNOWN'
  }
}

export default function (data) {
  sleep(((__VU - 1) * PREPARE_SPREAD_SECONDS) / VUS)

  const jar = new http.CookieJar()
  let tripId = null
  try {
    tripId = prepare(jar)
  } catch (error) {
    // issueCsrfHeaders가 fail()로 멈추면 iteration이 그대로 끝나 아래에서 세지 못한다.
    // 출발선에 서지 못한 VU는 모두 같은 이름으로 센다.
    console.error(`focus prepare failed: ${error}`)
  }

  if (tripId === null) {
    prepareFailed.add(1)
    return
  }

  const waitMs = data.startAt - Date.now()
  if (waitMs > 0) {
    sleep(waitMs / 1000)
  } else {
    lateStart.add(1)
  }

  const response = http.post(
    http.url`${BASE_URL}/api/v1/appointments/${APPOINTMENT_ID}/members`,
    JSON.stringify({ tripId }),
    { headers: withCsrf(jar, true), jar, tags: { name: 'focus-join' } },
  )
  joinDuration.add(response.timings.duration)

  if (response.status >= 200 && response.status < 300) {
    joinSuccess.add(1)
    return
  }

  const code = errorCodeOf(response)
  if (response.status === 409 && code === 'APPOINTMENT-002') {
    joinFull.add(1)
    return
  }

  joinUnexpected.add(1)
  // 본문·쿠키는 남기지 않는다. 공개 계약인 상태와 오류 코드만 남긴다.
  console.error(`focus join failed: status=${response.status} code=${code}`)
}
