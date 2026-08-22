import http from 'k6/http'
import { group, sleep } from 'k6'
import {
  BASE_URL,
  baseHeaders,
  dataOf,
  expectOk,
  idempotencyKey,
  issueCsrfHeaders,
  login,
  runScopedBase,
  withCsrf,
} from './common.js'

/**
 * 시나리오 2 — QR 결제 → 리포트 (업무 API 20건 / 5분 / 목표 595 TPS)
 *
 * 이 시나리오가 시나리오 1보다 까다로운 이유는 단계마다 특정 상태의 데이터를
 * 전제로 하기 때문이다. Notion "부하 테스트 시나리오" 2-3절 끝에 정리한 네 가지
 * 제약 중 셋이 여기에 걸린다.
 *
 *   - QR 토큰은 남이 만든 것이어야 한다(자기 QR은 결제 불가) + 60초 만료
 *   - 정산 후보가 되려면 공동지출(shared) 범위로 약속을 지정해 결제해야 한다
 *   - 출석 확정은 방장만, IN_PROGRESS 상태에서, 활동이 끝난 뒤에만 된다
 *
 * 마지막 것은 런타임에 만들 수 없어 시드가 미리 넣어 둬야 한다.
 * 별도 파일로 실행하므로 결제자·QR 수취인·정산 참여자 세 세션의 test-login과
 * CSRF 발급 6건이 측정 트래픽에 추가된다. README의 해석 주의를 함께 본다.
 */

const DAU = Number(__ENV.VUS || 8920)
const START_WINDOW_SECONDS = Number(__ENV.START_WINDOW_SECONDS || 300)

export const options = {
  scenarios: {
    qr_to_report: {
      executor: 'per-vu-iterations',
      vus: DAU,
      iterations: 1,
      maxDuration: __ENV.MAX_DURATION || '10m',
    },
  },
  thresholds: {
    // abortOnFail 없이 threshold만 적으면 k6는 끝날 때 실패로만 표시하고
    // 실행은 끝까지 계속한다. delayAbortEval은 초반 표본 몇 개로 즉시
    // 끊기지 않게 30초 유예를 준다.
    checks: [{ threshold: 'rate>0.99', abortOnFail: true, delayAbortEval: '30s' }],
    http_req_failed: [{ threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: '30s' }],
    http_req_duration: ['p(95)<2000'],
  },
}

function memberIdFor(vu) {
  return Number(__ENV.MEMBER_ID_BASE || 900000) + vu
}

/**
 * QR을 받아 줄 상대.
 *
 * 자기 QR은 결제할 수 없어서(`QR_SELF_PAYMENT_NOT_ALLOWED`) 별도 계정이 필요하다.
 * 시드가 만든 수취인 풀에서 VU마다 하나씩 고른다.
 */
function payeeIdFor(vu) {
  const base = Number(__ENV.PAYEE_ID_BASE || 950000)
  const size = Number(__ENV.PAYEE_POOL_SIZE || 100)

  return base + (vu % size)
}

/** 정산 생성자가 아닌 참여자가 정산금을 내야 한다. 시드 번호 범위와 맞춘다. */
function settlementParticipantIdFor(vu) {
  return Number(__ENV.SETTLEMENT_PARTICIPANT_MEMBER_ID_BASE || 970000) + vu
}

export default function () {
  sleep(((__VU - 1) * START_WINDOW_SECONDS) / DAU)

  const jar = new http.CookieJar()
  const memberId = memberIdFor(__VU)

  login(memberId, jar)
  // 쿠키에 XSRF-TOKEN 을 심는다. 이후 withCsrf 가 쿠키에서 최신 값을 읽는다.
  issueCsrfHeaders(jar)

  const payeeJar = new http.CookieJar()
  login(payeeIdFor(__VU), payeeJar)
  issueCsrfHeaders(payeeJar)

  let appointmentId = null
  let transferId = null

  group('1. QR 결제', () => {
    // 수취인이 QR을 만든다. 60초 만료라 미리 만들어 둘 수 없어 매번 새로 받는다.
    const qr = dataOf(
      expectOk(
        http.post(
          `${BASE_URL}/api/v1/wallet/qr/create`,
          JSON.stringify({ amount: 9000, memo: 'loadtest' }),
          { headers: withCsrf(payeeJar, true), jar: payeeJar },
        ),
        'qr/create',
      ),
    )

    expectOk(http.get(`${BASE_URL}/api/v1/wallet`, { headers: baseHeaders, jar }), 'wallet')

    // 공동지출로 결제해야 정산 후보가 생긴다. 대상 약속을 고르는 조회가 따라붙는다.
    const ongoing = dataOf(
      expectOk(http.get(`${BASE_URL}/api/v1/appointments/me`, { headers: baseHeaders, jar }), 'appointments/me'),
    )

    appointmentId =
      ongoing && ongoing.length > 0 ? ongoing[0].appointmentId : null

    if (!qr || !qr.qrToken || !appointmentId) {
      return
    }

    expectOk(
      http.post(
        `${BASE_URL}/api/v1/wallet/qr/resolve`,
        JSON.stringify({ qrToken: qr.qrToken }),
        { headers: withCsrf(jar, true), jar },
      ),
      'qr/resolve',
    )

    const previewBody = JSON.stringify({
      qrToken: qr.qrToken,
      amount: 9000,
      spendingScope: 'SHARED',
      appointmentId,
    })
    const executeBody = JSON.stringify({
      qrToken: qr.qrToken,
      amount: 9000,
      spendingScope: 'SHARED',
      appointmentId,
      spendingCategory: 'FOOD',
    })

    expectOk(
      http.post(`${BASE_URL}/api/v1/wallet/qr/payment/preview`, previewBody, {
        headers: withCsrf(jar, true),
        jar,
      }),
      'payment/preview',
    )

    const executed = dataOf(
      expectOk(
        http.post(`${BASE_URL}/api/v1/wallet/qr/payment/execute`, executeBody, {
          // VU·iteration·실행을 섞은 키. 겹치면 서버가 실제 결제 대신
          // 멱등 응답을 돌려줘서 결제 성능을 재지 못한다.
          headers: {
            ...withCsrf(jar, true),
            'Idempotency-Key': idempotencyKey('qr-pay'),
          },
          jar,
        }),
        'payment/execute',
      ),
    )

    transferId = executed && executed.transferId

    if (transferId) {
      expectOk(
        http.get(http.url`${BASE_URL}/api/v1/wallet/qr/payment/${transferId}`, { headers: baseHeaders, jar }),
        '결제 상세',
      )
    }
  })

  group('2. 보증금 환급 (출석 체크)', () => {
    // 시드가 넣어 둔, 이 VU가 방장이고 이미 끝난 약속.
    const hostedId =
      runScopedBase(Number(__ENV.HOSTED_APPOINTMENT_BASE || 2000000)) + __VU

    expectOk(http.get(http.url`${BASE_URL}/api/v1/appointments/${hostedId}`, { headers: baseHeaders, jar }), '방장 약속 상세')

    const members = dataOf(
      expectOk(
        http.get(http.url`${BASE_URL}/api/v1/appointments/${hostedId}/members`, { headers: baseHeaders, jar }),
        '방장 약속 멤버',
      ),
    )

    expectOk(
      http.get(http.url`${BASE_URL}/api/v1/members/${memberId}/appointment-profile`, { headers: baseHeaders, jar }),
      'appointment-profile',
    )

    if (!members || members.length === 0) {
      return
    }

    expectOk(
      http.patch(
        http.url`${BASE_URL}/api/v1/appointments/${hostedId}/attendance`,
        JSON.stringify({
          members: members.map((m) => ({
            memberId: m.memberId,
            attendanceStatus: 'ATTENDED',
          })),
        }),
        { headers: withCsrf(jar, true), jar },
      ),
      '출석 확정',
    )
  })

  group('3. 거래 내역 조회', () => {
    const list = dataOf(
      expectOk(http.get(`${BASE_URL}/api/v1/me/transactions?size=20`, { headers: baseHeaders, jar }), '거래 목록'),
    )

    const txId =
      list && list.transactions && list.transactions.length > 0
        ? list.transactions[0].transferId
        : transferId

    if (txId) {
      expectOk(http.get(http.url`${BASE_URL}/api/v1/me/transactions/${txId}`, { headers: baseHeaders, jar }), '거래 상세')
    }
  })

  let settlementId = null

  group('4. 정산 요청 생성', () => {
    const candidates = dataOf(
      expectOk(http.get(`${BASE_URL}/api/v1/settlements/candidates`, { headers: baseHeaders, jar }), '정산 후보'),
    )

    const candidate = candidates && candidates.length > 0 ? candidates[0] : null
    const participantIds = candidate && candidate.participants
      ? candidate.participants.map((participant) => participant.id)
      : []

    if (!candidate || participantIds.length < 2) {
      return
    }

    const created = dataOf(
      expectOk(
        http.post(
          http.url`${BASE_URL}/api/v1/appointments/${candidate.appointmentId}/settlements`,
          JSON.stringify({
            sourceTransferId: candidate.transferId,
            type: 'EQUAL',
            participantAppointmentMemberIds: participantIds,
          }),
          {
            headers: {
              ...withCsrf(jar, true),
              'Idempotency-Key': idempotencyKey('settlement-create'),
            },
            jar,
          },
        ),
        '정산 생성',
      ),
    )

    settlementId = created && created.id
  })

  group('5. 정산 완료', () => {
    const participantJar = new http.CookieJar()
    login(settlementParticipantIdFor(__VU), participantJar)
    issueCsrfHeaders(participantJar)

    const list = dataOf(
      expectOk(
        http.get(`${BASE_URL}/api/v1/settlements`, {
          headers: baseHeaders,
          jar: participantJar,
        }),
        '정산 목록',
      ),
    )

    const targetId =
      settlementId ||
      (list && list.received && list.received.length > 0 ? list.received[0].id : null)

    if (!targetId) {
      return
    }

    expectOk(
      http.get(http.url`${BASE_URL}/api/v1/settlements/${targetId}`, {
        headers: baseHeaders,
        jar: participantJar,
      }),
      '정산 상세',
    )
    expectOk(
      http.post(http.url`${BASE_URL}/api/v1/settlements/${targetId}/members/me/pay`, null, {
        headers: {
          ...withCsrf(participantJar),
          'Idempotency-Key': idempotencyKey('settlement-pay'),
        },
        jar: participantJar,
      }),
      '정산 결제',
    )
  })

  group('6. 리포트 조회', () => {
    const reports = dataOf(
      expectOk(http.get(`${BASE_URL}/api/v1/reports`, { headers: baseHeaders, jar }), '리포트 목록'),
    )

    const reportId = reports && reports.length > 0 ? reports[0].reportId : null

    if (reportId) {
      expectOk(http.get(http.url`${BASE_URL}/api/v1/reports/${reportId}`, { headers: baseHeaders, jar }), '리포트 상세')
    }
  })
}
