# 약속 및 후기 API 계약

이 문서는 탐색 Event·Place에서 생성하는 약속의 조회와 회원 후기 API 계약을 정의합니다.
모든 경로는 인증이 필요하며 공통 `ApiResponse` 형식을 사용합니다. ENUM·상태 전이·
구현 여부는 [APPOINTMENT_DEPOSIT_STATE_MACHINE.md](APPOINTMENT_DEPOSIT_STATE_MACHINE.md)
를 따릅니다.

## 공통 정책

- 날짜와 시각은 `yyyy-MM-dd'T'HH:mm:ss` 형식의 문자열을 사용합니다.
- 약속 대상 `itemType`은 `EVENT`, `PLACE` 중 하나입니다.
- 지원 언어는 `en`, `ja`, `zh-TW`, `vi`입니다.
- `maxMembers`는 방장을 포함하여 2명 이상 10명 이하입니다.
- 보증금은 5,000원 이상 50,000원 이하의 정수 금액입니다.
- 약속 생성·참여는 회원 지갑 차감, 시스템 에스크로(`DEPOSIT_POOL`) 입금, 양쪽
  원장 기록을 하나의 트랜잭션으로 처리한 뒤 보증금을 `HELD`, 회원을 `ACTIVE`로
  전환합니다.
- 출석 확정이 성공하면 `DepositPayoutBatch`를 `PENDING`으로 생성하고, 60초 주기
  비동기 배치 처리가 실제 환급·노쇼 분배 지갑 이체를 실행합니다.
- `LEFT`가 된 회원은 같은 약속에 다시 참여할 수 없습니다.

## 약속 상태 전이

상태 전이는 별도 API 없이 서버가 조건에 따라 자동으로 처리합니다. 방장의 확정
버튼 같은 수동 액션은 없습니다.

- `RECRUITING` → `CLOSED`: 정원 도달은 참여 성공 시점에 즉시, 참여 마감 시각
  도달은 60초 주기 스케줄러가 전환합니다.
- `CLOSED` → `IN_PROGRESS`: 활동 시작 시각이 되면 스케줄러가 전환합니다. 방장의
  별도 확정 절차는 없습니다.
- `IN_PROGRESS` → `COMPLETED`: 방장이 모든 `ACTIVE` 회원의 출석을 확정한 경우

목록·상세 조회 응답의 `appointmentStatus`는 스케줄러가 아직 못 따라잡았어도
마감·시작 시각 기준으로 즉시 계산한 값을 보여줍니다. 단, `GET /appointments/me`는
DB에 실제로 반영된 값만 사용하므로 활동 시작 후 최대 60초까지 지연될 수
있습니다. 자세한 내용은
[APPOINTMENT_DEPOSIT_STATE_MACHINE.md](./APPOINTMENT_DEPOSIT_STATE_MACHINE.md)
15절을 참고하세요.

## 약속 목록 조회

`GET /api/v1/appointments`

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | ---: | --- |
| `itemId` | number | N | 탐색 항목 ID |
| `itemType` | string | N | `EVENT`, `PLACE` |
| `language` | string | N | 약속 언어 |
| `keyword` | string | N | 약속명·설명·만남 장소 검색어 |
| `status` | string | N | 약속 상태 |
| `page` | number | N | 0부터 시작, 기본값 0 |
| `size` | number | N | 기본값 20, 최대 100 |

`PAYMENT_PENDING` 약속은 공개 목록에서 제외합니다. 응답은 `content`, `page`,
`size`, `totalElements`, `totalPages`, `hasNext`를 포함합니다.

## 약속 상세 및 회원 조회

- `GET /api/v1/appointments/{appointmentId}`
- `GET /api/v1/appointments/{appointmentId}/members`
- `GET /api/v1/appointments/{appointmentId}/members/me`

상세 응답에는 약속 정보와 현재 `ACTIVE`인 회원 목록이 포함됩니다. 회원 목록 API도
같은 활성 회원 계약을 사용합니다. `PAYMENT_PENDING`, `CANCELLED` 약속은 방장만 상세
조회할 수 있으며 다른 회원에게는 `APPOINTMENT-001`을 반환합니다.
`members/me`는 현재 로그인 회원의 참여 여부, 참여·출석 상태와 방장 여부를 반환합니다.

## 내가 참여 중인 약속 조회

`GET /api/v1/appointments/me?scope=ONGOING`

로그인 회원이 `ACTIVE`로 참여 중이고 여행(trip)이 연결된 약속을 배열로
반환합니다. 참여만 하고 여행에 연결되지 않은 약속은 QR 공동 소비(SHARED) 결제가
트립 비용 연결(`trip_expense_links`)을 만들 수 없어 제외합니다.

`scope`가 범위와 정렬을 정합니다. 그 밖의 값은 `COMMON-001`을 반환합니다.

| scope | 범위 | 정렬 |
| --- | --- | --- |
| `ONGOING`(기본) | `IN_PROGRESS` 약속만 | `activityStartAt` 오름차순 — QR 공동 소비 결제가 쓰는 기존 계약 그대로 |
| `ALL` | `CANCELLED`를 제외한 전체 | 예정 약속을 임박한 순으로 먼저, 지난 약속을 최근 순으로 뒤에 — 프로필의 약속 목록이 사용 |

`ALL`은 `PAYMENT_PENDING`을 포함합니다. `PAYMENT_PENDING`에 한해 상세 조회의
호스트 노출 기준을 따릅니다 — 보증금 결제 전에는 모집이 열리지 않아 호스트 외에는
`ACTIVE` 멤버십이 생길 수 없고, 본인 목록에서는 결제를 마치도록 보이는 편이 맞습니다.
`CANCELLED`는 상세 조회가 호스트 본인에게는 보여주는 것과 달리, 프로필 목록에 노출할
근거가 없어 호스트에게도 제외합니다. 공개 약속 목록(`GET /api/v1/appointments`)이
`PAYMENT_PENDING`을 제외하는 것과 근거가 다르니 혼동하지 마세요.

```json
[
  {
    "appointmentId": 42,
    "appointmentName": "Seongsu K-Beauty Tour",
    "tripId": 9,
    "meetingPlace": "Olive Young N Seongsu",
    "activityStartAt": "2026-08-21T18:30:00",
    "activityEndAt": "2026-08-21T22:00:00",
    "itemId": 100,
    "itemType": "EVENT",
    "appointmentStatus": "IN_PROGRESS"
  }
]
```

`itemType`은 `EVENT` 또는 `PLACE`이며 화면의 탭 구분에 사용합니다.

이 엔드포인트는 스케줄러가 실제로 반영한 DB의 `appointment_status` 값만
사용합니다(목록·상세 조회와 달리 즉시 계산한 값을 쓰지 않습니다 — 근거는
[APPOINTMENT_DEPOSIT_STATE_MACHINE.md](./APPOINTMENT_DEPOSIT_STATE_MACHINE.md)
15절). 그래서 활동 시작 시각이 지나도 스케줄러가 아직 상태를 못 바꿨다면,
`scope=ONGOING`에서는 해당 약속이 최대 60초까지 배열에서 빠질 수 있고
`scope=ALL`에서는 `appointmentStatus`가 그만큼 늦게 반영될 수 있습니다.

## 약속 생성

`POST /api/v1/appointments`

```json
{
  "itemId": 100,
  "itemType": "EVENT",
  "languageCode": "en",
  "appointmentName": "Seongsu K-Beauty Tour",
  "maxMembers": 5,
  "joinDeadline": "2026-08-20T18:00:00",
  "depositAmount": 10000,
  "meetingPlace": "Olive Young N Seongsu",
  "meetingAddress": "Seongdong-gu, Seoul",
  "activityStartAt": "2026-08-21T18:30:00",
  "activityEndAt": "2026-08-21T22:00:00"
}
```

참여 마감은 활동 시작 시각보다 늦을 수 없으며, 활동 시작 시각은 종료 시각보다
빨라야 합니다. 성공하면 방장의 보증금을 즉시 예치(`DEPOSIT_HOLD`)하고 약속을
`RECRUITING` 상태로 생성합니다.

## 참여 요청과 취소

- `POST /api/v1/appointments/{appointmentId}/members`
- `DELETE /api/v1/appointments/{appointmentId}/members/me`

참여 요청은 `RECRUITING` 상태, 참여 마감 전, 정원 미달 조건을 모두 만족해야
하며, 성공하면 참여자의 보증금을 즉시 예치(`DEPOSIT_HOLD`)하고 `ACTIVE`로
전환합니다.

참여 성공 시 보증금 예치와 `ACTIVE` 전환이 같은 트랜잭션에서 함께 끝나므로,
방장이 아닌 회원의 참여 취소는 실질적으로 `ACTIVE` 상태에서 일어납니다. 이
경우 `HELD` 보증금을 환급(`DEPOSIT_REFUND`)하고 참가 상태를 `LEFT`로 변경하는
걸 같은 트랜잭션에서 처리합니다. 결제 전(`PENDING`) 취소 경로도 남아있지만,
참여 성공 시 `PENDING`으로 남는 경우가 없어 참여가 실패해 트랜잭션이 롤백된
경우를 빼면 실질적으로 도달하지 않습니다.

방장은 참여 취소 API로 약속에서 빠질 수 없습니다. 상태와 무관하게 항상
`APPOINTMENT-007`을 반환합니다. 방장이 아닌 회원의 참여 취소는 참여 마감
시각(`joinDeadline`) 전까지만 가능하며, 지난 뒤에는 `APPOINTMENT-007`을
반환합니다. 자세한 근거는 [APPOINTMENT_DEPOSIT_STATE_MACHINE.md](./APPOINTMENT_DEPOSIT_STATE_MACHINE.md)
12절을 참고하세요.

## 출석 확정

`PATCH /api/v1/appointments/{appointmentId}/attendance`

```json
{
  "members": [
    { "memberId": 1, "attendanceStatus": "ATTENDED" },
    { "memberId": 2, "attendanceStatus": "NO_SHOW" }
  ]
}
```

- 방장만 `IN_PROGRESS` 상태에서 모든 `ACTIVE` 회원을 정확히 한 번씩 `ATTENDED`
  또는 `NO_SHOW`로 확정할 수 있습니다. 방장이 아니면 `APPOINTMENT-004`, 약속이
  `IN_PROGRESS`가 아니거나 출석자가 한 명도 없거나 요청이 활성 회원 전원을
  정확히 한 번씩 포함하지 않으면 `APPOINTMENT-006`을 반환합니다.
- 성공하면 같은 트랜잭션에서 각 회원의 출석 상태를 반영하고 약속을 `COMPLETED`로
  전환한 뒤, 보증금 정산 배치(`DepositPayoutBatch`)를 `PENDING`으로 생성합니다.
  이 시점에는 아직 지갑 이체가 일어나지 않습니다 — 실제 환급(출석 회원 본인
  보증금)·노쇼 분배(노쇼 회원 보증금을 출석 회원에게 균등 분배)는 60초 주기
  비동기 배치 처리가 이어서 실행합니다. 자세한 내용은
  [APPOINTMENT_DEPOSIT_STATE_MACHINE.md](./APPOINTMENT_DEPOSIT_STATE_MACHINE.md)
  16절을 참고하세요.

## 회원 후기 등록

`POST /api/v1/appointments/{appointmentId}/reviews`

```json
{
  "reviewedAppointmentMemberId": 30,
  "scores": {
    "PUNCTUALITY": 5,
    "MANNERS": 4,
    "COMMUNICATION": 5
  },
  "keywordCodes": ["FRIENDLY", "ON_TIME"]
}
```

- 기존 `COMPLETED` 약속에서만 작성할 수 있습니다.
- 작성자와 평가 대상 모두 `ACTIVE`, `ATTENDED` 회원이어야 합니다.
- 자기 자신과 이미 평가한 회원을 다시 평가할 수 없습니다.
- `PUNCTUALITY`, `MANNERS`, `COMMUNICATION` 점수를 모두 입력하며 각 점수는
  1~5의 정수입니다.
- 키워드는 0~5개이며 중복 없이 `FRIENDLY`, `ON_TIME`, `CONSIDERATE`,
  `GOOD_COMMUNICATOR`, `WOULD_JOIN_AGAIN` 중에서 선택합니다.
- 성공하면 `201 Created`를 반환합니다.
