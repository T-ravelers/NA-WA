import http from 'k6/http'
import { sleep } from 'k6'
import { Counter, Trend } from 'k6/metrics'
import { BASE_URL, baseHeaders, dataOf, expectOk, login } from './common.js'

/**
 * 시나리오 4 — 약속 참여자의 공개 프로필 조회 (#582)
 *
 * 약속 참여자 목록에서 프로필을 열면 서버는 그 회원의 출석·후기를 요청마다 집계한다
 * (#173 「조회 시점 집계」). 이 조회가 후기 수에 따라 얼마나 느려지는지를 잰다.
 *
 * 흐름은 화면과 같다. 로그인 → 내 약속 목록(scope=ALL) → 끝난 약속 → 참여자 목록 →
 * 참여자마다 프로필. 앞의 셋은 VU마다 처음 한 번만 하고, 반복마다 참여자 목록과
 * 프로필만 다시 연다. 재는 것은 프로필 요청(`appointment-profile` 태그)이다.
 *
 * 약속 번호를 시드 계약으로 받지 않고 API 응답에서 찾는다(#450). 시드가 VU n을
 * n번째 후기 약속에 넣어 두므로, 이 VU의 COMPLETED 약속이 바로 그 약속이다.
 *
 * 생각 시간(sleep)을 두지 않는다. VU 수가 곧 동시에 처리 중인 요청 수의 상한이다.
 */

const VUS = Number(__ENV.VUS || 10)
const DURATION = __ENV.DURATION || '60s'

const profileDuration = new Trend('profile_duration', true)
const profileUnexpected = new Counter('profile_unexpected')
const profileWithoutReviews = new Counter('profile_without_reviews')
const prepareFailed = new Counter('profile_prepare_failed')

export const options = {
  scenarios: {
    member_profile: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
    },
  },
  thresholds: {
    // 프로필 응답은 모두 200이어야 한다. 403(MEMBER-010)이 나오면 VU가 그 약속의
    // 참여자가 아니라는 뜻이라 시드가 어긋난 것이다.
    profile_unexpected: ['count==0'],
    // VU가 끝난 약속을 찾지 못하면 프로필 요청 없이 반복만 돈다.
    profile_prepare_failed: ['count==0'],
    // 후기가 0건인 회원만 보면 후기 조인을 재지 못한다. 시드의 REVIEWS를 확인한다.
    profile_without_reviews: ['count==0'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
}

function memberIdFor(vu) {
  return Number(__ENV.MEMBER_ID_BASE || 900000) + vu
}

// k6는 VU마다 이 모듈을 따로 실행하므로 아래 값은 VU 하나의 상태다.
let jar = null
let appointmentId = null
let prepared = false
let reviewsChecked = false

function prepare() {
  jar = new http.CookieJar()
  login(memberIdFor(__VU), jar)

  const appointments = dataOf(
    expectOk(
      http.get(`${BASE_URL}/api/v1/appointments/me?scope=ALL`, {
        headers: baseHeaders,
        jar,
        tags: { name: 'profile-my-appointments' },
      }),
      '내 약속 목록',
    ),
  )
  const completed = (appointments || []).find((item) => item.appointmentStatus === 'COMPLETED')

  return completed ? completed.appointmentId : null
}

export default function () {
  if (!prepared) {
    prepared = true
    appointmentId = prepare()
    if (appointmentId === null) {
      prepareFailed.add(1)
      console.error(`profile prepare failed: vu=${__VU} has no completed appointment`)
    }
  }
  if (appointmentId === null) {
    // 실패한 VU가 반복마다 다시 로그인하면 같은 실행의 다른 VU 측정을 흐린다.
    // 쉬면서 기다리기만 하고, 실패는 위에서 한 번만 센다.
    sleep(1)
    return
  }

  const members = dataOf(
    expectOk(
      http.get(http.url`${BASE_URL}/api/v1/appointments/${appointmentId}/members`, {
        headers: baseHeaders,
        jar,
        tags: { name: 'profile-members' },
      }),
      '참여자 목록',
    ),
  )
  const self = memberIdFor(__VU)

  for (const member of members || []) {
    if (member.memberId === self) {
      continue
    }

    const response = http.get(
      http.url`${BASE_URL}/api/v1/members/${member.memberId}/appointment-profile`,
      { headers: baseHeaders, jar, tags: { name: 'appointment-profile' } },
    )
    profileDuration.add(response.timings.duration)

    const profile = dataOf(expectOk(response, 'appointment-profile'))
    if (profile === null) {
      profileUnexpected.add(1)
      continue
    }
    if (!reviewsChecked && !(profile.reviewCount > 0)) {
      profileWithoutReviews.add(1)
    }
  }
  reviewsChecked = true
}
